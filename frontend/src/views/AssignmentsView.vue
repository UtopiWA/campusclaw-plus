<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PagedList from '../components/PagedList.vue'
import { getQuestion, listQuestions, saveQuestion, submitQuestion, getQuestionResponse, deleteQuestion, setQuestionAvailability } from '../services/questions.js'
import { renderAssistantMarkdown } from '../utils/assistantMarkdown.js'
import AppShell from '../components/AppShell.vue'
import AppIcon from '../components/AppIcon.vue'
import BaseModal from '../components/BaseModal.vue'
import { authState } from '../services/auth.js'
import { listMaterials } from '../services/agents.js'
import { showFeedback } from '../services/feedback.js'
import {
  createAssignment, getAssignment, getGradingTask, listAssignments,
  listTeacherSubmissions, reviewSubmission, submitAssignment,
} from '../services/assignments.js'

const props = defineProps({ questionBank: Boolean })
const route = useRoute()
const router = useRouter()
const basePath = computed(() => props.questionBank ? '/questions' : '/assignments')
const detailId = computed(() => route.params.id ? Number(route.params.id) : null)
const title = computed(() => props.questionBank ? '题库' : isTeacher.value ? '作业与批改' : '我的作业')
const keyword = ref(String(route.query.q || ''))
const tagFilter = ref(String(route.query.tag || ''))
const stateFilter = ref(String(route.query.state || ''))
const listPage = computed(() => Math.max(1, Number(route.query.page) || 1))
const pageSize = computed(() => [10, 20, 50].includes(Number(route.query.size)) ? Number(route.query.size) : 10)
const selectedTags = computed(() => [...new Map(assignments.value.flatMap((item) => item.tags || []).map((tag) => [tag.id, tag])).values()])
const filtered = computed(() => assignments.value.filter((item) => (!keyword.value.trim() || `${item.title} ${item.prompt}`.toLocaleLowerCase().includes(keyword.value.trim().toLocaleLowerCase())) && (!tagFilter.value || item.tags?.some((tag) => String(tag.id) === tagFilter.value)) && (!stateFilter.value || (isTeacher.value && props.questionBank ? (item.enabled ? 'enabled' : 'disabled') : item.myLatest?.status || 'NOT_SUBMITTED') === stateFilter.value)))
const isTeacher = computed(() => authState.user?.role === 'teacher')
const redo = ref(false)
const submissionKey = ref(null)
const submissionDraft = ref(null)
const deleteOpen = ref(false)
const deleteBusy = ref(false)
const referenceQuery = ref('')
const assignments = ref([])
const selected = ref(null)
const loading = ref(true)
const loadFailed = ref(false)
const submissions = ref([])
const submissionsLoading = ref(false)
const submitting = ref(false)
const submitContent = ref('')
const task = ref(null)
const submitted = computed(() => !!selected.value?.myLatest || !!task.value)
const effectiveReview = computed(() => task.value?.teacherReview || selected.value?.myLatest?.teacherReview)
const createOpen = ref(false)
const createBusy = ref(false)
const materials = ref([])
const form = ref({ title: '', prompt: '', maxScore: 100, rubricMaterialIds: [] })
const reviewTarget = ref(null)
const reviewBusy = ref(false)
const reviewForm = ref({ score: '', comment: '' })
let disposed = false
let selectionVersion = 0
let pollTimer = null
let reviewTimer = null
let failures = 0
const requests = new Set()

const terminal = (status) => ['SUCCEEDED', 'FAILED'].includes(status)
const statusLabel = (status) => ({ PENDING: '待批改', RUNNING: '批改中', SUCCEEDED: '已批改', FAILED: '待教师处理', NOT_SUBMITTED: props.questionBank ? '未作答' : '未提交' })[status] || '待提交'
const errorMessage = (exception) => exception?.message || '操作失败，请稍后重试'
const notifyError = (exception) => showFeedback(errorMessage(exception))
const finalScore = computed(() => effectiveReview.value?.score ?? task.value?.result?.score)
const resultTotal = computed(() => task.value?.result?.maxScore ?? selected.value?.maxScore)
const finalComment = computed(() => effectiveReview.value?.comment ?? task.value?.result?.rationale)
const rubricFiles = computed(() => materials.value.filter((item) => item.indexStatus === 'READY' && (item.hasFile || item.originalFilename) && item.title.includes(referenceQuery.value.trim())))

function listState(item) {
  if (isTeacher.value) return props.questionBank ? item.enabled ? '已启用' : '已停用' : '已发布'
  if (Number.isFinite(item.myLatest?.score)) return props.questionBank && item.myLatest.maxScore ? `${item.myLatest.score} / ${item.myLatest.maxScore} 分` : `${item.myLatest.score} 分`
  return statusLabel(item.myLatest?.status || 'NOT_SUBMITTED')
}
function stopPolling() { clearTimeout(pollTimer); pollTimer = null }
function cancelSelection() {
  selectionVersion += 1
  stopPolling()
  failures = 0
  for (const controller of requests) controller.abort()
  requests.clear()
}
function controller() { const value = new AbortController(); requests.add(value); return value }
function current(version, request) { return !disposed && version === selectionVersion && !request.signal.aborted }
function mergeTask(response) {
  const previous = task.value?.taskId === response.taskId ? task.value.teacherReview : null
  task.value = { ...response, teacherReview: response.teacherReview || previous || selected.value?.myLatest?.teacherReview }
}
async function loadSubmissions() {
  if (!selected.value) return
  const version = selectionVersion
  const request = controller()
  submissionsLoading.value = true
  try {
    const items = await listTeacherSubmissions(selected.value.id, { signal: request.signal })
    if (current(version, request)) submissions.value = items
  } catch (exception) { if (current(version, request)) notifyError(exception) }
  finally { requests.delete(request); if (current(version, request)) submissionsLoading.value = false }
}
function schedulePoll() {
  stopPolling()
  if (!disposed && task.value && !terminal(task.value.status)) {
    pollTimer = setTimeout(readTask, Math.min(10000, 2000 * 1.4 ** failures))
  }
}
async function readTask() {
  pollTimer = null
  const taskId = task.value?.taskId || selected.value?.myLatest?.taskId
  if (!taskId) return
  const version = selectionVersion
  const request = controller()
  try {
    const response = await (props.questionBank ? getQuestionResponse(detailId.value, { signal: request.signal }) : getGradingTask(taskId, { signal: request.signal }))
    if (!current(version, request)) return
    mergeTask(response)
    failures = 0
    const item = selected.value
    if (item) item.myLatest = {
      ...item.myLatest, taskId, status: response.status, submissionId: response.submissionId,
      score: response.teacherReview?.score ?? response.result?.score,
      teacherReview: response.teacherReview || item.myLatest?.teacherReview,
    }
    if (terminal(response.status)) {
      stopPolling()
      if (response.status === 'FAILED' && !response.teacherReview) showFeedback(props.questionBank ? '批改未能完成，可以重新作答' : '批改未能完成，请等待教师批阅')
    } else schedulePoll()
  } catch {
    if (!current(version, request)) return
    failures += 1
    if (failures >= 8) showFeedback('结果查询失败，请点击刷新重试')
    else schedulePoll()
  } finally { requests.delete(request) }
}
function updateQuery(changes) {
  // 路由更新是异步的；分页收敛时也带上当前筛选，避免覆盖尚未提交到路由的输入。
  const query = { ...route.query, q: keyword.value, tag: tagFilter.value, state: stateFilter.value, ...changes }
  for (const key of Object.keys(query)) if (query[key] === '' || query[key] == null || (key === 'page' && Number(query[key]) === 1)) delete query[key]
  router.replace({ path: route.path, query })
}
watch([keyword, tagFilter, stateFilter], () => {
  if (keyword.value === String(route.query.q || '') && tagFilter.value === String(route.query.tag || '') && stateFilter.value === String(route.query.state || '')) return
  updateQuery({ page: null })
})
watch(() => route.query, (query) => { keyword.value = String(query.q || ''); tagFilter.value = String(query.tag || ''); stateFilter.value = String(query.state || '') })
function openItem(item) { router.push({ path: `${basePath.value}/${item.id}`, query: route.query }) }
async function loadAssignments() {
  cancelSelection()
  const version = selectionVersion
  const request = controller()
  loading.value = true
  loadFailed.value = false
  selected.value = null
  task.value = null
  submitContent.value = ''
  redo.value = false
  submissionKey.value = null
  submissions.value = []
  try {
    if (detailId.value) {
      const item = await (props.questionBank ? getQuestion(detailId.value, { signal: request.signal }) : getAssignment(detailId.value, { signal: request.signal }))
      if (!current(version, request)) return
      selected.value = item
      if (isTeacher.value && !props.questionBank) await loadSubmissions()
      else if (!isTeacher.value && item.myLatest?.taskId) {
        if (props.questionBank) { task.value = item.myLatest; schedulePoll() }
        else await readTask()
      }
    } else {
      const items = await (props.questionBank ? listQuestions({ signal: request.signal }) : listAssignments())
      if (current(version, request)) assignments.value = items
    }
  } catch (exception) { if (current(version, request)) { loadFailed.value = true; notifyError(exception) } }
  finally { requests.delete(request); if (current(version, request)) loading.value = false }
}
async function openCreate() {
  referenceQuery.value = ''
  form.value = { title: '', prompt: '', maxScore: 100, rubricMaterialIds: [], enabled: true }
  createOpen.value = true
  try { const items = await listMaterials(); if (!disposed) materials.value = items }
  catch (exception) { if (!disposed) notifyError(exception) }
}
async function openEdit() {
  referenceQuery.value = ''
  form.value = { id: selected.value.id, title: selected.value.title, prompt: selected.value.prompt, maxScore: selected.value.maxScore, rubricMaterialIds: (selected.value.rubricSources || []).map((source) => source.id), enabled: selected.value.enabled }
  createOpen.value = true
  try { const items = await listMaterials(); if (!disposed) materials.value = items }
  catch (exception) { if (!disposed) notifyError(exception) }
}
async function removeQuestion() {
  if (deleteBusy.value) return
  deleteBusy.value = true
  try { await deleteQuestion(selected.value.id); if (!disposed) { deleteOpen.value = false; showFeedback('题目已删除', 'success'); await router.push({ path: basePath.value, query: route.query }) } }
  catch (exception) { if (!disposed) notifyError(exception) }
  finally { deleteBusy.value = false }
}
async function toggleQuestion() {
  try { await setQuestionAvailability(selected.value.id, !selected.value.enabled); if (!disposed) { showFeedback(selected.value.enabled ? '题目已停用' : '题目已启用', 'success'); await loadAssignments() } }
  catch (exception) { if (!disposed) notifyError(exception) }
}
async function saveAssignment() {
  if (createBusy.value) return
  const payload = { title: form.value.title.trim(), prompt: form.value.prompt.trim(), maxScore: Number(form.value.maxScore), rubricMaterialIds: [...form.value.rubricMaterialIds], ...(props.questionBank ? { enabled: form.value.enabled } : {}) }
  if (!payload.title || !payload.prompt) return showFeedback('请填写作业标题和详细题目')
  if (!Number.isInteger(payload.maxScore) || payload.maxScore < 1 || payload.maxScore > 100) return showFeedback('总分应为 1～100 的整数')
  if (!payload.rubricMaterialIds.length || payload.rubricMaterialIds.length > 5) return showFeedback('请选择 1～5 个参考答案文件')
  createBusy.value = true
  try {
    const item = await (props.questionBank ? saveQuestion(form.value.id, payload) : createAssignment(payload))
    if (disposed) return
    createOpen.value = false
    showFeedback(props.questionBank ? '题目已保存' : '作业已创建', 'success')
    if (detailId.value === item.id) await loadAssignments()
    else await router.push({ path: `${basePath.value}/${item.id}`, query: route.query })
  } catch (exception) { if (!disposed) notifyError(exception) }
  finally { createBusy.value = false }
}
async function submit() {
  if (submitting.value || (submitted.value && !redo.value) || !selected.value) return
  const content = submitContent.value.trim()
  if (!content || [...content].length > 10000) return showFeedback('请输入 1～10000 字的作答')
  if (submissionDraft.value !== content) { submissionKey.value = crypto.randomUUID(); submissionDraft.value = content }
  const assignmentId = selected.value.id
  const version = selectionVersion
  submitting.value = true
  try {
    const accepted = await (props.questionBank ? submitQuestion(assignmentId, content, submissionKey.value ||= crypto.randomUUID(), selected.value.contentVersion) : submitAssignment(assignmentId, content, submissionKey.value ||= crypto.randomUUID()))
    if (disposed || version !== selectionVersion) return
    selected.value.myLatest = { ...accepted }
    if (props.questionBank) { selected.value.questionChanged = false; selected.value.submittedPrompt = selected.value.prompt }
    task.value = { ...accepted, submissionContent: content }
    submitContent.value = ''
    redo.value = false
    submissionKey.value = null
    submissionDraft.value = null
    showFeedback('作答已提交', 'success')
    schedulePoll()
  } catch (exception) { if (!disposed && version === selectionVersion) notifyError(exception) }
  finally { if (!disposed) submitting.value = false }
}
function openReview(item) {
  reviewTarget.value = item
  reviewForm.value = { score: item.teacherReview?.score ?? item.aiResult?.score ?? '', comment: item.teacherReview?.comment || '' }
}
async function saveReview() {
  if (reviewBusy.value || !reviewTarget.value) return
  const score = reviewForm.value.score === '' ? NaN : Number(reviewForm.value.score)
  const comment = reviewForm.value.comment.trim()
  if (!Number.isInteger(score) || score < 0 || score > selected.value.maxScore) return showFeedback(`终评分数应在 0～${selected.value.maxScore} 之间`)
  if (!comment || [...comment].length > 2000) return showFeedback('请填写 1～2000 字的终评评语')
  reviewBusy.value = true
  const id = selected.value.id
  const version = selectionVersion
  try {
    await reviewSubmission(id, reviewTarget.value.submissionId, { score, comment })
    if (disposed || version !== selectionVersion) return
    reviewTarget.value = null
    showFeedback('终评已保存', 'success')
    await loadSubmissions()
  } catch (exception) { if (!disposed) notifyError(exception) }
  finally { reviewBusy.value = false }
}
async function refresh() { if (isTeacher.value) await loadSubmissions(); else await readTask() }
// 批改任务终态后停止轮询，仅低频读取作业摘要，接收教师之后给出的终评。
async function refreshReview() {
  if (disposed) return
  if (!props.questionBank && !isTeacher.value && selected.value?.myLatest && terminal(task.value?.status) && !effectiveReview.value && document.visibilityState !== 'hidden') {
    const version = selectionVersion
    const request = controller()
    try {
      const response = await getAssignment(selected.value.id, { signal: request.signal })
      if (current(version, request)) {
        selected.value.myLatest = response.myLatest
        if (task.value && response.myLatest?.teacherReview) task.value.teacherReview = response.myLatest.teacherReview
      }
    } catch { /* 后台摘要刷新失败不干扰正在阅读的结果，用户仍可主动刷新。 */ }
    finally { requests.delete(request) }
  }
  if (!disposed) reviewTimer = setTimeout(refreshReview, 15000)
}
watch(detailId, loadAssignments)
function openRouteCreate() {
  if (route.query.action !== 'create') return
  if (isTeacher.value && !detailId.value) openCreate()
  const query = { ...route.query }
  delete query.action
  router.replace({ path: route.path, query })
}
watch(() => route.query.action, openRouteCreate)
onMounted(() => { loadAssignments(); openRouteCreate(); reviewTimer = setTimeout(refreshReview, 15000) })
onBeforeUnmount(() => { disposed = true; cancelSelection(); clearTimeout(reviewTimer) })
</script>

<template>
  <AppShell :current="questionBank ? 'questions' : 'assignments'">
    <div class="page-container assignment-page">
      <header class="page-heading"><div><p class="breadcrumb"><RouterLink v-if="detailId" :to="{ path: basePath, query: route.query }">{{ title }}</RouterLink><span v-else>学习空间</span><span>/</span>{{ detailId ? '详情' : title }}</p><h1>{{ title }}</h1><p>{{ questionBank ? isTeacher ? '管理练习题目与参考答案。' : '选择题目练习，查看批改结果。' : isTeacher ? '发布作业，查看学生作答并完成批阅。' : '完成作业，查看老师的批阅与反馈。' }}</p></div><div class="learning-actions"><button v-if="!isTeacher && questionBank && detailId" class="button button--soft" :disabled="loading || submitting" @click="loadAssignments">刷新题目</button><button v-if="isTeacher && questionBank && detailId && selected" class="button button--soft" @click="openEdit">编辑题目</button><button v-if="isTeacher && questionBank && detailId && selected" class="button button--soft" @click="toggleQuestion">{{ selected.enabled ? '停用' : '启用' }}</button><button v-if="isTeacher && questionBank && detailId && selected" class="button button--soft" @click="deleteOpen = true">删除题目</button><button v-if="isTeacher && !detailId" class="button button--primary" @click="openCreate">{{ questionBank ? '新建题目' : '新建作业' }}</button></div></header>
        <p v-if="loading" class="assignment-empty content-card">正在加载…</p>
        <section v-else-if="loadFailed" class="content-card assignment-empty"><p>内容暂时无法读取，请重试。</p><button class="button button--soft" @click="loadAssignments">重新加载</button></section>
        <template v-else-if="!detailId">
          <section class="content-card assessment-list-panel"><div class="assessment-filters"><label>查找<input v-model="keyword" type="search" placeholder="搜索标题或题目" /></label><label>知识标签<select v-model="tagFilter"><option value="">全部标签</option><option v-for="tag in selectedTags" :key="tag.id" :value="String(tag.id)">{{ tag.name }}</option></select></label><label v-if="!isTeacher || questionBank">状态<select v-model="stateFilter"><option value="">全部状态</option><template v-if="isTeacher"><option value="enabled">已启用</option><option value="disabled">已停用</option></template><template v-else><option value="NOT_SUBMITTED">{{ questionBank ? '未作答' : '未提交' }}</option><option value="PENDING">待批改</option><option value="RUNNING">批改中</option><option value="SUCCEEDED">已批改</option><option value="FAILED">批改未完成</option></template></select></label><button class="button button--soft" :disabled="loading" @click="loadAssignments">刷新</button></div>
          <p v-if="!filtered.length" class="assignment-empty">{{ assignments.length ? '没有匹配的内容，请调整查找条件。' : questionBank ? '暂无题目。' : '暂无作业。' }}</p>
          <PagedList :items="filtered" always-show :page="listPage" :page-size="pageSize" :label="questionBank ? '题库' : '作业'" @update:page="(page) => updateQuery({ page })" @update:page-size="(size) => updateQuery({ size, page: null })" v-slot="{ pageItems }"><div class="assessment-list"><button v-for="item in pageItems" :key="item.id" class="assessment-row" @click="openItem(item)">
            <h2>{{ item.title }}</h2>
            <div class="assessment-row-tags"><span v-for="tag in (item.tags || []).slice(0, 3)" :key="tag.id">{{ tag.name }}</span><span v-if="item.tags?.length > 3" :title="item.tags.slice(3).map((tag) => tag.name).join('、')">+{{ item.tags.length - 3 }}</span></div>
            <div class="assessment-row-result"><span class="learning-badge">{{ listState(item) }}</span><span>满分 {{ item.maxScore }} 分</span></div><span class="assessment-row-open" aria-hidden="true">→</span>
          </button></div></PagedList></section>
        </template>
        <template v-else>
          <div class="assessment-back"><RouterLink class="button button--soft" :to="{ path: basePath, query: route.query }">← 返回{{ questionBank ? '题库' : '作业列表' }}</RouterLink></div>
        <main v-if="selected" class="assignment-main">
          <article class="content-card assignment-detail">
            <div class="assignment-detail-heading"><h2>{{ selected.title }}</h2><span class="assignment-total">满分 {{ selected.maxScore }} 分</span></div>
            <div class="assignment-prompt assistant-markdown" v-html="renderAssistantMarkdown(selected.prompt)"></div><div v-if="selected.tags?.length" class="learning-actions"><span v-for="tag in selected.tags" :key="tag.id" class="learning-badge">{{ tag.name }}</span></div>
            <div v-if="isTeacher && selected.rubricSources?.length" class="rubric-sources"><span>参考答案</span><span v-for="source in selected.rubricSources" :key="source.id" class="rubric-file"><AppIcon name="book" />{{ source.title }}</span></div>
          </article>
          <section v-if="isTeacher && !questionBank" class="content-card submissions-panel">
            <div class="section-heading"><h2>学生作答</h2><button class="button button--soft" type="button" :disabled="submissionsLoading" @click="loadSubmissions">刷新</button></div>
            <p v-if="submissionsLoading" class="section-empty">正在加载作答…</p>
            <p v-else-if="!submissions.length" class="section-empty">还没有学生提交作答。</p>
            <PagedList v-else :items="submissions" :reset-key="detailId" label="学生作答" v-slot="{ pageItems }"><div class="submission-table-wrap"><table class="submissions-table">
              <thead><tr><th>学生</th><th>作答</th><th>状态</th><th>得分</th><th>作答时间</th><th><span class="sr-only">操作</span></th></tr></thead>
              <tbody><tr v-for="item in pageItems" :key="item.studentId">
                <td class="student-name">{{ item.studentUsername }}</td><td><div class="submission-excerpt assistant-markdown" v-html="renderAssistantMarkdown(item.content || '—')"></div></td>
                <td><span class="grading-status" :class="{ 'grading-status--done': item.teacherReview || item.status === 'SUCCEEDED' }">{{ item.teacherReview ? '已终评' : statusLabel(item.status) }}</span></td>
                <td class="submission-score">{{ item.score ?? '—' }}<span v-if="item.score !== null && item.score !== undefined"> / {{ selected.maxScore }}</span></td>
                <td>{{ item.createdAt ? new Date(item.createdAt).toLocaleString('zh-CN') : '—' }}</td><td><button v-if="item.submissionId" type="button" class="button button--soft" :aria-label="`给 ${item.studentUsername} 改分`" @click="openReview(item)">改分</button></td>
              </tr></tbody>
            </table></div></PagedList>
          </section>
          <section v-else-if="!isTeacher && (!submitted || redo)" class="content-card submit-panel">
            <div class="section-heading"><h2>填写作答</h2></div>
            <div class="answer-editor"><label class="sr-only" for="student-answer">作答内容</label><textarea id="student-answer" v-model="submitContent" rows="9" maxlength="10000" placeholder="在这里写下你的答案…"></textarea><div class="answer-actions"><button v-if="redo" class="button button--soft" @click="redo = false; submitContent = ''">取消重答</button><span>{{ questionBank ? '本次提交将替换上一次作答和批改结果' : '每份作业仅可提交一次' }}</span><button class="button button--primary" type="button" :disabled="submitting || !submitContent.trim()" @click="submit">{{ submitting ? '提交中…' : '提交' }}</button></div></div>
          </section>
          <section v-else-if="!isTeacher" class="content-card grading-result-panel">
            <div class="section-heading"><h2>批改结果</h2><div class="learning-actions"><button v-if="questionBank && terminal(task?.status)" class="button button--primary" @click="redo = true; submitContent = task?.submissionContent || ''">重新作答</button><button class="button button--soft" type="button" @click="refresh">刷新结果</button></div></div>
            <div class="result-body"><p v-if="questionBank && selected.questionChanged" class="learning-note">题目已更新。以下为上次题目与作答，重新作答将按当前题目批改。</p><details v-if="questionBank && selected.questionChanged"><summary>查看上次题目</summary><div class="assistant-markdown" v-html="renderAssistantMarkdown(selected.submittedPrompt)"></div></details>
              <section class="student-answer"><h3>我的作答</h3><div class="answer-body assistant-markdown" v-html="renderAssistantMarkdown(task?.submissionContent || '正在读取作答…')"></div></section>
              <section v-if="Number.isFinite(finalScore)" class="grade-feedback">
                <div class="grade-overview"><div><span class="grade-source">{{ effectiveReview ? '教师终评' : task?.result?.method === 'RULE' ? '系统批改' : 'AI 批改' }}</span><p class="grade-score">{{ finalScore }}<span> / {{ resultTotal }} 分</span></p></div><span class="grading-status grading-status--done">{{ effectiveReview ? '已终评' : '已批改' }}</span></div>
                <h3>评语</h3><div class="grade-comment assistant-markdown" v-html="renderAssistantMarkdown(finalComment || '')"></div>
              </section>
              <div v-else class="grading-waiting"><span class="grading-waiting-dot"></span><p>{{ task?.status === 'FAILED' ? questionBank ? '批改未完成，可以重新作答' : '等待教师批阅' : '已提交，正在批改中' }}</p></div>
            </div>
          </section>
        </main>
          <section v-else class="content-card assignment-empty"><p>内容暂时无法读取。</p><button class="button button--soft" @click="loadAssignments">重新加载</button></section>
        </template>
    </div>
    <BaseModal v-if="createOpen" :title="questionBank ? form.id ? '编辑题目' : '新建题目' : '新建作业'" wide @close="!createBusy && (createOpen = false)">
      <form id="assignment-create" class="config-form assignment-form" novalidate @submit.prevent="saveAssignment">
        <label class="field"><span>{{ questionBank ? '题目标题' : '作业标题' }}</span><input v-model="form.title" maxlength="120" placeholder="例如：分数运算练习" /></label>
        <label class="field"><span>详细题目</span><textarea v-model="form.prompt" rows="5" maxlength="4000" placeholder="写清题目、作答要求及必要条件"></textarea></label>
        <label class="field assignment-points-field"><span>总分</span><input v-model="form.maxScore" type="number" min="1" max="100" step="1" /></label>
        <fieldset class="rubric-picker"><legend>参考答案文件（最多5个）</legend>
          <label class="field reference-search"><span>查找讲义</span><input v-model="referenceQuery" type="search" placeholder="输入讲义名称" /></label>
          <div v-if="rubricFiles.length" class="rubric-options"><PagedList :items="rubricFiles" :reset-key="referenceQuery" label="参考答案文件" v-slot="{ pageItems }"><label v-for="material in pageItems" :key="material.id" class="rubric-option" :class="{ selected: form.rubricMaterialIds.includes(material.id) }"><input v-model="form.rubricMaterialIds" type="checkbox" :value="material.id" /><AppIcon name="book" /><span>{{ material.title }}</span></label></PagedList></div>
          <p v-else class="rubric-picker-empty">{{ referenceQuery ? '没有匹配的讲义，请更换关键词。' : '暂无可用文件，请先上传参考答案。' }}<a v-if="!referenceQuery" href="/materials">讲义与检索</a></p>
        </fieldset>
        <label v-if="questionBank" class="switch-field"><input v-model="form.enabled" type="checkbox" /><span>启用题目</span></label>
      </form>
      <template #footer><button class="button button--secondary" type="button" :disabled="createBusy" @click="createOpen = false">取消</button><button class="button button--primary" type="submit" form="assignment-create" :disabled="createBusy">{{ createBusy ? '保存中…' : questionBank ? '保存题目' : '创建作业' }}</button></template>
    </BaseModal>
    <BaseModal v-if="reviewTarget" title="批阅与终评" wide @close="!reviewBusy && (reviewTarget = null)">
      <div class="review-student"><strong>{{ reviewTarget.studentUsername }}</strong><span>{{ selected.title }}</span></div>
      <section class="review-answer"><h3>学生作答</h3><div class="answer-body assistant-markdown" v-html="renderAssistantMarkdown(reviewTarget.content)"></div></section>
      <section class="review-ai"><div class="review-ai-heading"><h3>{{ reviewTarget.aiResult?.method === 'RULE' ? '系统批改' : 'AI 批改' }}</h3><strong>{{ reviewTarget.aiResult ? `${reviewTarget.aiResult.score} / ${selected.maxScore} 分` : '暂无结果' }}</strong></div>
        <template v-if="reviewTarget.aiResult"><div class="assistant-markdown" v-html="renderAssistantMarkdown(reviewTarget.aiResult.rationale)"></div><details v-if="reviewTarget.aiResult.criteria?.length"><summary>查看原评分明细</summary><ul><li v-for="item in reviewTarget.aiResult.criteria" :key="item.key"><strong>{{ item.awardedPoints }} / {{ item.points }} 分</strong><div class="assistant-markdown" v-html="renderAssistantMarkdown(item.description)"></div><div class="assistant-markdown" v-html="renderAssistantMarkdown(item.explanation)"></div><div class="assistant-markdown" v-html="renderAssistantMarkdown(item.evidence)"></div></li></ul></details></template>
        <p v-else>可以直接填写教师终评。</p>
      </section>
      <form id="submission-review" class="config-form assignment-form review-form" novalidate @submit.prevent="saveReview">
        <label class="field assignment-points-field"><span>终评分数</span><input v-model="reviewForm.score" type="number" min="0" :max="selected.maxScore" step="1" /></label>
        <label class="field"><span>终评评语</span><textarea v-model="reviewForm.comment" rows="4" maxlength="2000" placeholder="写下给学生的终评意见"></textarea></label>
      </form>
      <template #footer><button class="button button--secondary" type="button" :disabled="reviewBusy" @click="reviewTarget = null">取消</button><button class="button button--primary" type="submit" form="submission-review" :disabled="reviewBusy">{{ reviewBusy ? '保存中…' : '保存终评' }}</button></template>
    </BaseModal>
    <BaseModal v-if="deleteOpen" title="删除题目" @close="!deleteBusy && (deleteOpen = false)"><p>删除“{{ selected.title }}”？题目将从题库中移除，已有学习成绩保留。</p><template #footer><button class="button button--soft" :disabled="deleteBusy" @click="deleteOpen = false">取消</button><button class="button button--primary" :disabled="deleteBusy" @click="removeQuestion">删除</button></template></BaseModal>
  </AppShell>
</template>
<style scoped>
.assignment-layout { display: grid; grid-template-columns: 260px minmax(0, 1fr); align-items: start; gap: 24px; }
.assignment-main { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; gap: 24px; }
.assignment-list { padding: 8px; overflow: hidden; }
.assignment-list-heading { display: flex; justify-content: space-between; align-items: center; padding: 14px 12px 20px; }
.assignment-list-heading h2 { margin: 0; font-size: 17px; }
.assignment-list-heading > span { color: var(--ink-500); font-size: 14px; }
.assignment-item { display: flex; flex-direction: column; align-items: start; gap: 10px; width: 100%; padding: 16px; text-align: left; border: 1px solid transparent; border-radius: 10px; background: #fff; cursor: pointer; }
.assignment-item + .assignment-item { margin-top: 4px; }
.assignment-item:hover { background: var(--canvas); }
.assignment-item.active { background: var(--brand-50); border-color: #eed2d3; }
.assignment-item-title { color: var(--ink-900); font-size: 15px; font-weight: 600; line-height: 1.5; overflow-wrap: anywhere; }
.assignment-item-status { color: var(--ink-500); font-size: 13px; }
.assignment-item.active .assignment-item-status { color: var(--brand-700); }
.assignment-detail { padding: 24px; }
.assignment-detail-heading { display: flex; align-items: start; justify-content: space-between; gap: 16px; }
.assignment-detail h2 { margin: 0; font-size: 21px; line-height: 1.5; overflow-wrap: anywhere; }
.assignment-total { padding: 7px 12px; border-radius: 8px; flex-shrink: 0; color: var(--brand-700); background: var(--brand-50); font-size: 14px; }
.assignment-prompt { margin: 22px 0 0; font-size: 15px; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }
.rubric-sources { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-top: 24px; padding-top: 18px; border-top: 1px solid var(--line); font-size: 14px; color: var(--ink-500); }
.rubric-file { display: inline-flex; align-items: center; gap: 7px; color: var(--ink-700); background: var(--canvas); padding: 7px 10px; border-radius: 8px; overflow-wrap: anywhere; }
.rubric-file svg { width: 16px; height: 16px; flex-shrink: 0; }
.section-heading { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px; border-bottom: 1px solid var(--line); gap: 12px; }
.section-heading h2 { margin: 0; font-size: 18px; }
.section-empty { padding: 40px 24px; margin: 0; text-align: center; color: var(--ink-500); font-size: 15px; }
.submission-table-wrap { position: relative; overflow-x: auto; }
.submissions-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.submissions-table th { color: var(--ink-500); background: #fafbfc; text-align: left; font-weight: 500; }
.submissions-table td, .submissions-table th { padding: 18px 16px; border-bottom: 1px solid var(--line); }
.submissions-table tr:last-child td { border-bottom: 0; }
.submissions-table th:first-child, .submissions-table td:first-child { padding-left: 24px; }
.student-name { font-weight: 600; white-space: nowrap; }
.submission-excerpt { margin: 0; min-width: 140px; line-height: 1.7; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; overflow-wrap: anywhere; }
.submission-score { font-weight: 650; white-space: nowrap; }
.submission-score > span { color: var(--ink-500); font-size: 13px; font-weight: 400; }
.grading-status { display: inline-block; padding: 5px 9px; border-radius: 6px; color: var(--warning); background: #fbf3e4; white-space: nowrap; font-size: 13px; }
.grading-status--done { color: var(--success); background: var(--success-bg); }
.answer-editor { padding: 24px; }
.answer-editor textarea { width: 100%; min-height: 220px; padding: 16px; border: 1px solid #dce0e5; border-radius: 10px; line-height: 1.8; font: inherit; resize: vertical; }
.answer-editor textarea:focus { outline: 2px solid var(--brand-100); border-color: var(--brand-700); }
.answer-actions { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-top: 18px; }
.answer-actions > span { color: var(--ink-500); font-size: 14px; }
.result-body { padding: 24px; }
.student-answer h3, .grade-feedback h3, .review-answer h3, .review-ai h3 { font-size: 15px; margin: 0 0 12px; }
.answer-body { padding: 16px; margin: 0; background: var(--canvas); border-radius: 10px; overflow-wrap: anywhere; font-size: 15px; line-height: 1.8; }
.grade-feedback { margin-top: 28px; padding-top: 24px; border-top: 1px solid var(--line); }
.grade-overview { display: flex; align-items: start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.grade-source { font-size: 14px; color: var(--ink-500); }
.grade-score { color: var(--brand-700); font-size: 36px; font-weight: 700; margin: 8px 0 0; line-height: 1.2; }
.grade-score > span { color: var(--ink-500); font-size: 15px; font-weight: 400; }
.grade-comment { margin: 0; font-size: 15px; overflow-wrap: anywhere; line-height: 1.9; }
.grading-waiting { display: flex; align-items: center; justify-content: center; gap: 10px; margin-top: 24px; padding: 24px 0; color: var(--ink-500); }
.grading-waiting-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--brand-700); }
.assignment-form { display: grid; gap: 20px; }
.assignment-form .field { display: grid; min-width: 0; margin: 0; gap: 8px; font-size: 14px; font-weight: 600; }
.assignment-form .reference-search { margin: 8px 0 16px; }
.assignment-form input[type="search"] { width: 100%; min-height: 42px; padding: 10px 12px; color: var(--ink-900); background: #fff; border: 1px solid var(--line); border-radius: 8px; outline: 0; }
.assignment-form input[type="number"] { width: 100%; padding: 10px 12px; color: #32363c; background: #fff; border: 1px solid #d9dce1; border-radius: 8px; outline: 0; }
.assignment-form textarea { resize: vertical; line-height: 1.7; }
.assignment-points-field { max-width: 180px; }
.rubric-picker { margin: 0; padding: 0; border: 0; min-width: 0; }
.rubric-picker legend { font-size: 14px; font-weight: 600; }
.rubric-picker > p { margin: 8px 0 14px; font-size: 14px; color: var(--ink-500); }
.rubric-options { display: grid; gap: 10px; max-height: 320px; overflow-y: auto; }
.rubric-options > .paged-list { display: grid; gap: 10px; }
.rubric-option { display: flex; align-items: center; gap: 12px; padding: 14px; border: 1px solid var(--line); border-radius: 10px; cursor: pointer; }
.rubric-option.selected { border-color: #d9aaac; background: var(--brand-50); }
.rubric-option input { accent-color: var(--brand-700); }
.rubric-option svg { width: 19px; height: 19px; color: var(--ink-500); flex-shrink: 0; }
.rubric-option span { font-size: 14px; overflow-wrap: anywhere; }
.rubric-picker-empty { padding: 18px; background: var(--canvas); border-radius: 10px; }
.rubric-picker-empty a { color: var(--brand-700); margin: 0 4px; }
.review-student { display: flex; align-items: center; gap: 12px; margin-bottom: 22px; font-size: 15px; }
.review-student span { color: var(--ink-500); }
.review-answer { margin-bottom: 22px; }
.review-ai { padding: 18px; background: #faf8f5; border: 1px solid #f0e9df; border-radius: 10px; }
.review-ai-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.review-ai-heading h3 { margin: 0; }
.review-ai > p, .review-ai li p { margin: 12px 0 0; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 14px; }
.review-ai summary { margin-top: 16px; cursor: pointer; font-size: 14px; }
.review-ai ul { padding-left: 20px; }
.review-ai li { margin-top: 16px; font-size: 14px; }
.review-form { border-top: 1px solid var(--line); padding-top: 24px; margin-top: 24px; }
.assignment-empty { display: grid; justify-items: center; gap: 14px; padding: 64px 24px; color: var(--ink-500); text-align: center; }
.assignment-empty > svg { width: 40px; height: 40px; color: #b9bec6; }
.assignment-empty h2 { color: var(--ink-700); margin: 0; font-size: 19px; }
.assignment-empty p { margin: 0; font-size: 15px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
@media (max-width: 1000px) { .assignment-layout { grid-template-columns: 220px minmax(0, 1fr); gap: 18px; } .assignment-main { gap: 18px; } }
@media (max-width: 760px) { .assignment-layout { grid-template-columns: 1fr; } .assignment-list { max-height: 260px; overflow-y: auto; } .assignment-item { flex-direction: row; align-items: center; justify-content: space-between; gap: 12px; } .assignment-item-status { flex-shrink: 0; } .assignment-list-heading { padding-bottom: 12px; } .assignment-detail, .result-body, .answer-editor { padding: 20px; } .submissions-table td, .submissions-table th { padding: 14px 12px; } }
@media (max-width: 470px) { .assignment-detail-heading { flex-direction: column; gap: 12px; } .section-heading { padding: 18px; } .answer-actions { align-items: end; } .answer-actions > span { max-width: 150px; line-height: 1.6; } .review-student { align-items: start; flex-direction: column; gap: 6px; } }
.assessment-list-panel { padding: 24px; }
.assessment-filters { display: flex; flex-wrap: wrap; align-items: end; gap: 16px; margin-bottom: 24px; }
.assessment-filters label { display: grid; gap: 8px; flex: 1; min-width: 160px; font-size: 14px; }
.assessment-filters input, .assessment-filters select { min-height: 42px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 8px; background: #fff; font: inherit; width: 100%; }
.assessment-list { border-top: 1px solid var(--line); }
.assessment-row { width: 100%; display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) 110px 24px; align-items: center; gap: 20px; min-height: 88px; text-align: left; padding: 18px 12px; border: 0; border-bottom: 1px solid var(--line); background: transparent; cursor: pointer; color: var(--ink-900); font: inherit; }
.assessment-row:hover { background: var(--brand-50); }
.assessment-row:focus-visible { outline: 2px solid var(--brand-700); outline-offset: -2px; }
.assessment-row h2 { min-width: 0; font-size: 17px; font-weight: 600; margin: 0; overflow-wrap: anywhere; line-height: 1.6; }
.assessment-row-tags { display: flex; flex-wrap: wrap; gap: 6px; min-width: 0; }
.assessment-row-tags > span { max-width: 100%; padding: 4px 8px; border-radius: 6px; color: var(--ink-500); background: var(--canvas); font-size: 14px; overflow-wrap: anywhere; }
.assessment-row-result { display: grid; justify-items: end; gap: 8px; color: var(--ink-500); font-size: 14px; }
.assessment-row-result .learning-badge { white-space: nowrap; }
.assessment-row-open { font-size: 20px; color: var(--ink-500); }
@media (max-width: 760px) {
  .assessment-row { grid-template-columns: minmax(0, 1fr) 110px 20px; gap: 10px; }
  .assessment-row-tags { grid-column: 1 / -1; grid-row: 2; }
  .assessment-row-result { grid-column: 2; grid-row: 1; }
  .assessment-row-open { grid-column: 3; grid-row: 1; }
}
.assessment-back { margin-bottom: 20px; }
.breadcrumb a { color: inherit; }
@media (max-width: 470px) { .assessment-list-panel { padding: 16px; } .assessment-filters label { min-width: 100%; } }
</style>

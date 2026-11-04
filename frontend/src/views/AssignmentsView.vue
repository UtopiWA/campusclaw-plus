<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { authState } from '../services/auth.js'
import { ApiError } from '../services/api.js'
import {
  createAssignment as createAssignmentRequest,
  getGradingTask,
  listAssignments,
  listTeacherSubmissions,
  submitAssignment,
} from '../services/assignments.js'

const isTeacher = computed(() => authState.user?.role === 'teacher')
const assignments = ref([])
const loading = ref(true)
const error = ref('')
const notice = ref('')
const selectedId = ref(null)
const selected = computed(() => assignments.value.find((item) => item.id === selectedId.value) || null)
const submissions = ref([])
const submissionsLoading = ref(false)
const submitting = ref(false)
const submitContent = ref('')
const task = ref(null)
const pollAttempts = ref(0)
let pollTimer = null
let selectionVersion = 0
let disposed = false
let taskRequest = null
let submissionsRequest = null

const createOpen = ref(false)
const createBusy = ref(false)
const createError = ref('')
let criterionSeq = 0
const form = ref({ title: '', prompt: '', criteria: [newCriterion()] })

function newCriterion() {
  criterionSeq += 1
  return { key: `c${criterionSeq}`, description: '', points: 10 }
}

const totalPoints = computed(() => form.value.criteria.reduce(
  (sum, item) => sum + (Number(item.points) || 0), 0))

const isTerminal = (status) => status === 'SUCCEEDED' || status === 'FAILED'

function taskStatusLabel(status) {
  return ({
    PENDING: '待处理',
    RUNNING: '批改中',
    SUCCEEDED: '已完成',
    FAILED: '失败',
  })[status] || '—'
}

function verdictLabel(verdict) {
  return ({ MET: '达成', PARTIAL: '部分达成', NOT_MET: '未达成' })[verdict] || verdict
}

function failureLabel(code) {
  return ({
    MODEL_UNAVAILABLE: '模型服务暂时不可用',
    MODEL_OUTPUT_INVALID: '模型输出不符合评分格式',
    MODEL_REQUEST_REJECTED: '模型服务拒绝请求，请联系教师检查配置',
    ASSIGNMENT_INVALID: '作业评分依据无效',
    SUBMISSION_MISSING: '关联作答不存在',
    ASSIGNMENT_MISSING: '关联作业不存在',
    GRADING_INTERNAL: '批改内部错误',
    STALE_RUNNING: '执行超时已回收',
  })[code] || code || '未知原因'
}

function message(exception) {
  return exception instanceof ApiError ? exception.message : (exception?.message || '请求失败')
}

function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function cancelSelection() {
  selectionVersion += 1
  stopPolling()
  taskRequest?.abort()
  submissionsRequest?.abort()
}

function schedulePoll() {
  if (disposed) return
  stopPolling()
  const delay = Math.min(10000, 2000 * Math.pow(1.4, pollAttempts.value))
  pollTimer = setTimeout(pollOnce, delay)
}

async function pollOnce() {
  pollTimer = null
  if (disposed || !task.value?.taskId) return
  const version = selectionVersion
  const taskId = task.value.taskId
  const controller = new AbortController()
  taskRequest = controller
  try {
    const response = await getGradingTask(taskId, { signal: controller.signal })
    if (disposed || version !== selectionVersion) return
    task.value = response
    pollAttempts.value = 0
    if (isTerminal(task.value.status)) {
      stopPolling()
      await reloadQuiet()
    } else {
      schedulePoll()
    }
  } catch {
    if (disposed || version !== selectionVersion || controller.signal.aborted) return
    pollAttempts.value += 1
    if (pollAttempts.value >= 8) {
      stopPolling()
      error.value = '状态查询失败，请刷新后重试。'
    } else {
      schedulePoll()
    }
  }
}

function startPolling() {
  stopPolling()
  pollAttempts.value = 0
  schedulePoll()
}

async function reloadQuiet() {
  try {
    const items = await listAssignments()
    if (!disposed) assignments.value = items
  } catch {
    // 保留当前选择；轮询失败由状态卡片展示。
  }
}

async function resumeFromLatest(item) {
  cancelSelection()
  task.value = null
  const latest = item?.myLatest
  if (!latest?.taskId) return
  const version = selectionVersion
  const controller = new AbortController()
  taskRequest = controller
  try {
    const response = await getGradingTask(latest.taskId, { signal: controller.signal })
    if (disposed || version !== selectionVersion) return
    task.value = response
    if (!isTerminal(task.value.status)) startPolling()
  } catch (exception) {
    if (disposed || version !== selectionVersion || controller.signal.aborted) return
    error.value = message(exception)
  }
}

async function loadAssignments() {
  loading.value = true
  error.value = ''
  try {
    const items = await listAssignments()
    if (disposed) return
    assignments.value = items
    if (selectedId.value && !assignments.value.some((item) => item.id === selectedId.value)) {
      selectedId.value = null
      task.value = null
      stopPolling()
    }
    if (!selectedId.value && assignments.value.length) selectedId.value = assignments.value[0].id
    if (selected.value && !isTeacher.value) await resumeFromLatest(selected.value)
  } catch (exception) {
    error.value = message(exception)
  } finally {
    loading.value = false
  }
}

async function selectAssignment(item) {
  cancelSelection()
  selectedId.value = item.id
  submitContent.value = ''
  error.value = ''
  notice.value = ''
  task.value = null
  submissions.value = []
  stopPolling()
  if (isTeacher.value) await loadSubmissions()
  else await resumeFromLatest(item)
}

async function loadSubmissions() {
  if (!isTeacher.value || !selected.value) return
  submissionsLoading.value = true
  const version = selectionVersion
  const controller = new AbortController()
  submissionsRequest = controller
  try {
    const items = await listTeacherSubmissions(selected.value.id, { signal: controller.signal })
    if (disposed || version !== selectionVersion) return
    submissions.value = items
  } catch (exception) {
    if (disposed || version !== selectionVersion || controller.signal.aborted) return
    error.value = message(exception)
  } finally {
    if (!disposed && version === selectionVersion) submissionsLoading.value = false
  }
}

function addCriterion() {
  if (form.value.criteria.length < 10) form.value.criteria.push(newCriterion())
}

function removeCriterion(index) {
  if (form.value.criteria.length > 1) form.value.criteria.splice(index, 1)
}

async function saveAssignment() {
  createError.value = ''
  const title = form.value.title.trim()
  const prompt = form.value.prompt.trim()
  if (!title || !prompt) {
    createError.value = '标题和题目不能为空。'
    return
  }
  const keys = new Set()
  let total = 0
  for (const criterion of form.value.criteria) {
    if (!/^[A-Za-z0-9_-]{1,32}$/.test(criterion.key)) {
      createError.value = '评分依据 key 只能使用字母、数字、下划线和短横线。'
      return
    }
    if (keys.has(criterion.key)) {
      createError.value = '评分依据 key 不能重复。'
      return
    }
    keys.add(criterion.key)
    const points = Number(criterion.points)
    if (!Number.isInteger(points) || points < 1 || points > 100) {
      createError.value = '每条评分依据分值应为 1～100 的整数。'
      return
    }
    if (!criterion.description.trim()) {
      createError.value = '评分依据说明不能为空。'
      return
    }
    total += points
  }
  if (total > 100) {
    createError.value = '评分依据总分不能超过 100。'
    return
  }
  createBusy.value = true
  try {
    const created = await createAssignmentRequest({
      title,
      prompt,
      criteria: form.value.criteria.map((item) => ({
        key: item.key,
        description: item.description.trim(),
        points: Number(item.points),
      })),
    })
    form.value = { title: '', prompt: '', criteria: [newCriterion()] }
    createOpen.value = false
    notice.value = '作业已创建，本班学生现在可以提交。'
    await loadAssignments()
    cancelSelection()
    selectedId.value = created.id
    await loadSubmissions()
  } catch (exception) {
    createError.value = message(exception)
  } finally {
    createBusy.value = false
  }
}

async function submit() {
  if (!selected.value || submitting.value) return
  const content = submitContent.value.trim()
  const length = [...content].length
  if (length < 1 || length > 10000) {
    error.value = '作答内容应为 1～10000 个字符。'
    return
  }
  submitting.value = true
  cancelSelection()
  error.value = ''
  notice.value = ''
  const version = selectionVersion
  const assignmentId = selected.value.id
  try {
    const key = globalThis.crypto?.randomUUID?.()
      || `ui-${Date.now()}-${Math.random().toString(36).slice(2)}`
    const response = await submitAssignment(assignmentId, content, key)
    if (disposed || version !== selectionVersion) return
    submitContent.value = ''
    task.value = {
      taskId: response.taskId,
      submissionId: response.submissionId,
      assignmentId,
      status: response.status,
      attempts: 0,
      maxAttempts: null,
      result: null,
    }
    notice.value = '已提交，后台正在批改；离开页面后刷新仍可继续查看。'
    startPolling()
    await reloadQuiet()
  } catch (exception) {
    if (disposed || version !== selectionVersion) return
    error.value = message(exception)
    if (task.value && !isTerminal(task.value.status)) startPolling()
  } finally {
    submitting.value = false
  }
}

onMounted(loadAssignments)
onBeforeUnmount(() => {
  disposed = true
  cancelSelection()
})
</script>

<template>
  <AppShell current="assignments">
    <div class="assignments-page">
      <header class="page-heading">
        <div>
          <p class="eyebrow">CLASS 08 · ASYNC GRADING</p>
          <h1>作业与批改</h1>
          <p>教师提供题目与评分依据，学生提交文本作答，后台异步完成模型辅助分项判断和规则计分。</p>
        </div>
        <button v-if="isTeacher" class="button button--primary" type="button" @click="createOpen = !createOpen">
          {{ createOpen ? '收起新建' : '新建作业' }}
        </button>
      </header>

      <p v-if="error" class="feedback feedback--error" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback feedback--success">{{ notice }}</p>

      <div class="assignments-layout">
        <section class="assignment-panel assignments-list" aria-label="作业列表">
          <h2>作业列表</h2>
          <p v-if="loading" class="muted">正在加载…</p>
          <p v-else-if="!assignments.length" class="muted">本班暂无作业。</p>
          <button
            v-for="item in assignments"
            :key="item.id"
            type="button"
            class="assignment-item"
            :class="{ 'assignment-item--active': item.id === selectedId }"
            @click="selectAssignment(item)"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.criteria.length }} 条评分依据 · 满分 {{ item.maxScore }}</span>
            <span v-if="item.myLatest" class="assignment-item-status">
              我的任务：{{ taskStatusLabel(item.myLatest.status) }}
              <template v-if="item.myLatest.score !== null && item.myLatest.score !== undefined">
                · {{ item.myLatest.score }} 分
              </template>
            </span>
          </button>
        </section>

        <section class="assignments-main">
          <div v-if="createOpen && isTeacher" class="assignment-panel create-panel">
            <h2>新建作业</h2>
            <label class="field">
              <span>标题</span>
              <input v-model="form.title" maxlength="120" placeholder="例如：勾股定理课堂练习" />
            </label>
            <label class="field">
              <span>题目</span>
              <textarea v-model="form.prompt" maxlength="4000" rows="4" placeholder="输入学生需要回答的问题"></textarea>
            </label>
            <div class="criteria-editor">
              <div v-for="(criterion, index) in form.criteria" :key="criterion.key" class="criterion-row">
                <input v-model="criterion.key" class="criterion-key" maxlength="32" aria-label="评分依据 key" />
                <input v-model="criterion.description" maxlength="500" placeholder="评分依据说明" aria-label="评分依据说明" />
                <input v-model.number="criterion.points" class="criterion-points" type="number" min="1" max="100" aria-label="分值" />
                <button type="button" class="button button--soft" :disabled="form.criteria.length <= 1" @click="removeCriterion(index)">删除</button>
              </div>
              <div class="criteria-actions">
                <button type="button" class="button button--soft" :disabled="form.criteria.length >= 10" @click="addCriterion">添加评分依据</button>
                <span class="muted">总分 {{ totalPoints }} / 100</span>
              </div>
            </div>
            <p v-if="createError" class="feedback feedback--error" role="alert">{{ createError }}</p>
            <div class="form-actions">
              <button type="button" class="button button--secondary" @click="createOpen = false">取消</button>
              <button type="button" class="button button--primary" :disabled="createBusy" @click="saveAssignment">
                {{ createBusy ? '保存中…' : '保存作业' }}
              </button>
            </div>
          </div>

          <div v-if="!selected" class="assignment-panel empty-panel">
            <p>选择左侧作业查看详情{{ isTeacher ? '和本班提交' : '并提交作答' }}。</p>
          </div>

          <template v-else>
            <article class="assignment-panel assignment-detail">
              <div class="detail-heading">
                <div>
                  <p class="eyebrow">ASSIGNMENT</p>
                  <h2>{{ selected.title }}</h2>
                </div>
                <span class="score-badge">满分 {{ selected.maxScore }}</span>
              </div>
              <p class="assignment-prompt">{{ selected.prompt }}</p>
              <ol class="rubric-list">
                <li v-for="criterion in selected.criteria" :key="criterion.key">
                  <span>{{ criterion.description }}</span>
                  <strong>{{ criterion.points }} 分</strong>
                </li>
              </ol>
            </article>

            <section v-if="!isTeacher" class="assignment-panel submit-panel">
              <h3>提交作答</h3>
              <textarea
                v-model="submitContent"
                rows="6"
                maxlength="10000"
                placeholder="输入你的文本作答；提交后由后台异步批改。"
              ></textarea>
              <div class="submit-actions">
                <span class="muted">{{ [...submitContent].length }} / 10000</span>
                <button class="button button--primary" type="button" :disabled="submitting || !submitContent.trim()" @click="submit">
                  {{ submitting ? '提交中…' : '提交并被批改' }}
                </button>
              </div>
              <p class="muted">提交响应只返回任务标识，不等待模型；刷新页面后会从最新任务恢复查询。</p>
            </section>

            <section v-if="task" class="assignment-panel task-panel" aria-live="polite">
              <div class="task-heading">
                <h3>批改任务</h3>
                <span class="task-status" :class="`task-status--${String(task.status || '').toLowerCase()}`">
                  {{ taskStatusLabel(task.status) }}
                </span>
              </div>
              <p class="muted task-meta">
                任务 {{ task.taskId }}
                <template v-if="task.attempts !== undefined">· 已执行 {{ task.attempts }} 次</template>
                <template v-if="task.maxAttempts"> / 上限 {{ task.maxAttempts }}</template>
              </p>
              <p v-if="task.status === 'FAILED'" class="feedback feedback--error">
                批改失败：{{ failureLabel(task.failureCode) }}。本次不会伪造分数，可稍后重试或联系教师。
              </p>
              <div v-if="task.result" class="grading-result">
                <p class="result-score">{{ task.result.score }}<span> / {{ task.result.maxScore }}</span></p>
                <p class="result-note">模型辅助分项判断 + 规则计分；教师复核未纳入本次。</p>
                <ul class="result-criteria">
                  <li v-for="criterion in task.result.criteria" :key="criterion.key">
                    <div class="criterion-heading">
                      <strong>{{ criterion.description }}</strong>
                      <span>{{ criterion.awardedPoints }} / {{ criterion.points }} 分 · {{ verdictLabel(criterion.verdict) }}</span>
                    </div>
                    <p>{{ criterion.explanation }}</p>
                    <p class="criterion-evidence">依据：{{ criterion.evidence }}</p>
                  </li>
                </ul>
                <p v-if="task.result.rationale" class="result-rationale">{{ task.result.rationale }}</p>
              </div>
            </section>

            <section v-if="isTeacher" class="assignment-panel submissions-panel">
              <h3>本班提交与结果</h3>
              <p class="muted">按学生和版本排序，最多显示 200 条提交。</p>
              <p v-if="submissionsLoading" class="muted">正在加载…</p>
              <p v-else-if="!submissions.length" class="muted">本班还没有提交。</p>
              <div v-else class="table-scroll">
                <table class="submissions-table">
                  <thead>
                    <tr><th>学生</th><th>版本</th><th>状态</th><th>得分</th><th>作答</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in submissions" :key="item.submissionId">
                      <td>{{ item.studentUsername }}</td>
                      <td>v{{ item.version }}</td>
                      <td>{{ taskStatusLabel(item.status) }}</td>
                      <td>
                        <template v-if="item.score !== null && item.score !== undefined">{{ item.score }} / {{ item.maxScore }}</template>
                        <template v-else>—</template>
                      </td>
                      <td class="answer-cell">{{ item.content }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          </template>
        </section>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.assignments-page { display: flex; flex-direction: column; }
.assignments-layout { display: grid; grid-template-columns: 320px minmax(0, 1fr); gap: 20px; align-items: start; }
.assignments-main { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.assignment-panel { padding: 20px; background: #fff; border: 1px solid #e4e7ec; border-radius: 14px; box-shadow: 0 1px 2px rgba(16, 24, 40, .04); }
.assignments-list { display: flex; flex-direction: column; gap: 10px; }
.assignments-list h2, .assignment-panel h2, .assignment-panel h3 { margin: 0 0 12px; color: var(--ink-900); }
.assignment-item { display: flex; flex-direction: column; gap: 4px; padding: 12px; text-align: left; color: var(--ink-700); background: #fff; border: 1px solid #e4e7ec; border-radius: 10px; cursor: pointer; }
.assignment-item:hover { border-color: #d9aaac; background: var(--brand-50); }
.assignment-item--active { border-color: var(--brand-700); background: var(--brand-50); }
.assignment-item strong { color: var(--ink-900); font-size: 14px; }
.assignment-item span { font-size: 12px; color: var(--ink-500); }
.assignment-item-status { color: var(--brand-700) !important; }
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; font-size: 13px; color: var(--ink-700); }
.field input, .field textarea, .submit-panel textarea { width: 100%; padding: 9px 11px; font: inherit; color: var(--ink-900); background: #fff; border: 1px solid #dcdfe4; border-radius: 8px; }
.criteria-editor { display: flex; flex-direction: column; gap: 8px; margin: 12px 0; }
.criterion-row { display: grid; grid-template-columns: 90px minmax(0, 1fr) 76px auto; gap: 8px; }
.criterion-row input { min-width: 0; padding: 8px 10px; border: 1px solid #dcdfe4; border-radius: 8px; }
.criteria-actions, .form-actions, .submit-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.form-actions { margin-top: 16px; }
.detail-heading, .task-heading, .criterion-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.score-badge { padding: 5px 10px; color: var(--brand-700); background: var(--brand-50); border-radius: 999px; font-size: 12px; font-weight: 700; white-space: nowrap; }
.assignment-prompt { margin: 0 0 16px; color: var(--ink-700); white-space: pre-wrap; line-height: 1.7; }
.rubric-list { margin: 0; padding-left: 20px; color: var(--ink-700); }
.rubric-list li { display: flex; justify-content: space-between; gap: 16px; padding: 6px 0; }
.submit-panel textarea { min-height: 140px; resize: vertical; }
.submit-actions { margin-top: 10px; }
.task-status { padding: 4px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; background: #f2f4f7; color: var(--ink-600); }
.task-status--pending, .task-status--running { color: #8a6100; background: #fff4d6; }
.task-status--succeeded { color: var(--success); background: var(--success-bg); }
.task-status--failed { color: #9d252c; background: var(--danger-bg); }
.task-meta { font-size: 12px; word-break: break-all; }
.grading-result { margin-top: 16px; }
.result-score { margin: 0; color: var(--brand-700); font-size: 42px; font-weight: 800; letter-spacing: -.04em; }
.result-score span { color: var(--ink-500); font-size: 20px; font-weight: 600; }
.result-note { margin: 2px 0 16px; color: var(--ink-500); font-size: 12px; }
.result-criteria { display: flex; flex-direction: column; gap: 12px; margin: 0; padding: 0; list-style: none; }
.result-criteria li { padding: 12px; background: #fafbfc; border: 1px solid #edf0f3; border-radius: 10px; }
.result-criteria p { margin: 6px 0 0; color: var(--ink-700); font-size: 13px; line-height: 1.6; }
.criterion-heading span { color: var(--ink-500); font-size: 12px; white-space: nowrap; }
.criterion-evidence { color: var(--ink-500) !important; }
.result-rationale { margin-top: 16px; padding-top: 12px; border-top: 1px solid #edf0f3; color: var(--ink-700); line-height: 1.7; }
.table-scroll { overflow-x: auto; }
.submissions-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.submissions-table th, .submissions-table td { padding: 9px 10px; text-align: left; border-bottom: 1px solid #edf0f3; vertical-align: top; }
.submissions-table th { color: var(--ink-500); font-weight: 600; white-space: nowrap; }
.answer-cell { max-width: 360px; color: var(--ink-700); white-space: pre-wrap; word-break: break-word; }
.muted { color: var(--ink-500); font-size: 13px; }
.empty-panel { color: var(--ink-500); }
@media (max-width: 960px) {
  .assignments-layout { grid-template-columns: 1fr; }
  .criterion-row { grid-template-columns: 1fr; }
}
</style>

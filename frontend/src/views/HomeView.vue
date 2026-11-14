<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import AppIcon from '../components/AppIcon.vue'
import OverviewDonut from '../components/OverviewDonut.vue'
import { authState } from '../services/auth.js'
import { apiRequest } from '../services/api.js'
import { showFeedback } from '../services/feedback.js'
import { renderAssistantMarkdown } from '../utils/assistantMarkdown.js'

const teacher = computed(() => authState.user?.role === 'teacher')
const data = ref(null)
const loading = ref(true)
const failed = ref(false)
const selectedId = ref('')
let controller
let revision = 0
let alive = true
async function load() {
  controller?.abort(); controller = new AbortController()
  const version = ++revision; loading.value = true; failed.value = false
  try {
    const response = await apiRequest(`/api/home/${teacher.value ? 'teacher' : 'student'}`, { signal: controller.signal })
    if (alive && revision === version) { data.value = response; if (!response.assignments?.some(a => String(a.id) === selectedId.value)) selectedId.value = String(response.assignments?.[0]?.id || '') }
  } catch (error) { if (alive && revision === version && error.name !== 'AbortError') { failed.value = true; showFeedback(error.message || '首页暂时无法读取') } }
  finally { if (alive && revision === version) loading.value = false }
}
onMounted(load)
onBeforeUnmount(() => { alive = false; revision++; controller?.abort() })
const today = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const cards = computed(() => {
  const s = data.value?.stats || {}
  return teacher.value ? [
    { label: '班级学生', value: s.students, unit: '人', icon: 'user', tone: 'blue', to: '/teaching' },
    { label: '已发布作业', value: s.assignments, unit: '份', icon: 'homework', tone: 'brand', to: '/assignments' },
    { label: '批改中', value: s.grading, unit: '份', icon: 'refresh', tone: 'amber' },
    { label: '待人工处理', value: s.attention, unit: '份', icon: 'edit', tone: 'rose', to: '#home-attention' },
  ] : [
    { label: '待交作业', value: s.pending, unit: '份', icon: 'homework', tone: 'brand', to: '/assignments?state=NOT_SUBMITTED' },
    { label: '批改中', value: s.grading, unit: '份', icon: 'refresh', tone: 'amber' },
    { label: '需要复习', value: s.weak, unit: '个标签', icon: 'book', tone: 'rose', to: '/learning/mastery?state=WEAK' },
    { label: '计划待完成', value: s.planPending, unit: '项', icon: 'audit', tone: 'blue', to: '/learning/plan' },
  ]
})
const selected = computed(() => data.value?.assignments?.find(a => String(a.id) === selectedId.value))
const submissionSegments = computed(() => [{ key: 'submitted', label: '已提交', count: selected.value?.submitted || 0, color: '#a7222c' }, { key: 'pending', label: '未提交', count: Math.max(0, (data.value?.stats.students || 0) - (selected.value?.submitted || 0)), color: '#e7e9ed' }])
const states = [{ key: 'MASTERED', label: '已掌握', color: '#4d957a' }, { key: 'DEVELOPING', label: '继续巩固', color: '#d9a251' }, { key: 'WEAK', label: '需要复习', color: '#b94956' }, { key: 'UNKNOWN', label: '暂无记录', color: '#dce0e5' }]
const masterySegments = computed(() => states.map(state => ({ ...state, count: data.value?.mastery?.find(item => item.state === state.key)?.count || 0 })))
const progress = computed(() => data.value?.plan.total ? Math.round(data.value.plan.done / data.value.plan.total * 100) : 0)
const percent = value => `${Math.max(0, Math.min(100, Number(value) || 0))}%`
const date = value => value ? new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) : '—'
const resultPath = item => `/${item.kind === 'PRACTICE' ? 'questions' : 'assignments'}/${item.itemId}`
function cardTag(card) { return !card.to ? 'div' : card.to.startsWith('#') ? 'a' : RouterLink }
</script>

<template>
  <AppShell current="home"><div class="page-container home-page">
    <header class="home-welcome"><div><p class="home-date">{{ today }}</p><h1>{{ teacher ? '教学工作台' : '我的学习空间' }}</h1><p>{{ teacher ? '查看班级学习情况，处理教学事项。' : '查看作业与学习安排，继续接下来的学习。' }}</p></div><div class="home-header-actions"><button class="button button--soft home-refresh" :disabled="loading" aria-label="刷新首页" @click="load"><AppIcon name="refresh" :size="17" /></button><RouterLink class="button button--primary" :to="teacher ? '/assignments?action=create' : '/conversations'"><AppIcon :name="teacher ? 'homework' : 'bot'" :size="18" />{{ teacher ? '布置作业' : '向助手提问' }}</RouterLink></div></header>
    <section v-if="loading" class="home-loading" aria-label="正在读取首页"><div v-for="n in 4" :key="n" class="home-skeleton"></div><div class="home-skeleton home-skeleton--wide"></div></section>
    <section v-else-if="failed" class="content-card home-empty"><AppIcon name="refresh" :size="30" /><h2>首页暂时无法读取</h2><p>请稍后重试。</p><button class="button button--primary" @click="load">重新加载</button></section>
    <template v-else-if="data">
      <section class="home-stats" aria-label="首页统计"><component :is="cardTag(card)" v-for="card in cards" :key="card.label" class="home-stat" :class="`home-tone-${card.tone}`" :to="card.to && !card.to.startsWith('#') ? card.to : undefined" :href="card.to?.startsWith('#') ? card.to : undefined"><div><span class="home-stat-label">{{ card.label }}</span><p><strong>{{ card.value ?? 0 }}</strong><span>{{ card.unit }}</span></p></div><span class="home-stat-icon"><AppIcon :name="card.icon" :size="23" /></span></component></section>
      <div class="home-main-grid">
        <template v-if="teacher">
          <section class="content-card home-panel home-mastery"><div class="home-panel-heading"><div><h2>班级掌握情况</h2><p>看看哪些内容值得再练习。</p></div><RouterLink class="home-text-link" to="/teaching">查看全部 <span>↗</span></RouterLink></div><div v-if="data.mastery.length" class="home-bars"><RouterLink v-for="point in data.mastery" :key="point.pointId" :to="`/teaching?tag=${point.pointId}`" class="home-bar" :title="`${point.name}：${point.mastery}%，${point.participants}/${data.stats.students}人有记录`"><span class="home-bar-title">{{ point.name }}</span><strong>{{ point.mastery }}<small>%</small></strong><span class="home-bar-track"><i :style="{ width: percent(point.mastery), background: point.mastery < 60 ? '#b94956' : point.mastery < 80 ? '#d9a251' : '#4d957a' }"></i></span></RouterLink><div class="home-bar-axis"><span>0%</span><span>50%</span><span>100%</span></div></div><div v-else class="home-empty home-empty--inline"><AppIcon name="audit" :size="34" /><h3>还没有掌握记录</h3><p>学生完成作答后，在这里查看班级情况。</p></div><RouterLink v-if="data.unknownTags" class="home-unknown" to="/teaching?state=UNKNOWN"><i></i>{{ data.unknownTags }} 个标签暂无记录<span>查看 ›</span></RouterLink></section>
          <section class="content-card home-panel"><div class="home-panel-heading"><div><h2>作业提交进度</h2><p>最近发布的作业。</p></div><AppIcon name="homework" :size="20" /></div><template v-if="selected"><label class="home-assignment-select"><span class="sr-only">选择作业</span><select v-model="selectedId"><option v-for="assignment in data.assignments" :key="assignment.id" :value="String(assignment.id)">{{ assignment.title }}</option></select></label><OverviewDonut :segments="submissionSegments" :value="`${selected.submitted}/${data.stats.students}`" caption="已提交 / 全班" label="作业提交进度" /><RouterLink class="home-panel-footer" :to="`/assignments/${selected.id}`">查看学生作答<AppIcon name="eye" :size="17" /></RouterLink></template><div v-else class="home-empty home-empty--inline"><AppIcon name="homework" :size="34" /><h3>还没有发布作业</h3><RouterLink class="button button--soft" to="/assignments?action=create">布置第一份作业</RouterLink></div></section>
        </template>
        <template v-else>
          <section class="content-card home-panel home-plan"><div class="home-panel-heading"><div><h2>继续学习</h2><p>从下一步开始，慢慢积累。</p></div><RouterLink class="home-text-link" to="/learning/plan">完整计划 <span>↗</span></RouterLink></div><template v-if="data.plan.total"><div class="home-plan-progress"><span>已完成 <strong>{{ data.plan.done }}</strong> / {{ data.plan.total }} 项</span><b>{{ progress }}%</b></div><div class="home-progress-track" role="progressbar" :aria-valuenow="data.plan.done" aria-valuemin="0" :aria-valuemax="data.plan.total" aria-label="计划完成进度"><i :style="{ width: `${progress}%` }"></i></div><div class="home-plan-tasks"><article v-for="(task, index) in data.plan.tasks" :key="task.id" class="home-plan-task"><span class="home-task-number">{{ String(index + 1).padStart(2, '0') }}</span><div><div class="home-task-heading"><h3>{{ task.title }}</h3><span>第 {{ task.day || 1 }} 天<span v-if="task.minutes"> · {{ task.minutes }} 分钟</span></span></div><div class="home-task-instruction assistant-markdown" v-html="renderAssistantMarkdown(task.instruction || '')"></div><div v-if="!task.resources.unavailable" class="home-task-resources"><RouterLink v-for="file in task.resources.materials" :key="file.id" class="button button--soft" :to="`/materials?material=${file.id}`"><AppIcon name="book" :size="15" />{{ file.title }}</RouterLink><RouterLink v-for="question in task.resources.questions" :key="question.id" class="button button--soft" :to="`/questions/${question.id}`"><AppIcon name="edit" :size="15" />{{ question.title }}</RouterLink></div><RouterLink v-else class="home-text-link" to="/learning/plan">资源已更新，调整计划 ›</RouterLink></div></article></div><p v-if="!data.plan.pending" class="home-plan-finished">这份计划已完成，去安排下一阶段的学习吧。</p></template><div v-else class="home-empty home-empty--inline"><span class="home-empty-orbit"><AppIcon name="book" :size="30" /></span><h3>安排接下来的学习</h3><p>选择目标和可用时间，生成适合自己的计划。</p><RouterLink class="button button--primary" to="/learning/plan">{{ data.plan.total === 0 ? '查看学习计划' : '生成学习计划' }}</RouterLink></div></section>
          <section class="content-card home-panel"><div class="home-panel-heading"><div><h2>我的掌握情况</h2><p>每一次练习，都是一点进步。</p></div><RouterLink class="home-text-link" to="/learning/mastery" aria-label="查看完整掌握情况">↗</RouterLink></div><OverviewDonut :segments="masterySegments" caption="知识标签" label="我的标签掌握情况" interactive @select="state => $router.push(`/learning/mastery?state=${state}`)" /><p v-if="!masterySegments.some(item => item.count)" class="home-no-tags">暂无知识标签</p><div class="home-quick-links"><RouterLink to="/questions"><AppIcon name="edit" />题库练习<span>›</span></RouterLink><RouterLink to="/materials"><AppIcon name="search" />查找讲义<span>›</span></RouterLink></div></section>
        </template>
      </div>
      <div class="home-bottom-grid">
        <section class="content-card home-panel"><div class="home-panel-heading"><div><h2>{{ teacher ? '最近作业' : '待交作业' }}</h2><p>{{ teacher ? '跟进近期的作答进展。' : '还有这些作业等你完成。' }}</p></div><RouterLink class="home-text-link" :to="teacher ? '/assignments' : '/assignments?state=NOT_SUBMITTED'">查看全部 <span>↗</span></RouterLink></div><div v-if="(teacher ? data.assignments : data.pending).length" class="home-row-list"><RouterLink v-for="assignment in (teacher ? data.assignments : data.pending)" :key="assignment.id" class="home-list-row" :to="`/assignments/${assignment.id}`"><span class="home-row-icon"><AppIcon name="homework" :size="19" /></span><div class="home-row-body"><strong>{{ assignment.title }}</strong><div class="home-row-detail"><span v-if="teacher">{{ assignment.submitted }} / {{ data.stats.students }} 人提交</span><template v-else><span v-for="tag in assignment.tags.slice(0, 2)" :key="tag" class="home-tag">{{ tag }}</span><span>满分 {{ assignment.maxScore }} 分</span></template></div></div><span v-if="teacher" class="home-row-date">{{ date(assignment.createdAt) }}</span><span class="home-row-arrow" aria-hidden="true">›</span></RouterLink></div><div v-else class="home-empty home-empty--small"><AppIcon name="homework" :size="28" /><p>{{ teacher ? '还没有发布作业' : '暂无待交作业' }}</p></div></section>
        <section :id="teacher ? 'home-attention' : 'home-results'" class="content-card home-panel"><div class="home-panel-heading"><div><h2>{{ teacher ? '待处理事项' : '最近批改' }}</h2><p>{{ teacher ? '这些作答需要人工查看。' : '查看刚刚收到的学习反馈。' }}</p></div><span class="home-panel-dot" :class="{ 'home-panel-dot--quiet': !(teacher ? data.attention : data.results).length }"></span></div><div v-if="(teacher ? data.attention : data.results).length" class="home-row-list"><RouterLink v-for="item in (teacher ? data.attention : data.results)" :key="item.submissionId" class="home-list-row" :to="teacher ? `/assignments/${item.assignmentId}` : resultPath(item)"><span class="home-row-icon" :class="{ 'home-row-icon--warning': teacher }"><AppIcon :name="teacher ? 'edit' : item.kind === 'PRACTICE' ? 'book' : 'homework'" :size="19" /></span><div class="home-row-body"><strong>{{ item.title }}</strong><div class="home-row-detail"><span v-if="teacher">{{ item.username }} · 等待人工批阅</span><template v-else><span>{{ item.kind === 'PRACTICE' ? '自主练习' : '班级作业' }}<template v-if="item.scoreSource === 'TEACHER'"> · 教师终评</template></span><span>{{ date(item.gradedAt) }}</span></template></div></div><span v-if="!teacher" class="home-result-score">{{ item.score }}<small>/{{ item.maxScore }}</small></span><span class="home-row-arrow" aria-hidden="true">›</span></RouterLink></div><div v-else class="home-empty home-empty--small"><AppIcon :name="teacher ? 'shield' : 'audit'" :size="28" /><p>{{ teacher ? '暂时没有需要处理的事项' : '完成作答后，在这里查看批改结果' }}</p></div></section>
      </div>
      <nav v-if="teacher" class="home-teacher-shortcuts" aria-label="教学快捷入口"><RouterLink to="/materials?action=upload"><AppIcon name="upload" /><div><strong>上传讲义</strong><span>准备课程资料</span></div><b>↗</b></RouterLink><RouterLink to="/questions?action=create"><AppIcon name="edit" /><div><strong>新建题目</strong><span>丰富自主练习</span></div><b>↗</b></RouterLink><RouterLink to="/assistants"><AppIcon name="bot" /><div><strong>助手配置</strong><span>管理教学助手</span></div><b>↗</b></RouterLink></nav>
    </template>
  </div></AppShell>
</template>
<style src="../home.css"></style>

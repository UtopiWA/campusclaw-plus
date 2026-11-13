<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import PagedList from './PagedList.vue'
import BaseModal from './BaseModal.vue'
import { apiRequest } from '../services/api.js'
import { showFeedback } from '../services/feedback.js'
import { completeLearningTask, generateLearningPlan, getLearningPlan } from '../services/learning.js'
import { renderAssistantMarkdown } from '../utils/assistantMarkdown.js'
const plan = ref({})
const goal = ref('')
const days = ref(7)
const dailyMinutes = ref(30)
const busy = ref(false)
const loading = ref(true)
const failed = ref(false)
const material = ref(null)
const controller = new AbortController()
const options = { signal: controller.signal }
let alive = true
let materialVersion = 0
async function load() {
  if (busy.value) return
  loading.value = true; failed.value = false
  try {
    const data = await getLearningPlan(options)
    if (alive) {
      plan.value = data
      days.value = data.days || 7
      dailyMinutes.value = data.dailyMinutes || 30
    }
  }
  catch (error) { if (alive && error.name !== 'AbortError') { failed.value = true; showFeedback(error.message || '计划读取失败') } }
  finally { if (alive) loading.value = false }
}
async function act(fn, message) {
  if (busy.value) return
  busy.value = true
  try { const data = await fn(); if (alive) { plan.value = data; showFeedback(message, 'success') } }
  catch (error) { if (alive && error.name !== 'AbortError') showFeedback(error.message || '操作失败') }
  finally { if (alive) busy.value = false }
}
function generate() {
  if (!validateBudget()) return
  return act(() => generateLearningPlan({ goal: goal.value.trim(), days: days.value, dailyMinutes: dailyMinutes.value }, options), '计划已更新')
}
const validDays = computed(() => Number.isInteger(days.value) && days.value >= 1 && days.value <= 14)
const validMinutes = computed(() => Number.isInteger(dailyMinutes.value) && dailyMinutes.value >= 10 && dailyMinutes.value <= 120)
function validateBudget() {
  if (!validDays.value) { showFeedback('计划天数应为1至14之间的整数'); return false }
  if (!validMinutes.value) { showFeedback('每天学习时长应为10至120分钟的整数'); return false }
  return true
}
async function openMaterial(id) {
  const version = ++materialVersion
  try { const text = await apiRequest(`/api/materials/${id}/content`, { ...options, responseType: 'text' }); if (alive && version === materialVersion) material.value = text }
  catch (error) { if (alive && version === materialVersion && error.name !== 'AbortError') showFeedback(error.message || '讲义读取失败') }
}
onMounted(load)
onBeforeUnmount(() => { alive = false; controller.abort() })
</script>
<template>
  <section class="learning-stack study-plan">
    <p v-if="loading" class="content-card learning-empty">正在读取学习计划…</p>
    <section v-else-if="failed" class="content-card learning-empty"><p>计划暂时无法读取。</p><button class="button button--soft" @click="load">重新加载</button></section>
    <template v-else>
      <section class="content-card learning-card">
        <div class="section-heading"><h2>学习计划</h2><button class="button button--soft" :disabled="busy" @click="load">刷新计划</button></div>
        <form class="learning-form" novalidate @submit.prevent="generate">
          <label>学习目标<input v-model="goal" maxlength="200" :disabled="busy" placeholder="留空时根据我的学情推荐" /></label>
          <div class="plan-budget"><label>计划天数<input v-model.number="days" type="number" min="1" max="14" step="1" :aria-invalid="!validDays || undefined" :disabled="busy" @blur="validateBudget" /></label><label>每天学习（分钟）<input v-model.number="dailyMinutes" type="number" min="10" max="120" step="1" :aria-invalid="!validMinutes || undefined" :disabled="busy" @blur="validateBudget" /></label></div>
          <button class="button button--primary" :disabled="busy">{{ busy ? '正在安排…' : plan.tasks?.length ? '调整学习计划' : '生成学习计划' }}</button>
        </form>
      </section>
      <section v-if="plan.id" class="content-card learning-card">
        <h2>我的计划</h2><p v-if="plan.goal">目标：{{ plan.goal }}</p><div class="assistant-markdown" v-html="renderAssistantMarkdown(plan.message)"></div>
        <p v-if="plan.generationState === 'RUNNING'" class="learning-note">正在生成新安排，可稍后刷新查看。</p>
        <PagedList :items="plan.tasks || []" always-show :reset-key="plan.revision" label="计划任务" v-slot="{ pageItems }">
          <ol class="plan-task-list"><li v-for="task in pageItems" :key="task.id">
            <div>
              <p v-if="task.day" class="learning-note">第 {{ task.day }} 天 · 约 {{ task.minutes }} 分钟</p>
              <h3>{{ task.title }}<span v-if="task.status === 'DONE'" class="learning-badge">已完成</span></h3>
              <div v-if="task.instruction" class="assistant-markdown" v-html="renderAssistantMarkdown(task.instruction)"></div>
              <p v-if="task.status !== 'DONE' && task.resources.unavailable" class="learning-note">资源已更新或暂不可用，请调整计划。</p>
              <div class="learning-actions"><button v-for="file in task.resources.materials" :key="file.id" class="button button--soft" @click="openMaterial(file.id)">{{ file.title }}</button><RouterLink v-for="question in task.resources.questions || []" :key="question.id" class="button button--soft" :to="`/questions/${question.id}`">{{ question.title }}</RouterLink></div>
            </div>
            <button v-if="task.status !== 'DONE' && task.type !== 'PRACTICE'" class="button button--primary" :disabled="busy || task.resources.unavailable" @click="act(() => completeLearningTask(plan.id, task.id, options), '已标记完成')">标记完成</button>
          </li></ol>
        </PagedList>
      </section>
    </template>
    <BaseModal v-if="material !== null" title="学习讲义" wide @close="materialVersion++; material = null"><div class="assistant-markdown" v-html="renderAssistantMarkdown(material)"></div></BaseModal>
  </section>
</template>
<style scoped>
.plan-budget { display: grid; grid-template-columns: repeat(2, minmax(0, 220px)); gap: 16px; }
.plan-budget input[aria-invalid=true] { border-color: var(--danger); }
.plan-task-list h3 { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
@media (max-width: 470px) { .plan-budget { grid-template-columns: 1fr; } }
</style>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import AppShell from '../components/AppShell.vue'
import MasteryTable from '../components/MasteryTable.vue'
import LearningPlan from '../components/LearningPlan.vue'
import { getLearningProfile } from '../services/learning.js'
import { showFeedback } from '../services/feedback.js'
const props = defineProps({ section: { type: String, default: 'mastery' } })
const data = ref(null)
const loading = ref(true)
const failed = ref(false)
let controller
let alive = true
let revision = 0
async function load() {
  controller?.abort()
  controller = new AbortController()
  const version = ++revision
  loading.value = true
  failed.value = false
  try { const result = await getLearningProfile({ signal: controller.signal }); if (alive && version === revision) data.value = result }
  catch (error) { if (alive && version === revision && error.name !== 'AbortError') { failed.value = true; showFeedback(error.message || '学情暂时无法读取') } }
  finally { if (alive && version === revision) loading.value = false }
}
watch(() => props.section, (section) => {
  revision++
  controller?.abort()
  if (section === 'mastery') load()
}, { immediate: true })
onBeforeUnmount(() => { alive = false; revision++; controller?.abort() })
</script>
<template>
  <AppShell current="learning"><div class="page-container learning-page">
    <header class="page-heading"><div><p class="breadcrumb">学习情况 <span>/</span> 我的学情</p><h1>我的学情</h1><p>查看掌握情况，安排接下来的学习。</p></div><button v-if="section === 'mastery'" class="button button--soft" :disabled="loading" @click="load">刷新</button></header>
    <nav class="learning-tabs" aria-label="我的学情子页面">
      <RouterLink to="/learning/mastery" class="button" :class="section === 'mastery' ? 'button--primary' : 'button--soft'">掌握情况</RouterLink>
      <RouterLink to="/learning/plan" class="button" :class="section === 'plan' ? 'button--primary' : 'button--soft'">学习计划</RouterLink>
    </nav>
    <template v-if="section === 'mastery'">
      <p v-if="loading" class="content-card learning-empty">正在读取学情…</p>
      <section v-else-if="failed" class="content-card learning-empty"><p>学情暂时无法读取。</p><button class="button button--soft" @click="load">重新加载</button></section>
      <MasteryTable v-else-if="data" :points="data.points" />
    </template>
    <LearningPlan v-else />
  </div></AppShell>
</template>

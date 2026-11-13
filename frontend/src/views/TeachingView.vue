<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import MasteryTable from '../components/MasteryTable.vue'
import { getClassLearning } from '../services/learning.js'
import { showFeedback } from '../services/feedback.js'
const from = ref('')
const to = ref('')
const data = ref(null)
const loading = ref(true)
const failed = ref(false)
const controller = new AbortController()
let alive = true
let revision = 0
async function load() {
  const version = ++revision
  loading.value = true
  failed.value = false
  try { const result = await getClassLearning(from.value, to.value, { signal: controller.signal }); if (alive && version === revision) data.value = result }
  catch (error) { if (alive && version === revision && error.name !== 'AbortError') { failed.value = true; showFeedback(error.message || '学情暂时无法读取') } }
  finally { if (alive && version === revision) loading.value = false }
}
onMounted(load)
onBeforeUnmount(() => { alive = false; revision++; controller.abort() })
</script>
<template>
  <AppShell current="teaching"><div class="page-container learning-page">
    <header class="page-heading"><div><p class="breadcrumb">学习情况 <span>/</span> 教学与学情</p><h1>教学与学情</h1><p>查看各个知识标签的全班掌握情况。</p></div><button class="button button--soft" :disabled="loading" @click="load">刷新</button></header>
    <form class="content-card learning-card learning-inline-form" @submit.prevent="load"><label>开始日期<input v-model="from" type="date" /></label><label>结束日期<input v-model="to" type="date" /></label><button class="button button--primary" :disabled="loading">查询</button><button type="button" class="button button--soft" :disabled="loading" @click="from = ''; to = ''; load()">全部时间</button></form>
    <p v-if="loading" class="content-card learning-empty">正在读取学情…</p>
    <section v-else-if="failed" class="content-card learning-empty"><p>学情暂时无法读取。</p><button class="button button--soft" @click="load">重新加载</button></section>
    <MasteryTable v-else-if="data" :points="data.points" :students="data.students" teacher />
  </div></AppShell>
</template>

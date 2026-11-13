<script setup>
import { computed, ref } from 'vue'
import PagedList from './PagedList.vue'
const props = defineProps({ points: { type: Array, default: () => [] }, teacher: Boolean, students: { type: Number, default: 0 } })
const keyword = ref('')
const selectedId = ref(null)
const selected = computed(() => props.points.find((point) => point.pointId === selectedId.value) || null)
const filtered = computed(() => props.points.filter((p) => p.name.includes(keyword.value.trim())))
const labels = { UNKNOWN: '暂无记录', WEAK: '需要复习', DEVELOPING: '继续巩固', MASTERED: '已掌握' }
const percentage = (value) => value == null ? '—' : `${value}%`
const date = (value) => value ? new Date(value).toLocaleString('zh-CN') : '—'
</script>
<template>
  <section class="content-card learning-card">
    <div class="section-heading"><h2>{{ teacher ? '全班掌握情况' : '我的掌握情况' }}</h2><label class="learning-search">查找标签<input v-model="keyword" type="search" placeholder="输入标签名称" /></label></div>
    <p v-if="!filtered.length" class="learning-empty">{{ points.length ? '没有匹配的标签。' : '暂无知识标签，请先在讲义页添加标签。' }}</p>
    <PagedList v-else :items="filtered" always-show :reset-key="keyword" label="掌握情况" v-slot="{ pageItems }"><div class="learning-table-wrap"><table class="learning-table"><thead><tr><th>知识标签</th><th>{{ teacher ? '平均掌握度' : '掌握度' }}</th><template v-if="teacher"><th>有记录 / 全班</th><th>已掌握</th><th>需复习</th><th>暂无记录</th></template><template v-else><th>近期有效作答</th><th>最近作答时间</th><th>状态</th></template></tr></thead><tbody><tr v-for="point in pageItems" :key="point.pointId ?? point.id"><td><button v-if="teacher" class="mastery-link" @click="selectedId = point.pointId">{{ point.name }}</button><span v-else>{{ point.name }}</span><span v-if="!point.enabled" class="learning-note">（已停用）</span></td><td>{{ percentage(point.mastery) }}</td><template v-if="teacher"><td>{{ point.participants }} / {{ students }}</td><td>{{ point.mastered }}</td><td>{{ point.weak }}</td><td>{{ point.notParticipating }}</td></template><template v-else><td>{{ point.sampleCount }} 次</td><td>{{ date(point.lastStudiedAt) }}</td><td>{{ labels[point.state] }}</td></template></tr></tbody></table></div></PagedList>
  </section>
  <section v-if="teacher && selected" class="content-card learning-card"><div class="section-heading"><h2>{{ selected.name }} · 学生明细</h2><button class="button button--soft" @click="selectedId = null">收起</button></div><PagedList :items="selected.students" always-show :reset-key="selected.pointId" label="学生掌握情况" v-slot="{ pageItems }"><div class="learning-table-wrap"><table class="learning-table"><thead><tr><th>学生</th><th>掌握度</th><th>近期有效作答</th><th>最近作答时间</th><th>状态</th></tr></thead><tbody><tr v-for="student in pageItems" :key="student.studentId"><td>{{ student.username }}</td><td>{{ percentage(student.mastery) }}</td><td>{{ student.sampleCount }} 次</td><td>{{ date(student.lastStudiedAt) }}</td><td>{{ labels[student.state] }}</td></tr></tbody></table></div></PagedList></section>
</template>
<style scoped>
.learning-search { display: flex; gap: 10px; align-items: center; font-size: 14px; }
.learning-search input { min-width: 0; max-width: 180px; padding: 9px; border: 1px solid var(--line); border-radius: 8px; }
.mastery-link { border: 0; background: none; padding: 0; color: var(--brand-700); cursor: pointer; font: inherit; text-align: left; }
@media(max-width: 600px) { .learning-search { flex-wrap: wrap; } }
</style>

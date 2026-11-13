<script setup>
import { computed, ref } from 'vue'
import BaseModal from './BaseModal.vue'
import PagedList from './PagedList.vue'
import { saveLearningPoint, replaceMaterialTags } from '../services/learning.js'
import { showFeedback } from '../services/feedback.js'
const props = defineProps({ tags: { type: Array, default: () => [] }, materialTags: { type: Array, default: () => [] } })
const emit = defineEmits(['updated'])
const expanded = ref(false)
const editing = ref(null)
const target = ref(null)
const selected = ref([])
const keyword = ref('')
const busy = ref(false)
const filtered = computed(() => props.tags.filter((tag) => tag.name.includes(keyword.value.trim())))
const enabled = computed(() => props.tags.filter((tag) => tag.enabled))
function edit(tag = null) { editing.value = tag ? { ...tag } : { code: `T${crypto.randomUUID().replaceAll('-', '').slice(0, 24)}`, name: '', description: '', enabled: true } }
function open(material) {
  target.value = material
  selected.value = props.materialTags.filter((row) => row.id === material.id && props.tags.some((tag) => tag.id === row.pointId && tag.enabled)).map((row) => row.pointId)
}
defineExpose({ open })
async function save() {
  if (!editing.value.name.trim()) return showFeedback('请填写标签名称')
  if (busy.value) return
  busy.value = true
  try { const { id, code, name, description, enabled } = editing.value; await saveLearningPoint(id, { code, name: name.trim(), description, enabled }); editing.value = null; emit('updated'); showFeedback('标签已保存', 'success') }
  catch (error) { showFeedback(error.message || '标签保存失败') }
  finally { busy.value = false }
}
async function bind() {
  if (selected.value.length > 20) return showFeedback('每份讲义最多选择20个标签')
  if (busy.value) return
  busy.value = true
  try { await replaceMaterialTags(target.value.id, selected.value); target.value = null; emit('updated'); showFeedback('讲义标签已保存', 'success') }
  catch (error) { showFeedback(error.message || '讲义标签保存失败') }
  finally { busy.value = false }
}
</script>
<template>
  <section class="content-card learning-card material-tags"><div class="section-heading"><div><h2>知识标签</h2></div><div class="learning-actions"><button class="button button--soft" @click="expanded = !expanded">{{ expanded ? '收起管理' : '管理标签' }}</button><button class="button button--primary" @click="edit()">新建标签</button></div></div>
    <template v-if="expanded"><label class="learning-form">查找标签<input v-model="keyword" type="search" placeholder="输入标签名称" /></label><p v-if="!filtered.length" class="learning-empty">暂无匹配标签。</p><PagedList :items="filtered" :reset-key="keyword" label="标签" v-slot="{ pageItems }"><ul class="tag-manager-list"><li v-for="tag in pageItems" :key="tag.id"><div><strong>{{ tag.name }}</strong><span v-if="!tag.enabled">（已停用）</span><p v-if="tag.description">{{ tag.description }}</p></div><button class="button button--soft" @click="edit(tag)">编辑</button></li></ul></PagedList></template>
  </section>
  <BaseModal v-if="editing" :title="editing.id ? '编辑标签' : '新建标签'" @close="!busy && (editing = null)"><form id="tag-edit" class="learning-form" @submit.prevent="save"><label>名称<input v-model="editing.name" maxlength="100" required /></label><label>说明（选填）<textarea v-model="editing.description" rows="3" maxlength="2000"></textarea></label><label class="learning-choice"><input v-model="editing.enabled" type="checkbox" />启用标签</label></form><template #footer><button class="button button--soft" :disabled="busy" @click="editing = null">取消</button><button form="tag-edit" class="button button--primary" :disabled="busy">保存</button></template></BaseModal>
  <BaseModal v-if="target" title="讲义标签" @close="!busy && (target = null)"><p>{{ target.title }}</p><p v-if="!enabled.length">请先新建标签。</p><PagedList :items="enabled" :reset-key="target.id" label="可选标签" v-slot="{ pageItems }"><label v-for="tag in pageItems" :key="tag.id" class="learning-choice"><input v-model="selected" type="checkbox" :value="tag.id" :disabled="busy" />{{ tag.name }}</label></PagedList><p class="learning-note">已选 {{ selected.length }} 个（最多20个）</p><template #footer><button class="button button--soft" :disabled="busy" @click="target = null">取消</button><button class="button button--primary" :disabled="busy" @click="bind">保存</button></template></BaseModal>
</template>
<style scoped>
.tag-manager-list { list-style: none; padding: 0; margin: 12px 0 0; }
.tag-manager-list li { display: flex; justify-content: space-between; gap: 16px; padding: 14px 0; border-bottom: 1px solid var(--line); }
.tag-manager-list p { color: var(--ink-500); font-size: 14px; margin: 6px 0 0; overflow-wrap: anywhere; }
</style>

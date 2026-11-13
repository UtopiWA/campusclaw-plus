<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  resetKey: { type: [String, Number], default: '' },
  page: { type: Number, default: undefined },
  pageSize: { type: Number, default: undefined },
  followEnd: Boolean,
  alwaysShow: Boolean,
  label: { type: String, default: '列表' },
})
const emit = defineEmits(['update:page', 'update:pageSize'])
const page = ref(props.page || 1)
const size = ref(props.pageSize || 10)
const pages = computed(() => Math.max(1, Math.ceil(props.items.length / size.value)))
const offset = computed(() => (page.value - 1) * size.value)
const pageItems = computed(() => props.items.slice(offset.value, offset.value + size.value))
watch(() => props.resetKey, () => { page.value = props.followEnd ? pages.value : (props.page || 1) }, { immediate: true })
watch(() => props.items.length, (length, previous) => {
  page.value = props.followEnd && length > previous ? pages.value : Math.min(page.value, pages.value)
}, { immediate: true })
watch(() => props.page, (value) => { if (value) page.value = Math.max(1, Math.min(value, pages.value)) })
watch(() => props.pageSize, (value) => { if ([10, 20, 50].includes(value)) size.value = value })
watch(page, (value) => emit('update:page', value))
watch(size, (value) => { page.value = props.followEnd ? pages.value : 1; emit('update:pageSize', value) })
</script>

<template>
  <div class="paged-list">
    <slot :page-items="pageItems" :offset="offset" />
    <nav v-if="alwaysShow || items.length > 10" class="list-pagination" :aria-label="`${label}分页`">
      <span>共 {{ items.length }} 条</span>
      <label>每页 <select v-model.number="size" :aria-label="`${label}每页条数`"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select></label>
      <button type="button" class="button button--soft" :disabled="page === 1" @click="page--">上一页</button>
      <span aria-live="polite">{{ page }} / {{ pages }}</span>
      <button type="button" class="button button--soft" :disabled="page === pages" @click="page++">下一页</button>
    </nav>
  </div>
</template>

<style scoped>
.paged-list { min-width: 0; }
.list-pagination { grid-column: 1 / -1; display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 10px; padding: 16px 8px; color: var(--ink-500); font-size: 14px; }
.list-pagination label { display: flex; align-items: center; gap: 5px; }
.list-pagination select { padding: 5px; border: 1px solid var(--line); border-radius: 6px; color: var(--ink-700); background: #fff; }
.list-pagination .button { min-height: 32px; padding: 6px 9px; }
</style>

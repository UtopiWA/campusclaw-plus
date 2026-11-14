<script setup>
import { computed } from 'vue'
const props = defineProps({ segments: { type: Array, required: true }, value: { type: [String, Number], default: undefined }, caption: { type: String, default: '' }, label: { type: String, required: true }, interactive: Boolean })
const emit = defineEmits(['select'])
const total = computed(() => props.segments.reduce((sum, item) => sum + item.count, 0))
const arcs = computed(() => {
  let offset = 0
  return props.segments.map(item => { const length = total.value ? item.count / total.value * 100 : 0; const arc = { ...item, length, offset }; offset += length; return arc })
})
const description = computed(() => `${props.label}：${props.segments.map(item => `${item.label}${item.count}`).join('，')}`)
</script>
<template>
  <div class="overview-donut">
    <div class="overview-donut-graphic">
      <svg viewBox="0 0 144 144" role="img" :aria-label="description"><circle class="donut-track" cx="72" cy="72" r="56" fill="none" stroke-width="13" /><circle v-for="arc in arcs.filter(item => item.count > 0)" :key="arc.key" cx="72" cy="72" r="56" fill="none" stroke-width="13" pathLength="100" :stroke="arc.color" :stroke-dasharray="`${arc.length} ${100 - arc.length}`" :stroke-dashoffset="-arc.offset" transform="rotate(-90 72 72)"><title>{{ arc.label }}：{{ arc.count }}</title></circle></svg>
      <div class="donut-center" aria-hidden="true"><strong>{{ value ?? total }}</strong><span>{{ caption }}</span></div>
    </div>
    <div class="donut-legend"><component :is="interactive ? 'button' : 'div'" v-for="item in segments" :key="item.key" class="donut-legend-row" :type="interactive ? 'button' : undefined" @click="interactive && emit('select', item.key)"><i :style="{ background: item.color }"></i><span>{{ item.label }}</span><strong>{{ item.count }}</strong><span v-if="interactive" class="legend-arrow" aria-hidden="true">›</span></component></div>
  </div>
</template>
<style scoped>
.overview-donut { display: grid; grid-template-columns: minmax(120px, 170px) minmax(0, 1fr); align-items: center; gap: 24px; }
.overview-donut-graphic { position: relative; width: 100%; aspect-ratio: 1; }
svg { display: block; width: 100%; height: 100%; }
.donut-track { stroke: #edf0f2; }
.donut-center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; pointer-events: none; }
.donut-center strong { font-size: clamp(24px, 2.2vw, 32px); letter-spacing: -1px; color: var(--ink-900); }
.donut-center span { font-size: 13px; color: var(--ink-500); }
.donut-legend { display: grid; gap: 8px; min-width: 0; }
.donut-legend-row { display: flex; align-items: center; gap: 9px; width: 100%; padding: 8px 0; color: var(--ink-700); background: transparent; border: 0; text-align: left; font-size: 14px; }
button.donut-legend-row:hover { color: var(--brand-700); }
button:focus-visible { outline: 3px solid var(--brand-100); outline-offset: 3px; border-radius: 5px; }
.donut-legend-row i { width: 9px; height: 9px; flex-shrink: 0; border-radius: 50%; }
.donut-legend-row strong { margin-left: auto; color: var(--ink-900); font-size: 16px; font-variant-numeric: tabular-nums; }
.legend-arrow { color: var(--ink-500); }
@media(max-width: 400px) { .overview-donut { gap: 15px; grid-template-columns: 125px minmax(0, 1fr); } }
</style>

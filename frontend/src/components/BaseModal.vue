<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import AppIcon from './AppIcon.vue'

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  wide: { type: Boolean, default: false },
})

const emit = defineEmits(['close'])
const dialog = ref(null)
const titleId = `modal-title-${useId()}`
let previousFocus = null

function handleKeydown(event) {
  if (event.key === 'Escape') emit('close')
  if (event.key !== 'Tab') return
  const items = [...(dialog.value?.querySelectorAll('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), a[href]') || [])]
  if (!items.length) { event.preventDefault(); dialog.value?.focus(); return }
  if (event.shiftKey && (document.activeElement === items[0] || document.activeElement === dialog.value)) {
    event.preventDefault(); items.at(-1).focus()
  } else if (!event.shiftKey && document.activeElement === items.at(-1)) {
    event.preventDefault(); items[0].focus()
  }
}

// Give keyboard users an immediate dialog focus target and clean up the global Escape listener.
onMounted(async () => {
  previousFocus = document.activeElement
  window.addEventListener('keydown', handleKeydown)
  await nextTick()
  dialog.value?.focus()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (previousFocus?.isConnected) previousFocus.focus()
})
</script>

<template>
  <div class="modal-backdrop" @mousedown.self="emit('close')">
    <section
      ref="dialog"
      class="modal-card"
      :class="{ 'modal-card--wide': wide }"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      tabindex="-1"
    >
      <header class="modal-header">
        <div>
          <h2 :id="titleId">{{ title }}</h2>
          <p v-if="description">{{ description }}</p>
        </div>
        <button class="icon-button" type="button" aria-label="关闭弹窗" title="关闭" @click="emit('close')">
          <AppIcon name="close" />
        </button>
      </header>
      <div class="modal-body"><slot /></div>
      <footer v-if="$slots.footer" class="modal-footer"><slot name="footer" /></footer>
    </section>
  </div>
</template>

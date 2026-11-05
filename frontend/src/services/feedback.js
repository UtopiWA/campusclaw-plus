import { customRef, ref } from 'vue'

export const feedbackMessages = ref([])
const timers = new Map()
let sequence = 0

export function dismissFeedback(id) {
  clearTimeout(timers.get(id))
  timers.delete(id)
  feedbackMessages.value = feedbackMessages.value.filter((item) => item.id !== id)
}

export function showFeedback(message, type = 'error') {
  if (!message) return
  const id = ++sequence
  feedbackMessages.value.push({ id, message: String(message), type })
  if (feedbackMessages.value.length > 4) dismissFeedback(feedbackMessages.value[0].id)
  timers.set(id, setTimeout(() => dismissFeedback(id), type === 'success' ? 4000 : 6000))
}

// 沿用页面的错误/成功状态写法，每次赋值都提示，重复同文案也不丢失。
export function feedbackRef(type = 'error') {
  let value = ''
  return customRef((track, trigger) => ({
    get() { track(); return value },
    set(next) { value = next; if (next) showFeedback(next, type); trigger() },
  }))
}

export function clearFeedback() {
  for (const id of timers.keys()) dismissFeedback(id)
}

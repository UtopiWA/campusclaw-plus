<script setup>
import AppIcon from './AppIcon.vue'
import { dismissFeedback, feedbackMessages } from '../services/feedback.js'
</script>

<template>
  <div class="app-feedback" aria-label="操作消息">
    <TransitionGroup name="feedback-pop">
      <div v-for="item in feedbackMessages" :key="item.id" class="app-feedback__item"
        :class="`app-feedback__item--${item.type}`" :role="item.type === 'error' ? 'alert' : 'status'">
        <span class="app-feedback__symbol" aria-hidden="true">{{ item.type === 'success' ? '✓' : '!' }}</span>
        <span class="app-feedback__text">{{ item.message }}</span>
        <button class="icon-button" type="button" aria-label="关闭消息" @click="dismissFeedback(item.id)">
          <AppIcon name="close" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.app-feedback { position: fixed; top: 20px; left: 50%; transform: translateX(-50%); width: min(520px, calc(100vw - 32px)); z-index: 3000; display: grid; gap: 10px; pointer-events: none; }
.app-feedback__item { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid var(--line); border-radius: 12px; background: #fff; box-shadow: 0 8px 28px #18302a26; pointer-events: auto; }
.app-feedback__symbol { display: grid; place-items: center; flex: 0 0 24px; height: 24px; border-radius: 50%; font-weight: 700; background: var(--danger-bg, #fff0ef); color: var(--danger); }
.app-feedback__item--success .app-feedback__symbol { color: var(--success); background: var(--success-bg); }
.app-feedback__text { flex: 1; min-width: 0; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.app-feedback__item .icon-button { flex-shrink: 0; }
.feedback-pop-enter-active, .feedback-pop-leave-active { transition: opacity .18s, transform .18s; }
.feedback-pop-enter-from, .feedback-pop-leave-to { opacity: 0; transform: translateY(-10px); }
@media (prefers-reduced-motion: reduce) { .feedback-pop-enter-active, .feedback-pop-leave-active { transition: none; } }
</style>

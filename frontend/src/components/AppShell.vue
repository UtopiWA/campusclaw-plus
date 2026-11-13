<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { authState, logout } from '../services/auth.js'
import { showFeedback } from '../services/feedback.js'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  current: { type: String, required: true },
})

const router = useRouter()
const isTeacher = computed(() => authState.user?.role === 'teacher')
const roleLabel = computed(() => isTeacher.value ? '教师' : '学生')

// 统一导航避免讲义检索与智能体问答出现两个职责重叠的入口。
const navItems = computed(() => isTeacher.value
  ? [
      { key: 'home', label: '教师首页', icon: 'home' },
      { key: 'materials', label: '讲义与检索', icon: 'book', to: '/materials' },
      { key: 'assistant-chat', label: '助手对话', icon: 'bot', to: '/conversations' },
      { key: 'assistant-config', label: '助手配置', icon: 'layers', to: '/assistants' },
      { key: 'assignments', label: '作业与批改', icon: 'homework', to: '/assignments' },
      { key: 'questions', label: '题库', icon: 'book', to: '/questions' },
      { key: 'teaching', label: '教学与学情', icon: 'audit', to: '/teaching' },
    ]
  : [
      { key: 'home', label: '学生首页', icon: 'home' },
      { key: 'materials', label: '讲义与检索', icon: 'book', to: '/materials' },
      { key: 'assistant-chat', label: '助手对话', icon: 'bot', to: '/conversations' },
      { key: 'assignments', label: '作业', icon: 'homework', to: '/assignments' },
      { key: 'questions', label: '题库', icon: 'book', to: '/questions' },
      { key: 'learning', label: '我的学情', icon: 'audit', to: '/learning' },
    ])

async function signOut() {
  try { await logout() }
  catch (exception) { showFeedback(exception.message || '退出请求失败，请稍后重试') }
  finally { await router.replace('/login') }
}
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-symbol" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
        <span class="brand-copy"><strong>CampusClaw</strong><small>智能教学平台</small></span>
      </div>

      <nav class="sidebar-nav" aria-label="主导航">
        <p class="nav-section-label">教学空间</p>
        <template v-for="item in navItems" :key="item.key">
          <RouterLink
            v-if="item.to"
            :to="item.to"
            class="nav-item"
            :class="{ active: props.current === item.key }"
            :aria-current="props.current === item.key ? 'page' : undefined"
            :aria-label="item.label"
            :title="item.label"
          >
            <AppIcon :name="item.icon" />
            <span class="nav-item-label">{{ item.label }}</span>
          </RouterLink>
          <span
            v-else
            class="nav-item nav-item--disabled"
            :aria-label="`${item.label}，暂未开放`"
            :title="`${item.label} · 暂未开放`"
          >
            <AppIcon :name="item.icon" />
            <span class="nav-item-label">{{ item.label }}</span>
            <span class="coming-soon">待建设</span>
          </span>
        </template>
      </nav>

    </aside>

    <div class="app-body">
      <header class="app-topbar">
        <div class="mobile-brand">
          <span class="brand-symbol" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
          <strong>CampusClaw</strong>
        </div>
        <div class="class-context">
          <span class="status-dot"></span>
          <span>{{ authState.user?.className }}</span>
          <span class="context-divider"></span>
          <span>{{ roleLabel }}工作空间</span>
        </div>
        <div class="topbar-account">
          <span class="user-avatar">{{ isTeacher ? '师' : '生' }}</span>
          <span class="account-copy"><strong>{{ authState.user?.username }}</strong><small>{{ roleLabel }}</small></span>
          <button class="topbar-logout" type="button" title="退出登录" @click="signOut">
            <AppIcon name="logout" /><span>退出</span>
          </button>
        </div>
      </header>

      <main class="workspace"><slot /></main>
    </div>
  </div>
</template>

<script setup>
import { feedbackRef } from '../services/feedback.js'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { login } from '../services/auth.js'

const username = ref('')
const password = ref('')
const error = feedbackRef('error')
const busy = ref(false)
const route = useRoute()
const router = useRouter()

async function submit() {
  // 优先恢复登录前的页面，其余情况进入当前角色的首页。
  error.value = ''
  busy.value = true
  try {
    await login(username.value.trim(), password.value)
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    await router.replace(target)
  } catch (exception) {
    error.value = exception.status === 401 ? '账号或密码错误，请重新输入。' : exception.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-layout">
    <section class="login-brand-panel">
      <div class="login-brand">
        <span class="brand-symbol brand-symbol--light" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
        <div><strong>CampusClaw</strong><span>智能教学平台</span></div>
      </div>
      <div class="login-hero">
        <h1>课程资料、助手对话<br />与作业批改</h1>
        <p>整理课程讲义、使用教学助手，查看作业与学习反馈。</p>
        <ul>
          <li><span><AppIcon name="book" /></span><div><strong>讲义与检索</strong><small>上传讲义，查找课程内容</small></div></li>
          <li><span><AppIcon name="bot" /></span><div><strong>助手对话</strong><small>围绕课程提问，获取讲解</small></div></li>
          <li><span><AppIcon name="homework" /></span><div><strong>作业批改</strong><small>提交作答，查看评分与教师评语</small></div></li>
        </ul>
      </div>
      <p class="login-panel-footer">CampusClaw · 教学空间</p>
    </section>

    <section class="login-form-panel">
      <div class="login-form-wrap">
        <div class="mobile-login-brand"><span class="brand-symbol" aria-hidden="true"><i></i><i></i><i></i><i></i></span><strong>CampusClaw</strong></div>
        <h2>登录教学空间</h2>
        <p class="login-intro">使用教师或学生账号登录。</p>
        <form class="login-form" @submit.prevent="submit">
          <label><span>账号</span><span class="input-with-icon"><AppIcon name="user" /><input v-model="username" name="username" autocomplete="username" placeholder="请输入账号" required /></span></label>
          <label><span>密码</span><span class="input-with-icon"><AppIcon name="lock" /><input v-model="password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" required /></span></label>

          <button class="button button--primary login-submit" type="submit" :disabled="busy">{{ busy ? '正在验证…' : '登录' }}</button>
        </form>
      </div>
    </section>
  </main>
</template>

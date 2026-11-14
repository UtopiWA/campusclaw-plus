import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import MaterialsView from '../views/MaterialsView.vue'
import AgentConfigView from '../views/AgentConfigView.vue'
import ConversationsView from '../views/ConversationsView.vue'
import QuestionBankView from '../views/QuestionBankView.vue'
import AssignmentsView from '../views/AssignmentsView.vue'
import LearningView from '../views/LearningView.vue'
import TeachingView from '../views/TeachingView.vue'
import HomeView from '../views/HomeView.vue'
import { authState, loadCurrentUser } from '../services/auth.js'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', component: LoginView },
    { path: '/home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/materials', component: MaterialsView, meta: { requiresAuth: true } },
    { path: '/search', redirect: '/materials' },
    { path: '/assistants', component: AgentConfigView, meta: { requiresAuth: true, role: 'teacher' } },
    { path: '/conversations/:id?', component: ConversationsView, meta: { requiresAuth: true } },
    { path: '/questions/:id?', component: QuestionBankView, meta: { requiresAuth: true } },
    { path: '/assignments/:id?', component: AssignmentsView, meta: { requiresAuth: true } },
    { path: '/learning', redirect: '/learning/mastery', meta: { requiresAuth: true, role: 'student' } },
    { path: '/learning/mastery', component: LearningView, props: { section: 'mastery' }, meta: { requiresAuth: true, role: 'student' } },
    { path: '/learning/plan', component: LearningView, props: { section: 'plan' }, meta: { requiresAuth: true, role: 'student' } },
    { path: '/teaching', component: TeachingView, meta: { requiresAuth: true, role: 'teacher' } },
    { path: '/:pathMatch(.*)*', redirect: '/home' },
  ],
})

export async function authGuard(to) {
  // This guard provides navigation UX; the backend remains the authoritative access-control boundary.
  if (!authState.loaded) {
    await loadCurrentUser()
  }
  if (to.meta.requiresAuth && !authState.user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.role && authState.user?.role !== to.meta.role) {
    return '/conversations'
  }
  if (to.path === '/login' && authState.user) {
    return '/home'
  }
  return true
}

router.beforeEach(authGuard)

if (typeof window !== 'undefined') {
  window.addEventListener('campusclaw:unauthorized', () => {
    if (router.currentRoute.value.path !== '/login') {
      router.replace('/login')
    }
  })
}

export default router

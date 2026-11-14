import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
const mocks = vi.hoisted(() => ({ authState: { user: { role: 'teacher' } }, apiRequest: vi.fn() }))
vi.mock('../services/auth.js', () => ({ authState: mocks.authState }))
vi.mock('../services/api.js', () => ({ apiRequest: mocks.apiRequest }))
import HomeView from './HomeView.vue'
import { clearFeedback, feedbackMessages } from '../services/feedback.js'
let wrapper
const teacher = () => ({ stats: { students: 4, assignments: 2, grading: 1, attention: 1 }, mastery: [{ pointId: 7, name: '分数', mastery: 40, participants: 2 }], unknownTags: 1, assignments: [{ id: 1, title: '第一份作业', submitted: 2 }, { id: 2, title: '第二份作业', submitted: 0 }], attention: [{ assignmentId: 1, submissionId: 3, title: '第一份作业', username: 'student' }] })
const student = () => ({ stats: { pending: 1, grading: 0, weak: 1, planPending: 1 }, mastery: [{ state: 'UNKNOWN', count: 2 }, { state: 'WEAK', count: 1 }], plan: { total: 2, done: 1, pending: 1, tasks: [{ id: 1, title: '复习分数', day: 1, minutes: 20, instruction: '**先算** $2^3$<img src=x onerror=alert(1)>', resources: { materials: [{ id: 9, title: '讲义' }], questions: [{ id: 5, title: '习题' }], unavailable: false } }] }, pending: [{ id: 1, title: '数学作业', tags: ['分数'], maxScore: 100 }], results: [{ kind: 'PRACTICE', itemId: 5, submissionId: 7, title: '习题', score: 80, maxScore: 100, scoreSource: 'AUTO' }, { kind: 'ASSIGNMENT', itemId: 1, submissionId: 8, title: '数学作业', score: 90, maxScore: 100, scoreSource: 'TEACHER' }] })
async function view() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push('/home')
  wrapper = mount(HomeView, { global: { plugins: [router], stubs: { AppShell: { template: '<div><slot /></div>' } } } })
  await flushPromises()
  return router
}
beforeEach(() => { vi.clearAllMocks(); clearFeedback(); mocks.authState.user = { role: 'teacher' }; mocks.apiRequest.mockResolvedValue(teacher()) })
afterEach(() => { wrapper?.unmount(); clearFeedback() })
it('shows teacher bars, real denominators, attention and changes the selected submission chart', async () => {
  await view()
  expect(mocks.apiRequest).toHaveBeenCalledWith('/api/home/teacher', expect.objectContaining({ signal: expect.any(AbortSignal) }))
  expect(wrapper.find('.home-bar').attributes('href')).toBe('/teaching?tag=7')
  expect(wrapper.find('.donut-center').text()).toContain('2/4')
  await wrapper.find('select').setValue('2')
  expect(wrapper.find('.donut-center').text()).toContain('0/4')
  expect(wrapper.find('#home-attention').text()).toContain('student')
  expect(wrapper.find('[href="/assignments?action=create"]').exists()).toBe(true)
  expect(wrapper.find('[href="/materials?action=upload"]').exists()).toBe(true)
  expect(wrapper.find('[href="/questions?action=create"]').exists()).toBe(true)
})
it('uses a zero-data chart and empty states without fabricated records', async () => {
  mocks.apiRequest.mockResolvedValue({ ...teacher(), stats: { students: 0, assignments: 0, grading: 0, attention: 0 }, mastery: [], assignments: [], attention: [], unknownTags: 0 })
  await view()
  expect(wrapper.text()).toContain('还没有掌握记录')
  expect(wrapper.text()).toContain('还没有发布作业')
  expect(wrapper.find('.home-bar').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('NaN')
})
it('shows safe plan resources, progress, personal results and four separate mastery states', async () => {
  mocks.authState.user = { role: 'student' }; mocks.apiRequest.mockResolvedValue(student())
  const router = await view()
  expect(wrapper.find('[role=progressbar]').attributes('aria-valuenow')).toBe('1')
  expect(wrapper.find('.home-task-instruction strong').text()).toBe('先算')
  expect(wrapper.find('.home-task-instruction .katex').exists()).toBe(true)
  expect(wrapper.find('.home-task-instruction img').exists()).toBe(false)
  expect(wrapper.find('[href="/materials?material=9"]').exists()).toBe(true)
  expect(wrapper.find('[href="/questions/5"]').exists()).toBe(true)
  expect(wrapper.find('#home-results').text()).toContain('教师终评')
  await wrapper.findAll('.donut-legend-row').find(row => row.text().includes('暂无记录')).trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.fullPath).toBe('/learning/mastery?state=UNKNOWN')
  expect(mocks.apiRequest.mock.calls.every(([path, options]) => path === '/api/home/student' && !options.method)).toBe(true)
})
it('does not generate a missing plan and links to the plan page', async () => {
  mocks.authState.user = { role: 'student' }; mocks.apiRequest.mockResolvedValue({ ...student(), plan: {} })
  await view()
  expect(wrapper.text()).toContain('安排接下来的学习')
  expect(wrapper.find('[href="/learning/plan"]').exists()).toBe(true)
  expect(mocks.apiRequest).toHaveBeenCalledTimes(1)
})
it('offers retry and shows errors through the shared toast', async () => {
  mocks.apiRequest.mockRejectedValueOnce(new Error('读取失败'))
  await view()
  expect(wrapper.text()).toContain('首页暂时无法读取')
  expect(feedbackMessages.value.at(-1).message).toBe('读取失败')
  await wrapper.findAll('button').find(b => b.text() === '重新加载').trigger('click'); await flushPromises()
  expect(wrapper.find('.home-bars').exists()).toBe(true)
})
it('cancels a pending request and ignores completion after leaving the page', async () => {
  let complete
  mocks.apiRequest.mockImplementation(() => new Promise(resolve => { complete = resolve }))
  await view()
  expect(wrapper.find('[aria-label="正在读取首页"]').exists()).toBe(true)
  const signal = mocks.apiRequest.mock.calls[0][1].signal
  wrapper.unmount(); complete(teacher()); await flushPromises()
  expect(signal.aborted).toBe(true)
  expect(feedbackMessages.value).toHaveLength(0)
})

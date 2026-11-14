import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
vi.mock('../services/learning.js', () => ({ getClassLearning: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
import TeachingView from './TeachingView.vue'
import { getClassLearning } from '../services/learning.js'
import { clearFeedback, feedbackMessages } from '../services/feedback.js'
let wrapper
const view = () => (wrapper = mount(TeachingView, { global: { stubs: { AppShell: { template: '<div><slot /></div>' } } } }))
beforeEach(() => { vi.clearAllMocks(); clearFeedback(); getClassLearning.mockResolvedValue({ students: 2, points: [{ pointId: 1, name: '分数', enabled: true, mastery: null, participants: 0, notParticipating: 2, mastered: 0, weak: 0, students: [{ studentId: 2, username: 'student', mastery: null, state: 'UNKNOWN', sampleCount: 0 }] }] }) })
afterEach(() => { wrapper?.unmount(); clearFeedback() })
it('focuses on mastery without graph, question management, error types or advice', async () => {
  const w = view(); await flushPromises()
  expect(w.text()).toContain('平均掌握度')
  expect(w.text()).toContain('有记录 / 全班')
  expect(w.text()).not.toContain('错误类型')
  expect(w.text()).not.toContain('教学建议')
  expect(w.text()).not.toMatch(/最近20次|得分比例|80%|60%/)
  expect(w.find('[aria-label="掌握情况分页"]').exists()).toBe(true)
  expect(w.text()).not.toContain('练习题库')
  expect(w.find('.learning-tabs').exists()).toBe(false)
  await w.find('.mastery-link').trigger('click')
  expect(w.text()).toContain('分数 · 学生明细')
  expect(w.text()).toContain('student')
})
it('passes explicit dates but starts with cumulative statistics', async () => {
  const w = view(); await flushPromises()
  expect(getClassLearning).toHaveBeenCalledWith('', '', expect.any(Object))
  await w.findAll('input[type=date]')[0].setValue('2026-11-01')
  await w.find('form').trigger('submit'); await flushPromises()
  expect(getClassLearning).toHaveBeenLastCalledWith('2026-11-01', '', expect.any(Object))
})
it('reports failed loading through a toast and offers retry', async () => {
  getClassLearning.mockRejectedValueOnce(new Error('读取失败'))
  const w = view(); await flushPromises()
  expect(feedbackMessages.value.at(-1).message).toBe('读取失败')
  await w.findAll('button').find((button) => button.text() === '重新加载').trigger('click'); await flushPromises()
  expect(w.text()).toContain('平均掌握度')
})

it('paginates tag mastery and student details independently', async () => {
  const students = Array.from({ length: 21 }, (_, i) => ({ studentId: i + 1, username: `student-${i + 1}`, mastery: null, state: 'UNKNOWN', sampleCount: 0 }))
  getClassLearning.mockResolvedValue({ students: 21, points: Array.from({ length: 13 }, (_, i) => ({ pointId: i + 1, name: `标签${i + 1}`, mastery: null, students })) })
  const w = view(); await flushPromises()
  expect(w.findAll('tbody tr')).toHaveLength(10)
  await w.find('[aria-label="掌握情况分页"]').findAll('button')[1].trigger('click')
  expect(w.findAll('tbody tr')).toHaveLength(3)
  await w.find('.mastery-link').trigger('click')
  expect(w.findAll('.learning-table').at(-1).findAll('tbody tr')).toHaveLength(10)
  await w.find('[aria-label="学生掌握情况分页"]').findAll('button')[1].trigger('click')
  expect(w.findAll('.learning-table').at(-1).text()).toContain('student-11')
  await w.find('input[type=search]').setValue('标签1')
  expect(w.findAll('.learning-table')[0].findAll('tbody tr')).toHaveLength(5)
})

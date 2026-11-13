import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
vi.mock('../services/learning.js', () => ({ getLearningProfile: vi.fn(), getLearningPlan: vi.fn(), generateLearningPlan: vi.fn(), completeLearningTask: vi.fn() }))
import LearningView from './LearningView.vue'
import { getLearningPlan, getLearningProfile, generateLearningPlan } from '../services/learning.js'
let wrapper
beforeEach(() => {
  vi.clearAllMocks()
  getLearningProfile.mockResolvedValue({ points: [{ id: 1, name: '分数', enabled: true, mastery: null, state: 'UNKNOWN', sampleCount: 0 }] })
  getLearningPlan.mockResolvedValue({})
})
afterEach(() => wrapper?.unmount())
async function view(path = '/learning') {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/learning', redirect: '/learning/mastery' },
    { path: '/learning/mastery', component: LearningView, props: { section: 'mastery' } },
    { path: '/learning/plan', component: LearningView, props: { section: 'plan' } },
  ] })
  await router.push(path)
  wrapper = mount(RouterView, { global: { plugins: [router], stubs: { AppShell: { template: '<div><slot /></div>' } } } })
  await flushPromises()
  return router
}
it('redirects the original entry to mastery and does not mount the study plan', async () => {
  const router = await view()
  expect(router.currentRoute.value.path).toBe('/learning/mastery')
  expect(wrapper.text()).toContain('我的掌握情况')
  expect(wrapper.find('tbody tr td:nth-child(2)').text()).toBe('—')
  expect(wrapper.find('.study-plan').exists()).toBe(false)
  expect(wrapper.find('[aria-label="掌握情况分页"]').text()).toContain('共 1 条')
  expect(wrapper.text()).not.toMatch(/最近20次|得分比例|80%|60%|终评优先/)
  expect(getLearningPlan).not.toHaveBeenCalled()
})
it('opens the plan URL directly and switches subpages without automatic generation', async () => {
  const router = await view('/learning/plan')
  expect(wrapper.find('.study-plan').exists()).toBe(true)
  expect(wrapper.find('.learning-table').exists()).toBe(false)
  expect(getLearningProfile).not.toHaveBeenCalled()
  expect(getLearningPlan).toHaveBeenCalledTimes(1)
  await router.push('/learning/mastery'); await flushPromises()
  expect(wrapper.find('.study-plan').exists()).toBe(false)
  expect(wrapper.find('.learning-table').exists()).toBe(true)
  await router.push('/learning/plan'); await flushPromises()
  expect(wrapper.find('.learning-table').exists()).toBe(false)
  expect(generateLearningPlan).not.toHaveBeenCalled()
})
it('paginates mastery labels, supports page sizes and resets after searching', async () => {
  getLearningProfile.mockResolvedValue({ points: Array.from({ length: 23 }, (_, i) => ({ id: i + 1, name: `标签${i + 1}`, enabled: true, mastery: null, state: 'UNKNOWN', sampleCount: 0 })) })
  await view()
  expect(wrapper.findAll('tbody tr')).toHaveLength(10)
  const pager = wrapper.find('[aria-label="掌握情况分页"]')
  await pager.findAll('button')[1].trigger('click')
  expect(wrapper.find('tbody').text()).toContain('标签11')
  await wrapper.find('input[type=search]').setValue('标签23')
  expect(wrapper.findAll('tbody tr')).toHaveLength(1)
  expect(pager.text()).toContain('1 / 1')
  await wrapper.find('input[type=search]').setValue('')
  await pager.find('select').setValue('20')
  expect(wrapper.findAll('tbody tr')).toHaveLength(20)
})
it('cancels a pending mastery request when switching to the plan', async () => {
  let resolve
  getLearningProfile.mockImplementationOnce(() => new Promise((done) => { resolve = done }))
  const router = await view()
  const { signal } = getLearningProfile.mock.calls[0][0]
  await router.push('/learning/plan'); await flushPromises()
  expect(signal.aborted).toBe(true)
  resolve({ points: [] }); await flushPromises()
  expect(wrapper.find('.study-plan').exists()).toBe(true)
  expect(wrapper.find('.learning-table').exists()).toBe(false)
  expect(wrapper.text()).not.toContain('正在读取学情')
})

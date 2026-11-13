import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { afterEach, expect, it, vi } from 'vitest'
vi.mock('../services/auth.js', () => ({ authState: { user: { role: 'teacher' } } }))
vi.mock('../services/questions.js', () => ({ listQuestions: vi.fn(), getQuestion: vi.fn(), saveQuestion: vi.fn(), submitQuestion: vi.fn(), getQuestionResponse: vi.fn(), deleteQuestion: vi.fn(), setQuestionAvailability: vi.fn() }))
import QuestionBankView from './QuestionBankView.vue'
import { listQuestions } from '../services/questions.js'
let wrapper
afterEach(() => wrapper?.unmount())
it('keeps a search made on page two while the real asynchronous router clamps the page', async () => {
  listQuestions.mockResolvedValue(Array.from({ length: 16 }, (_, i) => ({ id: i + 1, title: i === 12 ? '两点确定函数' : `题目${i + 1}`, prompt: '数学练习', tags: [], enabled: true, maxScore: 100 })))
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/questions', component: QuestionBankView }] })
  await router.push('/questions?page=2')
  wrapper = mount(QuestionBankView, { global: { plugins: [router], stubs: { AppShell: { template: '<div><slot /></div>' } } } })
  await flushPromises()
  expect(wrapper.findAll('.assessment-row')).toHaveLength(6)
  await wrapper.find('input[type=search]').setValue('两点确定')
  await flushPromises()
  await flushPromises()
  expect(router.currentRoute.value.query).toEqual({ q: '两点确定' })
  expect(wrapper.find('input[type=search]').element.value).toBe('两点确定')
  expect(wrapper.findAll('.assessment-row')).toHaveLength(1)
  await router.push('/questions?page=2')
  await flushPromises()
  expect(router.currentRoute.value.query).toEqual({ page: '2' })
  expect(wrapper.findAll('.assessment-row')).toHaveLength(6)
})

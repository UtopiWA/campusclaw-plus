import { reactive } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
const state = reactive({ user: { role: 'student' }, route: { path: '/questions/1', params: { id: '1' }, query: {} } })
const navigation = { push: vi.fn(), replace: vi.fn() }
vi.mock('vue-router', () => ({ useRoute: () => state.route, useRouter: () => navigation }))
vi.mock('../services/auth.js', () => ({ authState: { get user() { return state.user } } }))
vi.mock('../services/agents.js', () => ({ listMaterials: vi.fn().mockResolvedValue([{ id: 5, title: '分数讲义', indexStatus: 'READY', hasFile: true }]) }))
vi.mock('../services/questions.js', () => ({ getQuestion: vi.fn(), listQuestions: vi.fn(), saveQuestion: vi.fn(), submitQuestion: vi.fn(), getQuestionResponse: vi.fn(), deleteQuestion: vi.fn(), setQuestionAvailability: vi.fn() }))
import QuestionBankView from './QuestionBankView.vue'
import { listMaterials } from '../services/agents.js'
import { getQuestion, listQuestions, saveQuestion, submitQuestion, getQuestionResponse } from '../services/questions.js'
import { clearFeedback } from '../services/feedback.js'
const question = { id: 1, contentVersion: 1, title: '分数练习', prompt: '解释 $1/4+1/2$', maxScore: 100, enabled: true, tags: [{ id: 1, name: '分数' }], rubricSources: [{ id: 5, title: '分数讲义' }] }
const result = { taskId: 'private-task', status: 'SUCCEEDED', submissionContent: '我的原作答', result: { score: 80, maxScore: 100, rationale: '原评分依据', method: 'AI' } }
let wrapper
const view = () => (wrapper = mount(QuestionBankView, { global: { stubs: { AppShell: { template: '<div><slot /></div>' }, RouterLink: { template: '<a><slot /></a>' } } } }))
beforeEach(() => { vi.clearAllMocks(); clearFeedback(); state.user = { role: 'student' }; state.route = { path: '/questions/1', params: { id: '1' }, query: {} }; getQuestion.mockResolvedValue({ ...question, myLatest: null }); listQuestions.mockResolvedValue([question]); getQuestionResponse.mockResolvedValue(result); listMaterials.mockResolvedValue([{ id: 5, title: '分数讲义', indexStatus: 'READY', hasFile: true }]) })
afterEach(() => { wrapper?.unmount(); clearFeedback(); vi.useRealTimers() })
it('shows only the latest personal AI result and replaces it after resubmission', async () => {
  vi.useFakeTimers()
  getQuestion.mockResolvedValue({ ...question, myLatest: result })
  submitQuestion.mockResolvedValue({ taskId: 'new-task', status: 'PENDING', submissionId: 8 })
  const w = view(); await flushPromises()
  expect(w.text()).toContain('原评分依据')
  await w.findAll('button').find((b) => b.text() === '重新作答').trigger('click')
  await w.find('#student-answer').setValue('新的作答')
  await w.find('.answer-actions .button--primary').trigger('click'); await flushPromises()
  expect(submitQuestion).toHaveBeenCalledWith(1, '新的作答', expect.any(String), 1)
  expect(w.text()).not.toContain('原评分依据')
  expect(w.text()).toContain('正在批改中')
  expect(w.findAll('button').some((b) => b.text() === '重新作答')).toBe(false)
  getQuestionResponse.mockResolvedValue({ ...result, taskId: 'new-task', submissionContent: '新的作答', result: { score: 95, maxScore: 100, rationale: '最新结果' } })
  await vi.advanceTimersByTimeAsync(2000); await flushPromises()
  expect(w.text()).toContain('最新结果')
  expect(w.text()).not.toContain('我的原作答')
})
it('does not allow re-answering while grading and restores the pending answer', async () => {
  getQuestion.mockResolvedValue({ ...question, myLatest: { ...result, status: 'RUNNING', result: null } })
  const w = view(); await flushPromises()
  expect(w.find('#student-answer').exists()).toBe(false)
  expect(w.findAll('button').some((b) => b.text() === '重新作答')).toBe(false)
  expect(w.text()).toContain('我的原作答')
})
it('teacher detail manages the question without student rows, result or review controls', async () => {
  state.user = { role: 'teacher' }
  getQuestion.mockResolvedValue(question)
  const w = view(); await flushPromises()
  expect(w.text()).toContain('编辑题目')
  expect(w.text()).toContain('停用')
  expect(w.text()).toContain('删除题目')
  expect(w.find('.submissions-panel').exists()).toBe(false)
  expect(w.find('.submit-panel').exists()).toBe(false)
  expect(w.find('.grading-result-panel').exists()).toBe(false)
  expect(w.text()).not.toContain('终评')
})
it('creates a question using lecture references and leaves a failed form available for correction', async () => {
  state.user = { role: 'teacher' }; state.route = { path: '/questions', params: {}, query: {} }
  saveQuestion.mockRejectedValueOnce(new Error('保存失败'))
  const w = view(); await flushPromises()
  await w.findAll('button').find((b) => b.text() === '新建题目').trigger('click'); await flushPromises()
  await w.find('input[maxlength="120"]').setValue('新题目')
  await w.find('#assignment-create textarea').setValue('计算并说明过程')
  await w.find('.rubric-option input').setValue(true)
  await w.find('#assignment-create').trigger('submit'); await flushPromises()
  expect(saveQuestion).toHaveBeenCalledWith(undefined, { title: '新题目', prompt: '计算并说明过程', maxScore: 100, rubricMaterialIds: [5], enabled: true })
  expect(w.find('[role=dialog]').exists()).toBe(true)
  expect(w.find('#assignment-create textarea').element.value).toBe('计算并说明过程')
})

it('preserves selected lectures while searching and saves the enabled checkbox state', async () => {
  state.user = { role: 'teacher' }; state.route = { path: '/questions', params: {}, query: {} }
  listMaterials.mockResolvedValue(Array.from({ length: 12 }, (_, i) => ({ id: i + 1, title: `讲义${i + 1}`, indexStatus: 'READY', hasFile: true })))
  saveQuestion.mockRejectedValueOnce(new Error('合成保存失败'))
  const w = view(); await flushPromises()
  await w.findAll('button').find(b => b.text() === '新建题目').trigger('click'); await flushPromises()
  await w.find('input[maxlength="120"]').setValue('新题目')
  await w.find('#assignment-create textarea').setValue('计算并说明过程')
  await w.find('.rubric-option input').setValue(true)
  await w.find('.reference-search input').setValue('讲义12')
  expect(w.findAll('.rubric-option')).toHaveLength(1)
  await w.find('.rubric-option input').setValue(true)
  const enabled = w.find('.switch-field input')
  expect(enabled.element.checked).toBe(true)
  await enabled.setValue(false)
  await w.find('#assignment-create').trigger('submit'); await flushPromises()
  expect(saveQuestion).toHaveBeenCalledWith(undefined, { title: '新题目', prompt: '计算并说明过程', maxScore: 100, rubricMaterialIds: [1, 12], enabled: false })
})

it('reuses the request key only while retrying the same unchanged draft', async () => {
  submitQuestion.mockRejectedValueOnce(new Error('网络失败')).mockRejectedValueOnce(new Error('网络失败')).mockResolvedValue({ taskId: 'accepted', status: 'PENDING' })
  const w = view(); await flushPromises()
  await w.find('#student-answer').setValue('第一份答案')
  await w.find('.answer-actions .button--primary').trigger('click'); await flushPromises()
  await w.find('.answer-actions .button--primary').trigger('click'); await flushPromises()
  expect(submitQuestion.mock.calls[0][2]).toBe(submitQuestion.mock.calls[1][2])
  await w.find('#student-answer').setValue('修改后的答案')
  await w.find('.answer-actions .button--primary').trigger('click'); await flushPromises()
  expect(submitQuestion.mock.calls[2][2]).not.toBe(submitQuestion.mock.calls[0][2])
})

it('lists and paginates questions without a learning-plan tab', async () => {
  state.route = { path: '/questions', params: {}, query: {} }
  navigation.replace.mockImplementation(({ query }) => { state.route.query = query })
  listQuestions.mockResolvedValue(Array.from({ length: 13 }, (_, i) => ({ ...question, id: i + 1, title: `题目${i + 1}` })))
  const w = view(); await flushPromises()
  expect(w.findAll('.assessment-row')).toHaveLength(10)
  expect(w.find('.assessment-row').text()).toContain('未作答')
  expect(w.text()).not.toContain('学习计划')
  await w.find('[aria-label="题库分页"]').findAll('button')[1].trigger('click'); await flushPromises()
  expect(w.findAll('.assessment-row')).toHaveLength(3)
  expect(state.route.query.page).toBe(2)
  await w.find('input[type=search]').setValue('题目13'); await flushPromises()
  expect(w.findAll('.assessment-row')).toHaveLength(1)
  expect(state.route.query.page).toBeUndefined()
})

import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ authState: { user: { role: 'student', username: 'student-a1' } } }))
vi.mock('../services/auth.js', () => ({ authState: mocks.authState }))
vi.mock('../services/agents.js', () => ({ listMaterials: vi.fn() }))
vi.mock('../services/assignments.js', () => ({ listAssignments: vi.fn(), getAssignment: vi.fn(), createAssignment: vi.fn(), submitAssignment: vi.fn(), getGradingTask: vi.fn(), listTeacherSubmissions: vi.fn(), reviewSubmission: vi.fn() }))
import AssignmentsView from './AssignmentsView.vue'
import { listMaterials } from '../services/agents.js'
import { clearFeedback, feedbackMessages } from '../services/feedback.js'
import { createAssignment, getAssignment, getGradingTask, listAssignments, listTeacherSubmissions, reviewSubmission, submitAssignment } from '../services/assignments.js'
const assignment = { id: 1, title: '勾股定理练习', prompt: '解释勾股定理', maxScore: 100, myLatest: null, rubricSources: [] }
const result = { taskId: 'task-1', submissionId: 7, status: 'SUCCEEDED', submissionContent: '我的答案', result: { score: 60, maxScore: 100, rationale: 'AI 原始评语', criteria: [] } }
let wrappers = []
function mountView() { const wrapper = mount(AssignmentsView, { global: { stubs: { AppShell: { template: '<div><slot /></div>' } } } }); wrappers.push(wrapper); return wrapper }
beforeEach(() => {
  vi.clearAllMocks(); clearFeedback()
  mocks.authState.user = { role: 'student', username: 'student-a1' }
  listAssignments.mockResolvedValue(structuredClone([assignment]))
  listMaterials.mockResolvedValue([{ id: 5, title: '评分标准.md', indexStatus: 'READY', hasFile: true }])
  listTeacherSubmissions.mockResolvedValue([])
  getGradingTask.mockResolvedValue(structuredClone(result))
})
afterEach(() => { wrappers.forEach((wrapper) => wrapper.unmount()); wrappers = []; clearFeedback(); vi.useRealTimers() })

it('accepts once, hides the editor immediately and stops task polling at a terminal result', async () => {
  vi.useFakeTimers()
  submitAssignment.mockResolvedValue({ taskId: 'task-1', status: 'PENDING', submissionId: 7 })
  const wrapper = mountView(); await flushPromises()
  await wrapper.find('#student-answer').setValue('我的答案')
  expect(wrapper.find('.answer-actions button').text()).toBe('提交')
  await wrapper.find('.answer-actions button').trigger('click'); await flushPromises()
  expect(submitAssignment).toHaveBeenCalledWith(1, '我的答案')
  expect(wrapper.find('#student-answer').exists()).toBe(false)
  expect(wrapper.text()).toContain('我的答案')
  await vi.advanceTimersByTimeAsync(2000); await flushPromises()
  expect(wrapper.text()).toContain('批改结果')
  expect(wrapper.text()).toContain('AI 原始评语')
  await vi.advanceTimersByTimeAsync(7000)
  expect(getGradingTask).toHaveBeenCalledTimes(1)
  expect(wrapper.text()).not.toMatch(/任务编号|执行次数|task-1|提交并被批改/)
})
it('restores a submitted answer and shows only teacher final feedback when available', async () => {
  listAssignments.mockResolvedValue([{ ...assignment, myLatest: { taskId: 'task-1', score: 88 } }])
  getGradingTask.mockResolvedValue({ ...result, teacherReview: { score: 88, comment: '教师最终评语' } })
  const wrapper = mountView(); await flushPromises()
  expect(wrapper.find('#student-answer').exists()).toBe(false)
  expect(wrapper.text()).toContain('教师终评')
  expect(wrapper.text()).toContain('教师最终评语')
  expect(wrapper.text()).not.toContain('AI 原始评语')
  expect(wrapper.find('.grade-score').text()).toContain('88')
})
it('receives a later teacher review through the assignment summary without restarting task polling', async () => {
  vi.useFakeTimers(); Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
  listAssignments.mockResolvedValue([{ ...assignment, myLatest: { taskId: 'task-1' } }])
  getAssignment.mockResolvedValue({ ...assignment, myLatest: { score: 92, teacherReview: { score: 92, comment: '新的终评' } } })
  const wrapper = mountView(); await flushPromises()
  await vi.advanceTimersByTimeAsync(15000); await flushPromises()
  expect(wrapper.text()).toContain('新的终评')
  expect(getGradingTask).toHaveBeenCalledTimes(1)
})
it('creates from a modal with total points and bound files, retaining the form on failure', async () => {
  mocks.authState.user.role = 'teacher'
  createAssignment.mockRejectedValueOnce(new Error('参考答案暂时不可用'))
  const wrapper = mountView(); await flushPromises()
  await wrapper.find('.page-heading .button--primary').trigger('click'); await flushPromises()
  expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
  expect(wrapper.find('[role="dialog"]').text()).toContain('参考答案文件')
  await wrapper.find('#assignment-create input[maxlength="120"]').setValue('新作业')
  await wrapper.find('#assignment-create textarea').setValue('详细题目')
  await wrapper.find('.rubric-option input').setValue(true)
  await wrapper.find('#assignment-create').trigger('submit'); await flushPromises()
  expect(createAssignment).toHaveBeenCalledWith({ title: '新作业', prompt: '详细题目', maxScore: 100, rubricMaterialIds: [5] })
  expect(wrapper.find('#assignment-create textarea').element.value).toBe('详细题目')
  expect(feedbackMessages.value.at(-1).message).toBe('参考答案暂时不可用')
  expect(wrapper.find('.feedback--error').exists()).toBe(false)
})
it('lists student, answer, status, score and saves an independent final review', async () => {
  mocks.authState.user.role = 'teacher'
  const row = { submissionId: 7, studentUsername: 'student-a1', content: '完整作答', status: 'SUCCEEDED', score: 60, aiResult: result.result }
  listTeacherSubmissions.mockResolvedValue([row])
  reviewSubmission.mockResolvedValue({ score: 90, comment: '教师评语' })
  const wrapper = mountView(); await flushPromises()
  expect(wrapper.findAll('thead th').slice(0,4).map((node) => node.text())).toEqual(['学生','作答','状态','得分'])
  expect(wrapper.find('thead').text()).not.toContain('版本')
  await wrapper.find('[aria-label="给 student-a1 改分"]').trigger('click')
  expect(wrapper.find('[role="dialog"]').text()).toContain('完整作答')
  expect(wrapper.find('[role="dialog"]').text()).toContain('AI 原始评语')
  await wrapper.find('#submission-review input').setValue(90)
  await wrapper.find('#submission-review textarea').setValue('教师评语')
  listTeacherSubmissions.mockResolvedValue([{ ...row, score: 90, teacherReview: { score: 90, comment: '教师评语' } }])
  await wrapper.find('#submission-review').trigger('submit'); await flushPromises()
  expect(reviewSubmission).toHaveBeenCalledWith(1, 7, { score: 90, comment: '教师评语' })
  expect(wrapper.text()).toContain('已终评')
  expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
})
it.each(['switch','unmount'])('aborts and discards a late task response after %s', async (action) => {
  vi.useFakeTimers()
  listAssignments.mockResolvedValue([{ ...assignment, myLatest: { taskId: 'old' } }, { ...assignment, id: 2, title: '第二份作业' }])
  let finish
  getGradingTask.mockResolvedValueOnce({ taskId: 'old', status: 'PENDING' }).mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
  const wrapper = mountView(); await flushPromises(); await vi.advanceTimersByTimeAsync(2000)
  const signal = getGradingTask.mock.calls[1][1].signal
  if (action === 'switch') await wrapper.findAll('.assignment-item')[1].trigger('click')
  else { wrapper.unmount(); wrappers = [] }
  expect(signal.aborted).toBe(true)
  finish({ taskId: 'old', status: 'RUNNING' }); await flushPromises(); await vi.advanceTimersByTimeAsync(10000)
  expect(getGradingTask).toHaveBeenCalledTimes(2)
  if (action === 'switch') { expect(wrapper.find('.assignment-item.active').text()).toContain('第二份作业'); expect(wrapper.find('#student-answer').exists()).toBe(true) }
})

it('shows a flexible AI score and rationale without requiring rubric details', async () => {
  listAssignments.mockResolvedValue([{ ...assignment, myLatest: { taskId: 'task-1', status: 'SUCCEEDED' } }])
  getGradingTask.mockResolvedValue({ ...result, result: { score: 87, maxScore: 100, criteria: [], rationale: '公式正确，需补充推理。' } })
  const wrapper = mountView(); await flushPromises()
  expect(wrapper.find('.grade-score').text()).toContain('87')
  expect(wrapper.find('.grade-comment').text()).toBe('公式正确，需补充推理。')
  expect(wrapper.find('#student-answer').exists()).toBe(false)
})

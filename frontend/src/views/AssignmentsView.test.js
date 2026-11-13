import { reactive } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ authState: { user: { role: 'student', username: 'student-a1' } } }))
vi.mock('../services/auth.js', () => ({ authState: mocks.authState }))
vi.mock('../services/agents.js', () => ({ listMaterials: vi.fn() }))
vi.mock('../services/assignments.js', () => ({ listAssignments: vi.fn(), getAssignment: vi.fn(), createAssignment: vi.fn(), submitAssignment: vi.fn(), getGradingTask: vi.fn(), listTeacherSubmissions: vi.fn(), reviewSubmission: vi.fn() }))
const routeState = reactive({ path: '/assignments/1', params: { id: '1' }, query: {} })
const navigate = async (target) => {
  routeState.path = target.path || routeState.path
  routeState.params = { id: routeState.path.split('/')[2] }
  routeState.query = target.query || {}
}
const navigation = { push: vi.fn(navigate), replace: vi.fn(navigate) }
vi.mock('vue-router', () => ({ useRoute: () => routeState, useRouter: () => navigation }))
import AssignmentsView from './AssignmentsView.vue'
import { listMaterials } from '../services/agents.js'
import { clearFeedback, feedbackMessages } from '../services/feedback.js'
import { createAssignment, getAssignment, getGradingTask, listAssignments, listTeacherSubmissions, reviewSubmission, submitAssignment } from '../services/assignments.js'
const assignment = { id: 1, title: '勾股定理练习', prompt: '解释勾股定理', maxScore: 100, myLatest: null, rubricSources: [] }
const result = { taskId: 'task-1', submissionId: 7, status: 'SUCCEEDED', submissionContent: '我的答案', result: { score: 60, maxScore: 100, rationale: 'AI 原始评语', criteria: [] } }
let wrappers = []
function mountView() { const wrapper = mount(AssignmentsView, { global: { stubs: { AppShell: { template: '<div><slot /></div>' }, RouterLink: { template: '<a><slot /></a>' } } } }); wrappers.push(wrapper); return wrapper }
beforeEach(() => {
  vi.clearAllMocks(); clearFeedback()
  routeState.path = '/assignments/1'; routeState.params = { id: '1' }; routeState.query = {}
  getAssignment.mockResolvedValue(structuredClone(assignment))
  mocks.authState.user = { role: 'student', username: 'student-a1' }
  listAssignments.mockResolvedValue(structuredClone([assignment]))
  listMaterials.mockResolvedValue([{ id: 5, title: '评分标准.md', indexStatus: 'READY', hasFile: true }])
  listTeacherSubmissions.mockResolvedValue([])
  getGradingTask.mockResolvedValue(structuredClone(result))
})
afterEach(() => { wrappers.forEach((wrapper) => wrapper.unmount()); wrappers = []; clearFeedback(); vi.useRealTimers() })

it.each([{ items: [] }, { items: [assignment] }])('always shows pagination below the assignment list, even with zero or one item', async ({ items }) => {
  routeState.path = '/assignments'; routeState.params = {}
  listAssignments.mockResolvedValue(items)
  const wrapper = mountView(); await flushPromises()
  const pager = wrapper.find('[aria-label="作业分页"]')
  expect(pager.exists()).toBe(true)
  expect(pager.text()).toContain(`共 ${items.length} 条`)
  expect(pager.findAll('button').every(button => button.element.disabled)).toBe(true)
})

it.each([false, true])('renders the student answer and current AI/teacher feedback as Markdown and LaTeX (final=%s)', async (final) => {
  getAssignment.mockResolvedValue({ ...assignment, myLatest: { taskId: 'task-1' } })
  getGradingTask.mockResolvedValue({ ...result, submissionContent: '## 我的过程\n\n计算 $$2^3=8$$ 后检查。', result: { ...result.result, rationale: '**依据**：\\(2^3=8\\)' }, ...(final ? { teacherReview: { score: 90, comment: '**终评**：$2^3=8$。' } } : {}) })
  const wrapper = mountView(); await flushPromises()
  expect(wrapper.find('.answer-body h2').text()).toBe('我的过程')
  expect(wrapper.find('.answer-body .katex').exists()).toBe(true)
  expect(wrapper.find('.grade-comment strong').text()).toBe(final ? '终评' : '依据')
  expect(wrapper.find('.grade-comment .katex').exists()).toBe(true)
})

it('renders teacher submission previews and the review dialog safely', async () => {
  mocks.authState.user.role = 'teacher'
  listTeacherSubmissions.mockResolvedValue([{ submissionId: 7, studentUsername: 'student-a1', content: '**过程**：$x=3$<img src=x onerror=alert(1)>', status: 'SUCCEEDED', score: 60, aiResult: { ...result.result, rationale: '## 评分依据\n\n推导：\\[x^2=9\\]。' } }])
  const wrapper = mountView(); await flushPromises()
  expect(wrapper.find('.submission-excerpt strong').text()).toBe('过程')
  expect(wrapper.find('.submission-excerpt .katex').exists()).toBe(true)
  expect(wrapper.find('.submission-excerpt img').exists()).toBe(false)
  await wrapper.find('[aria-label="给 student-a1 改分"]').trigger('click')
  const dialog = wrapper.find('[role="dialog"]')
  expect(dialog.find('.answer-body .katex').exists()).toBe(true)
  expect(dialog.find('.assistant-markdown h2').text()).toBe('评分依据')
  expect(dialog.findAll('.assistant-markdown .katex')).toHaveLength(2)
})

it('accepts once, hides the editor immediately and stops task polling at a terminal result', async () => {
  vi.useFakeTimers()
  submitAssignment.mockResolvedValue({ taskId: 'task-1', status: 'PENDING', submissionId: 7 })
  const wrapper = mountView(); await flushPromises()
  await wrapper.find('#student-answer').setValue('我的答案')
  expect(wrapper.find('.answer-actions button').text()).toBe('提交')
  await wrapper.find('.answer-actions button').trigger('click'); await flushPromises()
  expect(submitAssignment).toHaveBeenCalledWith(1, '我的答案', expect.any(String))
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
  getAssignment.mockResolvedValue({ ...assignment, myLatest: { taskId: 'task-1', score: 88 } })
  getGradingTask.mockResolvedValue({ ...result, teacherReview: { score: 88, comment: '教师最终评语' } })
  const wrapper = mountView(); await flushPromises()
  expect(wrapper.find('#student-answer').exists()).toBe(false)
  expect(wrapper.text()).toContain('教师终评')
  expect(wrapper.text()).toContain('教师最终评语')
  expect(wrapper.text()).not.toContain('AI 原始评语')
  expect(wrapper.find('.grade-score').text()).toContain('88')
})
it('labels a bank objective result as system grading instead of an AI score', async () => {
  getAssignment.mockResolvedValue({ ...assignment, myLatest: { taskId: 'task-1' } })
  getGradingTask.mockResolvedValue({ ...result, result: { ...result.result, method: 'RULE' } })
  const wrapper = mountView(); await flushPromises()
  expect(wrapper.find('.grade-source').text()).toBe('系统批改')
})
it('receives a later teacher review through the assignment summary without restarting task polling', async () => {
  vi.useFakeTimers(); Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
  getAssignment.mockResolvedValue({ ...assignment, myLatest: { taskId: 'task-1' } })
  getAssignment.mockResolvedValueOnce({ ...assignment, myLatest: { taskId: 'task-1' } }).mockResolvedValue({ ...assignment, myLatest: { taskId: 'task-1', score: 92, teacherReview: { score: 92, comment: '新的终评' } } })
  const wrapper = mountView(); await flushPromises()
  await vi.advanceTimersByTimeAsync(15000); await flushPromises()
  expect(wrapper.text()).toContain('新的终评')
  expect(getGradingTask).toHaveBeenCalledTimes(1)
})
it('creates from a modal with total points and bound files, retaining the form on failure', async () => {
  mocks.authState.user.role = 'teacher'
  routeState.path = '/assignments'; routeState.params = {}
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
  getAssignment.mockImplementation(async (id) => id === 1 ? { ...assignment, myLatest: { taskId: 'old' } } : { ...assignment, id: 2, title: '第二份作业' })
  let finish
  getGradingTask.mockResolvedValueOnce({ taskId: 'old', status: 'PENDING' }).mockImplementationOnce(() => new Promise((resolve) => { finish = resolve }))
  const wrapper = mountView(); await flushPromises(); await vi.advanceTimersByTimeAsync(2000)
  const signal = getGradingTask.mock.calls[1][1].signal
  if (action === 'switch') { await navigation.push({ path: '/assignments/2' }); await flushPromises() }
  else { wrapper.unmount(); wrappers = [] }
  expect(signal.aborted).toBe(true)
  finish({ taskId: 'old', status: 'RUNNING' }); await flushPromises(); await vi.advanceTimersByTimeAsync(10000)
  expect(getGradingTask).toHaveBeenCalledTimes(2)
  if (action === 'switch') { expect(wrapper.find('.assignment-detail h2').text()).toContain('第二份作业'); expect(wrapper.find('#student-answer').exists()).toBe(true) }
})

it('shows a flexible AI score and rationale without requiring rubric details', async () => {
  getAssignment.mockResolvedValue({ ...assignment, myLatest: { taskId: 'task-1', status: 'SUCCEEDED' } })
  getGradingTask.mockResolvedValue({ ...result, result: { score: 87, maxScore: 100, criteria: [], rationale: '公式正确，需补充推理。' } })
  const wrapper = mountView(); await flushPromises()
  expect(wrapper.find('.grade-score').text()).toContain('87')
  expect(wrapper.find('.grade-comment').text()).toBe('公式正确，需补充推理。')
  expect(wrapper.find('#student-answer').exists()).toBe(false)
})

it('lists every class student with pagination and never offers grading for an unsubmitted row', async () => {
  mocks.authState.user = { role: 'teacher', username: 'teacher-a' }
  listTeacherSubmissions.mockResolvedValue(Array.from({ length: 23 }, (_, i) => ({ studentId: i + 1, studentUsername: `student-${i + 1}`, submissionId: i === 0 ? 7 : null, content: i === 0 ? '已作答' : null, createdAt: i === 0 ? '2026-11-11T01:00:00Z' : null, status: i === 0 ? 'SUCCEEDED' : 'NOT_SUBMITTED', score: i === 0 ? 80 : null })))
  const w = mountView(); await flushPromises()
  expect(w.findAll('tbody tr')).toHaveLength(10)
  expect(w.findAll('tbody button')).toHaveLength(1)
  expect(w.text()).toContain('未提交')
  expect(w.text()).toContain('作答时间')
  const pager = w.find('[aria-label="学生作答分页"]')
  await pager.findAll('button')[1].trigger('click')
  expect(w.find('tbody').text()).toContain('student-11')
  expect(w.findAll('tbody button')).toHaveLength(0)
})

it('opens the assignment list first and preserves search and page when entering detail', async () => {
  routeState.path = '/assignments'; routeState.params = {}; routeState.query = { page: '2', q: '练习' }
  listAssignments.mockResolvedValue(Array.from({ length: 23 }, (_, i) => ({ ...assignment, id: i + 1, title: `练习${i + 1}`, tags: [{ id: 1, name: '分数' }] })))
  const w = mountView(); await flushPromises()
  expect(w.findAll('.assessment-row')).toHaveLength(10)
  expect(w.find('.assessment-row').text()).toContain('练习11')
  expect(w.find('#student-answer').exists()).toBe(false)
  await w.find('.assessment-row').trigger('click'); await flushPromises()
  expect(navigation.push).toHaveBeenLastCalledWith({ path: '/assignments/11', query: { page: '2', q: '练习' } })
  expect(w.find('#student-answer').exists()).toBe(true)
})
it('filters list results by search and state without exposing detail editors', async () => {
  routeState.path = '/assignments'; routeState.params = {}
  listAssignments.mockResolvedValue([{ ...assignment, title: '分数练习', myLatest: { status: 'SUCCEEDED', score: 80 } }, { ...assignment, id: 2, title: '方程练习' }])
  const w = mountView(); await flushPromises()
  await w.find('input[type=search]').setValue('方程'); await flushPromises()
  expect(w.findAll('.assessment-row')).toHaveLength(1)
  expect(w.find('.assessment-row').text()).toContain('方程练习')
  expect(routeState.query.page).toBeUndefined()
  expect(routeState.query.q).toBe('方程')
})

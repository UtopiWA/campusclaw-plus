import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  authState: { user: { role: 'student', username: 'student-a1', classId: 1 } },
}))

vi.mock('../services/auth.js', () => ({ authState: mocks.authState }))
vi.mock('../services/assignments.js', () => ({
  listAssignments: vi.fn(),
  createAssignment: vi.fn(),
  submitAssignment: vi.fn(),
  getGradingTask: vi.fn(),
  listTeacherSubmissions: vi.fn(),
}))

import AssignmentsView from './AssignmentsView.vue'
import {
  createAssignment,
  getGradingTask,
  listAssignments,
  listTeacherSubmissions,
  submitAssignment,
} from '../services/assignments.js'

const assignment = {
  id: 1,
  title: '勾股定理练习',
  prompt: '解释勾股定理',
  criteria: [{ key: 'c1', description: '公式正确', points: 100 }],
  maxScore: 100,
  myLatest: null,
}

function mountView() {
  return mount(AssignmentsView, {
    global: {
      stubs: {
        AppShell: { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('AssignmentsView', () => {
  beforeEach(() => {
    mocks.authState.user = { role: 'student', username: 'student-a1', classId: 1 }
    vi.mocked(listAssignments).mockResolvedValue([assignment])
    vi.mocked(createAssignment).mockReset()
    vi.mocked(submitAssignment).mockReset()
    vi.mocked(getGradingTask).mockReset()
    vi.mocked(listTeacherSubmissions).mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('submits and polls until the terminal result', async () => {
    vi.useFakeTimers()
    vi.mocked(submitAssignment).mockResolvedValue({
      taskId: 'task-1', status: 'PENDING', submissionId: 1, version: 1,
    })
    vi.mocked(getGradingTask).mockResolvedValue({
      taskId: 'task-1', status: 'SUCCEEDED', attempts: 1, maxAttempts: 5,
      result: {
        score: 80, maxScore: 100, rationale: '整体正确',
        criteria: [{
          key: 'c1', description: '公式正确', points: 100, verdict: 'MET',
          evidence: 'a^2+b^2=c^2', explanation: '公式正确', awardedPoints: 80,
        }],
      },
    })

    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('勾股定理练习')

    await wrapper.find('.assignment-item').trigger('click')
    await flushPromises()
    await wrapper.find('.submit-panel textarea').setValue('a^2+b^2=c^2')
    const submitButton = wrapper.findAll('button').find((button) => button.text().includes('提交并被批改'))
    await submitButton.trigger('click')
    await flushPromises()

    expect(submitAssignment).toHaveBeenCalledWith(1, 'a^2+b^2=c^2', expect.any(String))
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()

    expect(getGradingTask).toHaveBeenCalledWith('task-1')
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('80')
    expect(wrapper.text()).toContain('模型辅助分项判断')
  })

  it('shows teacher create action and class submissions', async () => {
    mocks.authState.user = { role: 'teacher', username: 'teacher-a', classId: 1 }
    vi.mocked(listTeacherSubmissions).mockResolvedValue([{
      submissionId: 1, studentId: 2, studentUsername: 'student-a1', version: 1,
      content: 'a^2+b^2=c^2', createdAt: '2026-11-03T00:00:00Z',
      taskId: 'task-1', status: 'SUCCEEDED', score: 80, maxScore: 100, failureCode: null,
    }])

    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('新建作业')

    await wrapper.find('.assignment-item').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('本班提交与结果')
    expect(wrapper.text()).toContain('student-a1')
    expect(wrapper.text()).toContain('80 / 100')
  })
})

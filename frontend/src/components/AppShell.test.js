import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'

const { authState, logout, replace } = vi.hoisted(() => ({
  authState: { user: null, loaded: true }, logout: vi.fn(), replace: vi.fn(),
}))

vi.mock('../services/auth.js', () => ({ authState, logout }))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace }) }))

import AppShell from './AppShell.vue'
import { clearFeedback, feedbackMessages } from '../services/feedback.js'

afterEach(() => { clearFeedback(); vi.clearAllMocks() })

const global = {
  stubs: { RouterLink: { props: ['to'], template: '<a :data-to="to"><slot /></a>' } },
}

describe('AppShell role-aware navigation', () => {
  beforeEach(() => {
    authState.user = { username: 'user', className: '班级 A', role: 'teacher' }
  })

  it('shows conversations to both roles and configuration only to teachers', () => {
    const teacher = mount(AppShell, { props: { current: 'assistant-chat' }, global })
    expect(teacher.find('[data-to="/assistants"]').exists()).toBe(true)
    expect(teacher.find('[data-to="/conversations"]').exists()).toBe(true)
    expect(teacher.find('[data-to="/search"]').exists()).toBe(false)
    expect(teacher.text()).toContain('讲义与检索')

    authState.user = { username: 'student', className: '班级 A', role: 'student' }
    const student = mount(AppShell, { props: { current: 'assistant-chat' }, global })
    expect(student.find('[data-to="/assistants"]').exists()).toBe(false)
    expect(student.find('[data-to="/conversations"]').exists()).toBe(true)
    expect(student.find('[data-to="/search"]').exists()).toBe(false)
    expect(student.text()).toContain('讲义与检索')
  })
})

it('shows a logout failure at the top and still leaves the cleared local session', async () => {
  authState.user = { username: 'user', role: 'teacher' }
  logout.mockRejectedValue(new Error('退出请求暂时不可用'))
  const wrapper = mount(AppShell, { props: { current: 'materials' }, global })
  await wrapper.find('.topbar-logout').trigger('click')
  await flushPromises()
  expect(feedbackMessages.value.at(-1)).toMatchObject({ type: 'error', message: '退出请求暂时不可用' })
  expect(replace).toHaveBeenCalledWith('/login')
  wrapper.unmount()
})

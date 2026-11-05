import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import AppFeedback from './AppFeedback.vue'
import { clearFeedback, feedbackRef, showFeedback } from '../services/feedback.js'

let wrapper
beforeEach(() => { clearFeedback(); vi.useFakeTimers(); wrapper = mount(AppFeedback) })
afterEach(() => { wrapper.unmount(); clearFeedback(); vi.useRealTimers() })

it('shows accessible top messages and automatically dismisses success before error', async () => {
  showFeedback('已保存', 'success'); showFeedback('连接失败')
  await vi.advanceTimersByTimeAsync(1)
  expect(wrapper.find('[role="status"]').text()).toContain('已保存')
  expect(wrapper.find('[role="alert"]').text()).toContain('连接失败')
  await vi.advanceTimersByTimeAsync(4000)
  expect(wrapper.find('[role="status"]').exists()).toBe(false)
  expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  await vi.advanceTimersByTimeAsync(2000)
  expect(wrapper.find('[role="alert"]').exists()).toBe(false)
})

it('can show the same error again and allows manual dismissal without rendering HTML', async () => {
  const error = feedbackRef()
  error.value = '<img src=x onerror=alert(1)>'
  await vi.advanceTimersByTimeAsync(6000)
  error.value = '<img src=x onerror=alert(1)>'
  await vi.advanceTimersByTimeAsync(1)
  expect(wrapper.find('[role="alert"]').text()).toContain('<img')
  expect(wrapper.find('img').exists()).toBe(false)
  await wrapper.find('[aria-label="关闭消息"]').trigger('click')
  expect(wrapper.find('[role="alert"]').exists()).toBe(false)
})

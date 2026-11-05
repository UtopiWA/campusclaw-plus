import { mount } from '@vue/test-utils'
import { beforeEach, afterEach } from 'vitest'
import AppFeedback from '../src/components/AppFeedback.vue'
import { clearFeedback } from '../src/services/feedback.js'

beforeEach(clearFeedback)
afterEach(clearFeedback)

// 页面实际运行于 App.vue 的全局消息容器旁，测试同样保留这条展示链路。
export function mountWithFeedback(component, options) {
  return mount({ components: { TestView: component, AppFeedback }, template: '<TestView /><AppFeedback />' }, options)
}

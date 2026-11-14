import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import OverviewDonut from './OverviewDonut.vue'
it('keeps zero totals finite and all labels readable', () => {
  const wrapper = mount(OverviewDonut, { props: { label: '提交', caption: '人', segments: [{ key: 'done', label: '已交', count: 0, color: '#a7222c' }] } })
  expect(wrapper.find('svg').attributes('aria-label')).toContain('已交0')
  expect(wrapper.findAll('circle')).toHaveLength(1)
  expect(wrapper.find('.donut-center').text()).toBe('0人')
  expect(wrapper.html()).not.toContain('NaN')
})
it('makes interactive legends real buttons with values independent of color', async () => {
  const wrapper = mount(OverviewDonut, { props: { label: '掌握', interactive: true, segments: [{ key: 'UNKNOWN', label: '暂无记录', count: 3, color: '#ddd' }, { key: 'WEAK', label: '需要复习', count: 1, color: '#a7222c' }] } })
  await wrapper.findAll('button')[1].trigger('click')
  expect(wrapper.emitted('select')).toEqual([['WEAK']])
  expect(wrapper.findAll('circle')[1].attributes('stroke-dasharray')).toBe('75 25')
})

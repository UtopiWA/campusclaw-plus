import { afterEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
vi.mock('../services/learning.js', () => ({ saveLearningPoint: vi.fn(), replaceMaterialTags: vi.fn() }))
import MaterialTags from './MaterialTags.vue'
import { replaceMaterialTags } from '../services/learning.js'
let wrapper
afterEach(() => wrapper?.unmount())
it('retains checkbox selection across pages and can explicitly clear all tags', async () => {
  const tags = Array.from({ length: 21 }, (_, i) => ({ id: i + 1, name: `标签${i + 1}`, enabled: true }))
  wrapper = mount(MaterialTags, { props: { tags, materialTags: [] } })
  wrapper.vm.open({ id: 5, title: '讲义' }); await flushPromises()
  await wrapper.find('[role="dialog"] input[type="checkbox"]').setValue(true)
  await wrapper.find('[aria-label="可选标签分页"]').findAll('button')[1].trigger('click')
  await wrapper.find('[role="dialog"] input[type="checkbox"]').setValue(true)
  await wrapper.findAll('[role="dialog"] button').find((b) => b.text() === '保存').trigger('click'); await flushPromises()
  expect(replaceMaterialTags).toHaveBeenLastCalledWith(5, [1, 11])
  wrapper.vm.open({ id: 5, title: '讲义' }); await flushPromises()
  await wrapper.findAll('[role="dialog"] button').find((b) => b.text() === '保存').trigger('click'); await flushPromises()
  expect(replaceMaterialTags).toHaveBeenLastCalledWith(5, [])
})

import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import PagedList from './PagedList.vue'
const items = Array.from({ length: 25 }, (_, i) => ({ id: i + 1 }))
const create = (props = {}) => mount(PagedList, { props: { items, ...props }, slots: { default: '<template #default="{ pageItems }"><p v-for="item in pageItems" :key="item.id">条目{{ item.id }}</p></template>' } })
it('paginates without losing entries, clamps after deletion and resets on filtering', async () => {
  const w = create()
  expect(w.findAll('p')).toHaveLength(10)
  await w.findAll('button')[1].trigger('click')
  expect(w.find('p').text()).toBe('条目11')
  await w.findAll('button')[1].trigger('click')
  expect(w.findAll('p')).toHaveLength(5)
  await w.setProps({ items: items.slice(0, 11) })
  expect(w.find('p').text()).toBe('条目11')
  await w.setProps({ resetKey: 'new-filter' })
  expect(w.find('p').text()).toBe('条目1')
  await w.find('select').setValue('20')
  expect(w.findAll('p')).toHaveLength(11)
  w.unmount()
})
it('opens chat history at the latest page and follows appended messages', async () => {
  const w = create({ followEnd: true, resetKey: 1 })
  expect(w.find('p').text()).toBe('条目21')
  await w.findAll('button')[0].trigger('click')
  expect(w.find('p').text()).toBe('条目11')
  await w.setProps({ items: [...items, { id: 26 }] })
  expect(w.findAll('p').at(-1).text()).toBe('条目26')
  w.unmount()
})

it('can keep controls visible for a single page without changing other lists', async () => {
  const w = create({ items: items.slice(0, 3) })
  expect(w.find('nav').exists()).toBe(false)
  await w.setProps({ alwaysShow: true })
  expect(w.find('nav').text()).toContain('共 3 条')
  expect(w.findAll('button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  expect(w.findAll('option').map((option) => option.text())).toEqual(['10', '20', '50'])
  w.unmount()
})

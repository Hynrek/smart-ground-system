import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import UserPickerDropdown from '../competition/UserPickerDropdown.vue'

const users = [
  { id: 'u1', displayName: 'Anna Müller' },
  { id: 'u2', displayName: 'Bernd Koch' },
  { id: 'u3', displayName: 'Clara Zinn' },
]

describe('UserPickerDropdown', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders all users when search is empty', () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    const items = wrapper.findAll('.picker-item')
    expect(items).toHaveLength(3)
    expect(items[0].text()).toBe('Anna Müller')
  })

  it('filters users by search text (case-insensitive)', async () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    await wrapper.find('.picker-search').setValue('anna')
    const items = wrapper.findAll('.picker-item')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toBe('Anna Müller')
  })

  it('emits select with user when a row is clicked', async () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    await wrapper.findAll('.picker-item')[1].trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')[0][0]).toEqual({ id: 'u2', displayName: 'Bernd Koch' })
  })

  it('emits close on Escape keydown', async () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    await wrapper.find('.picker-search').trigger('keydown', { key: 'Escape' })
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('shows empty state when no users match search', async () => {
    const wrapper = mount(UserPickerDropdown, { props: { users } })
    await wrapper.find('.picker-search').setValue('zzznomatch')
    expect(wrapper.find('.picker-empty').exists()).toBe(true)
    expect(wrapper.findAll('.picker-item')).toHaveLength(0)
  })
})

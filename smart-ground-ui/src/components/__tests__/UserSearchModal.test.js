import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import UserSearchModal from '../UserSearchModal.vue'

const users = [
  { id: 'u1', fullName: 'Alice Müller' },
  { id: 'u2', fullName: 'Bob Meier' },
  { id: 'u3', fullName: 'Charlie Baum' },
]

describe('UserSearchModal', () => {
  it('renders all users when search is empty', () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    expect(wrapper.findAll('.user-row')).toHaveLength(3)
  })

  it('filters users by fullName (case-insensitive)', async () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    await wrapper.find('input').setValue('alice')
    expect(wrapper.findAll('.user-row')).toHaveLength(1)
    expect(wrapper.find('.user-row').text()).toContain('Alice Müller')
  })

  it('shows empty hint when no match', async () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    await wrapper.find('input').setValue('zzz')
    expect(wrapper.findAll('.user-row')).toHaveLength(0)
    expect(wrapper.find('.empty-hint').exists()).toBe(true)
  })

  it('emits select with the user when a row is clicked', async () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    await wrapper.findAll('.user-row')[1].trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')[0][0]).toEqual(users[1])
  })

  it('emits close when backdrop is clicked', async () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    await wrapper.find('.modal-backdrop').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})

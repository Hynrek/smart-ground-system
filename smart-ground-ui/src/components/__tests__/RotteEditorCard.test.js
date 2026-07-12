// src/components/__tests__/RotteEditorCard.test.js
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import RotteEditorCard from '../competition/RotteEditorCard.vue'

const rotte = {
  rotteId: 'r1',
  name: 'Rotte A',
  players: [
    { id: 'p1', userId: 'u1', displayName: 'Anna Müller', paid: false },
    { id: 'p2', userId: 'u2', displayName: 'Bernd Koch', paid: true },
  ],
}
const availableUsers = [
  { id: 'u3', displayName: 'Clara Zinn' },
]

describe('RotteEditorCard', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows locked display name span instead of editable input per player', () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    const lockedNames = wrapper.findAll('.player-name-locked')
    expect(lockedNames).toHaveLength(2)
    expect(lockedNames[0].text()).toBe('Anna Müller')
    expect(wrapper.find('.player-name-input').exists()).toBe(false)
  })

  it('shows UserPickerDropdown when add button is clicked', async () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    await wrapper.find('.add-player-btn').trigger('click')
    expect(wrapper.findComponent({ name: 'UserPickerDropdown' }).exists()).toBe(true)
  })

  it('emits add-player with user object when picker selects a user', async () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    await wrapper.find('.add-player-btn').trigger('click')
    await wrapper.findComponent({ name: 'UserPickerDropdown' }).vm.$emit('select', availableUsers[0])
    expect(wrapper.emitted('add-player')).toBeTruthy()
    expect(wrapper.emitted('add-player')[0][0]).toEqual(availableUsers[0])
  })

  it('closes picker after user is selected', async () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    await wrapper.find('.add-player-btn').trigger('click')
    await wrapper.findComponent({ name: 'UserPickerDropdown' }).vm.$emit('select', availableUsers[0])
    expect(wrapper.findComponent({ name: 'UserPickerDropdown' }).exists()).toBe(false)
  })

  it('closes picker when picker emits close', async () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers } })
    await wrapper.find('.add-player-btn').trigger('click')
    await wrapper.findComponent({ name: 'UserPickerDropdown' }).vm.$emit('close')
    expect(wrapper.findComponent({ name: 'UserPickerDropdown' }).exists()).toBe(false)
  })

  it('disables add button when availableUsers is empty', () => {
    const wrapper = mount(RotteEditorCard, { props: { rotte, availableUsers: [] } })
    expect(wrapper.find('.add-player-btn').attributes('disabled')).toBeDefined()
  })
})

import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DeviceSearchModal from '../DeviceSearchModal.vue'

const devices = [
  { id: 'd1', alias: 'Werfer 1', smartBoxId: 'aaaaaaaa-1111', deviceType: 'THROWER', groupName: null },
  { id: 'd2', alias: 'Werfer 2', smartBoxId: 'bbbbbbbb-2222', deviceType: 'THROWER', groupName: 'Wurfmaschine' },
  { id: 'd3', alias: 'Lampe',    smartBoxId: null,            deviceType: 'LED',     groupName: null },
]

describe('DeviceSearchModal', () => {
  it('renders all devices when search is empty', () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    expect(wrapper.findAll('.device-row')).toHaveLength(3)
  })

  it('filters devices by alias (case-insensitive)', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.find('input').setValue('lampe')
    expect(wrapper.findAll('.device-row')).toHaveLength(1)
    expect(wrapper.find('.device-row').text()).toContain('Lampe')
  })

  it('shows the empty hint when nothing matches', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.find('input').setValue('zzz')
    expect(wrapper.findAll('.device-row')).toHaveLength(0)
    expect(wrapper.find('.empty-hint').exists()).toBe(true)
  })

  it('shows the empty hint when there are no free devices at all', () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices: [] } })
    expect(wrapper.find('.empty-hint').text()).toContain('Keine freien Geräte verfügbar')
  })

  it('emits select with the device when a row is clicked', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.findAll('.device-row')[1].trigger('click')
    expect(wrapper.emitted('select')[0][0]).toEqual(devices[1])
  })

  it('emits close when the backdrop is clicked', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.find('.modal-backdrop').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('does not emit close when the modal body is clicked', async () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    await wrapper.find('.modal').trigger('click')
    expect(wrapper.emitted('close')).toBeFalsy()
  })

  it('renders each row as a button so it is keyboard operable', () => {
    const wrapper = mount(DeviceSearchModal, { props: { devices } })
    expect(wrapper.find('.device-row').element.tagName).toBe('BUTTON')
  })
})

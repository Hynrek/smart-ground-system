import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import PositionCard from '../PositionCard.vue'

let permissions = []

vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({ hasPermission: (p) => permissions.includes(p) }),
}))
vi.mock('@/stores/deviceStore.js', () => ({
  useDeviceStore: () => ({ blockDevice: vi.fn(), unblockDevice: vi.fn() }),
}))

const emptyPosition = { id: 'p1', label: 'A', device: null }

function mountCard(position = emptyPosition) {
  return mount(PositionCard, {
    props: { position },
    global: { stubs: { Icons: true, DeviceCard: true } },
  })
}

describe('PositionCard empty slot', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    permissions = ['MANAGE_RANGES']
  })

  it('renders the empty slot as a native button for an admin', () => {
    const wrapper = mountCard()
    expect(wrapper.find('.empty-slot').element.tagName).toBe('BUTTON')
  })

  it('emits assign-device when the empty slot is clicked', async () => {
    const wrapper = mountCard()
    await wrapper.find('.empty-slot').trigger('click')
    expect(wrapper.emitted('assign-device')).toBeTruthy()
    expect(wrapper.emitted('assign-device')).toHaveLength(1)
  })

  it('renders an inert slot and emits nothing for a non-admin', async () => {
    permissions = []
    const wrapper = mountCard()
    expect(wrapper.find('.empty-slot').element.tagName).not.toBe('BUTTON')
    await wrapper.find('.empty-slot').trigger('click')
    expect(wrapper.emitted('assign-device')).toBeFalsy()
  })

  it('does not render an empty slot when the position holds a device', () => {
    const wrapper = mountCard({ id: 'p2', label: 'B', device: { id: 'd1', healthy: true } })
    expect(wrapper.find('.empty-slot').exists()).toBe(false)
  })
})

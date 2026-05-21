import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ShooterRemoteView from '../../views/shooter/ShooterRemoteView.vue'
import { useRangeStore } from '../../stores/rangeStore.js'
import { useShooterRemoteStore } from '../../stores/shooterRemoteStore.js'

vi.mock('../../services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue({}),
  fetchPositions: vi.fn().mockResolvedValue([]),
}))

vi.mock('../../components/shooter-remote/ShooterFlyoutPanel.vue', () => ({
  default: { template: '<div />' },
}))

const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })

function mountView(positions = []) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const rangeStore = useRangeStore()
  rangeStore.ranges = [{ id: 'range-1', name: 'Platz 1', locked: false }]
  rangeStore.positions['range-1'] = positions

  const remoteStore = useShooterRemoteStore()
  remoteStore.reservePlatz('range-1')

  return mount(ShooterRemoteView, {
    props: { rangeId: 'range-1' },
    global: { plugins: [pinia, router] },
  })
}

describe('ShooterRemoteView (position-based)', () => {
  it('shows position label as button letter', async () => {
    const wrapper = mountView([
      { id: 'pos-1', label: 'A', device: { id: 'dev-1', alias: 'Werfer 1', blocked: false, healthy: true } },
    ])
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('A')
    expect(wrapper.text()).toContain('Werfer 1')
  })

  it('renders empty position as disabled button', async () => {
    const wrapper = mountView([
      { id: 'pos-2', label: 'B', device: null },
    ])
    await wrapper.vm.$nextTick()
    const btn = wrapper.find('button.device-btn')
    expect(btn.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Kein Gerät')
  })

  it('shows no group dropdown', async () => {
    const wrapper = mountView([
      { id: 'pos-1', label: 'A', device: { id: 'dev-1', alias: 'W1', blocked: false, healthy: true } },
      { id: 'pos-2', label: 'B', device: { id: 'dev-2', alias: 'W2', blocked: false, healthy: true } },
    ])
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.group-dropdown-wrapper').exists()).toBe(false)
  })
})

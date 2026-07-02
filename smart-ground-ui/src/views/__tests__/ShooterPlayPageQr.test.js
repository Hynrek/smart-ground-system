import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterPlayPage from '../shooter/ShooterPlayPage.vue'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({ savedPassen: [] }),
}))
vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/services/userApi.js', () => ({
  fetchMyQrToken: vi.fn(),
  rotateMyQrToken: vi.fn(),
  resolveUserByQr: vi.fn(),
  fetchMyPlayResults: vi.fn(),
}))

const mountGroupSetup = () => {
  const store = usePlaySessionStore()
  store.showGroupSetup = true
  return mount(ShooterPlayPage, {
    props: { rangeId: 'r1' },
    global: { stubs: { Icons: true, QrScanModal: true } },
  })
}

describe('ShooterPlayPage QR check-in', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows a QR add button in the group setup', () => {
    const wrapper = mountGroupSetup()
    expect(wrapper.find('.add-player-qr-btn').exists()).toBe(true)
  })

  it('opens the scan modal on click', async () => {
    const wrapper = mountGroupSetup()
    expect(wrapper.findComponent({ name: 'QrScanModal' }).exists()).toBe(false)

    await wrapper.get('.add-player-qr-btn').trigger('click')

    expect(wrapper.findComponent({ name: 'QrScanModal' }).exists()).toBe(true)
  })

  it('adds a resolved account player with a badge and closes the modal', async () => {
    const wrapper = mountGroupSetup()
    await wrapper.get('.add-player-qr-btn').trigger('click')

    wrapper.findComponent({ name: 'QrScanModal' }).vm.$emit('resolved', {
      userId: 'acc-1',
      displayName: 'Anna Muster',
    })
    await wrapper.vm.$nextTick()

    const names = wrapper.findAll('.player-display-name').map((n) => n.text())
    expect(names).toContain('Anna Muster')
    expect(wrapper.find('.player-account-badge').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'QrScanModal' }).exists()).toBe(false)
  })

  it('rejects a duplicate account with a notice instead of adding twice', async () => {
    const wrapper = mountGroupSetup()
    const scanned = { userId: 'acc-1', displayName: 'Anna Muster' }

    await wrapper.get('.add-player-qr-btn').trigger('click')
    wrapper.findComponent({ name: 'QrScanModal' }).vm.$emit('resolved', scanned)
    await wrapper.vm.$nextTick()

    await wrapper.get('.add-player-qr-btn').trigger('click')
    wrapper.findComponent({ name: 'QrScanModal' }).vm.$emit('resolved', scanned)
    await wrapper.vm.$nextTick()

    const names = wrapper.findAll('.player-display-name').map((n) => n.text())
    expect(names.filter((n) => n === 'Anna Muster')).toHaveLength(1)
    expect(wrapper.get('.qr-scan-notice').text()).toContain('bereits in der Gruppe')
  })
})

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import RangeDetailView from '@/views/admin/RangeDetailView.vue'
import PositionCard from '@/components/PositionCard.vue'
import DeviceSearchModal from '@/components/DeviceSearchModal.vue'

const DEVICE = { id: 'd1', alias: 'Werfer 1', smartBoxId: 'aaaaaaaa-1111', deviceType: 'THROWER', groupName: null, rangeId: null }

const mockRangeStore = {
  ranges: [{ id: 'r1', name: 'Platz 1', description: '', assignedUserId: null }],
  positions: { r1: [{ id: 'p1', label: 'A', device: null }] },
  loadPositions: vi.fn().mockResolvedValue(),
  createPosition: vi.fn().mockResolvedValue(),
  assignDeviceToPosition: vi.fn().mockResolvedValue(),
  removeDeviceFromPosition: vi.fn().mockResolvedValue(),
  renamePosition: vi.fn().mockResolvedValue(),
  deletePosition: vi.fn().mockResolvedValue(),
  assignUser: vi.fn().mockResolvedValue(),
}
const mockDeviceStore = { devices: [DEVICE], loadDevicesForBox: vi.fn().mockResolvedValue() }
const mockSmartBoxStore = { smartboxes: [{ id: 'b1' }], loadApiData: vi.fn().mockResolvedValue() }
const mockReservationStore = {
  getReservationForRange: vi.fn().mockResolvedValue(null),
  reserve: vi.fn(), release: vi.fn(), forceRelease: vi.fn(),
}
const mockUserStore = { users: [], currentUser: { username: 'admin', role: 'ADMIN' }, loadUsers: vi.fn().mockResolvedValue() }

vi.mock('@/stores/rangeStore.js', () => ({ useRangeStore: () => mockRangeStore }))
vi.mock('@/stores/deviceStore.js', () => ({ useDeviceStore: () => mockDeviceStore }))
vi.mock('@/stores/smartBoxStore.js', () => ({ useSmartBoxStore: () => mockSmartBoxStore }))
vi.mock('@/stores/reservationStore.js', () => ({ useReservationStore: () => mockReservationStore }))
vi.mock('@/stores/userStore.js', () => ({ useUserStore: () => mockUserStore }))

const originalMatchMedia = window.matchMedia

function stubMatchMedia(matches) {
  window.matchMedia = vi.fn(() => ({
    matches, media: '', addEventListener: vi.fn(), removeEventListener: vi.fn(),
  }))
}

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })
  const wrapper = mount(RangeDetailView, {
    props: { id: 'r1' },
    shallow: true,
    global: { plugins: [router] },
  })
  await flushPromises()
  return wrapper
}

describe('RangeDetailView device picker', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    stubMatchMedia(false)
  })
  afterEach(() => { window.matchMedia = originalMatchMedia })

  it('opens the device picker when a position requests assignment', async () => {
    const wrapper = await mountView()
    expect(wrapper.findComponent(DeviceSearchModal).exists()).toBe(false)

    await wrapper.findComponent(PositionCard).vm.$emit('assign-device')
    await flushPromises()

    const modal = wrapper.findComponent(DeviceSearchModal)
    expect(modal.exists()).toBe(true)
    expect(modal.props('devices')).toEqual([DEVICE])
  })

  it('assigns the selected device to the position that opened the picker', async () => {
    const wrapper = await mountView()
    await wrapper.findComponent(PositionCard).vm.$emit('assign-device')
    await flushPromises()

    await wrapper.findComponent(DeviceSearchModal).vm.$emit('select', DEVICE)
    await flushPromises()

    expect(mockRangeStore.assignDeviceToPosition).toHaveBeenCalledWith('r1', 'p1', 'd1')
    expect(wrapper.findComponent(DeviceSearchModal).exists()).toBe(false)
  })

  it('closes the picker without assigning when close is emitted', async () => {
    const wrapper = await mountView()
    await wrapper.findComponent(PositionCard).vm.$emit('assign-device')
    await flushPromises()

    await wrapper.findComponent(DeviceSearchModal).vm.$emit('close')
    await flushPromises()

    expect(mockRangeStore.assignDeviceToPosition).not.toHaveBeenCalled()
    expect(wrapper.findComponent(DeviceSearchModal).exists()).toBe(false)
  })
})

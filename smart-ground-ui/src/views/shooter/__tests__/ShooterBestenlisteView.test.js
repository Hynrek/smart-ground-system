import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterBestenlisteView from '@/views/shooter/ShooterBestenlisteView.vue'
import { useScoreStore } from '@/stores/scoreStore.js'

vi.mock('@/services/scoreApi.js', () => ({
  fetchMyScores: vi.fn(),
  fetchMyScoreSummary: vi.fn(),
  fetchLeaderboard: vi.fn().mockResolvedValue({ metric: 'best', entries: [] }),
}))

// rangeStore and passeStore are touched by onMounted(); mock their service imports inert
vi.mock('@/services/rangeApi.js', () => ({
  fetchRanges: vi.fn().mockResolvedValue({ content: [] }),
}))
vi.mock('@/services/deviceApi.js', () => ({
  assignDeviceToRange: vi.fn(),
  removeDeviceFromRange: vi.fn(),
}))
vi.mock('@/services/rangePositionApi.js', () => ({
  fetchPositions: vi.fn(),
  createPosition: vi.fn(),
  renamePosition: vi.fn(),
  deletePosition: vi.fn(),
  assignDevice: vi.fn(),
  removeDevice: vi.fn(),
}))
vi.mock('@/services/serieApi.js', () => ({
  fetchSerien: vi.fn().mockResolvedValue([]),
}))
vi.mock('@/services/passeApi.js', () => ({
  fetchPassen: vi.fn().mockResolvedValue([]),
}))

describe('ShooterBestenlisteView', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders leaderboard entries ranked with position numbers', async () => {
    const wrapper = mount(ShooterBestenlisteView)
    const store = useScoreStore()
    store.leaderboard = {
      metric: 'best',
      entries: [
        { userId: 'u1', displayName: 'Alice', serieCount: 4, bestPercent: 90, averagePercent: 75, totalPoints: 30, maxPoints: 40 },
        { userId: 'u2', displayName: 'Bob', serieCount: 2, bestPercent: 80, averagePercent: 80, totalPoints: 16, maxPoints: 20 },
      ],
    }
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('90%')
    const rows = wrapper.findAll('[data-testid="leaderboard-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('1')
  })

  it('shows an empty state without entries', async () => {
    const wrapper = mount(ShooterBestenlisteView)
    const store = useScoreStore()
    store.leaderboard = { metric: 'best', entries: [] }
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Noch keine Einträge')
  })

  it('reloads when the metric changes', async () => {
    const scoreApi = await import('@/services/scoreApi.js')
    const wrapper = mount(ShooterBestenlisteView)
    await wrapper.find('[data-testid="metric-average"]').trigger('click')
    expect(scoreApi.fetchLeaderboard).toHaveBeenLastCalledWith(
      expect.objectContaining({ metric: 'average' }))
  })
})

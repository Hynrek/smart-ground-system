import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CompetitionResultsView from '../CompetitionResultsView.vue'
import { useAuthStore } from '@/stores/authStore.js'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

const pushMock = vi.fn()
vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal()),
  useRouter: () => ({ push: pushMock }),
}))

vi.mock('@/services/wettkampfApi.js', () => ({
  getLeaderboard: vi.fn(),
  getSession: vi.fn(),
}))

import * as wettkampfApi from '@/services/wettkampfApi.js'

const leaderboard = {
  playerScores: [
    { playerId: 'm2', displayName: 'Bob',   totalScore: 47, maxScore: 50, rank: 1, tied: false, tieResolvedByStechen: false },
    { playerId: 'm1', displayName: 'Alice', totalScore: 40, maxScore: 50, rank: 2, tied: false, tieResolvedByStechen: false },
  ],
}

const session = {
  id: 'ev-1', completedAt: '2026-06-17T10:00:00Z',
  groups: [
    { id: 'g1', name: 'Rotte A', members: [{ id: 'm1', userId: 'u1', displayName: 'Alice' }] },
    { id: 'g2', name: 'Rotte B', members: [{ id: 'm2', userId: 'u2', displayName: 'Bob' }] },
  ],
  playerResults: [
    {
      playerId: 'm1', totalScore: 40, maxScore: 50,
      programResults: JSON.stringify([
        { passeIndex: 0, serieId: 'x', totalPoints: 18, maxPoints: 25 },
        { passeIndex: 1, serieId: 'z', totalPoints: 22, maxPoints: 25 },
      ]),
    },
  ],
}

const mountView = () => mount(CompetitionResultsView, {
  props: { id: 'ev-1' },
  global: { stubs: { Icons: true } },
})

describe('CompetitionResultsView (shooter)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    wettkampfApi.getLeaderboard.mockResolvedValue(leaderboard)
    wettkampfApi.getSession.mockResolvedValue(session)
  })

  it('renders the standings in rank order', async () => {
    const wrapper = mountView()
    await flushPromises()
    const rows = wrapper.findAll('.standing-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('Bob')
    expect(rows[1].text()).toContain('Alice')
  })

  it('highlights the logged-in shooter row', async () => {
    const auth = useAuthStore()
    auth.profile = { id: 'u1' }
    const wrapper = mountView()
    await flushPromises()
    const rows = wrapper.findAll('.standing-row')
    expect(rows[0].classes()).not.toContain('is-me')
    expect(rows[1].classes()).toContain('is-me') // Alice = u1
  })

  it('expands per-Passe detail on tap', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.findAll('.standing-row')[1].trigger('click') // Alice has detail
    const detail = wrapper.find('.player-detail')
    expect(detail.exists()).toBe(true)
    expect(detail.text()).toContain('Passe 1')
    expect(detail.text()).toContain('18 / 25')
  })

  it('navigates back when the back button is clicked', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('.back-btn').trigger('click')
    expect(pushMock).toHaveBeenCalled()
  })

  it('shows an empty state when there are no standings', async () => {
    wettkampfApi.getLeaderboard.mockResolvedValue({ playerScores: [] })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })
})

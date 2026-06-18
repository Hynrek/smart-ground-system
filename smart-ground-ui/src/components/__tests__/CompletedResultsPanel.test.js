import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CompletedResultsPanel from '../competition/CompletedResultsPanel.vue'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

vi.mock('@/services/wettkampfApi.js', () => ({
  getLeaderboard: vi.fn(),
  getSession: vi.fn(),
  exportLeaderboard: vi.fn().mockResolvedValue(undefined),
}))

import * as wettkampfApi from '@/services/wettkampfApi.js'

const leaderboard = {
  sessionId: 'ev-1',
  status: 'COMPLETED',
  playerScores: [
    { playerId: 'm2', displayName: 'Bob',   totalScore: 47, maxScore: 50, rank: 1, tied: false, tieResolvedByStechen: false },
    { playerId: 'm1', displayName: 'Alice', totalScore: 40, maxScore: 50, rank: 2, tied: true,  tieResolvedByStechen: true  },
  ],
  groupScores: [],
}

const session = {
  id: 'ev-1', status: 'COMPLETED', completedAt: '2026-06-17T10:00:00Z',
  groups: [
    { id: 'g1', name: 'Rotte A', members: [{ id: 'm1', displayName: 'Alice' }] },
    { id: 'g2', name: 'Rotte B', members: [{ id: 'm2', displayName: 'Bob' }] },
  ],
  playerResults: [
    {
      playerId: 'm2', totalScore: 47, maxScore: 50,
      programResults: JSON.stringify([
        { passeIndex: 0, serieId: 'x', totalPoints: 24, maxPoints: 25 },
        { passeIndex: 1, serieId: 'z', totalPoints: 23, maxPoints: 25 },
      ]),
    },
  ],
}

const mountPanel = () => mount(CompletedResultsPanel, {
  props: { event: { id: 'ev-1', name: 'Frühjahrspokal', status: 'COMPLETED' } },
  global: { stubs: { Icons: true } },
})

describe('CompletedResultsPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    wettkampfApi.getLeaderboard.mockResolvedValue(leaderboard)
    wettkampfApi.getSession.mockResolvedValue(session)
  })

  it('loads and renders standings in rank order with Rotte and totals', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    const rows = wrapper.findAll('.standing-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('Bob')
    expect(rows[0].text()).toContain('Rotte B')
    expect(rows[0].text()).toContain('47 / 50')
    expect(rows[1].text()).toContain('Alice')
  })

  it('shows a Stechen badge for a tie resolved by Stechen', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('.stechen-badge').exists()).toBe(true)
    expect(wrapper.find('.stechen-badge').text()).toContain('Stechen')
  })

  it('expands per-Passe detail when a row is clicked', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('.player-detail').exists()).toBe(false)
    await wrapper.findAll('.standing-row')[0].trigger('click')
    const detail = wrapper.find('.player-detail')
    expect(detail.exists()).toBe(true)
    expect(detail.text()).toContain('Passe 1')
    expect(detail.text()).toContain('24 / 25')
    expect(detail.text()).toContain('Passe 2')
  })

  it('calls exportLeaderboard when the export button is clicked', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.find('.export-btn').trigger('click')
    expect(wettkampfApi.exportLeaderboard).toHaveBeenCalledWith('ev-1', 'csv')
  })

  it('shows an empty state when there are no standings', async () => {
    wettkampfApi.getLeaderboard.mockResolvedValue({ ...leaderboard, playerScores: [] })
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })
})

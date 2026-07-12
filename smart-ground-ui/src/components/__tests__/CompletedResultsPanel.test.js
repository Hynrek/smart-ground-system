import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CompletedResultsPanel from '../competition/CompletedResultsPanel.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

vi.mock('@/services/wettkampfApi.js', () => ({
  getLeaderboard: vi.fn(),
  getSession: vi.fn(),
  getSerieResults: vi.fn(),
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
  passen: [
    { serien: [{ id: 'x', alias: 'Morgen', rangeName: 'Stand 1', steps: [
      { type: 'solo', letter: 'A' },
      { type: 'pair', letter1: 'B', letter2: 'D' },
    ] }] },
    { serien: [{ id: 'z', alias: 'Abend', rangeName: 'Stand 2', steps: [
      { type: 'solo', letter: 'C' },
    ] }] },
  ],
}

const serieResults = [
  { groupId: 'g2', passeIndex: 0, serieId: 'x',
    serieSnapshot: { serieName: 'Morgen', rangeName: 'Stand 1', steps: [
      { type: 'solo', letter: 'A' },
      { type: 'pair', letter1: 'B', letter2: 'D' },
    ] },
    results: [
      { playerId: 'm2', totalPoints: 24, maxPoints: 25, stepStates: [
        { stepIndex: 0, state: 'done', pointsEarned: 2, pointValue: 2 },
        { stepIndex: 1, state: 'failed-a', pointsEarned: 1, pointValue: 2 },
      ] },
    ] },
  { groupId: 'g2', passeIndex: 1, serieId: 'z',
    serieSnapshot: { serieName: 'Abend', rangeName: 'Stand 2', steps: [
      { type: 'solo', letter: 'C' },
    ] },
    results: [
      { playerId: 'm2', totalPoints: 23, maxPoints: 25, stepStates: [
        { stepIndex: 0, state: 'done', pointsEarned: 2, pointValue: 2 },
      ] },
    ] },
]

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
    wettkampfApi.getSerieResults.mockResolvedValue(serieResults)
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

  it('renders serie-grouped step chips in the expanded detail', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('.standing-row')[0].trigger('click') // Bob (m2)
    expect(wrapper.find('.serie-label').text()).toContain('Stand 1 – Morgen')
    expect(wrapper.findAll('.step-chip').length).toBeGreaterThanOrEqual(2)
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

  // ── read-only parity (COMPLETED view must not regress) ─────────────────────

  it('read-only mode renders non-editable step chips and no picker', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('.standing-row')[0].trigger('click') // Bob (m2)
    expect(wrapper.findAll('.step-chip').length).toBeGreaterThanOrEqual(2)
    // not editable: no editable modifier on any chip and no picker overlay
    expect(wrapper.find('.step-chip--editable').exists()).toBe(false)
    expect(wrapper.find('.picker-overlay').exists()).toBe(false)
  })

  // ── editable mode (PRE_COMPLETE correction reuse) ──────────────────────────

  it('editable mode shows editable chips, opens the picker and calls correctSerieResult with displayName', async () => {
    const wrapper = mount(CompletedResultsPanel, {
      props: { event: { id: 'ev-1', name: 'Frühjahrspokal', status: 'PRE_COMPLETE' }, editable: true },
      global: { stubs: { Icons: true } },
    })
    await flushPromises()

    const store = useCompetitionEventStore()
    const spy = vi.spyOn(store, 'correctSerieResult').mockResolvedValue()

    await wrapper.findAll('.standing-row')[0].trigger('click') // Bob (m2)

    // editable chips are rendered
    const chips = wrapper.findAll('.step-chip--editable')
    expect(chips.length).toBeGreaterThanOrEqual(1)
    expect(wrapper.find('.picker-overlay').exists()).toBe(false)

    // clicking a chip opens the picker
    await chips[0].trigger('click')
    expect(wrapper.find('.picker-overlay').exists()).toBe(true)

    // picking a state persists the correction
    await wrapper.find('.picker-btn--hit').trigger('click')
    await flushPromises()

    expect(spy).toHaveBeenCalledTimes(1)
    const [sessionId, groupId, serieId, passeIndex, results] = spy.mock.calls[0]
    expect(sessionId).toBe('ev-1')
    expect(groupId).toBe('g2')
    expect(serieId).toBe('x')
    expect(passeIndex).toBe(0)
    expect(results.length).toBeGreaterThanOrEqual(1)
    results.forEach(r => expect(typeof r.displayName).toBe('string'))
  })
})

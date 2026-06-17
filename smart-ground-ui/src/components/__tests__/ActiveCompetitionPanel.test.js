import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { usePasseStore } from '@/stores/passeStore.js'
import ActiveCompetitionPanel from '../competition/ActiveCompetitionPanel.vue'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

// The panel polls progress + leaderboard; resolve both empty by default.
vi.mock('@/services/wettkampfApi.js', () => ({
  getProgress: vi.fn().mockResolvedValue({ groups: [] }),
  getLeaderboard: vi.fn().mockResolvedValue({ playerScores: [] }),
}))

import * as wettkampfApi from '@/services/wettkampfApi.js'

const makeEvent = () => ({
  id: 'ev-1',
  name: 'Test WK',
  status: 'ACTIVE',
  groups: [{ id: 'g1', name: 'Rotte A', members: [{ id: 'p1', displayName: 'Alice' }] }],
  passen: [{ id: 'pa1', name: 'Passe 1' }],
})

const mountPanel = (event) => mount(ActiveCompetitionPanel, {
  props: { event },
  global: { stubs: { Icons: true } },
})

describe('ActiveCompetitionPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders without errors', () => {
    const wrapper = mountPanel(makeEvent())
    expect(wrapper.exists()).toBe(true)
  })

  it('renders the abort button', () => {
    const wrapper = mountPanel(makeEvent())
    expect(wrapper.find('.stop-btn').exists()).toBe(true)
    expect(wrapper.find('.stop-btn').text()).toContain('Wettkampf abbrechen')
  })

  it('emits stop when abort button is clicked', async () => {
    const wrapper = mountPanel(makeEvent())
    await wrapper.find('.stop-btn').trigger('click')
    expect(wrapper.emitted('stop')).toBeTruthy()
  })

  it('renders the Passen stepper from event.passen', () => {
    const wrapper = mountPanel(makeEvent())
    expect(wrapper.text()).toContain('Passe 1')
  })

  it('renders serie cards for the active Passe on the Fortschritt tab', async () => {
    const passeStore = usePasseStore()
    passeStore.savedPassen = [
      { id: 'pa1', name: 'Passe 1', serien: [
        { id: 's1', alias: 'Morgenserie' },
        { id: 's2', alias: 'Abendserie' },
      ] },
    ]
    const wrapper = mountPanel(makeEvent())
    await flushPromises()
    const fortschrittTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Fortschritt')
    await fortschrittTab.trigger('click')
    expect(wrapper.text()).toContain('Morgenserie')
    expect(wrapper.text()).toContain('Abendserie')
  })

  it('switches to the Rangliste tab and shows the empty state until results arrive', async () => {
    const wrapper = mountPanel(makeEvent())
    const ranglisteTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Rangliste')
    await ranglisteTab.trigger('click')
    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })

  it('renders ranked player scores on the Rangliste tab', async () => {
    wettkampfApi.getLeaderboard.mockResolvedValueOnce({
      playerScores: [
        { playerId: 'p1', displayName: 'Alice', totalScore: 8, maxScore: 10, rank: 1 },
        { playerId: 'p2', displayName: 'Bob', totalScore: 5, maxScore: 10, rank: 2 },
      ],
    })
    const wrapper = mountPanel(makeEvent())
    await flushPromises()
    const ranglisteTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Rangliste')
    await ranglisteTab.trigger('click')
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('8 / 10')
    expect(wrapper.text()).toContain('Bob')
  })
})

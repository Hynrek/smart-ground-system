import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { usePasseStore } from '@/stores/passeStore.js'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import ActiveCompetitionPanel from '../competition/ActiveCompetitionPanel.vue'
import CompletedResultsPanel from '../competition/CompletedResultsPanel.vue'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

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

const makeRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<div />' } }],
})

const mountPanel = async (event, query = {}) => {
  const router = makeRouter()
  await router.push({ path: '/', query })
  await router.isReady()
  const wrapper = mount(ActiveCompetitionPanel, {
    props: { event },
    global: {
      plugins: [router],
      stubs: { Icons: true, CompletedResultsPanel: true },
    },
  })
  return { wrapper, router }
}

describe('ActiveCompetitionPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders without errors', async () => {
    const { wrapper } = await mountPanel(makeEvent())
    expect(wrapper.exists()).toBe(true)
  })

  it('renders the abort button', async () => {
    const { wrapper } = await mountPanel(makeEvent())
    expect(wrapper.find('.stop-btn').exists()).toBe(true)
    expect(wrapper.find('.stop-btn').text()).toContain('Wettkampf abbrechen')
  })

  it('emits stop when abort button is clicked', async () => {
    const { wrapper } = await mountPanel(makeEvent())
    await wrapper.find('.stop-btn').trigger('click')
    expect(wrapper.emitted('stop')).toBeTruthy()
  })

  it('renders the Passen stepper from event.passen', async () => {
    const { wrapper } = await mountPanel(makeEvent())
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
    const { wrapper } = await mountPanel(makeEvent())
    await flushPromises()
    const fortschrittTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Fortschritt')
    await fortschrittTab.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Morgenserie')
    expect(wrapper.text()).toContain('Abendserie')
  })

  it('switches to the Rangliste tab and shows the empty state until results arrive', async () => {
    const { wrapper } = await mountPanel(makeEvent())
    const ranglisteTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Rangliste')
    await ranglisteTab.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })

  it('renders ranked player scores on the Rangliste tab', async () => {
    wettkampfApi.getLeaderboard.mockResolvedValueOnce({
      playerScores: [
        { playerId: 'p1', displayName: 'Alice', totalScore: 8, maxScore: 10, rank: 1 },
        { playerId: 'p2', displayName: 'Bob', totalScore: 5, maxScore: 10, rank: 2 },
      ],
    })
    const { wrapper } = await mountPanel(makeEvent())
    await flushPromises()
    const ranglisteTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Rangliste')
    await ranglisteTab.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('8 / 10')
    expect(wrapper.text()).toContain('Bob')
  })

  it('renders tabs in order Fortschritt, Rangliste, Rotten', async () => {
    const { wrapper } = await mountPanel(makeEvent())
    const labels = wrapper.findAll('.tab-btn').map(t => t.text())
    expect(labels).toEqual(['Fortschritt', 'Rangliste', 'Rotten'])
  })

  it('defaults to the Fortschritt tab when no ?tab is present', async () => {
    const { wrapper } = await mountPanel(makeEvent())
    const fortschrittTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Fortschritt')
    expect(fortschrittTab.classes()).toContain('active')
  })

  it('falls back to Fortschritt for an unknown ?tab value', async () => {
    const { wrapper } = await mountPanel(makeEvent(), { tab: 'bogus' })
    const fortschrittTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Fortschritt')
    expect(fortschrittTab.classes()).toContain('active')
  })

  it('honours the ?tab query param', async () => {
    const { wrapper } = await mountPanel(makeEvent(), { tab: 'rangliste' })
    await flushPromises()
    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })

  it('writes the active tab to the route query when a tab is clicked', async () => {
    const { wrapper, router } = await mountPanel(makeEvent())
    const ranglisteTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Rangliste')
    await ranglisteTab.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.tab).toBe('rangliste')
  })

  it('marks an individual Serie done from completedSerien (not whole-Passe)', async () => {
    wettkampfApi.getProgress.mockResolvedValue({
      groups: [{
        groupId: 'g1', groupName: 'Rotte A', passenTotal: 1, passenCompleted: 0,
        completions: [{ passeIndex: 0, passeName: 'Passe 1', completed: false }],
        completedSerien: [{ passeIndex: 0, serieId: 's1' }],
      }],
    })
    const passeStore = usePasseStore()
    passeStore.savedPassen = [{ id: 'pa1', name: 'Passe 1', serien: [
      { id: 's1', alias: 'Erste' },
      { id: 's2', alias: 'Zweite' },
    ] }]
    const event = {
      ...makeEvent(),
      groups: [{ id: 'g1', name: 'Rotte A', members: [] }],
    }
    const { wrapper } = await mountPanel(event)
    await flushPromises()
    const fortschrittTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Fortschritt')
    await fortschrittTab.trigger('click')
    const cards = wrapper.findAll('.serie-card')
    const erste = cards.find(c => c.find('.serie-card-header').text().includes('Erste'))
    const zweite = cards.find(c => c.find('.serie-card-header').text().includes('Zweite'))
    expect(erste.find('.rotte-chip').text()).toContain('Fertig')
    expect(zweite.find('.rotte-chip').text()).toContain('Offen')
  })

  it('labels serie cards "StandName: SerienName" when the serie has a Stand', async () => {
    const passeStore = usePasseStore()
    passeStore.savedPassen = [{ id: 'pa1', name: 'Passe 1', serien: [
      { id: 's1', alias: 'Erste', rangeName: 'Stand 1' },
    ] }]
    const event = { ...makeEvent(), groups: [{ id: 'g1', name: 'Rotte A', members: [] }] }
    const { wrapper } = await mountPanel(event)
    await flushPromises()
    const fortschrittTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Fortschritt')
    await fortschrittTab.trigger('click')
    expect(wrapper.find('.serie-card-header').text()).toBe('Stand 1: Erste')
  })

  it('labels serie cards with just the serie name when there is no Stand', async () => {
    const passeStore = usePasseStore()
    passeStore.savedPassen = [{ id: 'pa1', name: 'Passe 1', serien: [{ id: 's1', alias: 'Erste' }] }]
    const event = { ...makeEvent(), groups: [{ id: 'g1', name: 'Rotte A', members: [] }] }
    const { wrapper } = await mountPanel(event)
    await flushPromises()
    const fortschrittTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Fortschritt')
    await fortschrittTab.trigger('click')
    expect(wrapper.find('.serie-card-header').text()).toBe('Erste')
  })

  it('disables "Nächste Passe freigeben" until the released passe is complete', async () => {
    wettkampfApi.getProgress.mockResolvedValue({
      releasedPasseIndex: 0,
      groups: [{ groupId: 'g1', completions: [{ passeIndex: 0, completed: false }, { passeIndex: 1, completed: false }] }],
    })
    const event = { ...makeEvent(), passen: [{ id: 'pa0', name: 'Passe 1' }, { id: 'pa1', name: 'Passe 2' }] }
    const { wrapper } = await mountPanel(event)
    await flushPromises()
    const btn = wrapper.find('.release-passe-btn')
    expect(btn.exists()).toBe(true)
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('enables and calls releaseNextPasse when the released passe is complete', async () => {
    wettkampfApi.getProgress.mockResolvedValue({
      releasedPasseIndex: 0,
      groups: [{ groupId: 'g1', completions: [{ passeIndex: 0, completed: true }, { passeIndex: 1, completed: false }] }],
    })
    const store = useCompetitionEventStore()
    const spy = vi.spyOn(store, 'releaseNextPasse').mockResolvedValue({ releasedPasseIndex: 1 })
    const event = { ...makeEvent(), id: 'c1', passen: [{ id: 'pa0', name: 'Passe 1' }, { id: 'pa1', name: 'Passe 2' }] }
    const { wrapper } = await mountPanel(event)
    await flushPromises()
    const btn = wrapper.find('.release-passe-btn')
    expect(btn.attributes('disabled')).toBeUndefined()
    await btn.trigger('click')
    expect(spy).toHaveBeenCalledWith('c1')
  })

  it('hides the release button when there is only one Passe', async () => {
    const { wrapper } = await mountPanel(makeEvent())
    await flushPromises()
    expect(wrapper.find('.release-passe-btn').exists()).toBe(false)
  })

  it('keeps the Fortschritt tab showing serie progress for PRE_COMPLETE (no correction panel)', async () => {
    const { wrapper } = await mountPanel({ ...makeEvent(), status: 'PRE_COMPLETE' })
    await flushPromises()
    // The Fortschritt tab renders the normal serie progress (cards or empty state),
    // never the editable results panel.
    expect(wrapper.find('.serie-card, .empty-state').exists()).toBe(true)
    expect(wrapper.findComponent(CompletedResultsPanel).exists()).toBe(false)
  })

  it('renders the editable CompletedResultsPanel on the Rangliste tab for PRE_COMPLETE', async () => {
    const { wrapper } = await mountPanel({ ...makeEvent(), status: 'PRE_COMPLETE' })
    const ranglisteTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Rangliste')
    await ranglisteTab.trigger('click')
    await flushPromises()
    const panel = wrapper.findComponent(CompletedResultsPanel)
    expect(panel.exists()).toBe(true)
    expect(panel.props('editable')).toBe(true)
    expect(panel.props('event')).toMatchObject({ id: 'ev-1' })
  })

  it('keeps the live leaderboard rows on the Rangliste tab for ACTIVE (no CompletedResultsPanel)', async () => {
    wettkampfApi.getLeaderboard.mockResolvedValueOnce({
      playerScores: [{ playerId: 'p1', displayName: 'Alice', totalScore: 8, maxScore: 10, rank: 1 }],
    })
    const { wrapper } = await mountPanel(makeEvent())
    await flushPromises()
    const ranglisteTab = wrapper.findAll('.tab-btn').find(t => t.text() === 'Rangliste')
    await ranglisteTab.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CompletedResultsPanel).exists()).toBe(false)
    expect(wrapper.find('.rangliste-row').exists()).toBe(true)
  })
})

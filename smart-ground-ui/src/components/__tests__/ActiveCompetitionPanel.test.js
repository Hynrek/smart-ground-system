import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { usePasseStore } from '@/stores/passeStore.js'
import ActiveCompetitionPanel from '../competition/ActiveCompetitionPanel.vue'

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
    global: { plugins: [router], stubs: { Icons: true } },
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
})

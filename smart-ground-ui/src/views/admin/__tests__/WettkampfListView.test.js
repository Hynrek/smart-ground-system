import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import WettkampfListView from '@/views/admin/WettkampfListView.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { usePasseStore } from '@/stores/passeStore.js'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

const makeRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/admin/wettkampf', component: { template: '<div />' } },
    { path: '/admin/wettkampf/:id', component: { template: '<div />' } },
  ],
})

const setup = async ({ events = [], query = { tab: 'active' } } = {}) => {
  setActivePinia(createPinia())
  const store = useCompetitionEventStore()
  store.events = events
  vi.spyOn(store, 'loadEvents').mockResolvedValue()
  const passeStore = usePasseStore()
  vi.spyOn(passeStore, 'loadPassenFromStorage').mockResolvedValue()

  const router = makeRouter()
  await router.push({ path: '/admin/wettkampf', query })
  await router.isReady()

  const wrapper = mount(WettkampfListView, {
    global: { plugins: [router], stubs: { Icons: true } },
  })
  await flushPromises()
  return { wrapper, store, router }
}

const mkEvent = (o = {}) => ({
  id: 'ev-1', name: 'Test WK', status: 'ACTIVE', groups: [], ...o,
})

describe('WettkampfListView — Aktiv tab phase chip', () => {
  it('labels an ACTIVE event as "Aktiv"', async () => {
    const { wrapper } = await setup({ events: [mkEvent({ status: 'ACTIVE' })] })
    const chip = wrapper.find('.phase-chip')
    expect(chip.text()).toBe('Aktiv')
    expect(chip.classes()).toContain('phase-chip--active')
  })

  it('labels a PRE_COMPLETE event as "In Auswertung"', async () => {
    const { wrapper } = await setup({ events: [mkEvent({ status: 'PRE_COMPLETE' })] })
    const chip = wrapper.find('.phase-chip')
    expect(chip.text()).toBe('In Auswertung')
    expect(chip.classes()).toContain('phase-chip--pre-complete')
  })

  it('gives ACTIVE and PRE_COMPLETE cards visually distinct accent classes', async () => {
    const { wrapper } = await setup({
      events: [mkEvent({ id: 'a', status: 'ACTIVE' }), mkEvent({ id: 'b', status: 'PRE_COMPLETE' })],
    })
    const cards = wrapper.findAll('.event-card')
    expect(cards[0].classes()).toContain('event-card--active')
    expect(cards[1].classes()).toContain('event-card--pre-complete')
  })
})

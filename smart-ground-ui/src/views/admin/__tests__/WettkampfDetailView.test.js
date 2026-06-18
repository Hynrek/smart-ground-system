import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import WettkampfDetailView from '@/views/admin/WettkampfDetailView.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useUserStore } from '@/stores/userStore.js'
import { usePasseStore } from '@/stores/passeStore.js'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

const STUBS = {
  Icons: true,
  RotteEditorCard: true,
  PassePickerDropdown: true,
  ActiveCompetitionPanel: true,
  CompletedResultsPanel: { template: '<div class="completed-stub" />' },
  StechenPanel: true,
}

const makeEvent = (o = {}) => ({
  id: 'ev-1',
  name: 'Test WK',
  status: 'SETUP',
  groups: [{ id: 'g1', name: 'Rotte A', members: [{ id: 'm1', displayName: 'Alice', paid: true }] }],
  passen: [{ id: 'pa1', name: 'Passe 1', serien: [] }],
  ...o,
})

const makeRouter = () => createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/admin/wettkampf', component: { template: '<div />' } },
    { path: '/admin/wettkampf/:id', component: { template: '<div />' } },
  ],
})

// Seed stores + neutralise onMounted loaders, then mount with a memory router.
const setup = async ({ event = makeEvent(), query = {} } = {}) => {
  setActivePinia(createPinia())
  const store = useCompetitionEventStore()
  store.events = [event]
  vi.spyOn(store, 'loadEvents').mockResolvedValue()
  vi.spyOn(store, 'goLive').mockResolvedValue()
  vi.spyOn(store, 'loadTies').mockResolvedValue()
  const userStore = useUserStore()
  vi.spyOn(userStore, 'loadUsers').mockResolvedValue()
  const passeStore = usePasseStore()
  passeStore.savedPassen = [{ id: 'pa1', name: 'Passe 1', serien: [] }]
  vi.spyOn(passeStore, 'loadPassenFromStorage').mockResolvedValue()

  const router = makeRouter()
  await router.push({ path: `/admin/wettkampf/${event.id}`, query })
  await router.isReady()
  const push = vi.spyOn(router, 'push')

  const wrapper = mount(WettkampfDetailView, {
    props: { id: event.id },
    global: { plugins: [router], stubs: STUBS },
  })
  await flushPromises()
  return { wrapper, store, router, push }
}

describe('WettkampfDetailView', () => {
  it('opens the Passen setup tab from ?tab=passen', async () => {
    const { wrapper } = await setup({ query: { tab: 'passen' } })
    expect(wrapper.text()).toContain('Passe hinzufügen')
  })

  it('starts the competition in one click when everyone has paid', async () => {
    const { wrapper, store } = await setup()
    await wrapper.find('.start-btn').trigger('click')
    expect(store.goLive).toHaveBeenCalledWith('ev-1')
  })

  it('shows the payment warning before starting when a player is unpaid', async () => {
    const event = makeEvent({
      groups: [{ id: 'g1', name: 'Rotte A', members: [{ id: 'm1', displayName: 'Bob', paid: false }] }],
    })
    const { wrapper, store } = await setup({ event })
    await wrapper.find('.start-btn').trigger('click')
    expect(store.goLive).not.toHaveBeenCalled()
    expect(wrapper.find('.warning-modal').exists()).toBe(true)
    await wrapper.find('.action-btn--start').trigger('click')
    expect(store.goLive).toHaveBeenCalledWith('ev-1')
  })

  it('renders the planning screen (not a tab-less screen) for an OPEN event', async () => {
    const { wrapper } = await setup({ event: makeEvent({ status: 'OPEN' }) })
    expect(wrapper.find('.tab-row').exists()).toBe(true)
  })

  it('stays on the detail and shows the COMPLETED panel after finishing (no navigation)', async () => {
    const { wrapper, store, push } = await setup({ event: makeEvent({ status: 'PRE_COMPLETE' }) })
    // finishEvent flips the event to COMPLETED in the store, as the real action does.
    vi.spyOn(store, 'finishEvent').mockImplementation(async (id) => {
      store.events = store.events.map(e => e.id === id ? { ...e, status: 'COMPLETED' } : e)
      return { completed: true }
    })
    await wrapper.find('.start-btn').trigger('click') // "Wettkampf abschliessen"
    await flushPromises()
    expect(push).not.toHaveBeenCalled()
    expect(wrapper.find('.completed-stub').exists()).toBe(true)
  })
})

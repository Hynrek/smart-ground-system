import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import CompetitionLiveView from '../CompetitionLiveView.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { useRangeStore } from '@/stores/rangeStore.js'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

const pushMock = vi.fn()
vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal()),
  useRouter: () => ({ push: pushMock }),
}))

const mountView = () => mount(CompetitionLiveView, {
  props: { instanceId: 'i1' },
  global: { stubs: { Icons: true, RangePanelCard: true, LocalScoreboard: true } },
})

describe('CompetitionLiveView completion', () => {
  let store, rangeStore

  beforeEach(() => {
    setActivePinia(createPinia())
    pushMock.mockClear()
    store = useCompetitionEventStore()
    rangeStore = useRangeStore()
    rangeStore.ranges = [{ id: 'r1', name: 'Stand 1' }]
    store.competitionInstances = [{ instanceId: 'i1', sessionId: 'i1', templateName: 'X', rotten: [] }]
  })

  it('shows a Zur Rangliste link when the competition completes', async () => {
    const wrapper = mountView()
    await flushPromises()
    // Competition finishes: instance moves from active to completed.
    store.completedCompetitionInstances = [{ instanceId: 'i1', templateName: 'X', rotten: [] }]
    store.competitionInstances = []
    await nextTick()
    const link = wrapper.find('.results-link')
    expect(link.exists()).toBe(true)
    await link.trigger('click')
    expect(pushMock).toHaveBeenCalledWith('/wettkampf/i1/rangliste')
    expect(pushMock).not.toHaveBeenCalledWith('/wettkampf')
  })

  it('redirects to the list when the competition is abandoned (not completed)', async () => {
    const wrapper = mountView()
    await flushPromises()
    store.competitionInstances = [] // gone, and NOT in completed
    await nextTick()
    expect(wrapper.find('.results-link').exists()).toBe(false)
    expect(pushMock).toHaveBeenCalledWith('/wettkampf')
  })
})

import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import RangePanelCard from '../RangePanelCard.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

const seedInstance = (store, { releasedDone }) => {
  const ev = {
    id: 'c1', name: 'WK', status: 'ACTIVE',
    groups: [{ id: 'r1', name: 'Rotte A', members: [{ id: 'p1', displayName: 'A' }] }],
    passen: [
      { id: 'pa0', name: 'Passe 1', serien: [{ id: 's0', alias: 'S0', rangeId: 'rg1', steps: [] }] },
      { id: 'pa1', name: 'Passe 2', serien: [{ id: 's1', alias: 'S1', rangeId: 'rg1', steps: [] }] },
    ],
  }
  const inst = store.initCompetitionInstance(ev)
  inst.releasedPasseIndex = 0
  const r = inst.rotten[0]
  r.assignedRangeId = 'rg1'
  r.status = 'active'
  if (releasedDone) r.phases[0].blocks[0].status = 'done'
  return inst
}

const mountCard = () => mount(RangePanelCard, {
  props: { range: { id: 'rg1', name: 'Stand 1' }, instanceId: 'c1' },
  global: { stubs: { Icons: true, RotteProgressRow: true } },
})

describe('RangePanelCard waiting state', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows "Warte auf andere Rotten" when the assigned rotte finished the released passe', () => {
    const store = useCompetitionEventStore()
    seedInstance(store, { releasedDone: true })
    const wrapper = mountCard()
    expect(wrapper.text()).toContain('Warte auf andere Rotten')
  })

  it('does not show the waiting state while the rotte still has blocks', () => {
    const store = useCompetitionEventStore()
    seedInstance(store, { releasedDone: false })
    const wrapper = mountCard()
    expect(wrapper.text()).not.toContain('Warte auf andere Rotten')
  })
})

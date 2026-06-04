import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import ActiveCompetitionPanel from '../competition/ActiveCompetitionPanel.vue'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

const makeInstance = () => ({
  instanceId: 'inst-1',
  type: 'competition',
  rotten: [
    {
      rotteId: 'r1',
      name: 'Rotte A',
      currentPhaseIndex: 0,
      phases: [
        {
          phaseIndex: 0,
          passeName: 'Passe 1',
          status: 'active',
          blocks: [
            { blockId: 'b1', serieAlias: 'Morgenserie', status: 'done', result: {
              playerResults: [{ playerId: 'p1', displayName: 'Alice', totalPoints: 8, maxPoints: 10 }]
            }},
            { blockId: 'b2', serieAlias: 'Abendserie', status: 'pending', result: null },
          ],
        },
      ],
    },
  ],
})

const makeEvent = (instanceId = 'inst-1') => ({
  id: 'ev-1',
  name: 'Test WK',
  status: 'ACTIVE',
  activeInstanceId: instanceId,
  passen: [],
  rotten: [],
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

  it('renders passen stepper when a live instance exists', () => {
    const store = useActivePasseStore()
    store.activeInstances.push(makeInstance())
    const wrapper = mountPanel(makeEvent('inst-1'))
    expect(wrapper.text()).toContain('Passe 1')
  })

  it('renders serie cards on the default Serien tab', () => {
    const store = useActivePasseStore()
    store.activeInstances.push(makeInstance())
    const wrapper = mountPanel(makeEvent('inst-1'))
    expect(wrapper.text()).toContain('Morgenserie')
    expect(wrapper.text()).toContain('Abendserie')
  })

  it('switches to Rangliste tab and shows player scores', async () => {
    const store = useActivePasseStore()
    store.activeInstances.push(makeInstance())
    const wrapper = mountPanel(makeEvent('inst-1'))
    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Rangliste').trigger('click')
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('8')
  })
})

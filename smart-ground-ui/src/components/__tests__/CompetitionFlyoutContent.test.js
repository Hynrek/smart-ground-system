import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CompetitionFlyoutContent from '../shooter-remote/CompetitionFlyoutContent.vue'

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

describe('CompetitionFlyoutContent', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders without errors', () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders passen stepper with correct phase names', () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    expect(wrapper.text()).toContain('Passe 1')
  })

  it('renders serie cards on the Serien tab by default', () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    expect(wrapper.text()).toContain('Morgenserie')
    expect(wrapper.text()).toContain('Abendserie')
  })

  it('renders rotte rows inside serie cards', () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    expect(wrapper.text()).toContain('Rotte A')
  })

  it('switches to Rangliste tab and shows player scores', async () => {
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: makeInstance() },
      global: { stubs: { Icons: true } },
    })
    const tabs = wrapper.findAll('.tab-btn')
    const ranglisteTab = tabs.find(t => t.text() === 'Rangliste')
    await ranglisteTab.trigger('click')
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('8')
  })

  it('shows empty state on Rangliste when no blocks are done', async () => {
    const inst = makeInstance()
    inst.rotten[0].phases[0].blocks[0].status = 'pending'
    inst.rotten[0].phases[0].blocks[0].result = null
    const wrapper = mount(CompetitionFlyoutContent, {
      props: { instance: inst },
      global: { stubs: { Icons: true } },
    })
    const tabs = wrapper.findAll('.tab-btn')
    await tabs.find(t => t.text() === 'Rangliste').trigger('click')
    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })
})

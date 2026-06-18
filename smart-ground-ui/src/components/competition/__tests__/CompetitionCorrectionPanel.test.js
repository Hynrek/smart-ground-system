import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CompetitionCorrectionPanel from '../CompetitionCorrectionPanel.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'

const seed = (store) => {
  store.completedResultsBySession = {
    s1: {
      standings: [],
      serieResults: [
        { groupId: 'g1', passeIndex: 0, serieId: 'se1', results: [
          { playerId: 'm1', displayName: 'Max', totalPoints: 3, maxPoints: 4, stepStates: [
            { stepIndex: 0, state: 'done', pointValue: 2, pointsEarned: 2 },
            { stepIndex: 1, state: 'failed-a', pointValue: 2, pointsEarned: 1 },
          ] },
        ] },
        { groupId: 'g1', passeIndex: 1, serieId: 'se2', results: [
          { playerId: 'm1', displayName: 'Max', totalPoints: 1, maxPoints: 1, stepStates: [
            { stepIndex: 0, state: 'done', pointValue: 1, pointsEarned: 1 },
          ] },
        ] },
      ],
      serieDefs: {
        se1: { rangeName: 'Stand 1', serieName: 'Morgen', sortIndex: 0, steps: [
          { type: 'solo', letter: 'A', letter1: null, letter2: null },
          { type: 'pair', letter: null, letter1: 'B', letter2: 'D' },
        ] },
        se2: { rangeName: 'Stand 2', serieName: 'Abend', sortIndex: 1, steps: [
          { type: 'solo', letter: 'C', letter1: null, letter2: null },
        ] },
      },
      completedAt: null,
    },
  }
}

const passen = [{ id: 'pa0', name: 'Passe 1' }, { id: 'pa1', name: 'Passe 2' }]

const mountPanel = () => mount(CompetitionCorrectionPanel, {
  props: { sessionId: 's1', passen },
})

describe('CompetitionCorrectionPanel', () => {
  let store
  beforeEach(() => {
    setActivePinia(createPinia())
    store = useCompetitionEventStore()
    vi.spyOn(store, 'loadCompletedResults').mockResolvedValue()
    vi.spyOn(store, 'correctSerieResult').mockResolvedValue()
    seed(store)
  })

  it('renders a Passe switcher button per Passe', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.findAll('.passe-tab')).toHaveLength(2)
  })

  it('shows the chosen Passe and switches Serien when a Passe is selected', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('Stand 1 – Morgen')
    expect(wrapper.text()).not.toContain('Stand 2 – Abend')
    await wrapper.findAll('.passe-tab')[1].trigger('click')
    expect(wrapper.text()).toContain('Stand 2 – Abend')
    expect(wrapper.text()).not.toContain('Stand 1 – Morgen')
  })

  it('opens the picker on a chip click and persists the corrected Serie on pick', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    // first chip = solo A (done) of se1 / player m1
    await wrapper.findAll('.step-chip')[0].trigger('click')
    expect(wrapper.find('.picker-box').exists()).toBe(true)
    // pick "Fehler" → solo becomes failed-both (0 pts); serie total 0 + (failed-a→1) = 1
    await wrapper.findAll('.picker-btn').find(b => b.text() === 'Fehler').trigger('click')
    await flushPromises()
    expect(store.correctSerieResult).toHaveBeenCalledTimes(1)
    const [sessionId, groupId, serieId, passeIndex, results] = store.correctSerieResult.mock.calls[0]
    expect([sessionId, groupId, serieId, passeIndex]).toEqual(['s1', 'g1', 'se1', 0])
    const m1 = results.find(r => r.playerId === 'm1')
    expect(m1.stepStates[0].state).toBe('failed-both')
    expect(m1.totalPoints).toBe(1) // step0 now 0, step1 (failed-a) still 1
    expect(m1.maxPoints).toBe(4)
  })
})

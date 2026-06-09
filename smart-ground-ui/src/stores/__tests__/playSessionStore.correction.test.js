import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '../playSessionStore.js'
import { StepState } from '@/constants/playEnums.js'

vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({ savedPassen: [] }),
}))
vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))

describe('correctStep', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('overrides step state, marks corrected, saves originalState, and recalculates points', () => {
    const store = usePlaySessionStore()
    store.playScore.stepStates = [
      {
        playerId: 'p1', serieIndex: 0, stepIndex: 0,
        state: StepState.DONE, pointValue: 1, pointsEarned: 1,
        noBirds: 0, corrected: false, originalState: null,
      },
    ]
    store.playScore.totalPoints = 1

    store.correctStep('p1', 0, 0, StepState.FAILED_BOTH)

    const s = store.playScore.stepStates[0]
    expect(s.state).toBe(StepState.FAILED_BOTH)
    expect(s.corrected).toBe(true)
    expect(s.originalState).toBe(StepState.DONE)
    expect(s.pointsEarned).toBe(0)
    expect(store.playScore.totalPoints).toBe(0)
  })

  it('does not overwrite originalState on a second correction', () => {
    const store = usePlaySessionStore()
    store.playScore.stepStates = [
      {
        playerId: 'p1', serieIndex: 0, stepIndex: 0,
        state: StepState.FAILED_A, pointValue: 2, pointsEarned: 1,
        noBirds: 0, corrected: true, originalState: StepState.DONE,
      },
    ]
    store.playScore.totalPoints = 1

    store.correctStep('p1', 0, 0, StepState.FAILED_BOTH)

    expect(store.playScore.stepStates[0].originalState).toBe(StepState.DONE)
    expect(store.playScore.stepStates[0].state).toBe(StepState.FAILED_BOTH)
    expect(store.playScore.totalPoints).toBe(0)
  })

  it('correctly updates points when correcting a fail back to done', () => {
    const store = usePlaySessionStore()
    store.playScore.stepStates = [
      {
        playerId: 'p1', serieIndex: 0, stepIndex: 0,
        state: StepState.FAILED_BOTH, pointValue: 2, pointsEarned: 0,
        noBirds: 0, corrected: false, originalState: null,
      },
    ]
    store.playScore.totalPoints = 0

    store.correctStep('p1', 0, 0, StepState.DONE)

    expect(store.playScore.stepStates[0].pointsEarned).toBe(2)
    expect(store.playScore.totalPoints).toBe(2)
  })

  it('does nothing when newState is PENDING', () => {
    const store = usePlaySessionStore()
    store.playScore.stepStates = [
      {
        playerId: 'p1', serieIndex: 0, stepIndex: 0,
        state: StepState.DONE, pointValue: 1, pointsEarned: 1,
        noBirds: 0, corrected: false, originalState: null,
      },
    ]
    store.playScore.totalPoints = 1

    store.correctStep('p1', 0, 0, StepState.PENDING)

    expect(store.playScore.stepStates[0].corrected).toBe(false)
    expect(store.playScore.totalPoints).toBe(1)
  })

  it('does nothing when playerId/index does not match', () => {
    const store = usePlaySessionStore()
    store.playScore.stepStates = []
    store.playScore.totalPoints = 0

    store.correctStep('unknown', 0, 0, StepState.DONE)

    expect(store.playScore.totalPoints).toBe(0)
  })
})

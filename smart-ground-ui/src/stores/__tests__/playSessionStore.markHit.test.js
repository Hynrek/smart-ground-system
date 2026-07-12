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

const seedSinglePlayer = (store, state, pointValue, pointsEarned) => {
  store.sessionPlayers = [{ id: 'p1', displayName: 'Alice' }]
  store.roundOrder = [0]
  store.currentPlayerIndex = 0
  store.playScore.stepStates = [
    {
      playerId: 'p1', serieIndex: 0, stepIndex: 0,
      state, pointValue, pointsEarned,
      noBirds: 0, corrected: false, originalState: null,
    },
  ]
  store.playScore.totalPoints = pointsEarned
  store.playLastDeviceStep = { serieIdx: 0, stepIdx: 0 }
}

describe('canMarkHit', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('is false when there is no last-fired step', () => {
    const store = usePlaySessionStore()
    expect(store.canMarkHit).toBe(false)
  })

  it('is false when the last step is a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.DONE, 1, 1)
    expect(store.canMarkHit).toBe(false)
  })

  it('is true when the last step is in a failed state', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_A, 2, 1)
    expect(store.canMarkHit).toBe(true)
  })

  it('is false once the passe is complete', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_A, 2, 1)
    store.playComplete = true
    expect(store.canMarkHit).toBe(false)
  })
})

describe('markLastStepHit', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('restores a partial double fail to a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_A, 2, 1)
    store.markLastStepHit()
    const s = store.playScore.stepStates[0]
    expect(s.state).toBe(StepState.DONE)
    expect(s.pointsEarned).toBe(2)
    expect(store.playScore.totalPoints).toBe(2)
  })

  it('restores a full double fail to a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_BOTH, 2, 0)
    store.markLastStepHit()
    const s = store.playScore.stepStates[0]
    expect(s.state).toBe(StepState.DONE)
    expect(s.pointsEarned).toBe(2)
    expect(store.playScore.totalPoints).toBe(2)
  })

  it('restores a solo fail (capped at pointValue) to a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_BOTH, 1, 0)
    store.markLastStepHit()
    const s = store.playScore.stepStates[0]
    expect(s.state).toBe(StepState.DONE)
    expect(s.pointsEarned).toBe(1)
    expect(store.playScore.totalPoints).toBe(1)
  })

  it('does not flag the step as a correction (no audit trail)', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.FAILED_A, 2, 1)
    store.markLastStepHit()
    expect(store.playScore.stepStates[0].corrected).toBe(false)
    expect(store.playScore.stepStates[0].originalState).toBe(null)
  })

  it('is a no-op when the last step is already a full hit', () => {
    const store = usePlaySessionStore()
    seedSinglePlayer(store, StepState.DONE, 1, 1)
    store.markLastStepHit()
    expect(store.playScore.totalPoints).toBe(1)
    expect(store.playScore.stepStates[0].state).toBe(StepState.DONE)
  })
})

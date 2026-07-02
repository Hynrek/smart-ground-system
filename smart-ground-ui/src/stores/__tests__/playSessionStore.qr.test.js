import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepState } from '@/constants/playEnums.js'

describe('playSessionStore QR check-in', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('buildPlayerResults carries userId for account players and null for placeholders', () => {
    const store = usePlaySessionStore()
    store.sessionPlayers = [
      { id: 'gp-1', displayName: 'Anna', userId: 'acc-1' },
      { id: 'gp-2', displayName: 'Schütze 2' },
    ]
    store.playScore = {
      totalPoints: 0,
      stepStates: [
        { playerId: 'gp-1', serieIndex: 0, stepIndex: 0, state: StepState.DONE, pointValue: 1, pointsEarned: 1, noBirds: 0 },
        { playerId: 'gp-2', serieIndex: 0, stepIndex: 0, state: StepState.FAILED, pointValue: 1, pointsEarned: 0, noBirds: 0 },
      ],
    }

    const results = store.buildPlayerResults()

    expect(results[0].userId).toBe('acc-1')
    expect(results[1].userId).toBeNull()
  })
})

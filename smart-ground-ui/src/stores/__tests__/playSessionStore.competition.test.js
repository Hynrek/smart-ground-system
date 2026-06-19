import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '@/stores/playSessionStore.js'
import { StepState } from '@/constants/playEnums.js'

const markBlockDone = vi.fn()
vi.mock('@/stores/competitionEventStore.js', () => ({
  useCompetitionEventStore: () => ({ markBlockDone, markBlockInProgress: vi.fn() }),
}))

describe('playSessionStore competition commit timing', () => {
  beforeEach(() => { setActivePinia(createPinia()); markBlockDone.mockClear() })

  it('confirmComplete shows final screen but does NOT persist', async () => {
    const store = usePlaySessionStore()
    store.activeBlockContext = { instanceId: 'i1', blockId: 'b1', rotteId: 'r1', instanceType: 'competition' }
    store.sessionPlayers = [{ id: 'p1', displayName: 'Anna' }]
    store.playScore = { totalPoints: 1, stepStates: [
      { playerId: 'p1', serieIndex: 0, stepIndex: 0, state: StepState.DONE, pointValue: 1, pointsEarned: 1 },
    ] }
    await store.confirmComplete()
    expect(store.playComplete).toBe(true)
    expect(markBlockDone).not.toHaveBeenCalled()
  })

  it('commitResults persists the current (corrected) results, once', async () => {
    const store = usePlaySessionStore()
    store.activeBlockContext = { instanceId: 'i1', blockId: 'b1', rotteId: 'r1', instanceType: 'competition' }
    store.sessionPlayers = [{ id: 'p1', displayName: 'Anna' }]
    store.playScore = { totalPoints: 0, stepStates: [
      { playerId: 'p1', serieIndex: 0, stepIndex: 0, state: StepState.FAILED_BOTH, pointValue: 1, pointsEarned: 0 },
    ] }
    await store.confirmComplete()
    await store.commitResults()
    await store.commitResults() // idempotent: must not double-submit
    expect(markBlockDone).toHaveBeenCalledOnce()
    const [, , results] = markBlockDone.mock.calls[0]
    expect(results[0].totalPoints).toBe(0)
    expect(results[0].displayName).toBe('Anna')
  })
})

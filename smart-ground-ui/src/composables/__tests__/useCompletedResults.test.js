import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompletedResults } from '../useCompletedResults.js'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'

describe('useCompletedResults', () => {
  beforeEach(() => setActivePinia(createPinia()))

  const seed = (store) => {
    store.completedResultsBySession = {
      s1: {
        standings: [
          { rank: 1, playerId: 'm2', displayName: 'Bob',   rotteName: 'Rotte B', totalScore: 47, maxScore: 50 },
          { rank: 2, playerId: 'm1', displayName: 'Alice', rotteName: 'Rotte A', totalScore: 40, maxScore: 50 },
        ],
        playerResults: [
          {
            playerId: 'm1', totalScore: 40, maxScore: 50,
            programResults: JSON.stringify([
              { passeIndex: 0, serieId: 'x', totalPoints: 12, maxPoints: 14 },
              { passeIndex: 0, serieId: 'y', totalPoints: 8,  maxPoints: 11 },
              { passeIndex: 1, serieId: 'z', totalPoints: 20, maxPoints: 25 },
            ]),
          },
          { playerId: 'm2', totalScore: 47, maxScore: 50, programResults: 'not json' },
        ],
        completedAt: '2026-06-17T10:00:00Z',
      },
    }
  }

  it('exposes standings from the store cache for the session', () => {
    const store = useCompetitionEventStore()
    seed(store)
    const { standings } = useCompletedResults('s1')
    expect(standings.value.map(s => s.displayName)).toEqual(['Bob', 'Alice'])
  })

  it('returns empty standings for an unknown session', () => {
    useCompetitionEventStore()
    const { standings } = useCompletedResults('nope')
    expect(standings.value).toEqual([])
  })

  it('load() delegates to the store action with the session id', async () => {
    const store = useCompetitionEventStore()
    const spy = vi.spyOn(store, 'loadCompletedResults').mockResolvedValue()
    const { load } = useCompletedResults('s1')
    await load()
    expect(spy).toHaveBeenCalledWith('s1')
  })

  it('getPlayerDetail aggregates programResults into per-Passe totals', () => {
    const store = useCompetitionEventStore()
    seed(store)
    const { getPlayerDetail } = useCompletedResults('s1')
    const detail = getPlayerDetail('m1')
    expect(detail.passen).toEqual([
      { label: 'Passe 1', totalPoints: 20, maxPoints: 25 },
      { label: 'Passe 2', totalPoints: 20, maxPoints: 25 },
    ])
    expect(detail.total).toBe(40)
    expect(detail.max).toBe(50)
  })

  it('getPlayerDetail falls back to bare totals when programResults is unparseable', () => {
    const store = useCompetitionEventStore()
    seed(store)
    const { getPlayerDetail } = useCompletedResults('s1')
    const detail = getPlayerDetail('m2')
    expect(detail.passen).toEqual([])
    expect(detail.total).toBe(47)
    expect(detail.max).toBe(50)
  })
})

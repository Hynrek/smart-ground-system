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
        serieResults: [
          { groupId: 'g1', passeIndex: 0, serieId: 'se1', results: [
            { playerId: 'm1', totalPoints: 20, maxPoints: 25, stepStates: [
              { stepIndex: 0, state: 'done', pointsEarned: 2, pointValue: 2 },
              { stepIndex: 1, state: 'failed-a', pointsEarned: 1, pointValue: 2 },
            ] },
          ] },
          { groupId: 'g1', passeIndex: 1, serieId: 'se2', results: [
            { playerId: 'm1', totalPoints: 20, maxPoints: 25, stepStates: [
              { stepIndex: 0, state: 'done', pointsEarned: 2, pointValue: 2 },
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

  it('getPlayerDetail aggregates serie-results into per-Passe totals', () => {
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

  it('getPlayerDetail falls back to the standing total when the player has no serie results', () => {
    const store = useCompetitionEventStore()
    seed(store)
    const { getPlayerDetail } = useCompletedResults('s1')
    const detail = getPlayerDetail('m2')
    expect(detail.passen).toEqual([])
    expect(detail.total).toBe(47)
    expect(detail.max).toBe(50)
  })

  it('getPlayerSerien joins step states to serie definitions (range, name, letters)', () => {
    const store = useCompetitionEventStore()
    seed(store)
    const { getPlayerSerien } = useCompletedResults('s1')
    const serien = getPlayerSerien('m1')
    expect(serien.map(s => s.serieName)).toEqual(['Morgen', 'Abend'])
    expect(serien[0].rangeName).toBe('Stand 1')
    expect(serien[0].steps).toEqual([
      { stepIndex: 0, type: 'solo', letter: 'A', letter1: null, letter2: null, state: 'done' },
      { stepIndex: 1, type: 'pair', letter: null, letter1: 'B', letter2: 'D', state: 'failed-a' },
    ])
  })

  it('getPlayerSerien degrades gracefully when a serie has no definition', () => {
    const store = useCompetitionEventStore()
    seed(store)
    store.completedResultsBySession.s1.serieDefs = {} // no defs
    const { getPlayerSerien } = useCompletedResults('s1')
    const serien = getPlayerSerien('m1')
    expect(serien).toHaveLength(2)
    expect(serien[0].steps[0]).toEqual(
      { stepIndex: 0, type: null, letter: null, letter1: null, letter2: null, state: 'done' },
    )
  })

  it('getPlayerSerien returns [] for a player with no serie results', () => {
    const store = useCompetitionEventStore()
    seed(store)
    const { getPlayerSerien } = useCompletedResults('s1')
    expect(getPlayerSerien('m2')).toEqual([])
  })

  it('recomputeSerieTotals sums earned/value by state', () => {
    const { recomputeSerieTotals } = useCompletedResults('s1')
    const totals = recomputeSerieTotals([
      { state: 'done', pointValue: 2 },
      { state: 'failed-a', pointValue: 2 },
      { state: 'failed-both', pointValue: 2 },
      { state: 'pending', pointValue: 1 },
    ])
    expect(totals).toEqual({ totalPoints: 3, maxPoints: 7 })
  })

  it('getCorrectionData groups the chosen passe by serie with per-player steps', () => {
    const store = useCompetitionEventStore()
    seed(store)
    const { getCorrectionData } = useCompletedResults('s1')
    const data = getCorrectionData(0)
    expect(data.serien[0]).toMatchObject({ serieId: 'se1', rangeName: 'Stand 1', serieName: 'Morgen' })
    expect(data.serien[0].steps[0]).toMatchObject({ stepIndex: 0, type: 'solo', letter: 'A' })
    expect(data.serien[0].players[0]).toMatchObject({ playerId: 'm1' })
    expect(data.serien[0].players[0].steps[0]).toMatchObject({ stepIndex: 0, state: 'done' })
  })
})

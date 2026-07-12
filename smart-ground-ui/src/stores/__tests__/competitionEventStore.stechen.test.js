import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionEventStore } from '../competitionEventStore.js'

vi.mock('@/services/tiebreakerApi.js', () => ({
  getTies: vi.fn(),
  listTiebreakers: vi.fn(),
  startTiebreaker: vi.fn(),
}))
vi.mock('@/services/playInstanceApi.js', () => ({
  startBlock: vi.fn(),
  completeBlock: vi.fn(),
}))
vi.mock('@/services/wettkampfApi.js', () => ({
  listSessions: vi.fn(), getSession: vi.fn(), patchStatus: vi.fn(), getProgress: vi.fn(),
  createSession: vi.fn(), deleteSession: vi.fn(), createGroup: vi.fn(), updateGroup: vi.fn(),
  deleteGroup: vi.fn(), addMember: vi.fn(), removeMember: vi.fn(), patchMember: vi.fn(),
  addPasse: vi.fn(), removePasse: vi.fn(),
}))

import * as tb from '@/services/tiebreakerApi.js'
import * as api from '@/services/wettkampfApi.js'
import * as playApi from '@/services/playInstanceApi.js'

describe('competitionEventStore — Stechen', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('loadTies stores the tied blocks for a session', async () => {
    tb.getTies.mockResolvedValue({ sessionId: 's1', tiedBlocks: [{ tiePosition: 1, sharedScore: 24, resolved: false, players: [], rounds: [] }] })
    const store = useCompetitionEventStore()
    await store.loadTies('s1')
    expect(store.tiesBySession['s1'].tiedBlocks).toHaveLength(1)
  })

  it('startStechen posts and refreshes ties', async () => {
    tb.startTiebreaker.mockResolvedValue({ id: 'tb1', roundNumber: 1 })
    tb.getTies.mockResolvedValue({ sessionId: 's1', tiedBlocks: [] })
    const store = useCompetitionEventStore()
    const res = await store.startStechen('s1', { playerIds: ['p1', 'p2'], templateId: 't1', tiePosition: 1 })
    expect(tb.startTiebreaker).toHaveBeenCalledWith('s1', { playerIds: ['p1', 'p2'], templateId: 't1', tiePosition: 1 })
    expect(res.id).toBe('tb1')
    expect(tb.getTies).toHaveBeenCalledWith('s1')
  })

  it('completeStechenRun completes the block then reloads ties', async () => {
    playApi.completeBlock.mockResolvedValue({ status: 'completed' })
    tb.getTies.mockResolvedValue({ sessionId: 's1', tiedBlocks: [] })
    const store = useCompetitionEventStore()
    const results = [{ playerId: 'p1', totalPoints: 9, maxPoints: 10 }]
    await store.completeStechenRun('inst1', 'blk1', results, 's1')
    expect(playApi.completeBlock).toHaveBeenCalledWith('inst1', 'blk1', results)
    expect(tb.getTies).toHaveBeenCalledWith('s1')
  })

  it('getActiveStechenForRange returns active rounds matching the range', async () => {
    tb.getTies.mockResolvedValue({
      sessionId: 's1',
      tiedBlocks: [{
        tiePosition: 1, sharedScore: 24, resolved: false,
        players: [{ playerId: 'p1', displayName: 'Anna' }],
        rounds: [
          { sessionId: 's1', status: 'COMPLETED', playInstanceId: 'iX', run: { rangeId: 'r1' } },
          { sessionId: 's1', status: 'ACTIVE', playInstanceId: 'iA', blockId: 'bA', templateName: 'Stech',
            participants: [{ playerId: 'p1', displayName: 'Anna' }],
            run: { id: 'se1', alias: 'Stech', rangeId: 'r1', rangeName: 'Stand 1', steps: [] } },
        ],
      }],
    })
    const store = useCompetitionEventStore()
    await store.loadTies('s1')
    const onR1 = store.getActiveStechenForRange('r1')
    expect(onR1).toHaveLength(1)
    expect(onR1[0]).toMatchObject({ instanceId: 'iA', blockId: 'bA', serieId: 'se1', sessionId: 's1', tiePosition: 1 })
    expect(store.getActiveStechenForRange('r2')).toHaveLength(0)
  })

  it('finishEvent returns completed on success', async () => {
    api.patchStatus.mockResolvedValue({ id: 's1', status: 'COMPLETED' })
    api.getSession.mockResolvedValue({ id: 's1', status: 'COMPLETED', groups: [] })
    const store = useCompetitionEventStore()
    const res = await store.finishEvent('s1', false)
    expect(api.patchStatus).toHaveBeenCalledWith('s1', 'completed', { force: false })
    expect(res.completed).toBe(true)
  })

  it('finishEvent surfaces unresolved ties on 409 (not force)', async () => {
    const err = new Error('HTTP 409')
    err.status = 409
    err.body = { message: 'unresolved', unresolvedTies: [{ tiePosition: 1, resolved: false }] }
    api.patchStatus.mockRejectedValue(err)
    const store = useCompetitionEventStore()
    const res = await store.finishEvent('s1', false)
    expect(res.completed).toBe(false)
    expect(res.unresolvedTies).toHaveLength(1)
  })
})

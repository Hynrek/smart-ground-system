import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionEventStore } from '../competitionEventStore.js'

vi.mock('@/services/wettkampfApi.js', () => ({
  createSession: vi.fn(),
  listSessions:  vi.fn(),
  patchStatus:   vi.fn(),
  getSession:    vi.fn(),
  getProgress:   vi.fn(),
  releaseNextPasse: vi.fn(),
  completeSerie: vi.fn(),
  correctSerieResult: vi.fn(),
  getLeaderboard: vi.fn(),
  getSerieResults: vi.fn(),
  deleteSession: vi.fn(),
  createGroup:   vi.fn(),
  updateGroup:   vi.fn(),
  deleteGroup:   vi.fn(),
  addMember:     vi.fn(),
  removeMember:  vi.fn(),
  patchMember:   vi.fn(),
  addPasse:      vi.fn(),
  removePasse:   vi.fn(),
}))

import * as api from '@/services/wettkampfApi.js'

const mkSession = (o = {}) => ({
  id: 's1', name: 'Frühjahrspokal', status: 'SETUP', groups: [], createdAt: '2026-06-05T00:00:00Z', ...o,
})

describe('useCompetitionEventStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadEvents populates events from API', async () => {
    api.listSessions.mockResolvedValue({ content: [mkSession()] })
    const store = useCompetitionEventStore()
    await store.loadEvents()
    expect(store.events).toHaveLength(1)
    expect(store.events[0].name).toBe('Frühjahrspokal')
  })

  it('createEvent calls API and returns new id', async () => {
    api.createSession.mockResolvedValue(mkSession({ id: 'new-1' }))
    api.getSession.mockResolvedValue(mkSession({ id: 'new-1' }))
    const store = useCompetitionEventStore()
    const id = await store.createEvent('Frühjahrspokal', [{ id: 'p1', serieIds: [] }], [])
    expect(api.createSession).toHaveBeenCalledWith('Frühjahrspokal', [{ id: 'p1', serieIds: [] }], [])
    expect(id).toBe('new-1')
    expect(store.events).toHaveLength(1)
  })

  it('startEvent patches status to active', async () => {
    api.patchStatus.mockResolvedValue(mkSession({ status: 'ACTIVE' }))
    api.getSession.mockResolvedValue(mkSession({ status: 'ACTIVE' }))
    const store = useCompetitionEventStore()
    store.events = [mkSession({ status: 'SETUP' })]
    await store.startEvent('s1')
    expect(api.patchStatus).toHaveBeenCalledWith('s1', 'active')
    expect(store.events[0].status).toBe('ACTIVE')
  })

  it('stopEvent deletes the abandoned competition instead of archiving it', async () => {
    api.deleteSession.mockResolvedValue(undefined)
    const store = useCompetitionEventStore()
    store.events = [mkSession({ status: 'ACTIVE' })]
    await store.stopEvent('s1')
    expect(api.deleteSession).toHaveBeenCalledWith('s1')
    expect(api.patchStatus).not.toHaveBeenCalled()
    expect(store.events).toHaveLength(0)
  })

  it('deleteEvent calls API and removes from store', async () => {
    api.deleteSession.mockResolvedValue(undefined)
    const store = useCompetitionEventStore()
    store.events = [mkSession()]
    await store.deleteEvent('s1')
    expect(api.deleteSession).toHaveBeenCalledWith('s1')
    expect(store.events).toHaveLength(0)
  })

  it('addRotte calls createGroup and appends to event', async () => {
    api.createGroup.mockResolvedValue({ id: 'g1', name: 'Rotte A', members: [] })
    const store = useCompetitionEventStore()
    store.events = [mkSession()]
    await store.addRotte('s1')
    expect(api.createGroup).toHaveBeenCalledWith('s1', expect.stringContaining('Rotte'))
    expect(store.events[0].groups).toHaveLength(1)
  })

  it('addPlayer calls addMember API', async () => {
    api.addMember.mockResolvedValue({ id: 'm1', displayName: 'Max', paid: false })
    const store = useCompetitionEventStore()
    store.events = [mkSession({ groups: [{ id: 'g1', name: 'Rotte A', members: [] }] })]
    await store.addPlayer('s1', 'g1', { id: 'u1', displayName: 'Max' })
    expect(api.addMember).toHaveBeenCalledWith('s1', 'g1', expect.objectContaining({ displayName: 'Max' }))
    expect(store.events[0].groups[0].members).toHaveLength(1)
  })

  it('togglePlayerPaid calls patchMember with flipped paid value', async () => {
    api.patchMember.mockResolvedValue({ id: 'm1', paid: true })
    const store = useCompetitionEventStore()
    store.events = [mkSession({ groups: [{ id: 'g1', name: 'Rotte A', members: [{ id: 'm1', displayName: 'Max', paid: false }] }] })]
    await store.togglePlayerPaid('s1', 'g1', 'm1')
    expect(api.patchMember).toHaveBeenCalledWith('s1', 'g1', 'm1', true)
    expect(store.events[0].groups[0].members[0].paid).toBe(true)
  })

  it('planningEvents returns only SETUP events', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'SETUP' }), mkSession({ id: 's2', status: 'ACTIVE' })]
    expect(store.planningEvents).toHaveLength(1)
    expect(store.planningEvents[0].id).toBe('s1')
  })

  it('activeEvents returns ACTIVE and PRE_COMPLETE', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'ACTIVE' }), mkSession({ id: 's2', status: 'PRE_COMPLETE' }), mkSession({ id: 's3', status: 'COMPLETED' })]
    expect(store.activeEvents).toHaveLength(2)
  })

  it('completedEvents returns only COMPLETED', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'COMPLETED' }), mkSession({ id: 's2', status: 'ABANDONED' })]
    expect(store.completedEvents).toHaveLength(1)
  })

  it('addPasseToEvent calls addPasse API and appends to event.passen', async () => {
    const mockPasse = { id: 'p1', name: 'Passe 1', serien: [] }
    api.addPasse.mockResolvedValue(mockPasse)
    const store = useCompetitionEventStore()
    store.events = [mkSession({ passen: [] })]
    await store.addPasseToEvent('s1', 'p1')
    expect(api.addPasse).toHaveBeenCalledWith('s1', 'p1')
    expect(store.events[0].passen).toEqual([mockPasse])
  })

  it('removePasseFromEvent calls removePasse API and removes from event.passen', async () => {
    api.removePasse.mockResolvedValue(undefined)
    const store = useCompetitionEventStore()
    store.events = [mkSession({ passen: [{ id: 'p1', name: 'Passe 1', serien: [] }] })]
    await store.removePasseFromEvent('s1', 'p1')
    expect(api.removePasse).toHaveBeenCalledWith('s1', 'p1')
    expect(store.events[0].passen).toEqual([])
  })

  it('loadEvents hydrates persisted progress + releasedPasseIndex so released Passen advance', async () => {
    const ev = mkSession({
      id: 's1', status: 'ACTIVE',
      groups: [{ id: 'g1', name: 'Rotte A', members: [{ id: 'm1', displayName: 'Max' }] }],
      passen: [
        { id: 'p1', name: 'Passe 1', serien: [{ id: 'se1', alias: 'A', rangeId: 'r1', steps: [] }] },
        { id: 'p2', name: 'Passe 2', serien: [{ id: 'se2', alias: 'B', rangeId: 'r1', steps: [] }] },
      ],
    })
    api.listSessions.mockResolvedValue({ content: [ev] })
    api.getProgress.mockResolvedValue({
      releasedPasseIndex: 1,
      groups: [{ groupId: 'g1', completions: [{ passeIndex: 0, completed: true }, { passeIndex: 1, completed: false }] }],
    })
    const store = useCompetitionEventStore()
    await store.loadEvents()

    const rotten = store.getActiveCompetitionRotten()
    expect(rotten).toHaveLength(1)
    expect(rotten[0].passeName).toBe('Passe 2')
    expect(rotten[0].blocks.map(b => b.serieId)).toEqual(['se2'])
  })

  it('loadEvents hydrates per-Serie completion so a reload resumes mid-Passe', async () => {
    const ev = mkSession({
      id: 's1', status: 'ACTIVE',
      groups: [{ id: 'g1', name: 'Rotte A', members: [{ id: 'm1', displayName: 'Max' }] }],
      passen: [
        { id: 'p1', name: 'Passe 1', serien: [
          { id: 'se1', alias: 'A', rangeId: 'r1', steps: [] },
          { id: 'se2', alias: 'B', rangeId: 'r1', steps: [] },
        ] },
      ],
    })
    api.listSessions.mockResolvedValue({ content: [ev] })
    api.getProgress.mockResolvedValue({
      groups: [{
        groupId: 'g1',
        completions: [{ passeIndex: 0, completed: false }],
        completedSerien: [{ passeIndex: 0, serieId: 'se1' }],
      }],
    })
    const store = useCompetitionEventStore()
    await store.loadEvents()

    const rotten = store.getActiveCompetitionRotten()
    expect(rotten).toHaveLength(1)
    expect(rotten[0].passeName).toBe('Passe 1')
    // se1 already done → only se2 remains to be shot
    expect(rotten[0].blocks.map(b => b.serieId)).toEqual(['se2'])
  })

  it('getActiveCompetitionRotten hides completed Serien within the active Passe', () => {
    const store = useCompetitionEventStore()
    store.competitionInstances = [{
      instanceId: 'i1', sessionId: 'i1', templateName: 'X',
      rotten: [{
        rotteId: 'g1', name: 'Rotte A', players: [], status: 'active', currentPhaseIndex: 0,
        phases: [{
          phaseIndex: 0, passeName: 'Passe 1', status: 'active', blocks: [
            { blockId: 'b1', serieId: 'se1', status: 'done' },
            { blockId: 'b2', serieId: 'se2', status: 'pending' },
          ],
        }],
      }],
    }]
    const rotten = store.getActiveCompetitionRotten()
    expect(rotten[0].blocks.map(b => b.blockId)).toEqual(['b2'])
  })

  it('getActiveCompetitionRotten omits a rotte whose active Passe is fully done', () => {
    const store = useCompetitionEventStore()
    store.competitionInstances = [{
      instanceId: 'i1', sessionId: 'i1', templateName: 'X',
      rotten: [{
        rotteId: 'g1', name: 'Rotte A', players: [], status: 'active', currentPhaseIndex: 0,
        phases: [{
          phaseIndex: 0, passeName: 'Passe 1', status: 'active', blocks: [
            { blockId: 'b1', serieId: 'se1', status: 'done' },
          ],
        }],
      }],
    }]
    expect(store.getActiveCompetitionRotten()).toHaveLength(0)
  })

  it('getActiveCompetitionRotten locks a Wettkampf-Passe to its range when a rangeId is given', () => {
    const store = useCompetitionEventStore()
    store.competitionInstances = [{
      instanceId: 'i1', sessionId: 'i1', templateName: 'X',
      rotten: [{
        rotteId: 'g1', name: 'Rotte A', players: [], status: 'active', currentPhaseIndex: 0,
        phases: [{
          phaseIndex: 0, passeName: 'Passe 1', status: 'active', blocks: [
            { blockId: 'b1', serieId: 'se1', rangeId: 'r1', status: 'pending' },
            { blockId: 'b2', serieId: 'se2', rangeId: 'r2', status: 'pending' },
          ],
        }],
      }],
    }]
    // On r1 only the r1-bound Serie surfaces…
    const onR1 = store.getActiveCompetitionRotten('r1')
    expect(onR1).toHaveLength(1)
    expect(onR1[0].blocks.map(b => b.blockId)).toEqual(['b1'])
    // …on r2 only the r2-bound Serie…
    const onR2 = store.getActiveCompetitionRotten('r2')
    expect(onR2[0].blocks.map(b => b.blockId)).toEqual(['b2'])
    // …and a range with no bound Serien shows nothing.
    expect(store.getActiveCompetitionRotten('r3')).toHaveLength(0)
  })

  it('getActiveCompetitionRotten without a rangeId still returns all open Serien', () => {
    const store = useCompetitionEventStore()
    store.competitionInstances = [{
      instanceId: 'i1', sessionId: 'i1', templateName: 'X',
      rotten: [{
        rotteId: 'g1', name: 'Rotte A', players: [], status: 'active', currentPhaseIndex: 0,
        phases: [{
          phaseIndex: 0, passeName: 'Passe 1', status: 'active', blocks: [
            { blockId: 'b1', serieId: 'se1', rangeId: 'r1', status: 'pending' },
            { blockId: 'b2', serieId: 'se2', rangeId: 'r2', status: 'pending' },
          ],
        }],
      }],
    }]
    expect(store.getActiveCompetitionRotten()[0].blocks.map(b => b.blockId)).toEqual(['b1', 'b2'])
  })

  describe('loadCompletedResults', () => {
    const mkLeaderboard = () => ({
      sessionId: 's1',
      status: 'COMPLETED',
      playerScores: [
        { playerId: 'm2', displayName: 'Bob',   totalScore: 47, maxScore: 50, rank: 1, tied: false, tieResolvedByStechen: false },
        { playerId: 'm1', displayName: 'Alice', totalScore: 40, maxScore: 50, rank: 2, tied: true,  tieResolvedByStechen: true  },
      ],
      groupScores: [],
    })

    const mkCompletedSession = () => mkSession({
      status: 'COMPLETED',
      completedAt: '2026-06-17T10:00:00Z',
      groups: [
        { id: 'g1', name: 'Rotte A', members: [{ id: 'm1', userId: 'u1', displayName: 'Alice' }] },
        { id: 'g2', name: 'Rotte B', members: [{ id: 'm2', userId: 'u2', displayName: 'Bob' }] },
      ],
      playerResults: [{ playerId: 'm1', programResults: '[]', totalScore: 40, maxScore: 50 }],
    })

    const mkSerieResults = () => ([
      { groupId: 'g1', passeIndex: 0, serieId: 'se1', results: [
        { playerId: 'm1', stepStates: [{ stepIndex: 0, state: 'done', pointsEarned: 2, pointValue: 2 }] },
      ] },
    ])

    it('builds ranked standings with Rotte names joined and tie flags preserved', async () => {
      api.getLeaderboard.mockResolvedValue(mkLeaderboard())
      api.getSession.mockResolvedValue(mkCompletedSession())
      api.getSerieResults.mockResolvedValue(mkSerieResults())
      const store = useCompetitionEventStore()

      await store.loadCompletedResults('s1')

      const result = store.completedResultsBySession['s1']
      expect(result.standings.map(s => s.displayName)).toEqual(['Bob', 'Alice'])
      expect(result.standings[0]).toMatchObject({ rank: 1, playerId: 'm2', userId: 'u2', rotteName: 'Rotte B', totalScore: 47, maxScore: 50 })
      expect(result.standings[1]).toMatchObject({ rank: 2, playerId: 'm1', userId: 'u1', rotteName: 'Rotte A', tied: true, tieResolvedByStechen: true })
      expect(result.completedAt).toBe('2026-06-17T10:00:00Z')
      expect(result.serieResults).toHaveLength(1)
      expect(result.serieResults[0].results[0].stepStates[0].state).toBe('done')
    })

    it('builds a serieDefs map from the session passen', async () => {
      api.getLeaderboard.mockResolvedValue({ playerScores: [] })
      api.getSerieResults.mockResolvedValue([])
      api.getSession.mockResolvedValue(mkSession({
        status: 'COMPLETED', groups: [],
        passen: [
          { serien: [
            { id: 'se1', alias: 'Morgen', rangeName: 'Stand 1', steps: [
              { type: 'solo', letter: 'A' },
              { type: 'pair', letter1: 'B', letter2: 'D' },
            ] },
          ] },
          { serien: [{ id: 'se2', name: 'Abend', steps: [] }] },
        ],
      }))
      const store = useCompetitionEventStore()

      await store.loadCompletedResults('s1')

      const defs = store.completedResultsBySession['s1'].serieDefs
      expect(defs.se1).toEqual({
        rangeName: 'Stand 1', serieName: 'Morgen', sortIndex: 0,
        steps: [
          { type: 'solo', letter: 'A', letter1: null, letter2: null },
          { type: 'pair', letter: null, letter1: 'B', letter2: 'D' },
        ],
      })
      expect(defs.se2.serieName).toBe('Abend')
      expect(defs.se2.sortIndex).toBe(1)
    })

    it('tolerates a missing serie-results payload', async () => {
      api.getLeaderboard.mockResolvedValue(mkLeaderboard())
      api.getSession.mockResolvedValue(mkCompletedSession())
      api.getSerieResults.mockResolvedValue(undefined)
      const store = useCompetitionEventStore()

      await store.loadCompletedResults('s1')

      expect(store.completedResultsBySession['s1'].serieResults).toEqual([])
    })

    it('sets error and leaves cache empty when the API fails', async () => {
      api.getLeaderboard.mockRejectedValue(new Error('boom'))
      api.getSession.mockResolvedValue(mkCompletedSession())
      api.getSerieResults.mockResolvedValue([])
      const store = useCompetitionEventStore()

      await store.loadCompletedResults('s1')

      expect(store.error).toBe('boom')
      expect(store.completedResultsBySession['s1']).toBeUndefined()
    })
  })

  describe('synchronized passe phasing', () => {
    const buildInstanceWith = (store, releasedPasseIndex = 0) => {
      const ev = {
        id: 'c1', name: 'WK', status: 'ACTIVE',
        groups: [
          { id: 'r1', name: 'Rotte A', members: [{ id: 'p1', displayName: 'A' }] },
          { id: 'r2', name: 'Rotte B', members: [{ id: 'p2', displayName: 'B' }] },
        ],
        passen: [
          { id: 'pa0', name: 'Passe 1', serien: [{ id: 's0', alias: 'S0', rangeId: 'rg1', steps: [] }] },
          { id: 'pa1', name: 'Passe 2', serien: [{ id: 's1', alias: 'S1', rangeId: 'rg1', steps: [] }] },
        ],
      }
      const inst = store.initCompetitionInstance(ev)
      inst.releasedPasseIndex = releasedPasseIndex
      for (const r of inst.rotten) { r.assignedRangeId = 'rg1'; r.status = 'active' }
      return inst
    }

    it('only surfaces blocks from the released passe', () => {
      const store = useCompetitionEventStore()
      buildInstanceWith(store, 0)
      const blocks = store.getBlocksForRange('rg1')
      expect(blocks.map(b => b.serieId)).toEqual(['s0', 's0'])
    })

    it('a rotte that finished the released passe surfaces no blocks (waiting)', () => {
      const store = useCompetitionEventStore()
      const inst = buildInstanceWith(store, 0)
      inst.rotten[0].phases[0].blocks[0].status = 'done'
      const blocks = store.getBlocksForRange('rg1')
      expect(blocks.map(b => b.rotteId)).toEqual(['r2'])
    })

    it('_completeCompetitionBlock does not advance a rotte past releasedPasseIndex', async () => {
      api.completeSerie.mockResolvedValue(undefined)
      const store = useCompetitionEventStore()
      buildInstanceWith(store, 0)
      await store.markBlockDone('c1', 's0', [], 'r1')
      const surfaced = store.getActiveCompetitionRotten('rg1').flatMap(r => r.blocks.map(b => b.serieId))
      expect(surfaced).not.toContain('s1')
    })

    it('releaseNextPasse posts and bumps the instance index', async () => {
      api.releaseNextPasse.mockResolvedValue({ releasedPasseIndex: 1 })
      const store = useCompetitionEventStore()
      const inst = buildInstanceWith(store, 0)
      await store.releaseNextPasse('c1')
      expect(api.releaseNextPasse).toHaveBeenCalledWith('c1')
      expect(inst.releasedPasseIndex).toBe(1)
    })

    it('isReleasedPasseComplete is true only when all rotten finished the released passe', () => {
      const store = useCompetitionEventStore()
      const inst = buildInstanceWith(store, 0)
      expect(store.isReleasedPasseComplete('c1')).toBe(false)
      for (const r of inst.rotten) r.phases[0].blocks[0].status = 'done'
      expect(store.isReleasedPasseComplete('c1')).toBe(true)
    })
  })

  it('correctSerieResult posts the corrected serie then reloads results', async () => {
    api.correctSerieResult.mockResolvedValue({})
    api.getLeaderboard.mockResolvedValue({ playerScores: [] })
    api.getSession.mockResolvedValue({ id: 's1', groups: [], passen: [] })
    api.getSerieResults.mockResolvedValue([])
    const store = useCompetitionEventStore()
    const results = [{ playerId: 'm1', totalPoints: 2, maxPoints: 2, stepStates: [] }]
    await store.correctSerieResult('s1', 'g1', 'se1', 0, results)
    expect(api.correctSerieResult).toHaveBeenCalledWith('s1', 'g1', 'se1', 0, results)
    expect(api.getSerieResults).toHaveBeenCalledWith('s1')
  })
})

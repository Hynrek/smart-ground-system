import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionEventStore } from '../competitionEventStore.js'

vi.mock('@/services/wettkampfApi.js', () => ({
  listSessions: vi.fn().mockResolvedValue([]),
  completeSerie: vi.fn().mockResolvedValue({}),
  patchStatus: vi.fn().mockResolvedValue({}),
  getSession: vi.fn().mockResolvedValue({}),
  deleteSession: vi.fn().mockResolvedValue(undefined),
}))

import * as wettkampfApi from '@/services/wettkampfApi.js'

const mkInstance = (overrides = {}) => ({
  instanceId: 'inst-comp-1',
  sessionId: 'session-uuid-1',
  type: 'competition',
  templateName: 'WK 1',
  rotten: [
    {
      rotteId: 'ro1',
      name: 'Rotte A',
      players: [{ id: 'u1', displayName: 'Alice' }],
      status: 'waiting',
      assignedRangeId: null,
      currentPhaseIndex: 0,
      phases: [
        {
          phaseIndex: 0,
          passeId: 'p1',
          passeName: 'Passe 1',
          status: 'active',
          blocks: [
            {
              blockId: 'blk-1',
              serieId: 's1',
              serieAlias: 'S1',
              rangeId: 'r1',
              rangeName: 'Platz 1',
              steps: [],
              status: 'pending',
              completedAt: null,
              result: null,
            },
          ],
        },
      ],
    },
  ],
  ...overrides,
})

describe('competitionEventStore — runtime', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('assignRotteToRange updates rotte state in-memory', () => {
    const store = useCompetitionEventStore()
    store.competitionInstances = [mkInstance()]
    store.assignRotteToRange('inst-comp-1', 'ro1', 'r1')
    expect(store.competitionInstances[0].rotten[0].assignedRangeId).toBe('r1')
    expect(store.competitionInstances[0].rotten[0].status).toBe('active')
  })

  it('unassignRotte reverts rotte to paused', () => {
    const store = useCompetitionEventStore()
    const inst = mkInstance()
    inst.rotten[0].assignedRangeId = 'r1'
    inst.rotten[0].status = 'active'
    store.competitionInstances = [inst]
    store.unassignRotte('inst-comp-1', 'ro1')
    expect(store.competitionInstances[0].rotten[0].assignedRangeId).toBeNull()
    expect(store.competitionInstances[0].rotten[0].status).toBe('paused')
  })

  it('getBlocksForRange returns active competition blocks', () => {
    const store = useCompetitionEventStore()
    const inst = mkInstance()
    inst.rotten[0].assignedRangeId = 'r1'
    inst.rotten[0].status = 'active'
    store.competitionInstances = [inst]
    const blocks = store.getBlocksForRange('r1')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].instanceType).toBe('competition')
  })

  it('markBlockDone calls completeSerie API and updates in-memory', async () => {
    const store = useCompetitionEventStore()
    const inst = mkInstance()
    // Second block prevents the phase/rotte from completing so the instance stays active
    inst.rotten[0].phases[0].blocks.push({
      blockId: 'blk-2', serieId: 's2', serieAlias: 'S2', rangeId: 'r1', rangeName: 'Platz 1',
      steps: [], status: 'pending', completedAt: null, result: null,
    })
    inst.rotten[0].assignedRangeId = 'r1'
    inst.rotten[0].status = 'active'
    store.competitionInstances = [inst]
    const results = [{ playerId: 'u1', totalPoints: 8, maxPoints: 10 }]
    await store.markBlockDone('inst-comp-1', 'blk-1', results, 'ro1')
    expect(wettkampfApi.completeSerie).toHaveBeenCalledWith('session-uuid-1', 'ro1', 'blk-1', 0, null, results)
    expect(store.competitionInstances[0].rotten[0].phases[0].blocks[0].status).toBe('done')
  })

  it('stopCompetition deletes the session and removes instance', async () => {
    const store = useCompetitionEventStore()
    store.competitionInstances = [mkInstance()]
    await store.stopCompetition('inst-comp-1')
    expect(wettkampfApi.deleteSession).toHaveBeenCalledWith('session-uuid-1')
    expect(wettkampfApi.patchStatus).not.toHaveBeenCalled()
    expect(store.competitionInstances).toHaveLength(0)
  })

  it('getActiveCompetitionRotten returns active rotten with block info', () => {
    const store = useCompetitionEventStore()
    const inst = mkInstance()
    inst.rotten[0].status = 'active'
    store.competitionInstances = [inst]
    const result = store.getActiveCompetitionRotten()
    expect(result).toHaveLength(1)
    expect(result[0].rotteName).toBe('Rotte A')
  })
})

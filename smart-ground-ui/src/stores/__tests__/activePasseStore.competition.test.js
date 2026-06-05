import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useActivePasseStore } from '../activePasseStore.js'

vi.mock('@/services/playInstanceApi.js', () => ({
  listPlayInstances: vi.fn().mockResolvedValue([]),
  startProgrammeInstance: vi.fn(),
  startTrainingInstance: vi.fn(),
  startBlock: vi.fn(),
  completeBlock: vi.fn(),
  stopPlayInstance: vi.fn(),
}))

vi.mock('@/services/wettkampfApi.js', () => ({
  completeSerie: vi.fn().mockResolvedValue({}),
  patchStatus: vi.fn().mockResolvedValue({}),
}))

import * as wettkampfApi from '@/services/wettkampfApi.js'

const mkCompetitionInstance = (overrides = {}) => ({
  instanceId: 'inst-comp-1',
  type: 'competition',
  sessionId: 'session-uuid-1',
  templateId: 't1',
  templateName: 'WK 1',
  name: 'WK 1',
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
  startedAt: Date.now(),
  completedAt: null,
  ...overrides,
})

describe('activePasseStore — Competition', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('assignRotteToRange updates rotte state in-memory', () => {
    const store = useActivePasseStore()
    store.activeInstances = [mkCompetitionInstance()]
    store.assignRotteToRange('inst-comp-1', 'ro1', 'r1')
    expect(store.activeInstances[0].rotten[0].assignedRangeId).toBe('r1')
    expect(store.activeInstances[0].rotten[0].status).toBe('active')
  })

  it('unassignRotte reverts rotte to paused', () => {
    const store = useActivePasseStore()
    const inst = mkCompetitionInstance()
    inst.rotten[0].assignedRangeId = 'r1'
    inst.rotten[0].status = 'active'
    store.activeInstances = [inst]
    store.unassignRotte('inst-comp-1', 'ro1')
    expect(store.activeInstances[0].rotten[0].assignedRangeId).toBeNull()
    expect(store.activeInstances[0].rotten[0].status).toBe('paused')
  })

  it('getBlocksForRange returns active competition blocks', () => {
    const store = useActivePasseStore()
    const inst = mkCompetitionInstance()
    inst.rotten[0].assignedRangeId = 'r1'
    inst.rotten[0].status = 'active'
    store.activeInstances = [inst]
    const blocks = store.getBlocksForRange('r1')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].instanceType).toBe('competition')
  })

  it('markBlockDone calls completeSerie API and updates in-memory', async () => {
    const store = useActivePasseStore()
    const inst = mkCompetitionInstance()
    // Add a second block so the phase/rotte don't complete (instance stays in activeInstances)
    inst.rotten[0].phases[0].blocks.push({
      blockId: 'blk-2', serieId: 's2', serieAlias: 'S2', rangeId: 'r1', rangeName: 'Platz 1',
      steps: [], status: 'pending', completedAt: null, result: null,
    })
    inst.rotten[0].assignedRangeId = 'r1'
    inst.rotten[0].status = 'active'
    store.activeInstances = [inst]
    const results = [{ playerId: 'u1', totalPoints: 8, maxPoints: 10 }]
    await store.markBlockDone('inst-comp-1', 'blk-1', results, 'ro1')
    expect(wettkampfApi.completeSerie).toHaveBeenCalledWith('session-uuid-1', 'ro1', 'blk-1', 0, null, results)
    expect(store.activeInstances[0].rotten[0].phases[0].blocks[0].status).toBe('done')
  })

  it('stopCompetition calls patchStatus API and removes instance', async () => {
    const store = useActivePasseStore()
    store.activeInstances = [mkCompetitionInstance()]
    await store.stopCompetition('inst-comp-1')
    expect(wettkampfApi.patchStatus).toHaveBeenCalledWith('session-uuid-1', 'abandoned')
    expect(store.activeInstances).toHaveLength(0)
  })

  it('getActiveCompetitionRotten returns active rotten with block info', () => {
    const store = useActivePasseStore()
    const inst = mkCompetitionInstance()
    inst.rotten[0].assignedRangeId = 'r1'
    inst.rotten[0].status = 'active'
    store.activeInstances = [inst]
    const result = store.getActiveCompetitionRotten()
    expect(result).toHaveLength(1)
    expect(result[0].rotteName).toBe('Rotte A')
  })
})

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

describe('activePasseStore — Competition (in-memory)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('startCompetition creates an in-memory competition instance', () => {
    const store = useActivePasseStore()
    const template = {
      id: 't1',
      name: 'WK 1',
      passen: [
        {
          id: 'p1',
          name: 'Passe 1',
          serien: [{ id: 's1', name: 'S1', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }],
        },
      ],
    }
    const rotten = [
      { rotteId: 'ro1', name: 'Rotte A', players: [{ id: 'u1', displayName: 'Alice' }] },
    ]
    const inst = store.startCompetition(template, rotten)
    expect(inst.type).toBe('competition')
    expect(inst.rotten).toHaveLength(1)
    expect(inst.rotten[0].phases).toHaveLength(1)
    expect(store.activeInstances).toHaveLength(1)
  })

  it('assignRotteToRange updates rotte state in-memory', () => {
    const store = useActivePasseStore()
    const template = { id: 't1', name: 'WK 1', passen: [] }
    const rotten = [{ rotteId: 'ro1', name: 'Rotte A', players: [] }]
    const inst = store.startCompetition(template, rotten)
    store.assignRotteToRange(inst.instanceId, 'ro1', 'r1')
    expect(store.activeInstances[0].rotten[0].assignedRangeId).toBe('r1')
    expect(store.activeInstances[0].rotten[0].status).toBe('active')
  })

  it('unassignRotte reverts rotte to paused', () => {
    const store = useActivePasseStore()
    const template = { id: 't1', name: 'WK 1', passen: [] }
    const rotten = [{ rotteId: 'ro1', name: 'Rotte A', players: [] }]
    const inst = store.startCompetition(template, rotten)
    store.assignRotteToRange(inst.instanceId, 'ro1', 'r1')
    store.unassignRotte(inst.instanceId, 'ro1')
    expect(store.activeInstances[0].rotten[0].assignedRangeId).toBeNull()
    expect(store.activeInstances[0].rotten[0].status).toBe('paused')
  })

  it('getBlocksForRange returns active competition blocks', () => {
    const store = useActivePasseStore()
    const template = {
      id: 't1',
      name: 'WK 1',
      passen: [
        {
          id: 'p1',
          name: 'Passe 1',
          serien: [{ id: 's1', name: 'S1', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }],
        },
      ],
    }
    const rotten = [{ rotteId: 'ro1', name: 'Rotte A', players: [] }]
    const inst = store.startCompetition(template, rotten)
    store.assignRotteToRange(inst.instanceId, 'ro1', 'r1')
    const blocks = store.getBlocksForRange('r1')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].instanceType).toBe('competition')
  })

  it('stopCompetition removes instance from active list', async () => {
    const store = useActivePasseStore()
    const template = { id: 't1', name: 'WK 1', passen: [] }
    const rotten = [{ rotteId: 'ro1', name: 'Rotte A', players: [] }]
    const inst = store.startCompetition(template, rotten)
    await store.stopCompetition(inst.instanceId)
    expect(store.activeInstances).toHaveLength(0)
  })

  it('getActiveCompetitionRotten returns active rotten with block info', () => {
    const store = useActivePasseStore()
    const template = {
      id: 't1',
      name: 'WK 1',
      passen: [{ id: 'p1', name: 'Passe 1', serien: [] }],
    }
    const rotten = [{ rotteId: 'ro1', name: 'Rotte A', players: [{ id: 'u1', displayName: 'Alice' }] }]
    const inst = store.startCompetition(template, rotten)
    store.assignRotteToRange(inst.instanceId, 'ro1', 'r1')
    const result = store.getActiveCompetitionRotten()
    expect(result).toHaveLength(1)
    expect(result[0].rotteName).toBe('Rotte A')
  })
})

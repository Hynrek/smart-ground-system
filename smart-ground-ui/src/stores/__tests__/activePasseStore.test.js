import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useActivePasseStore } from '../activePasseStore'

const template = {
  id: 'passe-1',
  name: 'Test Passe',
  serien: [
    { id: 'serie-1', name: 'Serie 1', rangeId: 'r1', rangeName: 'Platz 1', steps: [{ id: 's1', type: 'solo' }] },
    { id: 'serie-2', name: 'Serie 2', rangeId: 'r2', rangeName: 'Platz 2', steps: [{ id: 's2', type: 'solo' }] },
  ],
}
const players = [{ id: 'p1', displayName: 'Schütze 1', type: 'guest' }]

describe('useActivePasseStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('startPasse creates one block per serie with pending status', () => {
    const store = useActivePasseStore()
    const inst = store.startPasse(template, players)
    expect(store.activeInstances).toHaveLength(1)
    expect(inst.type).toBe('passe')
    expect(inst.blocks).toHaveLength(2)
    expect(inst.blocks[0].serieId).toBe('serie-1')
    expect(inst.blocks[0].status).toBe('pending')
    expect(inst.blocks[0].steps).toHaveLength(1)
  })

  it('startPasse persists to localStorage', () => {
    const store = useActivePasseStore()
    store.startPasse(template, players)
    expect(localStorage.getItem('sg_active_passe_instances')).not.toBeNull()
  })

  it('getBlocksForRange returns only pending and in_progress blocks for that range', () => {
    const store = useActivePasseStore()
    store.startPasse(template, players)
    const blocks = store.getBlocksForRange('r1')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].serieId).toBe('serie-1')
    expect(blocks[0].instanceId).toBeDefined()
    expect(blocks[0].templateName).toBe('Test Passe')
  })

  it('getBlocksForRange excludes done blocks', () => {
    const store = useActivePasseStore()
    const inst = store.startPasse(template, players)
    store.markBlockDone(inst.instanceId, inst.blocks[0].blockId, [])
    expect(store.getBlocksForRange('r1')).toHaveLength(0)
  })

  it('markBlockInProgress sets status to in_progress', () => {
    const store = useActivePasseStore()
    const inst = store.startPasse(template, players)
    store.markBlockInProgress(inst.instanceId, inst.blocks[0].blockId)
    expect(store.activeInstances[0].blocks[0].status).toBe('in_progress')
  })

  it('markBlockDone stores result on block', () => {
    const store = useActivePasseStore()
    const inst = store.startPasse(template, players)
    const results = [{ playerId: 'p1', displayName: 'Schütze 1', totalPoints: 1, maxPoints: 1, stepStates: [] }]
    store.markBlockDone(inst.instanceId, inst.blocks[0].blockId, results)
    expect(store.activeInstances[0].blocks[0].status).toBe('done')
    expect(store.activeInstances[0].blocks[0].result.playerResults).toHaveLength(1)
  })

  it('markBlockDone moves instance to completedInstances when all blocks done', () => {
    const store = useActivePasseStore()
    const inst = store.startPasse(template, players)
    store.markBlockDone(inst.instanceId, inst.blocks[0].blockId, [])
    expect(store.activeInstances).toHaveLength(1)
    store.markBlockDone(inst.instanceId, inst.blocks[1].blockId, [])
    expect(store.activeInstances).toHaveLength(0)
    expect(store.completedInstances).toHaveLength(1)
    expect(store.completedInstances[0].completedAt).toBeDefined()
  })

  it('stopInstance removes active instance', () => {
    const store = useActivePasseStore()
    const inst = store.startPasse(template, players)
    store.stopInstance(inst.instanceId)
    expect(store.activeInstances).toHaveLength(0)
  })

  it('loads persisted data on init', () => {
    const store = useActivePasseStore()
    store.startPasse(template, players)
    setActivePinia(createPinia())
    const store2 = useActivePasseStore()
    expect(store2.activeInstances).toHaveLength(1)
  })
})

const trainingTemplate = {
  id: 'training-1',
  name: 'Training Woche 1',
  passen: [
    {
      id: 'passe-1',
      name: 'Aufwärmen',
      serien: [
        { id: 'serie-1', name: 'S1', alias: 'S1', rangeId: 'r1', rangeName: 'Platz 1', steps: [{ id: 's1', type: 'solo' }] },
      ],
    },
    {
      id: 'passe-2',
      name: 'Hauptteil',
      serien: [
        { id: 'serie-2', name: 'S2', alias: 'S2', rangeId: 'r2', rangeName: 'Platz 2', steps: [{ id: 's2', type: 'solo' }] },
      ],
    },
  ],
}

describe('useActivePasseStore — Training', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('startTraining creates instance with type training', () => {
    const store = useActivePasseStore()
    const inst = store.startTraining(trainingTemplate, players)
    expect(inst.type).toBe('training')
    expect(store.activeInstances).toHaveLength(1)
  })

  it('startTraining creates one phase per passe', () => {
    const store = useActivePasseStore()
    const inst = store.startTraining(trainingTemplate, players)
    expect(inst.phases).toHaveLength(2)
    expect(inst.phases[0].passeName).toBe('Aufwärmen')
    expect(inst.phases[1].passeName).toBe('Hauptteil')
  })

  it('startTraining sets first phase active, rest pending', () => {
    const store = useActivePasseStore()
    const inst = store.startTraining(trainingTemplate, players)
    expect(inst.phases[0].status).toBe('active')
    expect(inst.phases[1].status).toBe('pending')
    expect(inst.currentPhaseIndex).toBe(0)
  })

  it('startTraining creates blocks for each serie within each phase', () => {
    const store = useActivePasseStore()
    const inst = store.startTraining(trainingTemplate, players)
    expect(inst.phases[0].blocks).toHaveLength(1)
    expect(inst.phases[0].blocks[0].serieId).toBe('serie-1')
    expect(inst.phases[0].blocks[0].status).toBe('pending')
    expect(inst.phases[1].blocks).toHaveLength(1)
    expect(inst.phases[1].blocks[0].serieId).toBe('serie-2')
  })

  it('startTraining persists to localStorage', () => {
    const store = useActivePasseStore()
    store.startTraining(trainingTemplate, players)
    const saved = JSON.parse(localStorage.getItem('sg_active_passe_instances'))
    expect(saved).toHaveLength(1)
    expect(saved[0].type).toBe('training')
  })

  it('getBlocksForRange returns only current-phase blocks for training', () => {
    const store = useActivePasseStore()
    store.startTraining(trainingTemplate, players)
    expect(store.getBlocksForRange('r1')).toHaveLength(1)
    expect(store.getBlocksForRange('r2')).toHaveLength(0)
  })

  it('getBlocksForRange attaches instanceType training to training blocks', () => {
    const store = useActivePasseStore()
    store.startTraining(trainingTemplate, players)
    const blocks = store.getBlocksForRange('r1')
    expect(blocks[0].instanceType).toBe('training')
    expect(blocks[0].passeName).toBe('Aufwärmen')
  })

  it('getBlocksForRange attaches instanceType passe to passe blocks', () => {
    const store = useActivePasseStore()
    store.startPasse(template, players)
    const blocks = store.getBlocksForRange('r1')
    expect(blocks[0].instanceType).toBe('passe')
  })

  it('markBlockDone on training advances to next phase when all blocks in phase are done', () => {
    const store = useActivePasseStore()
    const inst = store.startTraining(trainingTemplate, players)
    const block = inst.phases[0].blocks[0]
    store.markBlockDone(inst.instanceId, block.blockId, [])
    const updated = store.activeInstances[0]
    expect(updated.currentPhaseIndex).toBe(1)
    expect(updated.phases[0].status).toBe('done')
    expect(updated.phases[1].status).toBe('active')
    expect(store.getBlocksForRange('r2')).toHaveLength(1)
  })

  it('markBlockDone on training completes instance when last phase done', () => {
    const store = useActivePasseStore()
    const inst = store.startTraining(trainingTemplate, players)
    const block0 = inst.phases[0].blocks[0]
    store.markBlockDone(inst.instanceId, block0.blockId, [])
    const block1 = store.activeInstances[0].phases[1].blocks[0]
    store.markBlockDone(inst.instanceId, block1.blockId, [])
    expect(store.activeInstances).toHaveLength(0)
    expect(store.completedInstances).toHaveLength(1)
    expect(store.completedInstances[0].type).toBe('training')
    expect(store.completedInstances[0].completedAt).toBeDefined()
  })

  it('markBlockDone on training stores result on block', () => {
    const store = useActivePasseStore()
    const inst = store.startTraining(trainingTemplate, players)
    const block = inst.phases[0].blocks[0]
    const results = [{ playerId: 'p1', displayName: 'Schütze 1', totalPoints: 5, maxPoints: 5, stepStates: [] }]
    store.markBlockDone(inst.instanceId, block.blockId, results)
    expect(store.activeInstances[0].phases[0].blocks[0].result.playerResults).toHaveLength(1)
  })
})

describe('getActiveCompetitionRotten', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('returns empty array when no competitions are active', () => {
    const store = useActivePasseStore()
    expect(store.getActiveCompetitionRotten()).toEqual([])
  })

  it('returns one entry per non-done rotte across all active competitions', () => {
    const store = useActivePasseStore()

    const template = {
      id: 'comp-1',
      name: 'Frühjahrspokal',
      passen: [
        {
          id: 'passe-1',
          name: 'Passe 1',
          serien: [
            { id: 'serie-1', name: 'A', alias: 'A', steps: [], rangeId: 'range-1', rangeName: 'Platz 1' },
          ],
        },
      ],
    }

    const rotten = [
      { rotteId: 'r1', name: 'Rotte 1', players: [{ id: 'p1', displayName: 'Max' }] },
      { rotteId: 'r2', name: 'Rotte 2', players: [{ id: 'p2', displayName: 'Lisa' }] },
    ]

    store.startCompetition(template, rotten)

    const result = store.getActiveCompetitionRotten()

    expect(result).toHaveLength(2)
    expect(result[0].rotteId).toBe('r1')
    expect(result[0].rotteName).toBe('Rotte 1')
    expect(result[0].passeName).toBe('Passe 1')
    expect(result[0].instanceName).toBe('Frühjahrspokal')
    expect(result[0].players).toEqual([{ id: 'p1', displayName: 'Max' }])
    expect(result[0].blocks).toHaveLength(1)
    expect(result[1].rotteId).toBe('r2')
  })

  it('excludes rotten with status done', () => {
    const store = useActivePasseStore()

    const template = {
      id: 'comp-1',
      name: 'Frühjahrspokal',
      passen: [
        {
          id: 'passe-1',
          name: 'Passe 1',
          serien: [
            { id: 'serie-1', name: 'A', alias: 'A', steps: [], rangeId: 'range-1', rangeName: 'Platz 1' },
          ],
        },
      ],
    }

    const rotten = [
      { rotteId: 'r1', name: 'Rotte 1', players: [] },
      { rotteId: 'r2', name: 'Rotte 2', players: [] },
    ]

    store.startCompetition(template, rotten)

    // Manually mark r1 as done
    const inst = store.activeInstances[0]
    inst.rotten.find(r => r.rotteId === 'r1').status = 'done'

    const result = store.getActiveCompetitionRotten()
    expect(result).toHaveLength(1)
    expect(result[0].rotteId).toBe('r2')
  })

  it('does not filter by range — all rotten appear regardless of assignedRangeId', () => {
    const store = useActivePasseStore()

    const template = {
      id: 'comp-1',
      name: 'Cup',
      passen: [{ id: 'p1', name: 'P1', serien: [{ id: 's1', name: 'A', alias: 'A', steps: [], rangeId: 'range-99', rangeName: 'X' }] }],
    }

    store.startCompetition(template, [
      { rotteId: 'r1', name: 'Rotte 1', players: [] },
    ])

    // No range assigned — should still appear
    const result = store.getActiveCompetitionRotten()
    expect(result).toHaveLength(1)
  })
})

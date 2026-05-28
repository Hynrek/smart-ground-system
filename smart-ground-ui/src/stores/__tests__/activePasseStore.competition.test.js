import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useActivePasseStore } from '../activePasseStore'

const competitionTemplate = {
  id: 'comp-tmpl-1',
  name: 'Liga Runde 1',
  passen: [
    {
      id: 'passe-1',
      name: 'Aufwärmen',
      serien: [
        {
          id: 'serie-1',
          name: 'S1',
          rangeId: 'r1',
          rangeName: 'Platz 1',
          steps: [{ id: 's1', type: 'solo' }],
        },
      ],
    },
    {
      id: 'passe-2',
      name: 'Hauptteil',
      serien: [
        {
          id: 'serie-2',
          name: 'S2',
          rangeId: 'r1',
          rangeName: 'Platz 1',
          steps: [{ id: 's2', type: 'solo' }],
        },
      ],
    },
  ],
}

const rotten = [
  {
    rotteId: 'rotte-uuid-1',
    name: 'Rotte 1',
    players: [{ id: 'p1', displayName: 'Alice', type: 'user' }],
  },
  {
    rotteId: 'rotte-uuid-2',
    name: 'Rotte 2',
    players: [{ id: 'p2', displayName: 'Bob', type: 'user' }],
  },
]

describe('useActivePasseStore — Competition', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  // Test 1: startCompetition creates instance with correct shape
  it('startCompetition creates instance with type competition and correct shape', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)

    expect(inst.type).toBe('competition')
    expect(inst.templateId).toBe('comp-tmpl-1')
    expect(inst.templateName).toBe('Liga Runde 1')
    expect(inst.instanceId).toBeDefined()
    expect(inst.startedAt).toBeDefined()
    expect(inst.completedAt).toBeNull()
    expect(inst.rotten).toHaveLength(2)

    const rotte1 = inst.rotten[0]
    expect(rotte1.rotteId).toBe('rotte-uuid-1')
    expect(rotte1.name).toBe('Rotte 1')
    expect(rotte1.players).toHaveLength(1)
    expect(rotte1.status).toBe('waiting')
    expect(rotte1.assignedRangeId).toBeNull()
    expect(rotte1.currentPhaseIndex).toBe(0)
    expect(rotte1.phases).toHaveLength(2)
    expect(rotte1.phases[0].passeName).toBe('Aufwärmen')
    expect(rotte1.phases[0].status).toBe('active')
    expect(rotte1.phases[1].passeName).toBe('Hauptteil')
    expect(rotte1.phases[1].status).toBe('pending')
    expect(rotte1.phases[0].blocks).toHaveLength(1)
    expect(rotte1.phases[0].blocks[0].serieId).toBe('serie-1')
    expect(rotte1.phases[0].blocks[0].status).toBe('pending')

    expect(store.activeInstances).toHaveLength(1)
  })

  // Test 2: assignRotteToRange sets assignedRangeId and status to 'active'
  it('assignRotteToRange sets assignedRangeId and status to active', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)

    store.assignRotteToRange(inst.instanceId, 'rotte-uuid-1', 'r1')

    const updated = store.activeInstances[0]
    const rotte1 = updated.rotten[0]
    expect(rotte1.assignedRangeId).toBe('r1')
    expect(rotte1.status).toBe('active')
    // Rotte 2 remains unchanged
    expect(updated.rotten[1].status).toBe('waiting')
  })

  // Test 3: markBlockDone with rotteId advances rotte's currentPhaseIndex when all blocks done
  it('markBlockDone with rotteId advances currentPhaseIndex when all blocks in phase done', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)
    store.assignRotteToRange(inst.instanceId, 'rotte-uuid-1', 'r1')

    const rotte1 = store.activeInstances[0].rotten[0]
    const block = rotte1.phases[0].blocks[0]

    store.markBlockDone(inst.instanceId, block.blockId, [], 'rotte-uuid-1')

    const updatedRotte = store.activeInstances[0].rotten[0]
    expect(updatedRotte.currentPhaseIndex).toBe(1)
    expect(updatedRotte.phases[0].status).toBe('done')
    expect(updatedRotte.phases[1].status).toBe('active')
    // Instance still active because rotte 2 is not done
    expect(store.activeInstances).toHaveLength(1)
  })

  // Test 4: markBlockDone with rotteId moves instance to completedInstances when all rotten done
  it('markBlockDone moves competition to completedInstances when all rotten are done', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)
    store.assignRotteToRange(inst.instanceId, 'rotte-uuid-1', 'r1')
    store.assignRotteToRange(inst.instanceId, 'rotte-uuid-2', 'r1')

    // Complete all phases for Rotte 1
    let rotte1 = store.activeInstances[0].rotten[0]
    const block1Phase0 = rotte1.phases[0].blocks[0]
    store.markBlockDone(inst.instanceId, block1Phase0.blockId, [], 'rotte-uuid-1')
    rotte1 = store.activeInstances[0].rotten[0]
    const block1Phase1 = rotte1.phases[1].blocks[0]
    store.markBlockDone(inst.instanceId, block1Phase1.blockId, [], 'rotte-uuid-1')

    // After rotte 1 is done, rotte 2 still pending — instance should still be active
    expect(store.activeInstances).toHaveLength(1)
    expect(store.activeInstances[0].rotten[0].status).toBe('done')

    // Complete all phases for Rotte 2
    let rotte2 = store.activeInstances[0].rotten[1]
    const block2Phase0 = rotte2.phases[0].blocks[0]
    store.markBlockDone(inst.instanceId, block2Phase0.blockId, [], 'rotte-uuid-2')
    rotte2 = store.activeInstances[0].rotten[1]
    const block2Phase1 = rotte2.phases[1].blocks[0]
    store.markBlockDone(inst.instanceId, block2Phase1.blockId, [], 'rotte-uuid-2')

    // Now both rotten done → instance moves to completed
    expect(store.activeInstances).toHaveLength(0)
    expect(store.completedInstances).toHaveLength(1)
    expect(store.completedInstances[0].type).toBe('competition')
    expect(store.completedInstances[0].completedAt).toBeDefined()
  })

  // Test 5: getBlocksForRange returns competition blocks for active rotte on that range
  it('getBlocksForRange returns competition blocks tagged with instanceType competition and rotteName', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)
    store.assignRotteToRange(inst.instanceId, 'rotte-uuid-1', 'r1')

    const blocks = store.getBlocksForRange('r1')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].instanceType).toBe('competition')
    expect(blocks[0].rotteName).toBe('Rotte 1')
    expect(blocks[0].rotteId).toBe('rotte-uuid-1')
    expect(blocks[0].instanceId).toBe(inst.instanceId)
    expect(blocks[0].templateName).toBe('Liga Runde 1')
    expect(blocks[0].passeName).toBe('Aufwärmen')
    expect(blocks[0].players).toHaveLength(1)
    expect(blocks[0].players[0].id).toBe('p1')
  })

  it('getBlocksForRange does not return blocks for rotten not active on that range', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)
    // Rotte 1 assigned to r1, rotte 2 still waiting
    store.assignRotteToRange(inst.instanceId, 'rotte-uuid-1', 'r1')

    // r2 has no active rotte assigned
    const blocksR2 = store.getBlocksForRange('r2')
    expect(blocksR2).toHaveLength(0)
  })

  it('getBlocksForRange does not return done blocks', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)
    store.assignRotteToRange(inst.instanceId, 'rotte-uuid-1', 'r1')

    const rotte1 = store.activeInstances[0].rotten[0]
    const block = rotte1.phases[0].blocks[0]
    store.markBlockDone(inst.instanceId, block.blockId, [], 'rotte-uuid-1')

    // Phase advances to phase 1 (also on r1), so a new block appears — but the old one is gone
    const blocks = store.getBlocksForRange('r1')
    // The done block should not appear; the next phase's block should appear
    expect(blocks.every((b) => b.status !== 'done')).toBe(true)
  })

  // Test 6: unassignRotte clears assignedRangeId and sets status to 'paused'
  it('unassignRotte clears assignedRangeId and sets status to paused', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)
    store.assignRotteToRange(inst.instanceId, 'rotte-uuid-1', 'r1')
    store.unassignRotte(inst.instanceId, 'rotte-uuid-1')

    const rotte1 = store.activeInstances[0].rotten[0]
    expect(rotte1.assignedRangeId).toBeNull()
    expect(rotte1.status).toBe('paused')

    // After unassign, getBlocksForRange should not return this rotte's blocks
    expect(store.getBlocksForRange('r1')).toHaveLength(0)
  })

  // markBlockInProgress with rotteId
  it('markBlockInProgress with rotteId sets block status to in_progress', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)
    store.assignRotteToRange(inst.instanceId, 'rotte-uuid-1', 'r1')

    const rotte1 = store.activeInstances[0].rotten[0]
    const block = rotte1.phases[0].blocks[0]

    store.markBlockInProgress(inst.instanceId, block.blockId, 'rotte-uuid-1')

    const updatedBlock = store.activeInstances[0].rotten[0].phases[0].blocks[0]
    expect(updatedBlock.status).toBe('in_progress')
  })

  // stopCompetition alias
  it('stopCompetition removes the competition instance', () => {
    const store = useActivePasseStore()
    const inst = store.startCompetition(competitionTemplate, rotten)
    expect(store.activeInstances).toHaveLength(1)
    store.stopCompetition(inst.instanceId)
    expect(store.activeInstances).toHaveLength(0)
  })

  // Existing training behaviour not broken
  it('markBlockDone without rotteId still advances training phases normally', () => {
    const store = useActivePasseStore()
    const trainingTemplate = {
      id: 'tr-1',
      name: 'Training',
      passen: [
        {
          id: 'p1',
          name: 'Phase 1',
          serien: [{ id: 's1', name: 'S1', rangeId: 'r1', steps: [] }],
        },
      ],
    }
    const players = [{ id: 'p1', displayName: 'Alice' }]
    const inst = store.startTraining(trainingTemplate, players)
    const block = inst.phases[0].blocks[0]
    store.markBlockDone(inst.instanceId, block.blockId, [])
    expect(store.activeInstances).toHaveLength(0)
    expect(store.completedInstances).toHaveLength(1)
  })
})

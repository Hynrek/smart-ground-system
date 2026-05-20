import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useActiveProgramStore } from '../activeProgramStore'

const template = {
  id: 'prog-1',
  name: 'Test Programm',
  ablaeufe: [
    { id: 'abl-1', name: 'Ablauf 1', rangeId: 'r1', rangeName: 'Platz 1', steps: [{ id: 's1', type: 'solo' }] },
    { id: 'abl-2', name: 'Ablauf 2', rangeId: 'r2', rangeName: 'Platz 2', steps: [{ id: 's2', type: 'solo' }] },
  ],
}
const players = [{ id: 'p1', displayName: 'Schütze 1', type: 'guest' }]

describe('useActiveProgramStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('startProgram creates one block per ablauf with pending status', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    expect(store.activeInstances).toHaveLength(1)
    expect(inst.type).toBe('programm')
    expect(inst.blocks).toHaveLength(2)
    expect(inst.blocks[0].ablaufId).toBe('abl-1')
    expect(inst.blocks[0].status).toBe('pending')
    expect(inst.blocks[0].steps).toHaveLength(1)
  })

  it('startProgram persists to localStorage', () => {
    const store = useActiveProgramStore()
    store.startProgram(template, players)
    expect(localStorage.getItem('sg_active_program_instances')).not.toBeNull()
  })

  it('getBlocksForRange returns only pending and in_progress blocks for that range', () => {
    const store = useActiveProgramStore()
    store.startProgram(template, players)
    const blocks = store.getBlocksForRange('r1')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].ablaufId).toBe('abl-1')
    expect(blocks[0].instanceId).toBeDefined()
    expect(blocks[0].templateName).toBe('Test Programm')
  })

  it('getBlocksForRange excludes done blocks', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    store.markBlockDone(inst.instanceId, inst.blocks[0].blockId, [])
    expect(store.getBlocksForRange('r1')).toHaveLength(0)
  })

  it('markBlockInProgress sets status to in_progress', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    store.markBlockInProgress(inst.instanceId, inst.blocks[0].blockId)
    expect(store.activeInstances[0].blocks[0].status).toBe('in_progress')
  })

  it('markBlockDone stores result on block', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    const results = [{ playerId: 'p1', displayName: 'Schütze 1', totalPoints: 1, maxPoints: 1, stepStates: [] }]
    store.markBlockDone(inst.instanceId, inst.blocks[0].blockId, results)
    expect(store.activeInstances[0].blocks[0].status).toBe('done')
    expect(store.activeInstances[0].blocks[0].result.playerResults).toHaveLength(1)
  })

  it('markBlockDone moves instance to completedInstances when all blocks done', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    store.markBlockDone(inst.instanceId, inst.blocks[0].blockId, [])
    expect(store.activeInstances).toHaveLength(1)
    store.markBlockDone(inst.instanceId, inst.blocks[1].blockId, [])
    expect(store.activeInstances).toHaveLength(0)
    expect(store.completedInstances).toHaveLength(1)
    expect(store.completedInstances[0].completedAt).toBeDefined()
  })

  it('stopInstance removes active instance', () => {
    const store = useActiveProgramStore()
    const inst = store.startProgram(template, players)
    store.stopInstance(inst.instanceId)
    expect(store.activeInstances).toHaveLength(0)
  })

  it('loads persisted data on init', () => {
    const store = useActiveProgramStore()
    store.startProgram(template, players)
    setActivePinia(createPinia())
    const store2 = useActiveProgramStore()
    expect(store2.activeInstances).toHaveLength(1)
  })
})

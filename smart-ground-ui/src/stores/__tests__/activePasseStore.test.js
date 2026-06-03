import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useActivePasseStore } from '../activePasseStore.js'
import * as playInstanceApi from '@/services/playInstanceApi.js'

vi.mock('@/services/playInstanceApi.js')

const mockProgrammeInstance = {
  instanceId: 'inst-1',
  type: 'programm',
  templateId: 'prog-1',
  templateName: 'Test Prog',
  status: 'active',
  players: [{ id: 'p1', type: 'user', displayName: 'Alice' }],
  startedAt: '2025-01-01T00:00:00Z',
  completedAt: null,
  blocks: [
    { blockId: 'blk-1', ablaufId: 'ab1', ablaufAlias: 'Serie A', rangeId: 'r1', rangeName: 'Platz 1', steps: [], status: 'pending', completedAt: null, result: null },
  ],
  phases: null,
}

describe('activePasseStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('startPasse calls API and stores instance', async () => {
    playInstanceApi.startProgrammeInstance.mockResolvedValue(mockProgrammeInstance)
    const store = useActivePasseStore()
    const template = { id: 'prog-1', name: 'Test Prog', serien: [] }
    const players = [{ id: 'p1', type: 'user', displayName: 'Alice' }]
    await store.startPasse(template, players)
    expect(playInstanceApi.startProgrammeInstance).toHaveBeenCalledWith('prog-1', players)
    expect(store.activeInstances).toHaveLength(1)
    expect(store.activeInstances[0].instanceId).toBe('inst-1')
  })

  it('stopInstance calls DELETE and removes from list', async () => {
    playInstanceApi.stopPlayInstance.mockResolvedValue(null)
    const store = useActivePasseStore()
    store.activeInstances = [{ ...mockProgrammeInstance }]
    await store.stopInstance('inst-1')
    expect(playInstanceApi.stopPlayInstance).toHaveBeenCalledWith('inst-1')
    expect(store.activeInstances).toHaveLength(0)
  })

  it('markBlockInProgress calls start block API', async () => {
    playInstanceApi.startBlock.mockResolvedValue({
      ...mockProgrammeInstance,
      blocks: [{ ...mockProgrammeInstance.blocks[0], status: 'in_progress' }],
    })
    const store = useActivePasseStore()
    store.activeInstances = [{ ...mockProgrammeInstance }]
    await store.markBlockInProgress('inst-1', 'blk-1')
    expect(playInstanceApi.startBlock).toHaveBeenCalledWith('inst-1', 'blk-1')
  })

  it('markBlockDone calls complete block API', async () => {
    const completedInstance = {
      ...mockProgrammeInstance,
      status: 'completed',
      completedAt: '2025-01-02T00:00:00Z',
      blocks: [{ ...mockProgrammeInstance.blocks[0], status: 'done' }],
    }
    playInstanceApi.completeBlock.mockResolvedValue(completedInstance)
    const store = useActivePasseStore()
    store.activeInstances = [{ ...mockProgrammeInstance }]
    const results = [{ playerId: 'p1', displayName: 'Alice', totalPoints: 10, maxPoints: 10, stepStates: [] }]
    await store.markBlockDone('inst-1', 'blk-1', results)
    expect(playInstanceApi.completeBlock).toHaveBeenCalledWith('inst-1', 'blk-1', results)
  })

  it('getBlocksForRange returns pending/in-progress blocks for a range', () => {
    const store = useActivePasseStore()
    store.activeInstances = [{ ...mockProgrammeInstance }]
    const blocks = store.getBlocksForRange('r1')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].blockId).toBe('blk-1')
  })
})

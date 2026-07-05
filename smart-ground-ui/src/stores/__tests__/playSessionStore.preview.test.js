import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePlaySessionStore } from '../playSessionStore'
import { StepType } from '@/constants/playEnums.js'
import { sendPositionCommand } from '@/services/rangePositionApi.js'

vi.mock('@/services/rangePositionApi.js', () => ({
  sendPositionCommand: vi.fn().mockResolvedValue(undefined),
}))

const prog = () => [{
  steps: [
    { type: StepType.SOLO, positionId: 'p0' },
    { type: StepType.SOLO, positionId: 'p1' },
    { type: StepType.SOLO, positionId: 'p2' },
  ],
}]

const seedFirstShooter = (store) => {
  store.playProg = prog()
  store.sessionPlayers = [{ id: 'A', displayName: 'A' }, { id: 'B', displayName: 'B' }]
  store.roundOrder = [0, 1]
  store.currentPlayerIndex = 0
  store.currentSerieIndex = 0
  store.currentStepIndex = 0
}

describe('playSessionStore — preview', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('startPreview engages preview and places the cursor at the frontier', () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    expect(store.previewMode).toBe(true)
    expect(store.previewEngaged).toBe(true)
    expect(store.previewStep.positionId).toBe('p0')
  })

  it('advancePreviewStep fires the device and moves the frontier forward', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    await store.advancePreviewStep()
    expect(sendPositionCommand).toHaveBeenCalledWith(null, 'p0')
    expect(store.previewFrontier).toBe(1)
    expect(store.previewStep.positionId).toBe('p1')
  })

  it('skipPreviewStep advances the frontier without firing a device', () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    store.skipPreviewStep()
    expect(sendPositionCommand).not.toHaveBeenCalled()
    expect(store.previewFrontier).toBe(1)
    expect(store.previewStep.positionId).toBe('p1')
  })

  it('needsPreview is false when preview was never engaged', () => {
    const store = usePlaySessionStore()
    seedFirstShooter(store)
    expect(store.needsPreview).toBe(false)
  })

  it('needsPreview pauses the first shooter at an unseen step and clears after previewing', async () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    await store.advancePreviewStep() // frontier = 1 (p0 seen)
    store.stopPreview()
    seedFirstShooter(store)

    store.currentStepIndex = 0
    expect(store.needsPreview).toBe(false) // scored index 0 < frontier 1
    store.currentStepIndex = 1
    expect(store.needsPreview).toBe(true)  // scored index 1 >= frontier 1

    store.startPreview()
    await store.advancePreviewStep() // frontier = 2
    store.stopPreview()
    expect(store.needsPreview).toBe(false)
  })

  it('needsPreview is false for shooters after the first', () => {
    const store = usePlaySessionStore()
    store.setPendingGroupSerien(prog())
    store.startPreview()
    store.stopPreview() // engaged, frontier 0
    seedFirstShooter(store)
    store.currentPlayerIndex = 1
    expect(store.needsPreview).toBe(false)
  })
})

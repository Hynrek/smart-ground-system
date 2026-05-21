import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProgramStore } from '../programStore.js'
import { useShooterRemoteStore } from '../shooterRemoteStore.js'

describe('programStore.addStep', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const remote = useShooterRemoteStore()
    remote.reservePlatz('range-1')
    const store = useProgramStore()
    store.startCapture()
  })

  it('solo step stores positionId and label letter', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('solo')
    const store = useProgramStore()

    const position = { id: 'pos-1', label: 'A', device: { alias: 'Werfer 1' } }
    store.addStep('pos-1', position, 'A')

    const step = store.editingAblauf[0].steps[0]
    expect(step.type).toBe('solo')
    expect(step.positionId).toBe('pos-1')
    expect(step.letter).toBe('A')
    expect(step.alias).toBe('Werfer 1')
  })

  it('solo step uses position.label as alias when device is null', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('solo')
    const store = useProgramStore()

    const position = { id: 'pos-2', label: 'B', device: null }
    store.addStep('pos-2', position, 'B')

    const step = store.editingAblauf[0].steps[0]
    expect(step.alias).toBe('B')
  })

  it('pair step stores positionId1 and positionId2', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('pair')
    const store = useProgramStore()

    const pos1 = { id: 'pos-1', label: 'A', device: { alias: 'Werfer 1' } }
    const pos2 = { id: 'pos-2', label: 'B', device: { alias: 'Werfer 2' } }

    store.addStep('pos-1', pos1, 'A')
    store.addStep('pos-2', pos2, 'B')

    const step = store.editingAblauf[0].steps[0]
    expect(step.type).toBe('pair')
    expect(step.positionId1).toBe('pos-1')
    expect(step.positionId2).toBe('pos-2')
    expect(step.letter1).toBe('A')
    expect(step.letter2).toBe('B')
  })

  it('raffale step stores positionId', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('raffale')
    const store = useProgramStore()

    const position = { id: 'pos-3', label: 'C', device: { alias: 'Werfer 3' } }
    store.addStep('pos-3', position, 'C')

    const step = store.editingAblauf[0].steps[0]
    expect(step.type).toBe('raffale')
    expect(step.positionId).toBe('pos-3')
    expect(step.letter).toBe('C')
  })

  it('recording flash is keyed by positionId', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('solo')
    const store = useProgramStore()

    const position = { id: 'pos-1', label: 'A', device: { alias: 'Werfer 1' } }
    store.addStep('pos-1', position, 'A')

    expect(store.recording['pos-1']).toBe(true)
  })
})

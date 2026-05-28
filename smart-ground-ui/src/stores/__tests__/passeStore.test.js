import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePasseStore } from '../passeStore.js'
import { useShooterRemoteStore } from '../shooterRemoteStore.js'

describe('passeStore.addStep', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const remote = useShooterRemoteStore()
    remote.reservePlatz('range-1')
    const store = usePasseStore()
    store.startCapture()
  })

  it('solo step stores positionId and label letter', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('solo')
    const store = usePasseStore()

    const position = { id: 'pos-1', label: 'A', device: { alias: 'Werfer 1' } }
    store.addStep('pos-1', position, 'A')

    const step = store.editingSerie[0].steps[0]
    expect(step.type).toBe('solo')
    expect(step.positionId).toBe('pos-1')
    expect(step.letter).toBe('A')
    expect(step.alias).toBe('Werfer 1')
  })

  it('solo step uses position.label as alias when device is null', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('solo')
    const store = usePasseStore()

    const position = { id: 'pos-2', label: 'B', device: null }
    store.addStep('pos-2', position, 'B')

    const step = store.editingSerie[0].steps[0]
    expect(step.alias).toBe('B')
  })

  it('pair step stores positionId1 and positionId2', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('pair')
    const store = usePasseStore()

    const pos1 = { id: 'pos-1', label: 'A', device: { alias: 'Werfer 1' } }
    const pos2 = { id: 'pos-2', label: 'B', device: { alias: 'Werfer 2' } }

    store.addStep('pos-1', pos1, 'A')
    store.addStep('pos-2', pos2, 'B')

    const step = store.editingSerie[0].steps[0]
    expect(step.type).toBe('pair')
    expect(step.positionId1).toBe('pos-1')
    expect(step.positionId2).toBe('pos-2')
    expect(step.letter1).toBe('A')
    expect(step.letter2).toBe('B')
  })

  it('raffale step stores positionId', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('raffale')
    const store = usePasseStore()

    const position = { id: 'pos-3', label: 'C', device: { alias: 'Werfer 3' } }
    store.addStep('pos-3', position, 'C')

    const step = store.editingSerie[0].steps[0]
    expect(step.type).toBe('raffale')
    expect(step.positionId).toBe('pos-3')
    expect(step.letter).toBe('C')
  })

  it('recording flash is keyed by positionId', () => {
    const remote = useShooterRemoteStore()
    remote.setMode('solo')
    const store = usePasseStore()

    const position = { id: 'pos-1', label: 'A', device: { alias: 'Werfer 1' } }
    store.addStep('pos-1', position, 'A')

    expect(store.recording['pos-1']).toBe(true)
  })
})

describe('passeStore.createRangeSerie', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('adds a range-owned serie to savedSerien', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'Werfer 1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('Test Serie', 'range-1', 'Platz 1', steps)
    expect(store.savedSerien).toHaveLength(1)
    const serie = store.savedSerien[0]
    expect(serie.name).toBe('Test Serie')
    expect(serie.ownership).toBe('range')
    expect(serie.rangeId).toBe('range-1')
    expect(serie.rangeName).toBe('Platz 1')
    expect(serie.steps).toHaveLength(1)
  })

  it('persists to localStorage with _sg_range_serie_ prefix', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('My Serie', 'range-1', 'Platz 1', steps)
    const key = Object.keys(localStorage).find(k => k.startsWith('_sg_range_serie_'))
    expect(key).toBeDefined()
    const stored = JSON.parse(localStorage.getItem(key))
    expect(stored.serieName).toBe('My Serie')
    expect(stored.ownership).toBe('range')
    expect(stored.rangeId).toBe('range-1')
  })

  it('does nothing when steps array is empty', () => {
    const store = usePasseStore()
    store.createRangeSerie('Empty', 'range-1', 'Platz 1', [])
    expect(store.savedSerien).toHaveLength(0)
    expect(Object.keys(localStorage)).toHaveLength(0)
  })

  it('falls back to generated name when name is blank', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('  ', 'range-1', 'Platz 1', steps)
    expect(store.savedSerien[0].name).toBe('Serie 1')
  })
})

describe('passeStore.updateSerie', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('updates name and steps in memory', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('Old Name', 'range-1', 'Platz 1', steps)
    const serieId = store.savedSerien[0].id
    const newSteps = [
      { id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' },
      { id: 2, type: 'solo', alias: 'W2', positionId: 'pos-2', letter: 'B' },
    ]
    store.updateSerie(serieId, 'New Name', newSteps)
    expect(store.savedSerien[0].name).toBe('New Name')
    expect(store.savedSerien[0].steps).toHaveLength(2)
  })

  it('persists changes to localStorage', () => {
    const store = usePasseStore()
    const steps = [{ id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' }]
    store.createRangeSerie('Old Name', 'range-1', 'Platz 1', steps)
    const serieId = store.savedSerien[0].id
    const newSteps = [
      { id: 1, type: 'solo', alias: 'W1', positionId: 'pos-1', letter: 'A' },
      { id: 2, type: 'raffale', alias: 'W2', positionId: 'pos-2', letter: 'B' },
    ]
    store.updateSerie(serieId, 'New Name', newSteps)
    const stored = JSON.parse(localStorage.getItem(serieId))
    expect(stored.serieName).toBe('New Name')
    expect(stored.steps).toHaveLength(2)
  })

  it('does not throw when serie id is not found', () => {
    const store = usePasseStore()
    expect(() => store.updateSerie('nonexistent-id', 'Name', [])).not.toThrow()
  })
})

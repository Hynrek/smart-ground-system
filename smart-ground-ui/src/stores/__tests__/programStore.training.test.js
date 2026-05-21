import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProgramStore } from '../programStore'

const prog1 = {
  id: 'p1',
  name: 'Aufwärmen',
  ablaeufe: [{ id: 'abl-1', name: 'A1', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }],
}
const prog2 = {
  id: 'p2',
  name: 'Hauptteil',
  ablaeufe: [{ id: 'abl-2', name: 'A2', rangeId: 'r2', rangeName: 'Platz 2', steps: [] }],
}

describe('programStore — Training CRUD', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('createTraining adds a training with snapshotted programmes', () => {
    const store = useProgramStore()
    store.createTraining('Training 1', [prog1, prog2])
    expect(store.savedTrainings).toHaveLength(1)
    expect(store.savedTrainings[0].name).toBe('Training 1')
    expect(store.savedTrainings[0].programmes).toHaveLength(2)
    expect(store.savedTrainings[0].programmes[0].name).toBe('Aufwärmen')
  })

  it('createTraining persists to localStorage', () => {
    const store = useProgramStore()
    store.createTraining('Training 1', [prog1])
    const keys = Object.keys(localStorage).filter(k => k.includes('_training_'))
    expect(keys).toHaveLength(1)
    const data = JSON.parse(localStorage.getItem(keys[0]))
    expect(data.trainingName).toBe('Training 1')
    expect(data.programmes).toHaveLength(1)
  })

  it('deleteTraining removes from memory and localStorage', () => {
    const store = useProgramStore()
    store.createTraining('Training 1', [prog1])
    const id = store.savedTrainings[0].id
    store.deleteTraining(id)
    expect(store.savedTrainings).toHaveLength(0)
    expect(localStorage.getItem(id)).toBeNull()
  })

  it('renameTraining updates name in memory and localStorage', () => {
    const store = useProgramStore()
    store.createTraining('Alter Name', [prog1])
    const id = store.savedTrainings[0].id
    store.renameTraining(id, 'Neuer Name')
    expect(store.savedTrainings[0].name).toBe('Neuer Name')
    const data = JSON.parse(localStorage.getItem(id))
    expect(data.trainingName).toBe('Neuer Name')
  })

  it('loadTrainingsFromStorage reloads after re-init', () => {
    const store = useProgramStore()
    store.createTraining('Training 1', [prog1, prog2])
    setActivePinia(createPinia())
    const store2 = useProgramStore()
    expect(store2.savedTrainings).toHaveLength(1)
    expect(store2.savedTrainings[0].programmes).toHaveLength(2)
  })

  it('createTraining snapshots steps so original mutation does not affect training', () => {
    const store = useProgramStore()
    const progWithSteps = {
      id: 'p1',
      name: 'Test',
      ablaeufe: [{ id: 'abl-1', name: 'A1', rangeId: 'r1', rangeName: 'Platz 1', steps: [{ id: 's1', type: 'solo' }] }],
    }
    store.createTraining('T1', [progWithSteps])
    // Mutate the original steps array
    progWithSteps.ablaeufe[0].steps.push({ id: 's2', type: 'pair' })
    // Training snapshot must not be affected
    expect(store.savedTrainings[0].programmes[0].ablaeufe[0].steps).toHaveLength(1)
  })
})

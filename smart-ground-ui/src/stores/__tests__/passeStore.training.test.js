import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePasseStore } from '../passeStore'

const passe1 = {
  id: 'p1',
  name: 'Aufwärmen',
  serien: [{ id: 'serie-1', name: 'S1', rangeId: 'r1', rangeName: 'Platz 1', steps: [] }],
}
const passe2 = {
  id: 'p2',
  name: 'Hauptteil',
  serien: [{ id: 'serie-2', name: 'S2', rangeId: 'r2', rangeName: 'Platz 2', steps: [] }],
}

describe('passeStore — Training CRUD', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('createTraining adds a training with snapshotted passen', () => {
    const store = usePasseStore()
    store.createTraining('Training 1', [passe1, passe2])
    expect(store.savedTrainings).toHaveLength(1)
    expect(store.savedTrainings[0].name).toBe('Training 1')
    expect(store.savedTrainings[0].passen).toHaveLength(2)
    expect(store.savedTrainings[0].passen[0].name).toBe('Aufwärmen')
  })

  it('createTraining persists to localStorage', () => {
    const store = usePasseStore()
    store.createTraining('Training 1', [passe1])
    const keys = Object.keys(localStorage).filter(k => k.includes('_training_'))
    expect(keys).toHaveLength(1)
    const data = JSON.parse(localStorage.getItem(keys[0]))
    expect(data.trainingName).toBe('Training 1')
    expect(data.passen).toHaveLength(1)
  })

  it('deleteTraining removes from memory and localStorage', () => {
    const store = usePasseStore()
    store.createTraining('Training 1', [passe1])
    const id = store.savedTrainings[0].id
    store.deleteTraining(id)
    expect(store.savedTrainings).toHaveLength(0)
    expect(localStorage.getItem(id)).toBeNull()
  })

  it('renameTraining updates name in memory and localStorage', () => {
    const store = usePasseStore()
    store.createTraining('Alter Name', [passe1])
    const id = store.savedTrainings[0].id
    store.renameTraining(id, 'Neuer Name')
    expect(store.savedTrainings[0].name).toBe('Neuer Name')
    const data = JSON.parse(localStorage.getItem(id))
    expect(data.trainingName).toBe('Neuer Name')
  })

  it('loadTrainingsFromStorage reloads after re-init', () => {
    const store = usePasseStore()
    store.createTraining('Training 1', [passe1, passe2])
    setActivePinia(createPinia())
    const store2 = usePasseStore()
    expect(store2.savedTrainings).toHaveLength(1)
    expect(store2.savedTrainings[0].passen).toHaveLength(2)
  })

  it('createTraining snapshots steps so original mutation does not affect training', () => {
    const store = usePasseStore()
    const passeWithSteps = {
      id: 'p1',
      name: 'Test',
      serien: [{ id: 'serie-1', name: 'S1', rangeId: 'r1', rangeName: 'Platz 1', steps: [{ id: 's1', type: 'solo' }] }],
    }
    store.createTraining('T1', [passeWithSteps])
    // Mutate the original steps array
    passeWithSteps.serien[0].steps.push({ id: 's2', type: 'pair' })
    // Training snapshot must not be affected
    expect(store.savedTrainings[0].passen[0].serien[0].steps).toHaveLength(1)
  })
})

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePasseStore } from '../passeStore.js'
import * as trainingApi from '@/services/trainingApi.js'

vi.mock('@/services/serieApi.js')
vi.mock('@/services/passeApi.js')
vi.mock('@/services/trainingApi.js')
vi.mock('@/stores/shooterRemoteStore.js', () => ({
  useShooterRemoteStore: () => ({ isReserved: true, mode: 'solo', setMode: vi.fn() }),
}))
vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({ profile: { email: 'test@test.com' } }),
}))

describe('passeStore — Training layer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('createTraining creates Training via API with passeIds', async () => {
    trainingApi.createTraining.mockResolvedValue({
      id: 't1',
      name: 'Training 1',
      passen: [],
      ownerUsername: 'user',
    })
    const store = usePasseStore()
    const passen = [
      { id: 'p1', name: 'Passe 1', serien: [] },
      { id: 'p2', name: 'Passe 2', serien: [] },
    ]
    await store.createTraining('Training 1', passen)
    expect(trainingApi.createTraining).toHaveBeenCalledWith('Training 1', ['p1', 'p2'])
    expect(store.savedTrainings).toHaveLength(1)
    expect(store.savedTrainings[0].name).toBe('Training 1')
  })

  it('createTraining with empty passen does nothing', async () => {
    const store = usePasseStore()
    await store.createTraining('Empty', [])
    expect(trainingApi.createTraining).not.toHaveBeenCalled()
    expect(store.savedTrainings).toHaveLength(0)
  })

  it('deleteTraining calls API and removes from list', async () => {
    trainingApi.deleteTraining.mockResolvedValue(null)
    const store = usePasseStore()
    store.savedTrainings = [{ id: 't1', name: 'Training 1', passen: [] }]
    await store.deleteTraining('t1')
    expect(trainingApi.deleteTraining).toHaveBeenCalledWith('t1')
    expect(store.savedTrainings).toHaveLength(0)
  })

  it('renameTraining calls API and updates name in list', async () => {
    trainingApi.updateTraining.mockResolvedValue(null)
    const store = usePasseStore()
    store.savedTrainings = [{ id: 't1', name: 'Old Name', passen: [] }]
    await store.renameTraining('t1', 'New Name')
    expect(trainingApi.updateTraining).toHaveBeenCalledWith('t1', 'New Name')
    expect(store.savedTrainings[0].name).toBe('New Name')
  })

  it('loadTrainingsFromStorage fetches from API and hydrates', async () => {
    trainingApi.fetchTrainings.mockResolvedValue([
      {
        id: 't1',
        name: 'Training A',
        passen: [
          {
            id: 'p1',
            name: 'Passe 1',
            serien: [
              { id: 'ab1', alias: 'Serie 1', rangeId: null, rangeName: null, steps: [] },
            ],
          },
        ],
        ownerUsername: 'user',
      },
    ])
    const store = usePasseStore()
    await store.loadTrainingsFromStorage()
    expect(store.savedTrainings).toHaveLength(1)
    expect(store.savedTrainings[0].name).toBe('Training A')
    expect(store.savedTrainings[0].passen).toHaveLength(1)
    expect(store.savedTrainings[0].passen[0].serien).toHaveLength(1)
  })

  it('createCompetition delegates to createTraining', async () => {
    trainingApi.createTraining.mockResolvedValue({
      id: 't2',
      name: 'Wettkampf 1',
      passen: [],
      ownerUsername: 'user',
    })
    const store = usePasseStore()
    const passen = [{ id: 'p1', name: 'Passe 1', serien: [] }]
    await store.createCompetition('Wettkampf 1', passen)
    expect(trainingApi.createTraining).toHaveBeenCalledWith('Wettkampf 1', ['p1'])
    expect(store.savedTrainings).toHaveLength(1)
  })
})

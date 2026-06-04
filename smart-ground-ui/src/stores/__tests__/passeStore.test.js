import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePasseStore } from '../passeStore.js'
import * as serieApi from '@/services/serieApi.js'

vi.mock('@/services/serieApi.js')
vi.mock('@/services/passeApi.js')
vi.mock('@/services/trainingApi.js')
vi.mock('@/stores/shooterRemoteStore.js', () => ({
  useShooterRemoteStore: () => ({ isReserved: true, mode: 'solo', setMode: vi.fn() }),
}))
vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({ profile: { email: 'test@test.com' } }),
}))

describe('passeStore — Serie layer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadSerienFromStorage fetches Serien from API', async () => {
    serieApi.fetchSerien.mockResolvedValue([
      { id: 'ab1', name: 'Test Serie', ownership: 'user', rangeId: null, rangeName: null, steps: [], createdAt: '2025-01-01T00:00:00Z' },
    ])
    const store = usePasseStore()
    await store.loadSerienFromStorage()
    expect(store.savedSerien).toHaveLength(1)
    expect(store.savedSerien[0].name).toBe('Test Serie')
  })

  it('saveSerie creates a Serie via API', async () => {
    serieApi.createSerie.mockResolvedValue({ id: 'ab2', name: 'New Serie', ownership: 'user', rangeId: null, rangeName: null, steps: [] })
    const store = usePasseStore()
    store.editingSerie = [{ id: 's1', alias: null, steps: [{ id: '1', type: 'solo', positionId: 'pos-uuid', alias: 'A' }] }]
    await store.saveSerie('New Serie')
    expect(serieApi.createSerie).toHaveBeenCalledWith(
      'New Serie',
      [{ id: '1', type: 'solo', posId: 'pos-uuid', alias: 'A', letter: null }],
      null,
      'user',
    )
  })

  it('deleteSerie calls deleteSerie on API', async () => {
    serieApi.deleteSerie.mockResolvedValue(null)
    const store = usePasseStore()
    store.savedSerien = [{ id: 'ab1', name: 'Test', steps: [], ownership: 'user' }]
    await store.deleteSerie('ab1')
    expect(serieApi.deleteSerie).toHaveBeenCalledWith('ab1')
    expect(store.savedSerien).toHaveLength(0)
  })
})

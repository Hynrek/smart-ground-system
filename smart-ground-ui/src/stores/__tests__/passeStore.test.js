import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePasseStore } from '../passeStore.js'
import * as ablaufApi from '@/services/ablaufApi.js'

vi.mock('@/services/ablaufApi.js')
vi.mock('@/services/programmeApi.js')
vi.mock('@/services/trainingApi.js')
vi.mock('@/stores/shooterRemoteStore.js', () => ({
  useShooterRemoteStore: () => ({ isReserved: true, mode: 'solo', setMode: vi.fn() }),
}))
vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({ profile: { email: 'test@test.com' } }),
}))

describe('passeStore — Ablauf layer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadSerienFromStorage fetches Abläufe from API', async () => {
    ablaufApi.fetchAblaeufe.mockResolvedValue([
      { id: 'ab1', name: 'Test Serie', ownership: 'user', rangeId: null, rangeName: null, steps: [], createdAt: '2025-01-01T00:00:00Z' },
    ])
    const store = usePasseStore()
    await store.loadSerienFromStorage()
    expect(store.savedSerien).toHaveLength(1)
    expect(store.savedSerien[0].name).toBe('Test Serie')
  })

  it('saveSerie creates an Ablauf via API', async () => {
    ablaufApi.createAblauf.mockResolvedValue({ id: 'ab2', name: 'New Serie', ownership: 'user', rangeId: null, rangeName: null, steps: [] })
    const store = usePasseStore()
    store.editingSerie = [{ id: 's1', alias: null, steps: [{ id: '1', type: 'solo', positionId: 'pos-uuid', alias: 'A' }] }]
    await store.saveSerie('New Serie')
    expect(ablaufApi.createAblauf).toHaveBeenCalledWith(
      'New Serie',
      [{ id: '1', type: 'solo', posId: 'pos-uuid', alias: 'A' }],
      null,
      'user',
    )
  })

  it('deleteSerie calls deleteAblauf on API', async () => {
    ablaufApi.deleteAblauf.mockResolvedValue(null)
    const store = usePasseStore()
    store.savedSerien = [{ id: 'ab1', name: 'Test', steps: [], ownership: 'user' }]
    await store.deleteSerie('ab1')
    expect(ablaufApi.deleteAblauf).toHaveBeenCalledWith('ab1')
    expect(store.savedSerien).toHaveLength(0)
  })
})

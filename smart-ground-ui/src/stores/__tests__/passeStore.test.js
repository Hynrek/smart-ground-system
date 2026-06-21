import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { usePasseStore } from '../passeStore.js'
import * as serieApi from '@/services/serieApi.js'

vi.mock('@/services/serieApi.js')
vi.mock('@/services/passeApi.js')
const remoteMock = vi.hoisted(() => ({ isReserved: true, mode: 'solo', setMode: vi.fn() }))
vi.mock('@/stores/shooterRemoteStore.js', () => ({
  useShooterRemoteStore: () => remoteMock,
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

  it('updateSerie edits in place via PUT with steps, keeping the same Serie ID', async () => {
    serieApi.updateSerie.mockResolvedValue({
      id: 'ab1', name: 'Edited', ownership: 'user', rangeId: 'r1', rangeName: 'Stand 1',
      steps: [{ id: '1', type: 'solo', posId: 'pos-uuid', alias: 'A1', letter: 'A1' }], published: false,
    })
    const store = usePasseStore()
    store.savedSerien = [{
      id: 'ab1', name: 'Old', rangeId: 'r1', rangeName: 'Stand 1',
      steps: [], ownership: 'user', published: false,
    }]

    await store.updateSerie('ab1', 'Edited', [
      { id: '1', type: 'solo', positionId: 'pos-uuid', alias: 'A1', letter: 'A1' },
    ])

    expect(serieApi.updateSerie).toHaveBeenCalledWith(
      'ab1', 'Edited', 'r1',
      [{ id: '1', type: 'solo', posId: 'pos-uuid', alias: 'A1', letter: 'A1' }],
    )
    expect(serieApi.deleteSerie).not.toHaveBeenCalled()
    expect(serieApi.createSerie).not.toHaveBeenCalled()
    // same ID retained, name updated
    expect(store.savedSerien).toHaveLength(1)
    expect(store.savedSerien[0].id).toBe('ab1')
    expect(store.savedSerien[0].name).toBe('Edited')
  })

  it('updateSerie preserves the published flag on a range Serie', async () => {
    serieApi.updateSerie.mockResolvedValue({
      id: 'ab9', name: 'Pub', ownership: 'range', rangeId: 'r1', rangeName: 'Stand 1',
      steps: [], published: true,
    })
    const store = usePasseStore()
    store.savedSerien = [{
      id: 'ab9', name: 'Pub', rangeId: 'r1', rangeName: 'Stand 1',
      steps: [], ownership: 'range', published: true,
    }]

    await store.updateSerie('ab9', 'Pub', [])

    expect(serieApi.patchSeriePublished).not.toHaveBeenCalled()
    expect(store.savedSerien[0].published).toBe(true)
  })
})

describe('passeStore — step recording', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    remoteMock.mode = 'solo'
  })

  it('addStep records letter1/letter2 for a pair step', () => {
    remoteMock.mode = 'pair'
    const store = usePasseStore()
    store.editingSerie = [{ id: 's1', alias: null, steps: [] }]

    store.addStep('p1', { label: 'A', device: { alias: 'Werfer 1' } }, 'A')
    store.addStep('p2', { label: 'B', device: { alias: 'Werfer 2' } }, 'B')

    const step = store.editingSerie[0].steps[0]
    expect(step).toMatchObject({
      type: 'pair',
      alias1: 'Werfer 1',
      alias2: 'Werfer 2',
      letter1: 'A',
      letter2: 'B',
    })
  })
})

describe('passeStore — setSeriePublished', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('maps published field from API response', async () => {
    serieApi.fetchSerien.mockResolvedValue([
      { id: 'ab1', name: 'Test', ownership: 'range', rangeId: null, rangeName: null, steps: [], published: true },
    ])
    const store = usePasseStore()
    await store.loadSerienFromStorage()
    expect(store.savedSerien[0].published).toBe(true)
  })

  it('defaults published to false when missing from API response', async () => {
    serieApi.fetchSerien.mockResolvedValue([
      { id: 'ab2', name: 'Test2', ownership: 'range', rangeId: null, rangeName: null, steps: [] },
    ])
    const store = usePasseStore()
    await store.loadSerienFromStorage()
    expect(store.savedSerien[0].published).toBe(false)
  })

  it('setSeriePublished updates state optimistically and calls API', async () => {
    vi.mocked(serieApi.patchSeriePublished).mockResolvedValue({})
    const store = usePasseStore()
    store.savedSerien = [{ id: 'ab1', name: 'Test', steps: [], ownership: 'range', published: false }]
    await store.setSeriePublished('ab1', true)
    expect(store.savedSerien[0].published).toBe(true)
    expect(serieApi.patchSeriePublished).toHaveBeenCalledWith('ab1', true)
  })

  it('setSeriePublished rolls back on API error', async () => {
    vi.mocked(serieApi.patchSeriePublished).mockRejectedValue(new Error('Network'))
    const store = usePasseStore()
    store.savedSerien = [{ id: 'ab1', name: 'Test', steps: [], ownership: 'range', published: false }]
    await expect(store.setSeriePublished('ab1', true)).rejects.toThrow('Network')
    expect(store.savedSerien[0].published).toBe(false)
  })

  it('setSeriePublished does nothing for unknown id', async () => {
    const store = usePasseStore()
    store.savedSerien = []
    await store.setSeriePublished('nonexistent', true)
    expect(serieApi.patchSeriePublished).not.toHaveBeenCalled()
  })
})

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useScoreStore } from '@/stores/scoreStore.js'
import * as scoreApi from '@/services/scoreApi.js'

vi.mock('@/services/scoreApi.js', () => ({
  fetchMyScores: vi.fn(),
  fetchMyScoreSummary: vi.fn(),
  fetchLeaderboard: vi.fn(),
  fetchMyPassen: vi.fn(),
  fetchMyWettkaempfe: vi.fn(),
}))

describe('scoreStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadScores stores rows and paging meta', async () => {
    scoreApi.fetchMyScores.mockResolvedValue({
      content: [{ id: 'a', totalPoints: 8, maxPoints: 10 }],
      meta: { page: 0, totalPages: 1, totalElements: 1 },
    })
    const store = useScoreStore()
    await store.loadScores({ context: 'TRAINING' })
    expect(scoreApi.fetchMyScores).toHaveBeenCalledWith({ context: 'TRAINING' })
    expect(store.scores).toHaveLength(1)
    expect(store.scoresMeta.totalElements).toBe(1)
    expect(store.error).toBeNull()
  })

  it('loadScores captures errors', async () => {
    scoreApi.fetchMyScores.mockRejectedValue(new Error('boom'))
    const store = useScoreStore()
    await store.loadScores()
    expect(store.error).toBe('boom')
    expect(store.isLoading).toBe(false)
  })

  it('loadSummary stores the summary', async () => {
    scoreApi.fetchMyScoreSummary.mockResolvedValue({ contexts: [], passen: [], wettkaempfe: [] })
    const store = useScoreStore()
    await store.loadSummary()
    expect(store.summary).toEqual({ contexts: [], passen: [], wettkaempfe: [] })
  })

  it('loadLeaderboard stores entries', async () => {
    scoreApi.fetchLeaderboard.mockResolvedValue({ metric: 'best', entries: [{ userId: 'u1' }] })
    const store = useScoreStore()
    await store.loadLeaderboard({ metric: 'best' })
    expect(store.leaderboard.entries).toHaveLength(1)
  })

  it('loadPassen stores grouped passen', async () => {
    scoreApi.fetchMyPassen.mockResolvedValue([{ key: 'p1', serien: [], totalPoints: 14 }])
    const store = useScoreStore()
    await store.loadPassen()
    expect(store.passen).toHaveLength(1)
    expect(store.passen[0].totalPoints).toBe(14)
  })

  it('loadWettkaempfe stores grouped wettkaempfe', async () => {
    scoreApi.fetchMyWettkaempfe.mockResolvedValue([{ key: 'w1', passen: [], totalPoints: 15 }])
    const store = useScoreStore()
    await store.loadWettkaempfe()
    expect(store.wettkaempfe).toHaveLength(1)
  })
})

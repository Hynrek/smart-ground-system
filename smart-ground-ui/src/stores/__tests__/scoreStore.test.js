import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useScoreStore } from '@/stores/scoreStore.js'
import * as scoreApi from '@/services/scoreApi.js'

vi.mock('@/services/scoreApi.js', () => ({
  fetchMyScores: vi.fn(),
  fetchMyScoreSummary: vi.fn(),
  fetchLeaderboard: vi.fn(),
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
})

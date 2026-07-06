import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as scoreApi from '@/services/scoreApi.js'
import { apiFetch } from '@/services/apiClient.js'

vi.mock('@/services/apiClient.js', () => ({
  apiFetch: vi.fn(),
}))

describe('scoreApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiFetch.mockResolvedValue({})
  })

  it('fetchMyScores builds the query string from params, skipping empty values', async () => {
    await scoreApi.fetchMyScores({ context: 'TRAINING', page: 0, serieId: '', from: null })
    expect(apiFetch).toHaveBeenCalledWith('/users/me/scores?context=TRAINING&page=0')
  })

  it('fetchMyScores without params calls the bare path', async () => {
    await scoreApi.fetchMyScores()
    expect(apiFetch).toHaveBeenCalledWith('/users/me/scores')
  })

  it('fetchMyScoreSummary calls the summary path', async () => {
    await scoreApi.fetchMyScoreSummary()
    expect(apiFetch).toHaveBeenCalledWith('/users/me/scores/summary')
  })

  it('fetchLeaderboard passes filters', async () => {
    await scoreApi.fetchLeaderboard({ serieId: 's1', metric: 'best' })
    expect(apiFetch).toHaveBeenCalledWith('/scores/leaderboard?serieId=s1&metric=best')
  })
})

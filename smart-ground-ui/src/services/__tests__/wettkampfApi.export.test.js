import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

vi.mock('@/services/authHeader.js', () => ({
  getAuthHeaders: () => ({ Authorization: 'Bearer t' }),
}))

import { exportLeaderboard } from '../wettkampfApi.js'

describe('exportLeaderboard', () => {
  let fetchMock, anchor, createObjectURL, revokeObjectURL

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue({ ok: true, blob: async () => ({}) })
    createObjectURL = vi.fn(() => 'blob:url')
    revokeObjectURL = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    anchor = { href: '', download: '', click: vi.fn(), remove: vi.fn() }
    vi.spyOn(document, 'createElement').mockReturnValue(anchor)
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => anchor)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('requests the CSV export with auth headers and triggers a download', async () => {
    await exportLeaderboard('s1', 'csv')
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/sessions/s1/leaderboard/export?format=csv',
      { headers: { Authorization: 'Bearer t' } },
    )
    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(anchor.click).toHaveBeenCalledOnce()
    expect(anchor.download).toBe('rangliste.csv')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:url')
  })

  it('defaults to csv format', async () => {
    await exportLeaderboard('s1')
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/sessions/s1/leaderboard/export?format=csv',
      expect.anything(),
    )
  })

  it('throws on a failed response', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 500 })
    await expect(exportLeaderboard('s1')).rejects.toThrow('500')
  })
})

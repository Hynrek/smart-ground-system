import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as apiClient from '../apiClient.js'

vi.mock('../apiClient.js', () => ({ apiFetch: vi.fn() }))

describe('tiebreakerApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('startTiebreaker posts a Serie payload without templateType', async () => {
    apiClient.apiFetch.mockResolvedValue({ id: 'tb1' })
    const { startTiebreaker } = await import('../tiebreakerApi.js')
    // Pass a stray templateType to prove it is stripped from the request body.
    await startTiebreaker('s1', { playerIds: ['p1', 'p2'], templateId: 'se1', tiePosition: 1, templateType: 'serie' })
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/sessions/s1/tiebreakers', {
      method: 'POST',
      body: JSON.stringify({ playerIds: ['p1', 'p2'], templateId: 'se1', tiePosition: 1 }),
    })
  })
})

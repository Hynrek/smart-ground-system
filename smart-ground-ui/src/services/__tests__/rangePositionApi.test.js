import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../apiClient.js', () => ({
  apiFetch: vi.fn(),
}))

describe('rangePositionApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('sendPositionCommand calls correct endpoint with POST', async () => {
    const { apiFetch } = await import('../apiClient.js')
    vi.mocked(apiFetch).mockResolvedValue({})

    const { sendPositionCommand } = await import('../rangePositionApi.js')
    await sendPositionCommand('range-1', 'pos-abc')

    expect(apiFetch).toHaveBeenCalledWith(
      '/ranges/range-1/positions/pos-abc/command',
      { method: 'POST' }
    )
  })
})

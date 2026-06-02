import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as apiClient from '../apiClient.js'

vi.mock('../apiClient.js', () => ({ apiFetch: vi.fn() }))

describe('ablaufApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchAblaeufe calls GET /ablaeufe', async () => {
    apiClient.apiFetch.mockResolvedValue([])
    const { fetchAblaeufe } = await import('../ablaufApi.js')
    await fetchAblaeufe()
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/ablaeufe')
  })

  it('createAblauf calls POST /ablaeufe with body', async () => {
    apiClient.apiFetch.mockResolvedValue({ id: 'abc', name: 'Test' })
    const { createAblauf } = await import('../ablaufApi.js')
    await createAblauf('Test', [], null, 'user')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/ablaeufe', {
      method: 'POST',
      body: JSON.stringify({ name: 'Test', steps: [], rangeId: null, ownership: 'user' }),
    })
  })

  it('deleteAblauf calls DELETE /ablaeufe/{id}', async () => {
    apiClient.apiFetch.mockResolvedValue(null)
    const { deleteAblauf } = await import('../ablaufApi.js')
    await deleteAblauf('abc')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/ablaeufe/abc', { method: 'DELETE' })
  })
})

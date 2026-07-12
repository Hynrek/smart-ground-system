import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as apiClient from '../apiClient.js'

vi.mock('../apiClient.js', () => ({ apiFetch: vi.fn() }))

describe('serieApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('fetchSerien calls GET /serien', async () => {
    apiClient.apiFetch.mockResolvedValue([])
    const { fetchSerien } = await import('../serieApi.js')
    await fetchSerien()
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/serien')
  })

  it('createSerie calls POST /serien with body', async () => {
    apiClient.apiFetch.mockResolvedValue({ id: 'abc', name: 'Test' })
    const { createSerie } = await import('../serieApi.js')
    await createSerie('Test', [], null, 'user')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/serien', {
      method: 'POST',
      body: JSON.stringify({ name: 'Test', steps: [], rangeId: null, ownership: 'user' }),
    })
  })

  it('deleteSerie calls DELETE /serien/{id}', async () => {
    apiClient.apiFetch.mockResolvedValue(null)
    const { deleteSerie } = await import('../serieApi.js')
    await deleteSerie('abc')
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/serien/abc', { method: 'DELETE' })
  })
})

import { describe, it, expect, vi, beforeEach } from 'vitest'

const { mockApiFetch } = vi.hoisted(() => ({ mockApiFetch: vi.fn() }))
vi.mock('@/services/apiClient.js', () => ({ apiFetch: mockApiFetch }))

import * as api from '@/services/wettkampfApi.js'

describe('wettkampfApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockApiFetch.mockResolvedValue({})
  })

  it('createSession posts to /sessions with competition type', async () => {
    mockApiFetch.mockResolvedValue({ id: 's1', status: 'SETUP' })
    await api.createSession('Frühjahrspokal', [{ id: 'p1', serieIds: [] }], [])
    expect(mockApiFetch).toHaveBeenCalledWith('/sessions', expect.objectContaining({ method: 'POST' }))
    const body = JSON.parse(mockApiFetch.mock.calls[0][1].body)
    expect(body.type).toBe('competition')
    expect(body.name).toBe('Frühjahrspokal')
  })

  it('listSessions appends type query param', async () => {
    mockApiFetch.mockResolvedValue({ content: [] })
    await api.listSessions('competition')
    expect(mockApiFetch).toHaveBeenCalledWith(
      expect.stringContaining('type=competition')
    )
  })

  it('patchStatus patches /sessions/:id/status', async () => {
    await api.patchStatus('s1', 'open')
    expect(mockApiFetch).toHaveBeenCalledWith(
      '/sessions/s1/status', expect.objectContaining({ method: 'PATCH' })
    )
    const body = JSON.parse(mockApiFetch.mock.calls[0][1].body)
    expect(body.status).toBe('open')
  })

  it('deleteSession calls DELETE /sessions/:id', async () => {
    await api.deleteSession('s1')
    expect(mockApiFetch).toHaveBeenCalledWith('/sessions/s1', expect.objectContaining({ method: 'DELETE' }))
  })

  it('addMember posts to members endpoint', async () => {
    await api.addMember('s1', 'g1', { displayName: 'Max', type: 'USER', paid: false })
    expect(mockApiFetch).toHaveBeenCalledWith(
      '/sessions/s1/groups/g1/members', expect.objectContaining({ method: 'POST' })
    )
  })

  it('patchMember patches member paid status', async () => {
    await api.patchMember('s1', 'g1', 'm1', true)
    const body = JSON.parse(mockApiFetch.mock.calls[0][1].body)
    expect(body.paid).toBe(true)
  })

  it('completeSerie posts to serien complete endpoint', async () => {
    await api.completeSerie('s1', 'g1', 'ser1', 0, null, null)
    expect(mockApiFetch).toHaveBeenCalledWith(
      '/sessions/s1/groups/g1/serien/ser1/complete',
      expect.objectContaining({ method: 'POST' })
    )
  })

  it('getProgress calls GET progress endpoint', async () => {
    mockApiFetch.mockResolvedValue({ groups: [] })
    const result = await api.getProgress('s1')
    expect(mockApiFetch).toHaveBeenCalledWith('/sessions/s1/progress')
    expect(result.groups).toEqual([])
  })
})

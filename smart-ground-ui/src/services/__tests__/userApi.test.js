import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../apiClient.js', () => ({
  apiFetch: vi.fn(),
}))

describe('userApi — new functions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('updateUser calls PATCH /users/{id}', async () => {
    const { apiFetch } = await import('../apiClient.js')
    vi.mocked(apiFetch).mockResolvedValue({ id: 'u1', vorname: 'Hans' })

    const { updateUser } = await import('../userApi.js')
    const data = { vorname: 'Hans', nachname: 'Müller' }
    await updateUser('u1', data)

    expect(apiFetch).toHaveBeenCalledWith('/users/u1', {
      method: 'PATCH',
      body: JSON.stringify(data),
    })
  })

  it('assignRole calls POST /users/{id}/roles with roleName', async () => {
    const { apiFetch } = await import('../apiClient.js')
    vi.mocked(apiFetch).mockResolvedValue({})

    const { assignRole } = await import('../userApi.js')
    await assignRole('u1', 'SHOOTER')

    expect(apiFetch).toHaveBeenCalledWith('/users/u1/roles', {
      method: 'POST',
      body: JSON.stringify({ roleName: 'SHOOTER' }),
    })
  })

  it('fetchUserRoles calls GET /users/{id}/roles', async () => {
    const { apiFetch } = await import('../apiClient.js')
    vi.mocked(apiFetch).mockResolvedValue([{ roleName: 'SHOOTER' }])

    const { fetchUserRoles } = await import('../userApi.js')
    const result = await fetchUserRoles('u1')

    expect(apiFetch).toHaveBeenCalledWith('/users/u1/roles')
    expect(result).toEqual([{ roleName: 'SHOOTER' }])
  })
})

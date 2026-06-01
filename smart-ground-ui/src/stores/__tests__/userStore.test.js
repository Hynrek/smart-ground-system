import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../userStore'
import * as userApi from '../../services/userApi'

vi.mock('../../services/userApi')

const mockUser1 = {
  id: 'u1',
  email: 'hans@test.ch',
  vorname: 'Hans',
  nachname: 'Müller',
  fullName: 'Hans Müller',
  status: 'ACTIVE',
  erstelltAm: '2024-01-01T00:00:00Z',
  aktualisiertAm: '2024-01-01T00:00:00Z',
}
const mockUser2 = {
  id: 'u2',
  email: 'anna@test.ch',
  vorname: 'Anna',
  nachname: 'Schmidt',
  fullName: 'Anna Schmidt',
  status: 'ACTIVE',
  erstelltAm: '2024-01-02T00:00:00Z',
  aktualisiertAm: '2024-01-02T00:00:00Z',
}
const mockRoles = [{ roleName: 'SHOOTER', roleId: 'r1', description: 'Schütze', assignedAt: '2024-01-01T00:00:00Z' }]

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadUsers fetches users and their roles in parallel', async () => {
    vi.mocked(userApi.fetchUsers).mockResolvedValue([mockUser1, mockUser2])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue(mockRoles)

    const store = useUserStore()
    await store.loadUsers()

    expect(store.users).toEqual([mockUser1, mockUser2])
    expect(store.userRolesMap['u1']).toEqual(mockRoles)
    expect(store.userRolesMap['u2']).toEqual(mockRoles)
    expect(userApi.fetchUserRoles).toHaveBeenCalledTimes(2)
  })

  it('loadUsers tolerates individual role-fetch failures gracefully', async () => {
    vi.mocked(userApi.fetchUsers).mockResolvedValue([mockUser1])
    vi.mocked(userApi.fetchUserRoles).mockRejectedValue(new Error('403'))

    const store = useUserStore()
    await store.loadUsers()

    expect(store.users).toEqual([mockUser1])
    expect(store.userRolesMap['u1']).toEqual([])
  })

  it('selectUser sets selectedUser', () => {
    const store = useUserStore()
    store.selectUser(mockUser1)
    expect(store.selectedUser).toEqual(mockUser1)
  })

  it('createUser creates user and assigns role, then reloads', async () => {
    vi.mocked(userApi.createUser).mockResolvedValue({ ...mockUser1, id: 'new-id' })
    vi.mocked(userApi.assignRole).mockResolvedValue({})
    vi.mocked(userApi.fetchUsers).mockResolvedValue([mockUser1])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue(mockRoles)

    const store = useUserStore()
    const userData = { vorname: 'Hans', nachname: 'Müller', email: 'hans@test.ch', password: 'secret' }
    await store.createUser(userData, 'ADMIN')

    expect(userApi.createUser).toHaveBeenCalledWith(userData)
    expect(userApi.assignRole).toHaveBeenCalledWith('new-id', 'ADMIN')
    expect(userApi.fetchUsers).toHaveBeenCalled()
  })

  it('createUser defaults role to SHOOTER when not provided', async () => {
    vi.mocked(userApi.createUser).mockResolvedValue({ ...mockUser1, id: 'new-id' })
    vi.mocked(userApi.assignRole).mockResolvedValue({})
    vi.mocked(userApi.fetchUsers).mockResolvedValue([])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue([])

    const store = useUserStore()
    await store.createUser({ vorname: 'Hans', nachname: 'Müller', email: 'h@t.ch', password: 'x' })

    expect(userApi.assignRole).toHaveBeenCalledWith('new-id', 'SHOOTER')
  })

  it('updateUser calls userApi.updateUser and reloads', async () => {
    vi.mocked(userApi.updateUser).mockResolvedValue({ ...mockUser1, vorname: 'Johannes' })
    vi.mocked(userApi.fetchUsers).mockResolvedValue([mockUser1])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue(mockRoles)

    const store = useUserStore()
    await store.updateUser('u1', { vorname: 'Johannes' })

    expect(userApi.updateUser).toHaveBeenCalledWith('u1', { vorname: 'Johannes' })
    expect(userApi.fetchUsers).toHaveBeenCalled()
  })

  it('deleteUser clears selectedUser if deleted user was selected', async () => {
    vi.mocked(userApi.deleteUser).mockResolvedValue(null)
    vi.mocked(userApi.fetchUsers).mockResolvedValue([])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue([])

    const store = useUserStore()
    store.users = [mockUser1]
    store.selectUser(mockUser1)

    await store.deleteUser('u1')

    expect(store.selectedUser).toBeNull()
  })
})

describe('useUserStore — toggleRole', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('assigns role when user does not have it', async () => {
    const store = useUserStore()
    store.userRolesMap = { 'u1': [{ roleName: 'SHOOTER' }] }
    userApi.assignRole.mockResolvedValue({ roleName: 'ADMIN' })
    userApi.fetchUserRoles.mockResolvedValue([{ roleName: 'SHOOTER' }, { roleName: 'ADMIN' }])

    await store.toggleRole('u1', 'ADMIN')

    expect(userApi.assignRole).toHaveBeenCalledWith('u1', 'ADMIN')
    expect(userApi.revokeRole).not.toHaveBeenCalled()
    expect(store.userRolesMap['u1']).toEqual([{ roleName: 'SHOOTER' }, { roleName: 'ADMIN' }])
  })

  it('revokes role when user already has it', async () => {
    const store = useUserStore()
    store.userRolesMap = { 'u1': [{ roleName: 'SHOOTER' }, { roleName: 'ADMIN' }] }
    userApi.revokeRole.mockResolvedValue(undefined)
    userApi.fetchUserRoles.mockResolvedValue([{ roleName: 'SHOOTER' }])

    await store.toggleRole('u1', 'ADMIN')

    expect(userApi.revokeRole).toHaveBeenCalledWith('u1', 'ADMIN')
    expect(userApi.assignRole).not.toHaveBeenCalled()
    expect(store.userRolesMap['u1']).toEqual([{ roleName: 'SHOOTER' }])
  })

  it('sets error and rethrows on API failure', async () => {
    const store = useUserStore()
    store.userRolesMap = { 'u1': [] }
    userApi.assignRole.mockRejectedValue(new Error('Network error'))

    await expect(store.toggleRole('u1', 'ADMIN')).rejects.toThrow('Network error')
    expect(store.error).toBe('Network error')
  })
})

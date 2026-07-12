// src/stores/__tests__/authStore.test.js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../authStore'
import * as authApi from '@/services/authApi'
import * as userApi from '@/services/userApi'
import { resetAppData } from '@/stores/dataLifecycle.js'

vi.mock('@/services/authApi')
vi.mock('@/services/userApi')
vi.mock('@/stores/dataLifecycle.js', () => ({ resetAppData: vi.fn() }))

const mockProfile = {
  id: 'user-1',
  email: 'admin@smartground.local',
  vorname: 'Max',
  nachname: 'Muster',
  status: 'ACTIVE',
  erstelltAm: '2024-01-01T00:00:00Z',
  permissions: ['MANAGE_USERS', 'MANAGE_RANGES'],
}

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('is not authenticated initially', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated()).toBe(false)
    expect(store.permissions).toEqual([])
    expect(store.profile).toBeNull()
  })

  it('login stores token and fetches profile', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.login('admin@smartground.local', 'admin123')

    expect(localStorage.getItem('sg_token')).toBe('test.jwt.token')
    expect(store.isAuthenticated()).toBe(true)
    expect(store.profile).toEqual(mockProfile)
    expect(store.permissions).toEqual(['MANAGE_USERS', 'MANAGE_RANGES'])
  })

  it('hasPermission returns true for granted permission', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.login('admin@smartground.local', 'admin123')

    expect(store.hasPermission('MANAGE_USERS')).toBe(true)
    expect(store.hasPermission('VIEW_REMOTE')).toBe(false)
  })

  it('logout clears all state', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.login('admin@smartground.local', 'admin123')
    store.logout()

    expect(store.isAuthenticated()).toBe(false)
    expect(store.profile).toBeNull()
    expect(store.permissions).toEqual([])
    expect(localStorage.getItem('sg_token')).toBeNull()
  })

  it('init fetches profile if token exists in localStorage', async () => {
    localStorage.setItem('sg_token', 'existing.jwt.token')
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.init()

    expect(store.profile).toEqual(mockProfile)
    expect(store.permissions).toEqual(['MANAGE_USERS', 'MANAGE_RANGES'])
  })

  it('init clears token if getMe fails', async () => {
    localStorage.setItem('sg_token', 'expired.jwt.token')
    vi.mocked(authApi.getMe).mockRejectedValue(new Error('HTTP 401'))

    const store = useAuthStore()
    await store.init()

    expect(store.isAuthenticated()).toBe(false)
    expect(localStorage.getItem('sg_token')).toBeNull()
  })

  it('login clears token when getMe fails after receiving JWT', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockRejectedValue(new Error('HTTP 500'))

    const store = useAuthStore()
    await expect(store.login('admin@smartground.local', 'admin123')).rejects.toThrow('HTTP 500')

    // Token must not be left dangling after a failed profile fetch
    expect(store.isAuthenticated()).toBe(false)
    expect(localStorage.getItem('sg_token')).toBeNull()
    expect(store.permissions).toEqual([])
  })

  it('displayName returns full name from profile', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.login('admin@smartground.local', 'admin123')

    expect(store.displayName).toBe('Max Muster')
  })

  describe('updateProfile', () => {
    it('calls updateUser with profile id and data, then reloads profile', async () => {
      vi.mocked(authApi.login).mockResolvedValue({ token: 'tok' })
      vi.mocked(authApi.getMe)
        .mockResolvedValueOnce({
          id: 'user-1',
          vorname: 'Hans',
          nachname: 'Alt',
          username: 'hans',
          email: 'hans@test.com',
          permissions: [],
        })
        .mockResolvedValueOnce({
          id: 'user-1',
          vorname: 'Hans',
          nachname: 'Neu',
          username: 'hans',
          email: 'hans@test.com',
          permissions: [],
        })
      vi.mocked(userApi.updateUser).mockResolvedValue({})

      const store = useAuthStore()
      await store.login('hans@test.com', 'pw')

      await store.updateProfile({ nachname: 'Neu' })

      expect(userApi.updateUser).toHaveBeenCalledWith('user-1', { nachname: 'Neu' })
      expect(store.profile.nachname).toBe('Neu')
    })

    it('sets error and rethrows when updateUser fails', async () => {
      vi.mocked(authApi.login).mockResolvedValue({ token: 'tok' })
      vi.mocked(authApi.getMe).mockResolvedValue({
        id: 'user-1',
        vorname: 'Hans',
        nachname: 'Alt',
        username: 'hans',
        email: 'hans@test.com',
        permissions: [],
      })
      vi.mocked(userApi.updateUser).mockRejectedValue(new Error('Network error'))

      const store = useAuthStore()
      await store.login('hans@test.com', 'pw')

      await expect(store.updateProfile({ nachname: 'Neu' })).rejects.toThrow('Network error')
      expect(store.error).toBe('Network error')
    })
  })
})

describe('authStore data reset on identity change', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('wipes app data on logout', () => {
    const store = useAuthStore()
    store.logout()
    expect(resetAppData).toHaveBeenCalled()
  })

  it('wipes app data before loading the new profile on login', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'test.jwt.token' })
    vi.mocked(authApi.getMe).mockResolvedValue(mockProfile)

    const store = useAuthStore()
    await store.login('userB', 'pw')
    expect(resetAppData).toHaveBeenCalled()
  })
})

// src/stores/__tests__/authStore.test.js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../authStore'
import * as userApi from '@/services/userApi'
import * as authApi from '@/services/authApi'

vi.mock('@/services/userApi')
vi.mock('@/services/authApi')

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
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
      vi.mocked(authApi.getMe).mockResolvedValue({
        id: 'user-1', vorname: 'Hans', nachname: 'Alt',
        username: 'hans', email: 'hans@test.com', permissions: [],
      })
      vi.mocked(userApi.updateUser).mockRejectedValue(new Error('Network error'))

      const store = useAuthStore()
      store.profile = { id: 'user-1', vorname: 'Hans', nachname: 'Alt', username: 'hans', email: 'hans@test.com' }

      await expect(store.updateProfile({ nachname: 'Neu' })).rejects.toThrow('Network error')
      expect(store.error).toBe('Network error')
    })
  })
})

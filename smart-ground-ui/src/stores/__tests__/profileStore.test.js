import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProfileStore } from '@/stores/profileStore.js'
import * as userApi from '@/services/userApi.js'

vi.mock('@/services/userApi.js', () => ({
  fetchMyQrToken: vi.fn(),
  rotateMyQrToken: vi.fn(),
  resolveUserByQr: vi.fn(),
  fetchMyPlayResults: vi.fn(),
}))

describe('profileStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadQrToken stores the token', async () => {
    userApi.fetchMyQrToken.mockResolvedValue({ qrToken: 'tok-1' })
    const store = useProfileStore()

    await store.loadQrToken()

    expect(store.qrToken).toBe('tok-1')
    expect(store.error).toBeNull()
  })

  it('rotateQrToken replaces the token', async () => {
    userApi.rotateMyQrToken.mockResolvedValue({ qrToken: 'tok-2' })
    const store = useProfileStore()
    store.qrToken = 'tok-1'

    await store.rotateQrToken()

    expect(store.qrToken).toBe('tok-2')
  })

  it('loadQrToken captures errors', async () => {
    userApi.fetchMyQrToken.mockRejectedValue(new Error('boom'))
    const store = useProfileStore()

    await store.loadQrToken()

    expect(store.error).toBe('boom')
    expect(store.qrToken).toBeNull()
  })

  it('loadMyResults stores the result list', async () => {
    userApi.fetchMyPlayResults.mockResolvedValue([{ resultId: 'r1', totalPoints: 12 }])
    const store = useProfileStore()

    await store.loadMyResults()

    expect(store.myResults).toHaveLength(1)
  })

  it('resolveCheckinToken passes through the resolved user and rethrows errors', async () => {
    userApi.resolveUserByQr.mockResolvedValue({ userId: 'u1', displayName: 'Anna' })
    const store = useProfileStore()

    await expect(store.resolveCheckinToken('tok')).resolves.toEqual({ userId: 'u1', displayName: 'Anna' })

    const notFound = Object.assign(new Error('nope'), { status: 404 })
    userApi.resolveUserByQr.mockRejectedValue(notFound)
    await expect(store.resolveCheckinToken('bad')).rejects.toMatchObject({ status: 404 })
  })
})

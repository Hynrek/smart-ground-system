import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useGuestStore } from '../guestStore.js'
import * as guestApi from '@/services/guestApi.js'

vi.mock('@/services/guestApi.js')

describe('guestStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadGuests fetches from API', async () => {
    guestApi.fetchGuests.mockResolvedValue([{ id: 'g1', displayName: 'Alice', createdAt: '2025-01-01T00:00:00Z' }])
    const store = useGuestStore()
    await store.loadGuests()
    expect(store.guests).toHaveLength(1)
    expect(store.guests[0].displayName).toBe('Alice')
  })

  it('addGuest calls POST and adds to list', async () => {
    guestApi.createGuest.mockResolvedValue({ id: 'g2', displayName: 'Bob', createdAt: '2025-01-01T00:00:00Z' })
    const store = useGuestStore()
    store.guests = []
    const result = await store.addGuest('Bob')
    expect(guestApi.createGuest).toHaveBeenCalledWith('Bob')
    expect(result.id).toBe('g2')
    expect(store.guests).toHaveLength(1)
  })

  it('removeGuest calls DELETE and removes from list', async () => {
    guestApi.deleteGuest.mockResolvedValue(null)
    const store = useGuestStore()
    store.guests = [{ id: 'g1', displayName: 'Alice' }]
    await store.removeGuest('g1')
    expect(guestApi.deleteGuest).toHaveBeenCalledWith('g1')
    expect(store.guests).toHaveLength(0)
  })
})

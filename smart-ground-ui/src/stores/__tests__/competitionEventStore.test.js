import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionEventStore } from '../competitionEventStore.js'

vi.mock('@/services/wettkampfApi.js', () => ({
  createSession: vi.fn(),
  listSessions:  vi.fn(),
  patchStatus:   vi.fn(),
  deleteSession: vi.fn(),
  createGroup:   vi.fn(),
  updateGroup:   vi.fn(),
  deleteGroup:   vi.fn(),
  addMember:     vi.fn(),
  removeMember:  vi.fn(),
  patchMember:   vi.fn(),
}))

import * as api from '@/services/wettkampfApi.js'

const mkSession = (o = {}) => ({
  id: 's1', name: 'Frühjahrspokal', status: 'SETUP', groups: [], createdAt: '2026-06-05T00:00:00Z', ...o,
})

describe('useCompetitionEventStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadEvents populates events from API', async () => {
    api.listSessions.mockResolvedValue({ content: [mkSession()] })
    const store = useCompetitionEventStore()
    await store.loadEvents()
    expect(store.events).toHaveLength(1)
    expect(store.events[0].name).toBe('Frühjahrspokal')
  })

  it('createEvent calls API and returns new id', async () => {
    api.createSession.mockResolvedValue(mkSession({ id: 'new-1' }))
    const store = useCompetitionEventStore()
    const id = await store.createEvent('Frühjahrspokal', [{ id: 'p1', serieIds: [] }], [])
    expect(api.createSession).toHaveBeenCalledWith('Frühjahrspokal', [{ id: 'p1', serieIds: [] }], [])
    expect(id).toBe('new-1')
    expect(store.events).toHaveLength(1)
  })

  it('openEvent patches status to open and updates local event', async () => {
    api.patchStatus.mockResolvedValue(mkSession({ status: 'OPEN' }))
    const store = useCompetitionEventStore()
    store.events = [mkSession()]
    await store.openEvent('s1')
    expect(api.patchStatus).toHaveBeenCalledWith('s1', 'open')
    expect(store.events[0].status).toBe('OPEN')
  })

  it('startEvent patches status to active', async () => {
    api.patchStatus.mockResolvedValue(mkSession({ status: 'ACTIVE' }))
    const store = useCompetitionEventStore()
    store.events = [mkSession({ status: 'OPEN' })]
    await store.startEvent('s1')
    expect(api.patchStatus).toHaveBeenCalledWith('s1', 'active')
    expect(store.events[0].status).toBe('ACTIVE')
  })

  it('stopEvent patches status to abandoned', async () => {
    api.patchStatus.mockResolvedValue(mkSession({ status: 'ABANDONED' }))
    const store = useCompetitionEventStore()
    store.events = [mkSession({ status: 'ACTIVE' })]
    await store.stopEvent('s1')
    expect(api.patchStatus).toHaveBeenCalledWith('s1', 'abandoned')
  })

  it('deleteEvent calls API and removes from store', async () => {
    api.deleteSession.mockResolvedValue(undefined)
    const store = useCompetitionEventStore()
    store.events = [mkSession()]
    await store.deleteEvent('s1')
    expect(api.deleteSession).toHaveBeenCalledWith('s1')
    expect(store.events).toHaveLength(0)
  })

  it('addRotte calls createGroup and appends to event', async () => {
    api.createGroup.mockResolvedValue({ id: 'g1', name: 'Rotte A', members: [] })
    const store = useCompetitionEventStore()
    store.events = [mkSession()]
    await store.addRotte('s1')
    expect(api.createGroup).toHaveBeenCalledWith('s1', expect.stringContaining('Rotte'))
    expect(store.events[0].groups).toHaveLength(1)
  })

  it('addPlayer calls addMember API', async () => {
    api.addMember.mockResolvedValue({ id: 'm1', displayName: 'Max', paid: false })
    const store = useCompetitionEventStore()
    store.events = [mkSession({ groups: [{ id: 'g1', name: 'Rotte A', members: [] }] })]
    await store.addPlayer('s1', 'g1', { id: 'u1', displayName: 'Max' })
    expect(api.addMember).toHaveBeenCalledWith('s1', 'g1', expect.objectContaining({ displayName: 'Max' }))
    expect(store.events[0].groups[0].members).toHaveLength(1)
  })

  it('togglePlayerPaid calls patchMember with flipped paid value', async () => {
    api.patchMember.mockResolvedValue({ id: 'm1', paid: true })
    const store = useCompetitionEventStore()
    store.events = [mkSession({ groups: [{ id: 'g1', name: 'Rotte A', members: [{ id: 'm1', displayName: 'Max', paid: false }] }] })]
    await store.togglePlayerPaid('s1', 'g1', 'm1')
    expect(api.patchMember).toHaveBeenCalledWith('s1', 'g1', 'm1', true)
    expect(store.events[0].groups[0].members[0].paid).toBe(true)
  })

  it('planningEvents returns SETUP and OPEN', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'SETUP' }), mkSession({ id: 's2', status: 'OPEN' }), mkSession({ id: 's3', status: 'ACTIVE' })]
    expect(store.planningEvents).toHaveLength(2)
  })

  it('activeEvents returns ACTIVE and PRE_COMPLETE', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'ACTIVE' }), mkSession({ id: 's2', status: 'PRE_COMPLETE' }), mkSession({ id: 's3', status: 'COMPLETED' })]
    expect(store.activeEvents).toHaveLength(2)
  })

  it('completedEvents returns only COMPLETED', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'COMPLETED' }), mkSession({ id: 's2', status: 'ABANDONED' })]
    expect(store.completedEvents).toHaveLength(1)
  })
})

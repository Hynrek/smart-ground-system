// src/stores/__tests__/competitionEventStore.test.js
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionEventStore } from '../competitionEventStore.js'

const mockStartCompetition = vi.fn(() => ({ instanceId: 'inst-abc' }))
const mockStopInstance = vi.fn()
let mockCompletedInstances = []

vi.mock('../activePasseStore.js', () => ({
  useActivePasseStore: vi.fn(() => ({
    startCompetition: mockStartCompetition,
    stopInstance: mockStopInstance,
    get completedInstances() { return mockCompletedInstances },
  }))
}))

const mockPassen = [{ id: 'p1', name: 'Passe 1', serien: [] }]

describe('useCompetitionEventStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    mockCompletedInstances = []
    vi.clearAllMocks()
  })

  it('starts empty', () => {
    const store = useCompetitionEventStore()
    expect(store.events).toHaveLength(0)
  })

  it('creates a PLANNING event and returns its id', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Frühjahrspokal', mockPassen)
    expect(store.planningEvents).toHaveLength(1)
    expect(store.planningEvents[0].name).toBe('Frühjahrspokal')
    expect(store.planningEvents[0].status).toBe('PLANNING')
    expect(store.planningEvents[0].id).toBe(id)
    expect(store.planningEvents[0].passen).toEqual(mockPassen)
  })

  it('updates event name while in PLANNING', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Old Name', mockPassen)
    store.updateEventName(id, 'New Name')
    expect(store.getEvent(id).name).toBe('New Name')
  })

  it('does not update name when ACTIVE', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    store.updateEventName(id, 'Should Not Apply')
    expect(store.getEvent(id).name).toBe('Test')
  })

  it('adds Rotten with auto-generated names A, B, C', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    store.addRotte(id)
    store.addRotte(id)
    const rotten = store.getEvent(id).rotten
    expect(rotten).toHaveLength(3)
    expect(rotten[0].name).toBe('Rotte A')
    expect(rotten[1].name).toBe('Rotte B')
    expect(rotten[2].name).toBe('Rotte C')
  })

  it('renames a Rotte', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.renameRotte(id, rotteId, 'Spezialrotte')
    expect(store.getEvent(id).rotten[0].name).toBe('Spezialrotte')
  })

  it('removes a Rotte', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.removeRotte(id, rotteId)
    expect(store.getEvent(id).rotten).toHaveLength(0)
  })

  it('addPlayer stores userId and displayName from user object', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotte = store.getEvent(id).rotten[0]
    const user = { id: 'user-uuid-1', displayName: 'Anna Müller' }

    store.addPlayer(id, rotte.rotteId, user)

    const player = store.getEvent(id).rotten[0].players[0]
    expect(player.userId).toBe('user-uuid-1')
    expect(player.displayName).toBe('Anna Müller')
    expect(player.paid).toBe(false)
    expect(player.id).toBeDefined()
  })

  it('does not expose updatePlayerName', () => {
    const store = useCompetitionEventStore()
    expect(store.updatePlayerName).toBeUndefined()
  })

  it('toggles player paid status', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.addPlayer(id, rotteId, { id: 'u1', displayName: 'Test User' })
    const playerId = store.getEvent(id).rotten[0].players[0].id
    expect(store.getEvent(id).rotten[0].players[0].paid).toBe(false)
    store.togglePlayerPaid(id, rotteId, playerId)
    expect(store.getEvent(id).rotten[0].players[0].paid).toBe(true)
    store.togglePlayerPaid(id, rotteId, playerId)
    expect(store.getEvent(id).rotten[0].players[0].paid).toBe(false)
  })

  it('removes a player', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.addRotte(id)
    const rotteId = store.getEvent(id).rotten[0].rotteId
    store.addPlayer(id, rotteId, { id: 'u1', displayName: 'Player One' })
    store.addPlayer(id, rotteId, { id: 'u2', displayName: 'Player Two' })
    const playerId = store.getEvent(id).rotten[0].players[0].id
    store.removePlayer(id, rotteId, playerId)
    expect(store.getEvent(id).rotten[0].players).toHaveLength(1)
  })

  it('starts an event: sets ACTIVE, stores activeInstanceId', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    const ev = store.getEvent(id)
    expect(ev.status).toBe('ACTIVE')
    expect(ev.activeInstanceId).toBe('inst-abc')
    expect(mockStartCompetition).toHaveBeenCalledOnce()
    expect(store.activeEvents).toHaveLength(1)
    expect(store.planningEvents).toHaveLength(0)
  })

  it('stops an event: sets CANCELLED, clears activeInstanceId', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    store.stopEvent(id)
    const ev = store.getEvent(id)
    expect(ev.status).toBe('CANCELLED')
    expect(ev.activeInstanceId).toBeNull()
    expect(mockStopInstance).toHaveBeenCalledWith('inst-abc')
  })

  it('checkAndCompleteEvent transitions to COMPLETED when instance is done', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    mockCompletedInstances = [{ instanceId: 'inst-abc' }]
    store.checkAndCompleteEvent(id)
    const ev = store.getEvent(id)
    expect(ev.status).toBe('COMPLETED')
    expect(ev.completedAt).not.toBeNull()
    expect(ev.activeInstanceId).toBeNull()
    expect(store.completedEvents).toHaveLength(1)
  })

  it('checkAndCompleteEvent does nothing when instance not yet done', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    mockCompletedInstances = []
    store.checkAndCompleteEvent(id)
    expect(store.getEvent(id).status).toBe('ACTIVE')
  })

  it('deletes a PLANNING event', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.deleteEvent(id)
    expect(store.events).toHaveLength(0)
  })

  it('does not delete an ACTIVE event', () => {
    const store = useCompetitionEventStore()
    const id = store.createEvent('Test', mockPassen)
    store.startEvent(id)
    store.deleteEvent(id)
    expect(store.events).toHaveLength(1)
  })

  it('persists events to localStorage on every mutation', () => {
    const store = useCompetitionEventStore()
    store.createEvent('Persist Test', mockPassen)
    const raw = localStorage.getItem('sg_competition_events')
    const parsed = JSON.parse(raw)
    expect(parsed).toHaveLength(1)
    expect(parsed[0].name).toBe('Persist Test')
  })

  it('loads events from localStorage on init', () => {
    localStorage.setItem('sg_competition_events', JSON.stringify([{
      id: 'restored-id', name: 'Restored', passen: [], status: 'PLANNING',
      rotten: [], activeInstanceId: null, createdAt: 0, startedAt: null, completedAt: null,
    }]))
    const store = useCompetitionEventStore()
    expect(store.events).toHaveLength(1)
    expect(store.events[0].name).toBe('Restored')
  })
})

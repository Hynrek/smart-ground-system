// src/stores/competitionEventStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useActivePasseStore } from './activePasseStore.js'

const STORAGE_KEY = 'sg_competition_events'

const uuid = () =>
  typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

export const useCompetitionEventStore = defineStore('competitionEvent', () => {
  const events = ref([])

  const _save = () => localStorage.setItem(STORAGE_KEY, JSON.stringify(events.value))

  const loadFromStorage = () => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) events.value = JSON.parse(raw)
    } catch { /* ignore malformed data */ }
  }

  const planningEvents = computed(() => events.value.filter(e => e.status === 'PLANNING'))
  const activeEvents = computed(() => events.value.filter(e => e.status === 'ACTIVE'))
  const completedEvents = computed(() => events.value.filter(e => e.status === 'COMPLETED'))

  const getEvent = (id) => events.value.find(e => e.id === id) ?? null

  const createEvent = (name, passen) => {
    const id = uuid()
    events.value.push({
      id,
      name,
      passen: [...passen],
      status: 'PLANNING',
      rotten: [],
      activeInstanceId: null,
      createdAt: Date.now(),
      startedAt: null,
      completedAt: null,
    })
    _save()
    return id
  }

  const updateEventName = (id, name) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    ev.name = name
    _save()
  }

  const addRotte = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const letters = 'ABCDEFGH'
    const name = `Rotte ${letters[ev.rotten.length] ?? ev.rotten.length + 1}`
    ev.rotten.push({ rotteId: uuid(), name, players: [] })
    _save()
  }

  const removeRotte = (id, rotteId) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    ev.rotten = ev.rotten.filter(r => r.rotteId !== rotteId)
    _save()
  }

  const renameRotte = (id, rotteId, name) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const rotte = ev.rotten.find(r => r.rotteId === rotteId)
    if (rotte) { rotte.name = name; _save() }
  }

  const addPlayer = (id, rotteId, user) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const rotte = ev.rotten.find(r => r.rotteId === rotteId)
    if (!rotte) return
    if (!user?.id || !user?.displayName) return
    rotte.players.push({ id: uuid(), userId: user.id, displayName: user.displayName, paid: false })
    _save()
  }

  const removePlayer = (id, rotteId, playerId) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const rotte = ev.rotten.find(r => r.rotteId === rotteId)
    if (!rotte) return
    rotte.players = rotte.players.filter(p => p.id !== playerId)
    _save()
  }

  const togglePlayerPaid = (id, rotteId, playerId) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const rotte = ev.rotten.find(r => r.rotteId === rotteId)
    const player = rotte?.players.find(p => p.id === playerId)
    if (player) { player.paid = !player.paid; _save() }
  }

  const startEvent = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    const activePasseStore = useActivePasseStore()
    const instance = activePasseStore.startCompetition(ev, ev.rotten)
    ev.activeInstanceId = instance.instanceId
    ev.status = 'ACTIVE'
    ev.startedAt = Date.now()
    _save()
  }

  const stopEvent = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'ACTIVE') return
    const activePasseStore = useActivePasseStore()
    activePasseStore.stopInstance(ev.activeInstanceId)
    ev.status = 'CANCELLED'
    ev.activeInstanceId = null
    _save()
  }

  const checkAndCompleteEvent = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'ACTIVE') return
    const activePasseStore = useActivePasseStore()
    const done = activePasseStore.completedInstances.find(
      i => i.instanceId === ev.activeInstanceId
    )
    if (done) {
      ev.status = 'COMPLETED'
      ev.completedAt = Date.now()
      ev.activeInstanceId = null
      _save()
    }
  }

  const deleteEvent = (id) => {
    const ev = getEvent(id)
    if (!ev || ev.status !== 'PLANNING') return
    events.value = events.value.filter(e => e.id !== id)
    _save()
  }

  loadFromStorage()

  return {
    events,
    planningEvents,
    activeEvents,
    completedEvents,
    getEvent,
    createEvent,
    updateEventName,
    addRotte,
    removeRotte,
    renameRotte,
    addPlayer,
    removePlayer,
    togglePlayerPaid,
    startEvent,
    stopEvent,
    checkAndCompleteEvent,
    deleteEvent,
  }
})

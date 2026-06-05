import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as wettkampfApi from '@/services/wettkampfApi.js'

export const useCompetitionEventStore = defineStore('competitionEvent', () => {
  const events  = ref([])
  const loading = ref(false)
  const error   = ref(null)

  // ── Computed ──────────────────────────────────────────────────────────────

  const planningEvents  = computed(() => events.value.filter(e => ['SETUP', 'OPEN'].includes(e.status?.toUpperCase())))
  const activeEvents    = computed(() => events.value.filter(e => ['ACTIVE', 'PRE_COMPLETE'].includes(e.status?.toUpperCase())))
  const completedEvents = computed(() => events.value.filter(e => e.status?.toUpperCase() === 'COMPLETED'))
  const getEvent        = (id) => events.value.find(e => e.id === id) ?? null

  // ── Load ──────────────────────────────────────────────────────────────────

  const loadEvents = async () => {
    loading.value = true
    error.value = null
    try {
      const res = await wettkampfApi.listSessions('competition')
      events.value = res.content ?? res ?? []
    } catch (e) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  const createEvent = async (name, passen, groups = []) => {
    const created = await wettkampfApi.createSession(name, passen, groups)
    events.value = [...events.value, created]
    return created.id
  }

  const openEvent  = async (id) => _replaceEvent(await wettkampfApi.patchStatus(id, 'open'))
  const startEvent = async (id) => _replaceEvent(await wettkampfApi.patchStatus(id, 'active'))
  const stopEvent  = async (id) => _replaceEvent(await wettkampfApi.patchStatus(id, 'abandoned'))

  const deleteEvent = async (id) => {
    await wettkampfApi.deleteSession(id)
    events.value = events.value.filter(e => e.id !== id)
  }

  // ── Rotte management ──────────────────────────────────────────────────────

  const addRotte = async (eventId) => {
    const ev = getEvent(eventId)
    if (!ev) return
    const letters = 'ABCDEFGH'
    const name = `Rotte ${letters[(ev.groups ?? []).length] ?? (ev.groups ?? []).length + 1}`
    const group = await wettkampfApi.createGroup(eventId, name)
    ev.groups = [...(ev.groups ?? []), group]
  }

  const removeRotte = async (eventId, groupId) => {
    await wettkampfApi.deleteGroup(eventId, groupId)
    const ev = getEvent(eventId)
    if (ev) ev.groups = (ev.groups ?? []).filter(g => g.id !== groupId)
  }

  const renameRotte = async (eventId, groupId, name) => {
    const updated = await wettkampfApi.updateGroup(eventId, groupId, name)
    const ev = getEvent(eventId)
    if (ev) ev.groups = (ev.groups ?? []).map(g => g.id === groupId ? { ...g, ...updated } : g)
  }

  // ── Player management ─────────────────────────────────────────────────────

  const addPlayer = async (eventId, groupId, user) => {
    if (!user?.displayName) return
    const member = await wettkampfApi.addMember(eventId, groupId, {
      displayName: user.displayName,
      userId: user.id ?? null,
      type: user.id ? 'USER' : 'GUEST',
      paid: false,
    })
    const ev = getEvent(eventId)
    const group = ev?.groups?.find(g => g.id === groupId)
    if (group) group.members = [...(group.members ?? []), member]
  }

  const removePlayer = async (eventId, groupId, memberId) => {
    await wettkampfApi.removeMember(eventId, groupId, memberId)
    const ev = getEvent(eventId)
    const group = ev?.groups?.find(g => g.id === groupId)
    if (group) group.members = (group.members ?? []).filter(m => m.id !== memberId)
  }

  const togglePlayerPaid = async (eventId, groupId, memberId) => {
    const ev = getEvent(eventId)
    const member = ev?.groups?.find(g => g.id === groupId)?.members?.find(m => m.id === memberId)
    if (!member) return
    const updated = await wettkampfApi.patchMember(eventId, groupId, memberId, !member.paid)
    member.paid = updated.paid
  }

  // ── Private ───────────────────────────────────────────────────────────────

  const _replaceEvent = (updated) => {
    events.value = events.value.map(e => e.id === updated.id ? updated : e)
  }

  return {
    events, loading, error,
    planningEvents, activeEvents, completedEvents, getEvent,
    loadEvents,
    createEvent, openEvent, startEvent, stopEvent, deleteEvent,
    addRotte, removeRotte, renameRotte,
    addPlayer, removePlayer, togglePlayerPaid,
  }
})

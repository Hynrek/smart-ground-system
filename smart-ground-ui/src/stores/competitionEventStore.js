import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as wettkampfApi from '@/services/wettkampfApi.js'
import * as tiebreakerApi from '@/services/tiebreakerApi.js'

export const useCompetitionEventStore = defineStore('competitionEvent', () => {
  // ── Planning state (server-synced) ─────────────────────────────────────────
  const events  = ref([])
  const loading = ref(false)
  const error   = ref(null)

  // ── Runtime state (in-memory, rebuilt from events + passe data) ───────────
  const competitionInstances          = ref([])
  const completedCompetitionInstances = ref([])

  // ── Stechen (tiebreaker) state ────────────────────────────────────────────
  const tiesBySession = ref({})

  // ── Computed ──────────────────────────────────────────────────────────────

  const planningEvents  = computed(() => events.value.filter(e => ['SETUP', 'OPEN'].includes(e.status?.toUpperCase())))
  const activeEvents    = computed(() => events.value.filter(e => ['ACTIVE', 'PRE_COMPLETE'].includes(e.status?.toUpperCase())))
  const completedEvents = computed(() => events.value.filter(e => e.status?.toUpperCase() === 'COMPLETED'))
  const getEvent        = (id) => events.value.find(e => e.id === id) ?? null

  const getCompetitionInstance = (instanceId) =>
    competitionInstances.value.find(i => i.instanceId === instanceId) ?? null

  // ── Load ──────────────────────────────────────────────────────────────────

  const loadEvents = async () => {
    loading.value = true
    error.value = null
    try {
      const res = await wettkampfApi.listSessions('COMPETITION')
      events.value = res.content ?? res ?? []
      const activeEvents = events.value.filter(
        ev => ['ACTIVE', 'PRE_COMPLETE'].includes(ev.status?.toUpperCase())
      )
      for (const ev of activeEvents) initCompetitionInstance(ev)
      // Rehydrate runtime progress from the server so reloads don't reset
      // completed Passen back to pending (otherwise the flyout shows stale Serien).
      await Promise.all(activeEvents.map(async (ev) => {
        try {
          _hydrateProgress(ev.id, await wettkampfApi.getProgress(ev.id))
        } catch (e) {
          console.error('[competitionEventStore] progress hydration failed:', e)
        }
      }))
    } catch (e) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  const createEvent = async (name, passen, groups = []) => {
    const created = await wettkampfApi.createSession(name, passen, groups)
    const full = await wettkampfApi.getSession(created.id)
    events.value = [...events.value, full]
    return full.id
  }

  const openEvent = async (id) => {
    await wettkampfApi.patchStatus(id, 'open')
    _replaceEvent(await wettkampfApi.getSession(id))
  }

  const startEvent = async (id) => {
    await wettkampfApi.patchStatus(id, 'active')
    const updated = await wettkampfApi.getSession(id)
    _replaceEvent(updated)
    initCompetitionInstance(updated)
  }

  // Abandoning a competition deletes it outright — abandoned events are not kept
  // around as archived clutter.
  const stopEvent = (id) => deleteEvent(id)

  const stopCompetition = async (instanceId) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    const sessionId = inst?.sessionId ?? instanceId
    await wettkampfApi.deleteSession(sessionId).catch(console.error)
    events.value = events.value.filter(e => e.id !== sessionId)
    competitionInstances.value = competitionInstances.value.filter(i => i.instanceId !== instanceId)
  }

  const deleteEvent = async (id) => {
    await wettkampfApi.deleteSession(id)
    events.value = events.value.filter(e => e.id !== id)
    competitionInstances.value = competitionInstances.value.filter(i => i.instanceId !== id)
  }

  // ── Competition instance builder ──────────────────────────────────────────

  const initCompetitionInstance = (event) => {
    return _buildInstance(event)
  }

  const _buildInstance = (event) => {
    const rotten = (event.groups ?? []).map(group => ({
      rotteId: group.id,
      name: group.name,
      players: (group.members ?? []).map(m => ({ id: m.id, displayName: m.displayName })),
      status: 'waiting',
      assignedRangeId: null,
      currentPhaseIndex: 0,
      phases: (event.passen ?? []).map((passe, phaseIndex) => ({
        phaseIndex,
        passeId: passe.id,
        passeName: passe.name,
        status: phaseIndex === 0 ? 'active' : 'pending',
        blocks: (passe.serien ?? []).map(serie => ({
          blockId: serie.id,
          serieId: serie.id,
          serieAlias: serie.alias ?? '?',
          rangeId: serie.rangeId ?? null,
          rangeName: serie.rangeName ?? null,
          steps: serie.steps ?? [],
          status: 'pending',
          completedAt: null,
          result: null,
        })),
      })),
    }))

    const instance = {
      instanceId: event.id,
      sessionId: event.id,
      type: 'competition',
      templateName: event.name,
      rotten,
    }

    const idx = competitionInstances.value.findIndex(i => i.instanceId === event.id)
    if (idx >= 0) {
      competitionInstances.value.splice(idx, 1, instance)
    } else {
      competitionInstances.value.push(instance)
    }
    return instance
  }

  // Apply persisted server progress onto a freshly built instance. Individual
  // Serien are marked done from completedSerien (so a reload mid-Passe resumes
  // at the right Serie); a Passe falls back to the per-Passe completed flag if
  // the finer-grained list is absent.
  const _hydrateProgress = (instanceId, progress) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    if (!inst || !progress?.groups) return
    for (const gp of progress.groups) {
      const rotte = inst.rotten.find(r => r.rotteId === gp.groupId)
      if (!rotte) continue
      const completions = gp.completions ?? []
      const doneSerien = new Set((gp.completedSerien ?? []).map(c => `${c.passeIndex}:${c.serieId}`))
      let firstOpen = -1
      rotte.phases.forEach((phase, idx) => {
        const passeDone = completions[idx]?.completed === true
        let allBlocksDone = phase.blocks.length > 0
        phase.blocks.forEach(b => {
          if (passeDone || doneSerien.has(`${idx}:${b.serieId}`)) {
            if (b.status !== 'done') { b.status = 'done'; b.completedAt = b.completedAt ?? Date.now() }
          } else {
            allBlocksDone = false
          }
        })
        if (allBlocksDone) {
          phase.status = 'done'
        } else if (firstOpen === -1) {
          firstOpen = idx
        }
      })
      if (firstOpen === -1) {
        rotte.currentPhaseIndex = Math.max(0, rotte.phases.length - 1)
        rotte.status = 'done'
      } else {
        rotte.currentPhaseIndex = firstOpen
        rotte.phases[firstOpen].status = 'active'
      }
    }
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

  // ── Passe management ──────────────────────────────────────────────────────

  const addPasseToEvent = async (eventId, passeId) => {
    const ev = getEvent(eventId)
    if (!ev) return
    const passe = await wettkampfApi.addPasse(eventId, passeId)
    ev.passen = [...(ev.passen ?? []), passe]
  }

  const removePasseFromEvent = async (eventId, passeId) => {
    await wettkampfApi.removePasse(eventId, passeId)
    const ev = getEvent(eventId)
    if (ev) ev.passen = (ev.passen ?? []).filter(p => p.id !== passeId)
  }

  // ── Runtime: rotte assignment ─────────────────────────────────────────────

  const assignRotteToRange = (instanceId, rotteId, rangeId) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    if (!inst) return
    const rotte = inst.rotten.find(r => r.rotteId === rotteId)
    if (!rotte) return
    rotte.assignedRangeId = rangeId
    rotte.status = 'active'
  }

  const unassignRotte = (instanceId, rotteId) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    if (!inst) return
    const rotte = inst.rotten.find(r => r.rotteId === rotteId)
    if (!rotte) return
    rotte.assignedRangeId = null
    rotte.status = 'paused'
  }

  // ── Runtime: block lifecycle ──────────────────────────────────────────────

  const markBlockInProgress = (instanceId, blockId, rotteId) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    if (!inst) return
    const rotte = inst.rotten?.find(r => r.rotteId === rotteId)
    const phase = rotte?.phases[rotte.currentPhaseIndex]
    const block = phase?.blocks.find(b => b.blockId === blockId)
    if (block && block.status === 'pending') block.status = 'in_progress'
  }

  const markBlockDone = async (instanceId, blockId, playerResults, rotteId) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    if (!inst) return
    const passeIndex = inst.rotten?.find(r => r.rotteId === rotteId)?.currentPhaseIndex ?? 0
    await wettkampfApi.completeSerie(inst.sessionId, rotteId, blockId, passeIndex, null, playerResults).catch(console.error)
    _completeCompetitionBlock(inst, blockId, playerResults, rotteId)
  }

  const _completeCompetitionBlock = (inst, blockId, playerResults, rotteId) => {
    const rotte = inst.rotten?.find(r => r.rotteId === rotteId)
    if (!rotte) return
    const phase = rotte.phases[rotte.currentPhaseIndex]
    if (!phase) return
    const block = phase.blocks.find(b => b.blockId === blockId)
    if (!block) return
    block.status = 'done'
    block.completedAt = Date.now()
    block.result = { playerResults }

    if (phase.blocks.every(b => b.status === 'done')) {
      phase.status = 'done'
      const nextIndex = rotte.currentPhaseIndex + 1
      if (nextIndex < rotte.phases.length) {
        rotte.currentPhaseIndex = nextIndex
        rotte.phases[nextIndex].status = 'active'
      } else {
        rotte.status = 'done'
        if (inst.rotten.every(r => r.status === 'done')) {
          completedCompetitionInstances.value.push({ ...inst, completedAt: Date.now() })
          competitionInstances.value = competitionInstances.value.filter(i => i.instanceId !== inst.instanceId)
        }
      }
    }
  }

  // ── Runtime: queries ──────────────────────────────────────────────────────

  const getActiveCompetitionRotten = () => {
    const result = []
    for (const inst of competitionInstances.value) {
      for (const rotte of (inst.rotten ?? [])) {
        if (rotte.status === 'done') continue
        const phase = rotte.phases[rotte.currentPhaseIndex]
        if (!phase) continue
        // Only surface Serien that still need shooting — completed ones must not
        // reappear in the flyout.
        const blocks = phase.blocks.filter(b => b.status !== 'done')
        if (blocks.length === 0) continue
        result.push({
          instanceId: inst.instanceId,
          instanceName: inst.templateName,
          rotteId: rotte.rotteId,
          rotteName: rotte.name,
          passeName: phase.passeName,
          players: rotte.players,
          blocks,
        })
      }
    }
    return result
  }

  const getBlocksForRange = (rangeId) => {
    const result = []
    for (const inst of competitionInstances.value) {
      for (const rotte of (inst.rotten ?? [])) {
        if (rotte.assignedRangeId !== rangeId || rotte.status !== 'active') continue
        const phase = rotte.phases[rotte.currentPhaseIndex]
        if (!phase) continue
        for (const block of phase.blocks) {
          if (block.rangeId === rangeId && block.status !== 'done') {
            result.push({
              ...block,
              instanceId: inst.instanceId,
              templateName: inst.templateName,
              players: rotte.players,
              instanceType: 'competition',
              rotteName: rotte.name,
              rotteId: rotte.rotteId,
            })
          }
        }
      }
    }
    return result
  }

  // ── Private ───────────────────────────────────────────────────────────────

  const _replaceEvent = (updated) => {
    events.value = events.value.map(e => e.id === updated.id ? updated : e)
  }

  // ── Stechen (tiebreaker) ──────────────────────────────────────────────────

  const loadTies = async (sessionId) => {
    const res = await tiebreakerApi.getTies(sessionId)
    tiesBySession.value = { ...tiesBySession.value, [sessionId]: res }
    return res
  }

  const startStechen = async (sessionId, payload) => {
    const created = await tiebreakerApi.startTiebreaker(sessionId, payload)
    await loadTies(sessionId)
    return created
  }

  const submitStechenResults = async (sessionId, tiebreakerId, results) => {
    const updated = await tiebreakerApi.submitTiebreakerResults(sessionId, tiebreakerId, results)
    tiesBySession.value = { ...tiesBySession.value, [sessionId]: updated }
    return updated
  }

  // Finish a competition. Returns { completed: true } on success, or
  // { completed: false, unresolvedTies } when the backend's finish guard (409) blocks it.
  const finishEvent = async (sessionId, force = false) => {
    try {
      await wettkampfApi.patchStatus(sessionId, 'completed', { force })
      const updated = await wettkampfApi.getSession(sessionId)
      _replaceEvent(updated)
      return { completed: true }
    } catch (e) {
      if (e.status === 409 && e.body?.unresolvedTies) {
        return { completed: false, unresolvedTies: e.body.unresolvedTies }
      }
      throw e
    }
  }

  return {
    // Planning state
    events, loading, error,
    planningEvents, activeEvents, completedEvents, getEvent,
    loadEvents,
    createEvent, openEvent, startEvent, stopEvent, stopCompetition, deleteEvent,
    addRotte, removeRotte, renameRotte,
    addPlayer, removePlayer, togglePlayerPaid,
    addPasseToEvent, removePasseFromEvent,
    // Runtime state
    competitionInstances, completedCompetitionInstances,
    getCompetitionInstance, initCompetitionInstance,
    assignRotteToRange, unassignRotte,
    markBlockInProgress, markBlockDone,
    getActiveCompetitionRotten, getBlocksForRange,
    // Stechen (tiebreaker)
    tiesBySession,
    loadTies, startStechen, submitStechenResults, finishEvent,
  }
})

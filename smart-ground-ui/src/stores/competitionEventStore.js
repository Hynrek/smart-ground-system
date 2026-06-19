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

  // ── Completed results (server-backed, survives reload) ────────────────────
  // Keyed by sessionId. Unlike completedCompetitionInstances (which is only
  // populated when a competition finishes in the current session), this is
  // fetched from the backend so a reloaded COMPLETED competition still shows
  // its final Rangliste.
  const completedResultsBySession = ref({})

  // ── Stechen (tiebreaker) state ────────────────────────────────────────────
  const tiesBySession = ref({})

  // ── Computed ──────────────────────────────────────────────────────────────

  const planningEvents  = computed(() => events.value.filter(e => e.status?.toUpperCase() === 'SETUP'))
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
      releasedPasseIndex: 0,
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
    inst.releasedPasseIndex = progress.releasedPasseIndex ?? 0
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
      // Gate display/play to the admin-released Passe (all Rotten share it).
      rotte.currentPhaseIndex = inst.releasedPasseIndex
      if (firstOpen === -1) {
        rotte.status = 'done'
      } else {
        const releasedPhase = rotte.phases[inst.releasedPasseIndex]
        if (releasedPhase && releasedPhase.status !== 'done') releasedPhase.status = 'active'
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
    const phaseIdx = inst.releasedPasseIndex ?? 0
    const phase = rotte.phases[phaseIdx]
    if (!phase) return
    const block = phase.blocks.find(b => b.blockId === blockId)
    if (!block) return
    block.status = 'done'
    block.completedAt = Date.now()
    block.result = { playerResults }

    // A Rotte never advances past the released Passe on its own — the admin gates
    // the next Passe. Only mark the phase done and detect overall completion.
    if (phase.blocks.every(b => b.status === 'done')) phase.status = 'done'
    const allDone = inst.rotten.every(r => r.phases.every(p => p.blocks.every(b => b.status === 'done')))
    if (allDone) {
      rotte.status = 'done'
      completedCompetitionInstances.value.push({ ...inst, completedAt: Date.now() })
      competitionInstances.value = competitionInstances.value.filter(i => i.instanceId !== inst.instanceId)
    }
  }

  // ── Runtime: admin Passe gate ─────────────────────────────────────────────

  // Release the next Passe for all Rotten (admin gate). The backend guards that the
  // current released Passe is fully complete; on success it returns the new index.
  const releaseNextPasse = async (instanceId) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    const sessionId = inst?.sessionId ?? instanceId
    const res = await wettkampfApi.releaseNextPasse(sessionId)
    if (inst) {
      inst.releasedPasseIndex = res?.releasedPasseIndex ?? (inst.releasedPasseIndex + 1)
      for (const r of inst.rotten) {
        r.currentPhaseIndex = inst.releasedPasseIndex
        const p = r.phases[inst.releasedPasseIndex]
        if (p && p.status !== 'done') p.status = 'active'
      }
    }
    return res
  }

  // True when every Rotte has completed every Serie of the released Passe.
  const isReleasedPasseComplete = (instanceId) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    if (!inst || inst.rotten.length === 0) return false
    const idx = inst.releasedPasseIndex ?? 0
    return inst.rotten.every(r => {
      const blocks = r.phases[idx]?.blocks ?? []
      return blocks.length > 0 && blocks.every(b => b.status === 'done')
    })
  }

  // A Rotte that finished the released Passe but the competition isn't over → waiting.
  const isRotteWaitingForPasse = (inst, rotte) => {
    const idx = inst?.releasedPasseIndex ?? 0
    const blocks = rotte?.phases?.[idx]?.blocks ?? []
    return blocks.length > 0 && blocks.every(b => b.status === 'done') && rotte.status !== 'done'
  }

  // ── Runtime: queries ──────────────────────────────────────────────────────

  // When a rangeId is given, a Wettkampf-Passe is locked to its range: only the
  // Serien bound to that range surface there (the same way training/Passen blocks
  // are range-scoped via getBlocksForRange). Omitting rangeId returns every open
  // Serie regardless of range.
  const getActiveCompetitionRotten = (rangeId = null) => {
    const result = []
    for (const inst of competitionInstances.value) {
      for (const rotte of (inst.rotten ?? [])) {
        if (rotte.status === 'done') continue
        const phase = rotte.phases[inst.releasedPasseIndex ?? 0]
        if (!phase) continue
        // Only surface Serien that still need shooting — completed ones must not
        // reappear in the flyout — and, when scoped, only those on this range.
        const blocks = phase.blocks.filter(
          b => b.status !== 'done' && (rangeId == null || b.rangeId === rangeId),
        )
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
        const phase = rotte.phases[inst.releasedPasseIndex ?? 0]
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

  // ── Completed results ───────────────────────────────────────────────────────

  // Fetch the final standings for a COMPLETED competition. Ranking and tie
  // resolution are computed server-side (leaderboard); Rotte names and the
  // per-player detail source (playerResults) come from the full session.
  const loadCompletedResults = async (sessionId) => {
    loading.value = true
    error.value = null
    try {
      const [leaderboard, session, serieResults] = await Promise.all([
        wettkampfApi.getLeaderboard(sessionId),
        wettkampfApi.getSession(sessionId),
        wettkampfApi.getSerieResults(sessionId),
      ])
      const memberMeta = new Map()
      for (const group of (session.groups ?? [])) {
        for (const member of (group.members ?? [])) {
          memberMeta.set(member.id, { rotteName: group.name, userId: member.userId ?? null })
        }
      }
      const standings = (leaderboard.playerScores ?? []).map(p => ({
        rank: p.rank,
        playerId: p.playerId,
        userId: memberMeta.get(p.playerId)?.userId ?? null,
        displayName: p.displayName,
        rotteName: memberMeta.get(p.playerId)?.rotteName ?? null,
        totalScore: p.totalScore,
        maxScore: p.maxScore,
        tied: p.tied ?? false,
        tieResolvedByStechen: p.tieResolvedByStechen ?? false,
      }))
      // Serie definitions (range, name, step position letters) for the per-Serie
      // step-chip view. Keyed by serieId; sortIndex preserves passe→serie order.
      const serieDefs = {}
      let serieSortIndex = 0
      for (const passe of (session.passen ?? [])) {
        for (const serie of (passe.serien ?? [])) {
          serieDefs[serie.id] = {
            rangeName: serie.rangeName ?? null,
            serieName: serie.alias ?? serie.name ?? 'Serie',
            sortIndex: serieSortIndex++,
            steps: (serie.steps ?? []).map(s => ({
              type: s.type ?? null,
              letter: s.letter ?? null,
              letter1: s.letter1 ?? null,
              letter2: s.letter2 ?? null,
            })),
          }
        }
      }
      completedResultsBySession.value = {
        ...completedResultsBySession.value,
        [sessionId]: {
          standings,
          serieResults: serieResults ?? [],
          serieDefs,
          completedAt: session.completedAt ?? null,
        },
      }
    } catch (e) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  // Correct a completed Serie's results (admin, PRE_COMPLETE), then refresh the
  // cached serie-results/standings so the corrected scores + Rangliste update.
  const correctSerieResult = async (sessionId, groupId, serieId, passeIndex, playerResults) => {
    await wettkampfApi.correctSerieResult(sessionId, groupId, serieId, passeIndex, playerResults)
    await loadCompletedResults(sessionId)
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
    createEvent, startEvent, stopEvent, stopCompetition, deleteEvent,
    addRotte, removeRotte, renameRotte,
    addPlayer, removePlayer, togglePlayerPaid,
    addPasseToEvent, removePasseFromEvent,
    // Runtime state
    competitionInstances, completedCompetitionInstances,
    completedResultsBySession, loadCompletedResults, correctSerieResult,
    getCompetitionInstance, initCompetitionInstance,
    assignRotteToRange, unassignRotte,
    markBlockInProgress, markBlockDone,
    releaseNextPasse, isReleasedPasseComplete, isRotteWaitingForPasse,
    getActiveCompetitionRotten, getBlocksForRange,
    // Stechen (tiebreaker)
    tiesBySession,
    loadTies, startStechen, submitStechenResults, finishEvent,
  }
})

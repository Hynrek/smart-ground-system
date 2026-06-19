import { computed, unref } from 'vue'
import { storeToRefs } from 'pinia'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'

/**
 * Reactive access to a COMPLETED competition's final standings.
 *
 * Standings (rank, totals, tie/Stechen flags) come from the server-backed
 * `completedResultsBySession` cache. Per-shooter detail is derived from the
 * `programResults` blob and degrades gracefully to bare totals when that blob
 * is absent or unparseable.
 *
 * @param {import('vue').Ref<string>|string} sessionId
 */
export function useCompletedResults(sessionId) {
  const store = useCompetitionEventStore()
  const { loading, error } = storeToRefs(store)

  const resolveId = () => (typeof sessionId === 'string' ? sessionId : unref(sessionId)) ?? null

  const entry = computed(() => {
    const id = resolveId()
    return id ? (store.completedResultsBySession[id] ?? null) : null
  })

  const standings = computed(() => entry.value?.standings ?? [])
  const completedAt = computed(() => entry.value?.completedAt ?? null)

  const load = () => {
    const id = resolveId()
    return id ? store.loadCompletedResults(id) : Promise.resolve()
  }

  // Per-Passe totals for a player, aggregated from the persisted serie-results
  // (each row carries the player's per-Serie totalPoints/maxPoints). getSession's
  // playerResults is empty in practice, so serie-results is the reliable source.
  // Falls back to the leaderboard standing's grand total when no per-Serie data.
  const getPlayerDetail = (playerId) => {
    const serieResults = entry.value?.serieResults ?? []
    const byPasse = new Map()
    for (const sr of serieResults) {
      const pe = (sr.results ?? []).find(r => r.playerId === playerId)
      if (!pe) continue
      const idx = sr.passeIndex ?? 0
      const agg = byPasse.get(idx) ?? { passeIndex: idx, totalPoints: 0, maxPoints: 0 }
      agg.totalPoints += pe.totalPoints ?? 0
      agg.maxPoints += pe.maxPoints ?? 0
      byPasse.set(idx, agg)
    }

    const passen = [...byPasse.values()]
      .sort((a, b) => a.passeIndex - b.passeIndex)
      .map(p => ({ label: `Passe ${p.passeIndex + 1}`, totalPoints: p.totalPoints, maxPoints: p.maxPoints }))

    if (passen.length > 0) {
      return {
        total: passen.reduce((s, p) => s + p.totalPoints, 0),
        max: passen.reduce((s, p) => s + p.maxPoints, 0),
        passen,
      }
    }

    const standing = (entry.value?.standings ?? []).find(s => s.playerId === playerId)
    return { total: standing?.totalScore ?? 0, max: standing?.maxScore ?? 0, passen: [] }
  }

  // Per-Serie step groups for a player, joined to the serie definitions (range,
  // name, per-step position letters). Ordered by the serie's sortIndex. Degrades
  // to letter-less steps when a serie definition is absent.
  const getPlayerSerien = (playerId) => {
    const serieResults = entry.value?.serieResults ?? []
    const defs = entry.value?.serieDefs ?? {}
    const groups = []
    for (const sr of serieResults) {
      const playerEntry = (sr.results ?? []).find(r => r.playerId === playerId)
      const states = playerEntry?.stepStates ?? []
      if (states.length === 0) continue
      const def = defs[sr.serieId] ?? null
      const steps = states.map(s => {
        const ds = def?.steps?.[s.stepIndex] ?? null
        return {
          stepIndex: s.stepIndex ?? 0,
          type: ds?.type ?? null,
          letter: ds?.letter ?? null,
          letter1: ds?.letter1 ?? null,
          letter2: ds?.letter2 ?? null,
          state: s.state,
        }
      })
      groups.push({
        key: `${sr.passeIndex ?? 0}:${sr.serieId}`,
        passeIndex: sr.passeIndex ?? 0,
        rangeName: def?.rangeName ?? null,
        serieName: def?.serieName ?? 'Serie',
        sortIndex: def?.sortIndex ?? (sr.passeIndex ?? 0),
        steps,
      })
    }
    return groups.sort((a, b) => a.sortIndex - b.sortIndex)
  }

  // ── PRE_COMPLETE correction support ─────────────────────────────────────────

  const STEP_DEDUCTION = { 'failed-both': 2, 'failed-a': 1, 'failed-b': 1 }
  const earnedFor = (state, pointValue) =>
    state === 'pending' ? 0 : Math.max(0, pointValue - (STEP_DEDUCTION[state] ?? 0))

  // Recompute a Serie's per-player totals from its step states (same rule as the kiosk).
  const recomputeSerieTotals = (steps) => steps.reduce(
    (acc, s) => ({
      totalPoints: acc.totalPoints + earnedFor(s.state, s.pointValue ?? 0),
      maxPoints: acc.maxPoints + (s.pointValue ?? 0),
    }),
    { totalPoints: 0, maxPoints: 0 },
  )

  // Per-Serie, per-player editable view for one Passe (PRE_COMPLETE correction).
  const getCorrectionData = (passeIndex) => {
    const serieResults = (entry.value?.serieResults ?? []).filter(sr => (sr.passeIndex ?? 0) === passeIndex)
    const defs = entry.value?.serieDefs ?? {}
    const standingsById = new Map((entry.value?.standings ?? []).map(s => [s.playerId, s.displayName]))
    const serien = serieResults
      .map(sr => {
        const def = defs[sr.serieId] ?? null
        const players = (sr.results ?? []).map(r => ({
          playerId: r.playerId,
          displayName: r.displayName ?? standingsById.get(r.playerId) ?? null,
          steps: (r.stepStates ?? []).map(s => ({
            stepIndex: s.stepIndex ?? 0,
            state: s.state,
            pointValue: s.pointValue ?? 0,
            pointsEarned: s.pointsEarned ?? 0,
          })),
        }))
        return {
          groupId: sr.groupId,
          serieId: sr.serieId,
          rangeName: def?.rangeName ?? null,
          serieName: def?.serieName ?? 'Serie',
          sortIndex: def?.sortIndex ?? 0,
          steps: (def?.steps ?? []).map((d, i) => ({
            stepIndex: i, type: d.type, letter: d.letter, letter1: d.letter1, letter2: d.letter2,
          })),
          players,
        }
      })
      .sort((a, b) => a.sortIndex - b.sortIndex)
    return { passeIndex, serien }
  }

  return {
    standings, completedAt, loading, error, load, getPlayerDetail, getPlayerSerien,
    recomputeSerieTotals, getCorrectionData,
  }
}

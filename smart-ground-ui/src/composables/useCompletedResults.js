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

  // Per-step scorecard for a player, grouped by Passe. Built from the persisted
  // serie-results (each carries the raw stepStates). Returns [] when no per-step
  // data is available for the player.
  const getPlayerSteps = (playerId) => {
    const serieResults = entry.value?.serieResults ?? []
    const byPasse = new Map()
    for (const sr of serieResults) {
      const playerEntry = (sr.results ?? []).find(r => r.playerId === playerId)
      const steps = playerEntry?.stepStates ?? []
      if (steps.length === 0) continue
      const idx = sr.passeIndex ?? 0
      const group = byPasse.get(idx) ?? { passeIndex: idx, steps: [] }
      for (const s of steps) {
        group.steps.push({
          stepIndex: s.stepIndex ?? 0,
          state: s.state,
          pointsEarned: s.pointsEarned ?? 0,
          pointValue: s.pointValue ?? 0,
        })
      }
      byPasse.set(idx, group)
    }
    return [...byPasse.values()]
      .sort((a, b) => a.passeIndex - b.passeIndex)
      .map(g => ({ passeIndex: g.passeIndex, label: `Passe ${g.passeIndex + 1}`, steps: g.steps }))
  }

  return { standings, completedAt, loading, error, load, getPlayerDetail, getPlayerSteps }
}

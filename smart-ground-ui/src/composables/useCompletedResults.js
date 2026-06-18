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

  // Aggregate a player's per-Serie result entries into per-Passe totals.
  // Competition programResults is a flat JSON array of
  // { passeIndex, serieId, totalPoints, maxPoints, completedAt }.
  const getPlayerDetail = (playerId) => {
    const pr = entry.value?.playerResults?.find(r => r.playerId === playerId) ?? null
    const fallback = { total: pr?.totalScore ?? 0, max: pr?.maxScore ?? 0, passen: [] }

    if (!pr?.programResults) return fallback

    let entries
    try {
      entries = JSON.parse(pr.programResults)
    } catch {
      return fallback
    }
    if (!Array.isArray(entries)) return fallback

    const byPasse = new Map()
    for (const e of entries) {
      const idx = e.passeIndex ?? 0
      const agg = byPasse.get(idx) ?? { passeIndex: idx, totalPoints: 0, maxPoints: 0 }
      agg.totalPoints += e.totalPoints ?? 0
      agg.maxPoints += e.maxPoints ?? 0
      byPasse.set(idx, agg)
    }

    const passen = [...byPasse.values()]
      .sort((a, b) => a.passeIndex - b.passeIndex)
      .map(p => ({ label: `Passe ${p.passeIndex + 1}`, totalPoints: p.totalPoints, maxPoints: p.maxPoints }))

    return {
      total: passen.reduce((s, p) => s + p.totalPoints, 0),
      max: passen.reduce((s, p) => s + p.maxPoints, 0),
      passen,
    }
  }

  return { standings, completedAt, loading, error, load, getPlayerDetail }
}

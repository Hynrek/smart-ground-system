import { computed } from 'vue'

export function useCompetitionProgress(instance) {
  const _rotten = computed(() => instance.value?.rotten ?? [])

  // The active/playable phase follows the admin-released Passe when the instance
  // provides one; otherwise it falls back to the lowest phase not done by all Rotten.
  const activePhaseIndex = computed(() => {
    if (_rotten.value.length === 0) return 0
    const released = instance.value?.releasedPasseIndex
    if (Number.isInteger(released)) return released
    const phaseCount = _rotten.value[0].phases?.length ?? 0
    for (let i = 0; i < phaseCount; i++) {
      if (!_rotten.value.every(r => r.phases[i]?.status === 'done')) return i
    }
    return Math.max(0, phaseCount - 1)
  })

  // All rotten share the same phase structure; rotten[0] is used as the canonical template.
  const passenProgress = computed(() => {
    if (_rotten.value.length === 0) return []
    const activeIdx = activePhaseIndex.value
    return (_rotten.value[0].phases ?? []).map((phase, i) => {
      const allDone = _rotten.value.every(r => r.phases[i]?.status === 'done')
      const status = allDone ? 'fertig' : i === activeIdx ? 'aktiv' : 'offen'
      return { phaseIndex: i, passeName: phase.passeName, status }
    })
  })

  const serieCards = computed(() => {
    if (_rotten.value.length === 0) return []
    const phaseIdx = activePhaseIndex.value
    const referenceBlocks = _rotten.value[0].phases?.[phaseIdx]?.blocks ?? []
    return referenceBlocks.map((block, j) => ({
      serieAlias: block.serieAlias,
      rotteRows: _rotten.value.map(rotte => ({
        rotteId: rotte.rotteId,
        rotteName: rotte.name,
        status: rotte.phases?.[phaseIdx]?.blocks?.[j]?.status ?? 'pending',
      })),
    }))
  })

  const leaderboard = computed(() => {
    const totals = new Map()
    for (const rotte of _rotten.value) {
      for (const phase of (rotte.phases ?? [])) {
        for (const block of (phase.blocks ?? [])) {
          if (block.status !== 'done' || !block.result?.playerResults) continue
          for (const pr of block.result.playerResults) {
            const entry = totals.get(pr.playerId) ?? {
              playerId: pr.playerId,
              displayName: pr.displayName,
              totalPoints: 0,
              maxPoints: 0,
            }
            entry.totalPoints += pr.totalPoints
            entry.maxPoints += pr.maxPoints
            totals.set(pr.playerId, entry)
          }
        }
      }
    }
    return [...totals.values()].sort((a, b) => b.totalPoints - a.totalPoints)
  })

  return { passenProgress, activePhaseIndex, serieCards, leaderboard }
}

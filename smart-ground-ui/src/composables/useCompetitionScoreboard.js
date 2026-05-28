import { computed } from 'vue'
import { useActivePasseStore } from '@/stores/activePasseStore.js'

export function useCompetitionScoreboard(instanceId) {
  // instanceId can be a ref or a plain string
  const activePasseStore = useActivePasseStore()

  const rankedPlayers = computed(() => {
    const id = typeof instanceId === 'string' ? instanceId : instanceId.value
    const inst =
      activePasseStore.activeInstances.find((i) => i.instanceId === id) ??
      activePasseStore.completedInstances.find((i) => i.instanceId === id)

    if (!inst || inst.type !== 'competition') return []

    // Accumulate points per player across all Rotten and all phases
    const playerMap = new Map() // playerId → { playerId, displayName, rotteName, totalPoints }

    for (const rotte of inst.rotten) {
      for (const phase of rotte.phases) {
        for (const block of phase.blocks) {
          if (block.status !== 'done' || !block.result?.playerResults) continue
          for (const pr of block.result.playerResults) {
            const existing = playerMap.get(pr.playerId)
            if (existing) {
              existing.totalPoints += pr.totalPoints ?? 0
            } else {
              // Find player in rotte.players
              const player = rotte.players.find((p) => p.id === pr.playerId)
              playerMap.set(pr.playerId, {
                playerId: pr.playerId,
                displayName: player?.displayName ?? pr.playerId,
                rotteName: rotte.name,
                totalPoints: pr.totalPoints ?? 0,
              })
            }
          }
        }
      }
    }

    // Sort descending by totalPoints
    return [...playerMap.values()].sort((a, b) => b.totalPoints - a.totalPoints)
  })

  return { rankedPlayers }
}

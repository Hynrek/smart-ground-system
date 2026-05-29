<!-- src/components/competition/CompletedResultsPanel.vue -->
<template>
  <div class="panel-content">
    <h2 class="panel-title">Ergebnisse</h2>

    <div
      v-for="rotte in completedRotten"
      :key="rotte.rotteId"
      class="rotte-result-card"
    >
      <div class="rr-header">
        <span class="rr-name">{{ rotte.name }}</span>
      </div>

      <div
        v-for="player in rotte.players"
        :key="player.id"
        class="player-result-row"
      >
        <span class="pr-name">{{ player.displayName || '–' }}</span>
        <div class="pr-phases">
          <div v-for="phase in rotte.phases" :key="phase.phaseIndex" class="phase-result">
            <span class="phase-label">{{ phase.passeName }}</span>
            <span class="phase-pts">
              {{ phasePoints(phase, player.id) }}/{{ phaseMaxPoints(phase, player.id) }}
            </span>
          </div>
        </div>
        <span class="pr-total">{{ totalPoints(rotte, player.id) }}/{{ maxPoints(rotte, player.id) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useActivePasseStore } from '@/stores/activePasseStore.js'

const props = defineProps({ event: { type: Object, required: true } })
const activePasseStore = useActivePasseStore()

const completedInstance = computed(() =>
  activePasseStore.completedInstances.find(i => i.instanceId === props.event.activeInstanceId)
    ?? activePasseStore.completedInstances.findLast?.(i =>
        props.event.rotten.some(r => r.rotteId === i.rotten?.[0]?.rotteId)
      )
)

const completedRotten = computed(() => completedInstance.value?.rotten ?? props.event.rotten)

const phasePoints = (phase, playerId) => {
  let pts = 0
  for (const block of phase.blocks ?? []) {
    const pr = block.result?.playerResults?.find(r => r.playerId === playerId)
    if (pr) pts += pr.totalPoints ?? 0
  }
  return pts
}

const phaseMaxPoints = (phase, playerId) => {
  let pts = 0
  for (const block of phase.blocks ?? []) {
    const pr = block.result?.playerResults?.find(r => r.playerId === playerId)
    if (pr) pts += pr.maxPoints ?? 0
  }
  return pts
}

const totalPoints = (rotte, playerId) =>
  (rotte.phases ?? []).reduce((s, phase) => s + phasePoints(phase, playerId), 0)

const maxPoints = (rotte, playerId) =>
  (rotte.phases ?? []).reduce((s, phase) => s + phaseMaxPoints(phase, playerId), 0)
</script>

<style scoped>
.panel-content { flex: 1; overflow-y: auto; padding: 24px 28px 40px; display: flex; flex-direction: column; gap: 20px; }

.panel-title { font-size: 18px; font-weight: 700; margin: 0; }

.rotte-result-card {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 14px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px;
}

.rr-header { padding-bottom: 8px; border-bottom: 1px solid rgba(79,195,247,0.12); }

.rr-name { font-size: 13px; font-weight: 700; color: rgba(79,195,247,0.8); text-transform: uppercase; letter-spacing: 0.4px; }

.player-result-row {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,0.05);
}
.player-result-row:last-child { border-bottom: none; }

.pr-name { font-size: 13px; font-weight: 600; min-width: 130px; color: rgba(255,255,255,0.8); }

.pr-phases { flex: 1; display: flex; flex-wrap: wrap; gap: 6px; }

.phase-result {
  display: flex; gap: 4px; font-size: 11px;
  background: rgba(255,255,255,0.04); border-radius: 6px; padding: 2px 8px;
}

.phase-label { color: rgba(255,255,255,0.3); }

.phase-pts { color: rgba(255,255,255,0.6); font-weight: 600; }

.pr-total { font-size: 13px; font-weight: 700; color: rgba(79,195,247,0.9); white-space: nowrap; }
</style>

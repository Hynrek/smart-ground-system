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
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'

const props = defineProps({ event: { type: Object, required: true } })
const competitionEventStore = useCompetitionEventStore()

const completedInstance = computed(() =>
  competitionEventStore.completedCompetitionInstances.find(i => i.instanceId === props.event.id)
)

const completedRotten = computed(() => completedInstance.value?.rotten ?? [])

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

.panel-title { font-size: 18px; font-weight: 700; color: #1a1a2e; margin: 0; }

.rotte-result-card {
  background: #fff; border: 1px solid #e2e8f0;
  border-radius: 14px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.rr-header { padding-bottom: 8px; border-bottom: 1px solid #bee3f8; }

.rr-name { font-size: 13px; font-weight: 700; color: #0288d1; text-transform: uppercase; letter-spacing: 0.4px; }

.player-result-row {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 6px 0; border-bottom: 1px solid #f7fafc;
}
.player-result-row:last-child { border-bottom: none; }

.pr-name { font-size: 13px; font-weight: 600; min-width: 130px; color: #2d3748; }

.pr-phases { flex: 1; display: flex; flex-wrap: wrap; gap: 6px; }

.phase-result {
  display: flex; gap: 4px; font-size: 11px;
  background: #f7fafc; border-radius: 6px; padding: 2px 8px;
}

.phase-label { color: #a0aec0; }

.phase-pts { color: #4a5568; font-weight: 600; }

.pr-total { font-size: 13px; font-weight: 700; color: #0288d1; white-space: nowrap; }
</style>

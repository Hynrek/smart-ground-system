<!-- src/components/competition/ActiveCompetitionPanel.vue -->
<template>
  <div class="panel-content">
    <div class="panel-header">
      <h2 class="panel-title">Laufender Wettkampf</h2>
      <button class="stop-btn" @click="emit('stop')">
        <Icons icon="x" :size="13" color="rgba(252,129,129,0.8)" />
        Wettkampf abbrechen
      </button>
    </div>

    <div class="rotte-list">
      <div
        v-for="rotte in activeRotten"
        :key="rotte.rotteId"
        class="rotte-progress-card"
      >
        <div class="rp-header">
          <div class="rp-dot" :class="`dot-${rotte.status}`" />
          <span class="rp-name">{{ rotte.name }}</span>
          <span class="rp-badge" :class="`badge-${rotte.status}`">{{ rotteBadge(rotte.status) }}</span>
        </div>

        <div class="rp-players">
          <span v-for="p in rotte.players" :key="p.id" class="rp-player">{{ p.displayName || '–' }}</span>
        </div>

        <div class="rp-phase">
          {{ currentPhaseName(rotte) }}
        </div>

        <div class="rp-range-row">
          <span class="rp-range-label">Platz:</span>
          <select
            class="rp-range-select"
            :value="rotte.assignedRangeId ?? ''"
            @change="onRangeChange(rotte, $event.target.value)"
          >
            <option value="">— Kein Platz —</option>
            <option v-for="range in rangeStore.ranges" :key="range.id" :value="range.id">
              {{ range.name }}
            </option>
          </select>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import Icons from '@/components/Icons.vue'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import { useRangeStore } from '@/stores/rangeStore.js'

const props = defineProps({ event: { type: Object, required: true } })
const emit = defineEmits(['stop'])

const activePasseStore = useActivePasseStore()
const rangeStore = useRangeStore()

const liveInstance = computed(() =>
  activePasseStore.activeInstances.find(i => i.instanceId === props.event.activeInstanceId)
)

const activeRotten = computed(() => liveInstance.value?.rotten ?? [])

const currentPhaseName = (rotte) => {
  const phase = rotte.phases?.[rotte.currentPhaseIndex]
  return phase?.passeName ?? '–'
}

const rotteBadge = (status) => {
  const map = { active: 'Aktiv', waiting: 'Wartend', done: 'Fertig', paused: 'Pausiert' }
  return map[status] ?? status
}

const onRangeChange = (rotte, rangeId) => {
  if (!liveInstance.value) return
  if (rangeId) {
    activePasseStore.assignRotteToRange(liveInstance.value.instanceId, rotte.rotteId, rangeId)
  } else {
    activePasseStore.unassignRotte(liveInstance.value.instanceId, rotte.rotteId)
  }
}
</script>

<style scoped>
.panel-content { flex: 1; overflow-y: auto; padding: 24px 28px 40px; display: flex; flex-direction: column; gap: 20px; }

.panel-header { display: flex; align-items: center; justify-content: space-between; }

.panel-title { font-size: 18px; font-weight: 700; color: #1a1a2e; margin: 0; }

.stop-btn {
  display: flex; align-items: center; gap: 6px;
  background: #fff5f5; border: 1px solid #fed7d7;
  border-radius: 10px; padding: 8px 14px;
  color: #c53030; font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.stop-btn:hover { background: #fed7d7; }

.rotte-list { display: flex; flex-direction: column; gap: 12px; }

.rotte-progress-card {
  background: #fff; border: 1px solid #e2e8f0;
  border-radius: 14px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.rp-header { display: flex; align-items: center; gap: 10px; }

.rp-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.rp-dot.dot-active { background: #f6ad55; }
.rp-dot.dot-waiting { background: #cbd5e0; }
.rp-dot.dot-done { background: #48bb78; }
.rp-dot.dot-paused { background: #a0aec0; }

.rp-name { font-size: 14px; font-weight: 700; color: #1a1a2e; flex: 1; }

.rp-badge { font-size: 10px; font-weight: 700; border-radius: 6px; padding: 2px 8px; }
.rp-badge.badge-active { background: #fffaf0; color: #dd6b20; }
.rp-badge.badge-waiting { background: #f7fafc; color: #a0aec0; }
.rp-badge.badge-done { background: #f0fff4; color: #276749; }
.rp-badge.badge-paused { background: #f7fafc; color: #a0aec0; }

.rp-players { display: flex; flex-wrap: wrap; gap: 6px; }

.rp-player {
  font-size: 12px; padding: 2px 10px;
  background: #f7fafc; border-radius: 20px; color: #718096;
}

.rp-phase { font-size: 12px; color: #0288d1; }

.rp-range-row { display: flex; align-items: center; gap: 10px; }

.rp-range-label { font-size: 12px; color: #a0aec0; white-space: nowrap; }

.rp-range-select {
  flex: 1; background: #f7fafc; border: 1px solid #e2e8f0;
  border-radius: 8px; color: #2d3748; font-size: 13px; font-family: inherit;
  padding: 6px 10px; outline: none; cursor: pointer;
}
</style>

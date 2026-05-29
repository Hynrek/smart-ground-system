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

.panel-title { font-size: 18px; font-weight: 700; margin: 0; }

.stop-btn {
  display: flex; align-items: center; gap: 6px;
  background: rgba(252,129,129,0.08); border: 1px solid rgba(252,129,129,0.2);
  border-radius: 10px; padding: 8px 14px;
  color: rgba(252,129,129,0.8); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.stop-btn:hover { background: rgba(252,129,129,0.14); }

.rotte-list { display: flex; flex-direction: column; gap: 12px; }

.rotte-progress-card {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08);
  border-radius: 14px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px;
}

.rp-header { display: flex; align-items: center; gap: 10px; }

.rp-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.rp-dot.dot-active { background: rgba(246,173,85,0.8); }
.rp-dot.dot-waiting { background: rgba(255,255,255,0.2); }
.rp-dot.dot-done { background: rgba(72,187,120,0.7); }
.rp-dot.dot-paused { background: rgba(255,255,255,0.25); }

.rp-name { font-size: 14px; font-weight: 700; flex: 1; }

.rp-badge { font-size: 10px; font-weight: 700; border-radius: 6px; padding: 2px 8px; }
.rp-badge.badge-active { background: rgba(246,173,85,0.15); color: rgba(246,173,85,0.9); }
.rp-badge.badge-waiting { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.4); }
.rp-badge.badge-done { background: rgba(72,187,120,0.12); color: rgba(72,187,120,0.8); }
.rp-badge.badge-paused { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.3); }

.rp-players { display: flex; flex-wrap: wrap; gap: 6px; }

.rp-player {
  font-size: 12px; padding: 2px 10px;
  background: rgba(255,255,255,0.05); border-radius: 20px; color: rgba(255,255,255,0.6);
}

.rp-phase { font-size: 12px; color: rgba(79,195,247,0.7); }

.rp-range-row { display: flex; align-items: center; gap: 10px; }

.rp-range-label { font-size: 12px; color: rgba(255,255,255,0.3); white-space: nowrap; }

.rp-range-select {
  flex: 1; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px; color: #fff; font-size: 13px; font-family: inherit;
  padding: 6px 10px; outline: none; cursor: pointer;
}
</style>

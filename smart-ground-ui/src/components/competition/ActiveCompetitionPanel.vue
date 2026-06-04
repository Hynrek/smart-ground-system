<!-- src/components/competition/ActiveCompetitionPanel.vue -->
<template>
  <div class="panel-content">

    <!-- Header with abort button only -->
    <div class="panel-header">
      <h2 class="panel-title">Laufender Wettkampf</h2>
      <button class="stop-btn" @click="emit('stop')">
        <Icons icon="x" :size="13" color="rgba(252,129,129,0.8)" />
        Wettkampf abbrechen
      </button>
    </div>

    <!-- Passen stepper -->
    <div v-if="passenProgress.length > 0" class="passen-stepper">
      <template v-for="(passe, i) in passenProgress" :key="passe.phaseIndex">
        <div class="passe-step">
          <span class="step-name">{{ passe.passeName }}</span>
          <span class="step-badge" :class="`badge-${passe.status}`">{{ passeStatusLabel(passe.status) }}</span>
        </div>
        <span v-if="i < passenProgress.length - 1" class="step-arrow">→</span>
      </template>
    </div>

    <!-- Tabs -->
    <div class="tab-bar">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'serien' }"
        @click="activeTab = 'serien'"
      >
        Serien
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'rangliste' }"
        @click="activeTab = 'rangliste'"
      >
        Rangliste
      </button>
    </div>

    <!-- Serien tab -->
    <div v-if="activeTab === 'serien'" class="serien-view">
      <div
        v-for="card in serieCards"
        :key="card.serieAlias"
        class="serie-card"
      >
        <div class="serie-card-header">{{ card.serieAlias }}</div>
        <div
          v-for="row in card.rotteRows"
          :key="row.rotteId"
          class="rotte-row"
        >
          <span class="rotte-name">{{ row.rotteName }}</span>
          <span class="rotte-chip" :class="`chip-${row.status}`">
            {{ rowStatusLabel(row.status) }}
          </span>
        </div>
      </div>
      <div v-if="serieCards.length === 0" class="empty-state">Keine Serien in dieser Passe</div>
    </div>

    <!-- Rangliste tab -->
    <div v-if="activeTab === 'rangliste'" class="rangliste-view">
      <div
        v-for="(entry, i) in leaderboard"
        :key="entry.playerId"
        class="rangliste-row"
      >
        <span class="rank">{{ i + 1 }}</span>
        <span class="player-name">{{ entry.displayName }}</span>
        <span class="score">{{ entry.totalPoints }} / {{ entry.maxPoints }}</span>
      </div>
      <div v-if="leaderboard.length === 0" class="empty-state">Noch keine Ergebnisse</div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import Icons from '@/components/Icons.vue'
import { useActivePasseStore } from '@/stores/activePasseStore.js'
import { useCompetitionProgress } from '@/composables/useCompetitionProgress.js'

const props = defineProps({ event: { type: Object, required: true } })
const emit = defineEmits(['stop'])

const activePasseStore = useActivePasseStore()

const liveInstance = computed(() =>
  activePasseStore.activeInstances.find(i => i.instanceId === props.event.activeInstanceId) ?? null
)

const { passenProgress, serieCards, leaderboard } = useCompetitionProgress(liveInstance)

const activeTab = ref('serien')

const passeStatusLabel = (status) => ({ fertig: 'Fertig', aktiv: 'Aktiv', offen: 'Offen' }[status] ?? status)
const rowStatusLabel = (status) => ({ done: '✓ Fertig', in_progress: '◑ Aktiv', pending: '○ Offen' }[status] ?? status)
</script>

<style scoped>
.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px 40px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── Header ── */
.panel-header { display: flex; align-items: center; justify-content: space-between; }

.panel-title { font-size: 18px; font-weight: 700; color: var(--sg-brand); margin: 0; }

.stop-btn {
  display: flex; align-items: center; gap: 6px;
  background: var(--sg-color-danger-bg, #fff5f5); border: 1px solid var(--sg-color-danger-border, #fed7d7);
  border-radius: 10px; padding: 8px 14px;
  color: var(--sg-color-danger-text, #c53030); font-size: 13px; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: all 0.15s;
}
.stop-btn:hover { background: var(--sg-color-danger-bg-hover, #fed7d7); }

/* ── Passen stepper ── */
.passen-stepper {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 14px 16px;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  box-shadow: var(--sg-shadow-sm);
}

.passe-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.step-name {
  font-size: 11px;
  font-weight: 600;
  color: var(--sg-text-muted);
  white-space: nowrap;
}

.step-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
}

.badge-fertig { background: var(--sg-color-success-bg); color: var(--sg-color-success-text); }
.badge-aktiv  { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); }
.badge-offen  { background: var(--sg-bg-panel); color: var(--sg-text-faint); }

.step-arrow {
  font-size: 12px;
  color: var(--sg-border-input);
  padding: 0 4px;
  align-self: center;
}

/* ── Tab bar ── */
.tab-bar {
  display: flex;
  border-bottom: 1px solid var(--sg-border);
}

.tab-btn {
  flex: 1;
  padding: 10px 0;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  color: var(--sg-text-faint);
  cursor: pointer;
  margin-bottom: -1px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  color: var(--sg-brand);
  border-bottom-color: var(--sg-accent);
}

.tab-btn:hover:not(.active) { color: var(--sg-text-muted); }

/* ── Serien view ── */
.serien-view {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.serie-card {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--sg-shadow-sm);
}

.serie-card-header {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-brand);
  padding: 10px 14px 8px;
  border-bottom: 1px solid var(--sg-border);
}

.rotte-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-bottom: 1px solid var(--sg-border);
}

.rotte-row:last-child { border-bottom: none; }

.rotte-name {
  font-size: 13px;
  color: var(--sg-text-muted);
}

.rotte-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 10px;
}

.chip-done        { background: var(--sg-color-success-bg); color: var(--sg-color-success-text); }
.chip-in_progress { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); }
.chip-pending     { background: var(--sg-bg-panel); color: var(--sg-text-faint); }

/* ── Rangliste view ── */
.rangliste-view {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rangliste-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
  box-shadow: var(--sg-shadow-sm);
}

.rank {
  font-size: 12px;
  font-weight: 700;
  color: var(--sg-accent);
  width: 20px;
  flex-shrink: 0;
}

.player-name {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: var(--sg-brand);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score {
  font-size: 13px;
  font-weight: 700;
  color: var(--sg-text-muted);
  white-space: nowrap;
}

/* ── Empty state ── */
.empty-state {
  text-align: center;
  padding: 32px 16px;
  font-size: 13px;
  color: var(--sg-text-faint);
  background: var(--sg-bg-card);
  border-radius: 12px;
  border: 1px solid var(--sg-border);
}
</style>

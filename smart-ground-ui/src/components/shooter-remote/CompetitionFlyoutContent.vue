<template>
  <div class="competition-content">

    <!-- Passen stepper -->
    <div class="passen-stepper">
      <template v-for="(passe, i) in passenProgress" :key="i">
        <div class="passe-step" :class="`step-${passe.status}`">
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
        v-for="(card, i) in serieCards"
        :key="i"
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
      <div v-if="serieCards.length === 0" class="empty-state">Keine Serien</div>
    </div>

    <!-- Rangliste tab -->
    <div v-if="activeTab === 'rangliste'" class="rangliste-view">
      <div
        v-for="(entry, i) in leaderboard"
        :key="entry.displayName"
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
import { useCompetitionProgress } from '@/composables/useCompetitionProgress.js'

const props = defineProps({
  instance: { type: Object, required: true },
})

const instanceRef = computed(() => props.instance)
const { passenProgress, serieCards, leaderboard } = useCompetitionProgress(instanceRef)

const activeTab = ref('serien')

const passeStatusLabel = (status) => ({ fertig: 'Fertig', aktiv: 'Aktiv', offen: 'Offen' }[status] ?? status)
const rowStatusLabel = (status) => ({ done: '✓ Fertig', in_progress: '◑ Aktiv', pending: '○ Offen' }[status] ?? status)
</script>

<style scoped>
.competition-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px 0;
}

/* ── Passen stepper ───────────────────────────────── */
.passen-stepper {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.07);
}

.passe-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.step-name {
  font-size: 10px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
  white-space: nowrap;
}

.step-badge {
  font-size: 9px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 10px;
}

.badge-fertig { background: rgba(72, 187, 120, 0.2); color: #48bb78; }
.badge-aktiv  { background: rgba(246, 173, 85, 0.2); color: #f6ad55; }
.badge-offen  { background: rgba(255, 255, 255, 0.07); color: rgba(255, 255, 255, 0.3); }

.step-arrow {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.2);
  padding: 0 2px;
  align-self: center;
}

/* ── Tab bar ──────────────────────────────────────── */
.tab-bar {
  display: flex;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.tab-btn {
  flex: 1;
  padding: 8px 0;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: rgba(255, 255, 255, 0.3);
  font-size: 11px;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
  margin-bottom: -1px;
}

.tab-btn.active {
  color: rgba(79, 195, 247, 0.9);
  border-bottom-color: rgba(79, 195, 247, 0.7);
}

.tab-btn:hover:not(.active) {
  color: rgba(255, 255, 255, 0.5);
}

/* ── Serien view ──────────────────────────────────── */
.serien-view {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 4px;
}

.serie-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  overflow: hidden;
}

.serie-card-header {
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.7);
  padding: 8px 10px 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.rotte-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.rotte-row:last-child { border-bottom: none; }

.rotte-name {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
}

.rotte-chip {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

.chip-done        { background: rgba(72, 187, 120, 0.15); color: #48bb78; }
.chip-in_progress { background: rgba(246, 173, 85, 0.15); color: #f6ad55; }
.chip-pending     { background: rgba(255, 255, 255, 0.07); color: rgba(255, 255, 255, 0.3); }

/* ── Rangliste view ───────────────────────────────── */
.rangliste-view {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 4px;
}

.rangliste-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 8px;
}

.rank {
  font-size: 11px;
  font-weight: 700;
  color: rgba(79, 195, 247, 0.6);
  width: 16px;
  flex-shrink: 0;
}

.player-name {
  flex: 1;
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score {
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.4);
  white-space: nowrap;
}

/* ── Empty state ──────────────────────────────────── */
.empty-state {
  text-align: center;
  padding: 24px 12px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.2);
}
</style>

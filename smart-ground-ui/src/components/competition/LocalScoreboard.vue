<template>
  <div class="local-scoreboard">
    <div class="scoreboard-header">
      <span class="scoreboard-title">Rangliste</span>
      <span v-if="rankedPlayers.length" class="scoreboard-badge">{{ rankedPlayers.length }}</span>
    </div>

    <div class="scoreboard-divider" />

    <div v-if="rankedPlayers.length === 0" class="scoreboard-empty">
      <span>Noch keine Ergebnisse</span>
    </div>

    <div v-else class="scoreboard-list">
      <div
        v-for="(player, index) in rankedPlayers"
        :key="player.playerId"
        class="scoreboard-row"
        :class="{ 'row--top3': index < 3 }"
      >
        <!-- Rank badge -->
        <span
          class="rank-badge"
          :style="{ color: rankColor(index) }"
        >{{ index + 1 }}</span>

        <!-- Player info -->
        <div class="player-info">
          <span class="player-name">{{ player.displayName }}</span>
          <span class="rotte-name">{{ player.rotteName }}</span>
        </div>

        <!-- Points -->
        <span class="player-points">{{ player.totalPoints }} Pts</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useCompetitionScoreboard } from '@/composables/useCompetitionScoreboard.js'

const props = defineProps({
  instanceId: {
    type: String,
    required: true,
  },
})

const instanceIdRef = computed(() => props.instanceId)
const { rankedPlayers } = useCompetitionScoreboard(instanceIdRef)

const RANK_COLORS = {
  0: '#f6c90e',                     // Gold
  1: 'rgba(192,192,192,0.9)',       // Silver
  2: 'rgba(205,127,50,0.9)',        // Bronze
}

const rankColor = (index) => RANK_COLORS[index] ?? 'rgba(255,255,255,0.3)'
</script>

<style scoped>
.local-scoreboard {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* ── Header ── */
.scoreboard-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.scoreboard-title {
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  flex: 1;
}

.scoreboard-badge {
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 2px 8px;
}

.scoreboard-divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.07);
  margin-bottom: 10px;
}

/* ── Empty state ── */
.scoreboard-empty {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.2);
}

/* ── List ── */
.scoreboard-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* ── Row ── */
.scoreboard-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-radius: 10px;
  transition: background 0.12s;
}

.scoreboard-row.row--top3 {
  background: rgba(255, 255, 255, 0.03);
}

.scoreboard-row:not(:last-child) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

/* Rank badge */
.rank-badge {
  font-size: 13px;
  font-weight: 800;
  min-width: 22px;
  text-align: center;
  flex-shrink: 0;
}

/* Player info */
.player-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.player-name {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.88);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rotte-name {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.28);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Points */
.player-points {
  font-size: 13px;
  font-weight: 700;
  color: #4fc3f7;
  white-space: nowrap;
  flex-shrink: 0;
}
</style>

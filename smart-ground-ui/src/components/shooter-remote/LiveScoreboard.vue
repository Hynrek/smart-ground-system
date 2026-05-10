<template>
  <div class="scoreboard-panel">
    <div class="scoreboard-header">
      <h3>Ergebnisse</h3>
      <button class="compact-toggle" :title="compact ? 'Erweitern' : 'Kompakt'" @click="toggleCompact">
        {{ compact ? '⊞' : '⊟' }}
      </button>
    </div>

    <div class="scores-container">
      <div
        v-for="(scoreEntry, index) in playStore.playerScoresSorted"
        :key="scoreEntry.player.id"
        class="score-row"
        :class="{ 'is-current': scoreEntry.player.id === playStore.currentPlayer?.id }"
      >
        <div class="rank">{{ index + 1 }}</div>

        <div class="player-name">{{ scoreEntry.player.displayName }}</div>

        <div v-if="!compact" class="score-details">
          <div class="completion-bar">
            <div class="completion-fill" :style="{ width: scoreEntry.completionPct + '%' }"></div>
          </div>
          <span class="completion-text">{{ scoreEntry.completionPct }}%</span>
        </div>

        <div class="score-value">
          <span class="points">{{ scoreEntry.totalPoints }}</span>
          <span v-if="!compact" class="max-points">/ {{ scoreEntry.maxPoints }}</span>
        </div>
      </div>
    </div>

    <div v-if="!compact" class="scoreboard-footer">
      <div class="round-info">
        Runde {{ currentRound }} von {{ totalRounds }}
      </div>
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: overallCompletion + '%' }"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';

const playStore = usePlaySessionStore();
const compact = ref(false);

const toggleCompact = () => {
  compact.value = !compact.value;
};

const totalRounds = computed(() => {
  if (!playStore.sessionPlayers.length || !playStore.playProg.value) return 1;
  const segmentCount = playStore.playProg.value.length;
  return Math.ceil(segmentCount / playStore.sessionPlayers.length);
});

const currentRound = computed(() => {
  if (!playStore.sessionPlayers.length) return 1;
  const segmentsPerRound = playStore.sessionPlayers.length;
  return Math.floor(playStore.currentSegmentIndex / segmentsPerRound) + 1;
});

const overallCompletion = computed(() => playStore.completionPct);
</script>

<style scoped>
.scoreboard-panel {
  background: rgba(30, 30, 35, 0.85);
  border: 1px solid rgba(100, 100, 110, 0.3);
  border-radius: 8px;
  overflow: hidden;
  max-height: 400px;
  display: flex;
  flex-direction: column;
}

.scoreboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(100, 100, 110, 0.2);
  background: rgba(50, 50, 55, 0.4);
}

.scoreboard-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: rgba(100, 180, 220, 0.9);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.compact-toggle {
  background: rgba(100, 180, 220, 0.15);
  border: 1px solid rgba(100, 180, 220, 0.3);
  color: rgba(100, 180, 220, 0.8);
  width: 28px;
  height: 28px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.compact-toggle:hover {
  background: rgba(100, 180, 220, 0.25);
  border-color: rgba(100, 180, 220, 0.5);
}

.scores-container {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.score-row {
  display: grid;
  grid-template-columns: 28px 1fr auto auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(50, 50, 60, 0.5);
  transition: background 0.2s ease;
}

.score-row:hover {
  background: rgba(60, 60, 70, 0.3);
}

.score-row.is-current {
  background: rgba(100, 180, 220, 0.1);
  border-left: 3px solid rgba(100, 180, 220, 0.5);
  padding-left: 9px;
}

.rank {
  font-weight: 700;
  color: rgba(150, 180, 220, 0.8);
  font-size: 14px;
  text-align: center;
}

.player-name {
  color: rgba(200, 200, 210, 0.9);
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 80px;
}

.completion-bar {
  height: 4px;
  background: rgba(50, 50, 60, 0.6);
  border-radius: 2px;
  overflow: hidden;
}

.completion-fill {
  height: 100%;
  background: linear-gradient(90deg, rgba(100, 180, 220, 0.6), rgba(100, 180, 220, 0.9));
  transition: width 0.3s ease;
}

.completion-text {
  font-size: 11px;
  color: rgba(150, 150, 160, 0.7);
  text-align: right;
}

.score-value {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 60px;
  justify-content: flex-end;
}

.points {
  font-weight: 700;
  font-size: 16px;
  color: rgba(100, 180, 220, 0.95);
}

.max-points {
  font-size: 11px;
  color: rgba(150, 150, 160, 0.6);
}

.scoreboard-footer {
  padding: 12px 12px;
  border-top: 1px solid rgba(100, 100, 110, 0.2);
  background: rgba(50, 50, 55, 0.3);
}

.round-info {
  font-size: 11px;
  color: rgba(150, 150, 160, 0.7);
  text-align: center;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.progress-bar {
  height: 3px;
  background: rgba(50, 50, 60, 0.6);
  border-radius: 1.5px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, rgba(100, 180, 100, 0.5), rgba(100, 180, 220, 0.7));
  transition: width 0.3s ease;
}

/* Compact mode adjustments */
.score-row {
  grid-template-columns: 28px 1fr auto;
}
</style>

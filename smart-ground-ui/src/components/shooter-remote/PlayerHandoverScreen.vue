<template>
  <div v-if="visible" class="handover-overlay">
    <div class="handover-card">
      <div class="handover-header">
        <h2>Spielerwechsel</h2>
      </div>

      <div class="player-info">
        <div class="leaving-player">
          <span class="label">{{ previousPlayerName }} war dran</span>
        </div>
        <div class="arrow-icon">→</div>
        <div class="entering-player">
          <span class="label">{{ currentPlayerName }} jetzt</span>
        </div>
      </div>

      <div class="handover-stats">
        <div class="stat">
          <span class="stat-label">Punkte</span>
          <span class="stat-value">{{ previousPlayerPoints }}</span>
        </div>
        <div class="stat">
          <span class="stat-label">Durchsatz</span>
          <span class="stat-value">{{ previousPlayerCompletion }}%</span>
        </div>
      </div>

      <button class="confirm-button" @click="confirmHandover">
        Bestätigen
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';

defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['confirm']);

const playStore = usePlaySessionStore();

const previousPlayerName = computed(() => {
  if (!playStore.sessionPlayers.length) return 'Spieler';
  const prevIdx = playStore.currentPlayerIndex - 1;
  if (prevIdx < 0) {
    const lastIdx = playStore.roundOrder.value[playStore.roundOrder.value.length - 1];
    return playStore.sessionPlayers[lastIdx]?.displayName || 'Spieler';
  }
  const prevPlayerIdx = playStore.roundOrder.value[prevIdx];
  return playStore.sessionPlayers[prevPlayerIdx]?.displayName || 'Spieler';
});

const currentPlayerName = computed(
  () => playStore.currentPlayer?.displayName || 'Spieler'
);

const previousPlayerPoints = computed(() => {
  if (!playStore.sessionPlayers.length) return 0;
  const prevIdx = playStore.currentPlayerIndex - 1;
  let playerIdx;
  if (prevIdx < 0) {
    playerIdx = playStore.roundOrder.value[playStore.roundOrder.value.length - 1];
  } else {
    playerIdx = playStore.roundOrder.value[prevIdx];
  }
  const scores = playStore.playerScores.find(
    (s) => s.player.id === playStore.sessionPlayers[playerIdx]?.id
  );
  return scores?.totalPoints || 0;
});

const previousPlayerCompletion = computed(() => {
  if (!playStore.sessionPlayers.length) return 0;
  const prevIdx = playStore.currentPlayerIndex - 1;
  let playerIdx;
  if (prevIdx < 0) {
    playerIdx = playStore.roundOrder.value[playStore.roundOrder.value.length - 1];
  } else {
    playerIdx = playStore.roundOrder.value[prevIdx];
  }
  const scores = playStore.playerScores.find(
    (s) => s.player.id === playStore.sessionPlayers[playerIdx]?.id
  );
  return scores?.completionPct || 0;
});

const confirmHandover = () => {
  emit('confirm');
};
</script>

<style scoped>
.handover-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

.handover-card {
  background: rgba(30, 30, 35, 0.95);
  border: 2px solid rgba(100, 180, 220, 0.3);
  border-radius: 12px;
  padding: 32px;
  max-width: 400px;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
}

.handover-header {
  margin-bottom: 24px;
}

.handover-header h2 {
  color: rgba(100, 180, 220, 0.9);
  font-size: 24px;
  margin: 0;
  font-weight: 600;
}

.player-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
  gap: 16px;
}

.leaving-player,
.entering-player {
  flex: 1;
  padding: 12px;
  border-radius: 8px;
  background: rgba(50, 50, 55, 0.6);
  border: 1px solid rgba(100, 100, 110, 0.3);
}

.label {
  display: block;
  color: rgba(200, 200, 210, 0.8);
  font-size: 13px;
  margin-bottom: 6px;
}

.leaving-player .label::before {
  content: '↪ ';
  color: rgba(150, 100, 100, 0.6);
}

.entering-player .label::before {
  content: '➜ ';
  color: rgba(100, 180, 100, 0.6);
}

.leaving-player .label {
  color: rgba(200, 150, 150, 0.8);
}

.entering-player .label {
  color: rgba(150, 200, 150, 0.8);
}

.arrow-icon {
  font-size: 28px;
  color: rgba(100, 180, 220, 0.6);
  font-weight: bold;
}

.handover-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 28px;
  justify-content: center;
}

.stat {
  display: flex;
  flex-direction: column;
  padding: 12px 20px;
  background: rgba(50, 50, 55, 0.5);
  border-radius: 8px;
  min-width: 100px;
}

.stat-label {
  font-size: 11px;
  color: rgba(150, 150, 160, 0.7);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: rgba(100, 180, 220, 0.9);
}

.confirm-button {
  width: 100%;
  padding: 14px 24px;
  background: rgba(100, 180, 220, 0.2);
  border: 2px solid rgba(100, 180, 220, 0.5);
  color: rgba(100, 180, 220, 0.95);
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.confirm-button:hover {
  background: rgba(100, 180, 220, 0.35);
  border-color: rgba(100, 180, 220, 0.8);
  box-shadow: 0 0 16px rgba(100, 180, 220, 0.2);
}

.confirm-button:active {
  transform: scale(0.98);
}
</style>

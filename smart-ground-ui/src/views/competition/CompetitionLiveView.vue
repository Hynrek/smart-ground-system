<template>
  <div class="competition-live">
    <div class="live-header">
      <h1>Wettkampf läuft</h1>
      <div class="session-info">
        <span>Status: {{ competitionStore.currentSession?.status }}</span>
        <div class="session-controls">
          <button @click="pauseSession" class="btn btn-secondary">Unterbrechen</button>
          <button @click="endSession" class="btn btn-danger">Beenden</button>
        </div>
      </div>
    </div>

    <div class="live-content">
      <!-- Range Selector -->
      <div class="range-section">
        <h2>Bereiche</h2>
        <div class="range-selector">
          <button
            v-for="range in availableRanges"
            :key="range.id"
            @click="selectRange(range.id)"
            class="btn range-btn"
            :class="{ active: competitionStore.selectedRange === range.id }"
          >
            {{ range.name }}
          </button>
        </div>

        <!-- Range Details -->
        <div v-if="competitionStore.selectedRange" class="range-details">
          <h3>Warteschlange</h3>
          <div class="queue-container">
            <div class="active-group">
              <h4>Aktiv</h4>
              <div v-if="competitionStore.groupsAtRange.length > 0" class="group-card">
                <p>{{ competitionStore.groupsAtRange[0].name }}</p>
                <p class="players">{{ competitionStore.groupsAtRange[0].members?.length || 0 }} Spieler</p>
              </div>
              <p v-else class="empty">Keine Gruppe aktiv</p>
            </div>

            <div class="queue">
              <h4>Warteschlange</h4>
              <div v-if="competitionStore.rangeQueue.length > 0" class="queue-list">
                <div v-for="group in competitionStore.rangeQueue" :key="group.id" class="queue-item">
                  <span>{{ group.name }}</span>
                  <button @click="registerGroupAtRange(group.id)" class="btn btn-small">Aktivieren</button>
                </div>
              </div>
              <p v-else class="empty">Keine wartenden Gruppen</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Live Scoreboard -->
      <div class="scoreboard-section">
        <h2>Live-Ergebnisse</h2>
        <live-scoreboard v-if="competitionStore.currentSession" :session-id="competitionStore.currentSession.id" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useCompetitionStore } from '@/stores/competitionStore.js';
import LiveScoreboard from '@/components/LiveScoreboard.vue';

const router = useRouter();
const route = useRoute();
const competitionStore = useCompetitionStore();

const availableRanges = ref([
  { id: '1', name: 'Bereich 1' },
  { id: '2', name: 'Bereich 2' },
  { id: '3', name: 'Bereich 3' },
]);

onMounted(async () => {
  const sessionId = route.params.sessionId;
  // Load current session if not already loaded
  if (!competitionStore.currentSession || competitionStore.currentSession.id !== sessionId) {
    // Fetch session from backend
    try {
      // TODO: Implement session loading
    } catch (err) {
      console.error('Failed to load session:', err);
      router.push('/competition');
    }
  }
});

const selectRange = async (rangeId) => {
  competitionStore.selectRange(rangeId);
  if (competitionStore.currentSession) {
    await competitionStore.loadGroupsAtRange(competitionStore.currentSession.id, rangeId);
  }
};

const registerGroupAtRange = async (groupId) => {
  if (competitionStore.currentSession && competitionStore.selectedRange) {
    await competitionStore.registerGroupAtRange(
      competitionStore.currentSession.id,
      groupId,
      competitionStore.selectedRange
    );
  }
};

const pauseSession = async () => {
  if (competitionStore.currentSession) {
    await competitionStore.updateSessionStatus(competitionStore.currentSession.id, 'PAUSED');
  }
};

const endSession = async () => {
  if (confirm('Wettkampf wirklich beenden?')) {
    if (competitionStore.currentSession) {
      await competitionStore.updateSessionStatus(competitionStore.currentSession.id, 'COMPLETED');
      router.push(`/competition/leaderboard/${competitionStore.currentSession.id}`);
    }
  }
};
</script>

<style scoped>
.competition-live {
  padding: 2rem;
}

.live-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 2px solid #333;
}

.session-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.session-controls {
  display: flex;
  gap: 0.5rem;
}

.live-content {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 2rem;
}

.range-section {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1.5rem;
}

.range-section h2 {
  margin-top: 0;
}

.range-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.range-btn {
  padding: 0.5rem 1rem;
  border: 1px solid #2196F3;
  background: white;
  color: #2196F3;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.range-btn.active {
  background: #2196F3;
  color: white;
}

.range-details {
  margin-top: 1rem;
}

.queue-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.active-group,
.queue {
  padding: 1rem;
  background: #f9f9f9;
  border-radius: 4px;
}

.active-group h4,
.queue h4 {
  margin-top: 0;
}

.group-card {
  padding: 0.5rem;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.group-card p {
  margin: 0.25rem 0;
}

.players {
  color: #666;
  font-size: 0.9rem;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.queue-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.empty {
  color: #999;
  font-style: italic;
}

.scoreboard-section {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1.5rem;
}

.scoreboard-section h2 {
  margin-top: 0;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-primary {
  background: #2196F3;
  color: white;
}

.btn-secondary {
  background: #9E9E9E;
  color: white;
}

.btn-danger {
  background: #f44336;
  color: white;
}

.btn-small {
  padding: 0.25rem 0.5rem;
  font-size: 0.8rem;
}
</style>

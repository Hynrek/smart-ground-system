<template>
  <div class="competition-leaderboard">
    <div class="leaderboard-header">
      <h1>Wettkampf-Ergebnisse</h1>
      <div class="header-actions">
        <button class="btn btn-secondary" @click="exportLeaderboard('json')">
          Als JSON exportieren
        </button>
        <button class="btn btn-secondary" @click="exportLeaderboard('csv')">
          Als CSV exportieren
        </button>
        <router-link to="/competition" class="btn btn-primary">Zurück zur Übersicht</router-link>
      </div>
    </div>

    <div v-if="competitionStore.isLoading" class="loading-spinner">
      Lade Leaderboard...
    </div>
    <div v-else-if="!competitionStore.sessionLeaderboard" class="error-message">
      Leaderboard konnte nicht geladen werden.
    </div>
    <div v-else class="leaderboard-content">
      <!-- Player Rankings -->
      <div class="leaderboard-section">
        <h2>Spieler-Rankings</h2>
        <table class="leaderboard-table">
          <thead>
            <tr>
              <th>Rang</th>
              <th>Spieler</th>
              <th>Punkte</th>
              <th>Maximal</th>
              <th>Prozent</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="score in competitionStore.sessionLeaderboard.playerScores" :key="score.playerId">
              <td class="rank">
                <strong>{{ score.rank }}</strong>
              </td>
              <td>{{ score.displayName }}</td>
              <td class="score">{{ score.totalScore }}</td>
              <td>{{ score.maxScore }}</td>
              <td class="percentage">
                {{ score.maxScore > 0 ? Math.round((score.totalScore / score.maxScore) * 100) : 0 }}%
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Group Rankings (if applicable) -->
      <div v-if="competitionStore.sessionLeaderboard.groupScores.length > 0" class="leaderboard-section">
        <h2>Gruppen-Rankings</h2>
        <table class="leaderboard-table">
          <thead>
            <tr>
              <th>Rang</th>
              <th>Gruppe</th>
              <th>Punkte</th>
              <th>Maximal</th>
              <th>Prozent</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="score in competitionStore.sessionLeaderboard.groupScores" :key="score.groupId">
              <td class="rank">
                <strong>{{ score.rank }}</strong>
              </td>
              <td>{{ score.groupName }}</td>
              <td class="score">{{ score.totalScore }}</td>
              <td>{{ score.maxScore }}</td>
              <td class="percentage">
                {{ score.maxScore > 0 ? Math.round((score.totalScore / score.maxScore) * 100) : 0 }}%
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useCompetitionStore } from '@/stores/competitionStore.js';

const route = useRoute();
const router = useRouter();
const competitionStore = useCompetitionStore();

onMounted(async () => {
  const sessionId = route.params.sessionId;
  await competitionStore.loadSessionLeaderboard(sessionId);
});

const exportLeaderboard = async (format) => {
  try {
    const sessionId = route.params.sessionId;
    const blob = await competitionStore.exportLeaderboard(sessionId, format);
    
    // Trigger download
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `leaderboard.${format === 'csv' ? 'csv' : 'json'}`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  } catch (err) {
    console.error('Failed to export leaderboard:', err);
  }
};
</script>

<style scoped>
.competition-leaderboard {
  padding: 2rem;
}

.leaderboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 2px solid #333;
}

.leaderboard-header h1 {
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.leaderboard-content {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.leaderboard-section {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1.5rem;
  overflow-x: auto;
}

.leaderboard-section h2 {
  margin-top: 0;
}

.leaderboard-table {
  width: 100%;
  border-collapse: collapse;
}

.leaderboard-table th {
  background: #f5f5f5;
  padding: 1rem;
  text-align: left;
  border-bottom: 2px solid #ddd;
  font-weight: bold;
}

.leaderboard-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #eee;
}

.leaderboard-table tr:nth-child(even) {
  background: #fafafa;
}

.rank {
  text-align: center;
  min-width: 60px;
}

.score {
  text-align: right;
  font-weight: bold;
}

.percentage {
  text-align: right;
  font-size: 0.9rem;
  color: #666;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  text-decoration: none;
  display: inline-block;
}

.btn-primary {
  background: #2196F3;
  color: white;
}

.btn-primary:hover {
  background: #1976D2;
}

.btn-secondary {
  background: #9E9E9E;
  color: white;
}

.btn-secondary:hover {
  background: #757575;
}

.loading-spinner,
.error-message {
  text-align: center;
  padding: 3rem;
  font-size: 1.1rem;
  color: #666;
}

.error-message {
  color: #f44336;
}
</style>

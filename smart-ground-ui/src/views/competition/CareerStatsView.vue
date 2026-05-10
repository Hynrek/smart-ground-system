<template>
  <div class="career-stats">
    <div class="stats-header">
      <h1>Karriere-Statistiken</h1>
      <div class="filter-tabs">
        <button
          @click="activeTab = 'score'"
          class="tab-btn"
          :class="{ active: activeTab === 'score' }"
        >
          Nach Punkten
        </button>
        <button
          @click="activeTab = 'wins'"
          class="tab-btn"
          :class="{ active: activeTab === 'wins' }"
        >
          Nach Siegen
        </button>
      </div>
    </div>

    <div v-if="isLoading" class="loading-spinner">Lade Statistiken...</div>
    <div v-else class="stats-content">
      <!-- Score Leaderboard -->
      <div v-if="activeTab === 'score'" class="leaderboard-section">
        <h2>Top-Spieler nach Gesamtpunkten</h2>
        <table class="stats-table">
          <thead>
            <tr>
              <th>Rang</th>
              <th>Spieler</th>
              <th>Siege</th>
              <th>Teilnahmen</th>
              <th>Gesamtpunkte</th>
              <th>Ø Punkte</th>
              <th>Zuletzt</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(stats, idx) in competitionStore.topPlayers" :key="stats.userId">
              <td class="rank">{{ idx + 1 }}</td>
              <td>{{ getUserDisplayName(stats.userId) }}</td>
              <td class="center">{{ stats.totalWins }}</td>
              <td class="center">{{ stats.participations }}</td>
              <td class="score">{{ stats.totalScore }}</td>
              <td class="score">{{ stats.avgScore.toFixed(1) }}</td>
              <td class="date">{{ formatDate(stats.lastCompeted) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Wins Leaderboard -->
      <div v-if="activeTab === 'wins'" class="leaderboard-section">
        <h2>Top-Spieler nach Siegen</h2>
        <table class="stats-table">
          <thead>
            <tr>
              <th>Rang</th>
              <th>Spieler</th>
              <th>Siege</th>
              <th>Teilnahmen</th>
              <th>Gesamtpunkte</th>
              <th>Ø Punkte</th>
              <th>Zuletzt</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(stats, idx) in competitionStore.topPlayersByWins" :key="stats.userId">
              <td class="rank">{{ idx + 1 }}</td>
              <td>{{ getUserDisplayName(stats.userId) }}</td>
              <td class="center">{{ stats.totalWins }}</td>
              <td class="center">{{ stats.participations }}</td>
              <td class="score">{{ stats.totalScore }}</td>
              <td class="score">{{ stats.avgScore.toFixed(1) }}</td>
              <td class="date">{{ formatDate(stats.lastCompeted) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useCompetitionStore } from '@/stores/competitionStore.js';

const competitionStore = useCompetitionStore();
const activeTab = ref('score');
const isLoading = ref(false);

onMounted(async () => {
  isLoading.value = true;
  await competitionStore.loadTopPlayers(50);
  await competitionStore.loadTopPlayersByWins(50);
  isLoading.value = false;
});

const getUserDisplayName = (userId) => {
  // TODO: Fetch user display name from backend
  return `Spieler ${userId.substring(0, 8)}`;
};

const formatDate = (date) => {
  if (!date) return '-';
  return new Date(date).toLocaleDateString('de-CH');
};
</script>

<style scoped>
.career-stats {
  padding: 2rem;
}

.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 2px solid #333;
}

.stats-header h1 {
  margin: 0;
}

.filter-tabs {
  display: flex;
  gap: 0.5rem;
}

.tab-btn {
  padding: 0.5rem 1rem;
  border: 1px solid #2196F3;
  background: white;
  color: #2196F3;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.tab-btn.active {
  background: #2196F3;
  color: white;
}

.stats-content {
  display: flex;
  flex-direction: column;
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

.stats-table {
  width: 100%;
  border-collapse: collapse;
}

.stats-table th {
  background: #f5f5f5;
  padding: 1rem;
  text-align: left;
  border-bottom: 2px solid #ddd;
  font-weight: bold;
  font-size: 0.9rem;
}

.stats-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #eee;
}

.stats-table tr:nth-child(even) {
  background: #fafafa;
}

.rank {
  text-align: center;
  min-width: 60px;
  font-weight: bold;
}

.center {
  text-align: center;
}

.score {
  text-align: right;
  font-weight: bold;
}

.date {
  font-size: 0.9rem;
  color: #666;
}

.loading-spinner {
  text-align: center;
  padding: 3rem;
  color: #999;
}
</style>

<template>
  <div class="competition-bracket-container">
    <!-- Header -->
    <div class="bracket-header">
      <h1>Tournament Bracket</h1>
      <div class="header-stats">
        <div class="stat">
          <span class="label">Phase:</span>
          <span class="value">{{ bracketPhase }}</span>
        </div>
        <div class="stat">
          <span class="label">Progress:</span>
          <span class="value">{{ bracketProgress }}%</span>
        </div>
        <div class="stat">
          <span class="label">Status:</span>
          <span class="value" :class="['status', statusClass]">{{ statusText }}</span>
        </div>
      </div>
    </div>

    <div class="bracket-content">
      <!-- Left: Bracket Visualization -->
      <div class="bracket-viz-panel">
        <div class="viz-header">
          <h2>Bracket Visualization</h2>
          <div class="viz-controls">
            <button
              class="btn-icon"
              title="Zoom In"
              @click="zoomIn"
            >
              🔍+
            </button>
            <button
              class="btn-icon"
              title="Zoom Out"
              @click="zoomOut"
            >
              🔍-
            </button>
            <button
              class="btn-icon"
              title="Reset Zoom"
              @click="resetZoom"
            >
              ⟲
            </button>
          </div>
        </div>
        <div class="viz-container" :style="{ transform: `scale(${zoomLevel})` }">
          <BracketVisualizer
            :bracketStore="store"
            :selectedMatch="selectedMatch"
            @selectMatch="selectMatchForRecording"
          />
        </div>
      </div>

      <!-- Right: Match Recording Panel -->
      <div class="recording-panel">
        <div class="panel-header">
          <h2>Match Recording</h2>
          <div class="status-indicator" :class="[connectionStatus]">
            <span class="dot"></span>
            {{ connectionStatus === 'connected' ? 'Connected' : 'Disconnected' }}
          </div>
        </div>

        <!-- Active Match Display -->
        <div v-if="selectedMatch" class="active-match">
          <div class="match-title">
            Round {{ selectedMatch.roundNumber }} - Match #{{ selectedMatch.matchNumber }}
          </div>

          <div v-if="selectedMatch.isBye" class="bye-match">
            <p>This is a BYE match. The contestant advances automatically.</p>
            <button
              class="btn btn-primary"
              :disabled="selectedMatch.isPlayed"
              @click="advanceByeWinner"
            >
              {{ selectedMatch.isPlayed ? 'Already Advanced' : 'Mark as Advanced' }}
            </button>
          </div>

          <div v-else class="match-recording">
            <!-- Contestant 1 -->
            <div class="contestant-section">
              <div class="contestant-header">Contestant 1</div>
              <div class="contestant-info">
                <div class="player-name">
                  {{ getPlayerName(selectedMatch.contestant1Id) || 'TBD' }}
                </div>
                <div class="score-input">
                  <label for="score1">Score:</label>
                  <input
                    id="score1"
                    v-model.number="matchResult.score1"
                    type="number"
                    min="0"
                    :disabled="selectedMatch.isPlayed"
                  />
                </div>
              </div>
              <button
                class="btn btn-contestant"
                :class="{ selected: matchResult.winnerId === selectedMatch.contestant1Id }"
                :disabled="!selectedMatch.contestant1Id || selectedMatch.isPlayed"
                @click="selectWinner(selectedMatch.contestant1Id)"
              >
                ✓ Select Winner
              </button>
            </div>

            <!-- VS -->
            <div class="vs-divider">VS</div>

            <!-- Contestant 2 -->
            <div class="contestant-section">
              <div class="contestant-header">Contestant 2</div>
              <div class="contestant-info">
                <div class="player-name">
                  {{ getPlayerName(selectedMatch.contestant2Id) || 'TBD' }}
                </div>
                <div class="score-input">
                  <label for="score2">Score:</label>
                  <input
                    id="score2"
                    v-model.number="matchResult.score2"
                    type="number"
                    min="0"
                    :disabled="selectedMatch.isPlayed"
                  />
                </div>
              </div>
              <button
                class="btn btn-contestant"
                :class="{ selected: matchResult.winnerId === selectedMatch.contestant2Id }"
                :disabled="!selectedMatch.contestant2Id || selectedMatch.isPlayed"
                @click="selectWinner(selectedMatch.contestant2Id)"
              >
                ✓ Select Winner
              </button>
            </div>
          </div>

          <!-- Submit Button -->
          <div v-if="!selectedMatch.isBye" class="submit-section">
            <div v-if="validationErrors.length > 0" class="validation-errors">
              <ul>
                <li v-for="(error, index) in validationErrors" :key="index">{{ error }}</li>
              </ul>
            </div>
            <button
              class="btn btn-submit"
              :disabled="!canSubmit || selectedMatch.isPlayed || submitting"
              @click="submitMatchResult"
            >
              <span v-if="submitting">Recording...</span>
              <span v-else>Record Result</span>
            </button>
          </div>

          <div v-if="selectedMatch.isPlayed" class="match-completed">
            <p>✓ This match has been completed</p>
            <div class="completed-info">
              <div>Gewinner: {{ getPlayerName(selectedMatch.winnerId) }}</div>
              <div v-if="selectedMatch.score1 !== null && selectedMatch.score2 !== null">
                Score: {{ selectedMatch.score1 }} - {{ selectedMatch.score2 }}
              </div>
            </div>
          </div>
        </div>

        <!-- No Match Selected -->
        <div v-else class="no-match-selected">
          <p>Click on a match in the bracket to record results.</p>
        </div>

        <!-- Message Display -->
        <div v-if="message" :class="['message', message.type]">
          {{ message.text }}
        </div>
      </div>
    </div>

    <!-- Bottom: Progress & Leaderboard Preview -->
    <div class="footer-section">
      <!-- Round Progress -->
      <div class="round-progress">
        <h3>Round Progress</h3>
        <div class="progress-bars">
          <div v-for="(matches, round) in matchesByRound" :key="round" class="progress-item">
            <span class="round-label">Round {{ round }}</span>
            <div class="progress-bar">
              <div
                class="progress-fill"
                :style="{ width: getRoundProgress(round) + '%' }"
              ></div>
            </div>
            <span class="progress-text">{{ getPlayedInRound(round) }}/{{ matches.length }}</span>
          </div>
        </div>
      </div>

      <!-- Leaderboard Preview -->
      <div v-if="leaderboard" class="leaderboard-preview">
        <h3>Current Standings</h3>
        <table class="leaderboard-table">
          <thead>
            <tr>
              <th>Rank</th>
              <th>Schütze</th>
              <th>Wins</th>
              <th>Score</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(entry, index) in leaderboardTop5" :key="index">
              <td class="rank">{{ index + 1 }}</td>
              <td class="name">{{ entry.name }}</td>
              <td class="wins">{{ entry.wins }}</td>
              <td class="score">{{ entry.score }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Export Button -->
      <div class="export-section">
        <button class="btn btn-secondary" @click="exportBracketJson">
          📥 Export JSON
        </button>
        <button class="btn btn-secondary" @click="exportBracketPdf">
          📥 Export PDF
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useBracketStore } from '@/stores/bracketStore'
import BracketVisualizer from '@/components/BracketVisualizer.vue'
import { bracketService } from '@/services/bracketService'

const store = useBracketStore()
const selectedMatch = ref(null)
const connectionStatus = ref('connecting')
const submitting = ref(false)
const message = ref(null)
const zoomLevel = ref(1)
const leaderboard = ref(null)

const matchResult = ref({
  winnerId: null,
  score1: null,
  score2: null
})

// Props (would come from route params in real app)
const sessionId = ref(null)

onMounted(() => {
  // Get session ID from route
  sessionId.value = store.currentSession?.id

  if (sessionId.value) {
    loadBracketData()
    setupWebSocketListener()
  }

  // Load leaderboard periodically
  setInterval(() => {
    loadLeaderboard()
  }, 5000)
})

onUnmounted(() => {
  // Cleanup WebSocket listeners
})

const loadBracketData = async () => {
  try {
    await store.loadBracketState(sessionId.value)
    connectionStatus.value = 'connected'
  } catch (error) {
    console.error('Failed to load bracket:', error)
    connectionStatus.value = 'disconnected'
  }
}

const loadLeaderboard = async () => {
  try {
    const response = await bracketService.getBracketLeaderboard(sessionId.value)
    leaderboard.value = response
  } catch (error) {
    // Leaderboard not available yet
  }
}

const setupWebSocketListener = () => {
  // WebSocket listeners are handled by bracketStore
  // The store automatically updates when receiving broadcasts
}

const selectMatchForRecording = (match) => {
  selectedMatch.value = match
  matchResult.value = {
    winnerId: null,
    score1: null,
    score2: null
  }
}

const selectWinner = (playerId) => {
  matchResult.value.winnerId = playerId
}

const getPlayerName = (playerId) => {
  if (!playerId) return null
  // Get player name from store's players map
  const player = store.seededPlayers?.find(p => p.playerId === playerId)
  return player ? `${player.firstName} ${player.lastName}` : null
}

const advanceByeWinner = async () => {
  if (!selectedMatch.value || !selectedMatch.value.contestant1Id) return

  try {
    submitting.value = true
    const contestant = selectedMatch.value.contestant1Id
    await bracketService.recordMatchWinner(
      sessionId.value,
      selectedMatch.value.matchNumber,
      {
        winnerId: contestant,
        score1: null,
        score2: null
      }
    )
    showMessage('Bye match advanced', 'success')
    selectedMatch.value.isPlayed = true
  } catch (error) {
    showMessage('Failed to record bye: ' + error.message, 'error')
  } finally {
    submitting.value = false
  }
}

const submitMatchResult = async () => {
  if (!canSubmit.value || !selectedMatch.value) return

  try {
    submitting.value = true

    await bracketService.recordMatchWinner(
      sessionId.value,
      selectedMatch.value.matchNumber,
      {
        winnerId: matchResult.value.winnerId,
        score1: matchResult.value.score1,
        score2: matchResult.value.score2
      }
    )

    showMessage('Match result recorded successfully', 'success')
    selectedMatch.value.isPlayed = true
    selectedMatch.value.winnerId = matchResult.value.winnerId
    selectedMatch.value.score1 = matchResult.value.score1
    selectedMatch.value.score2 = matchResult.value.score2

    // Clear selection
    setTimeout(() => {
      selectedMatch.value = null
    }, 1000)
  } catch (error) {
    showMessage('Error recording match: ' + error.message, 'error')
  } finally {
    submitting.value = false
  }
}

const getRoundProgress = (round) => {
  const matches = store.matchesByRound[round] || []
  if (matches.length === 0) return 0
  const played = matches.filter(m => m.isPlayed).length
  return Math.round((played / matches.length) * 100)
}

const getPlayedInRound = (round) => {
  const matches = store.matchesByRound[round] || []
  return matches.filter(m => m.isPlayed).length
}

const exportBracketJson = async () => {
  try {
    const json = await bracketService.exportBracket(sessionId.value, 'json')
    downloadJson(json, `bracket_${sessionId.value}.json`)
  } catch (error) {
    showMessage('Export failed: ' + error.message, 'error')
  }
}

const exportBracketPdf = async () => {
  try {
    const pdf = await bracketService.exportBracket(sessionId.value, 'pdf')
    downloadPdf(pdf, `bracket_${sessionId.value}.pdf`)
  } catch (error) {
    showMessage('Export failed: ' + error.message, 'error')
  }
}

const downloadJson = (data, filename) => {
  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: 'application/json'
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

const downloadPdf = (data, filename) => {
  const blob = new Blob([data], { type: 'application/pdf' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

const showMessage = (text, type = 'info') => {
  message.value = { text, type }
  setTimeout(() => {
    message.value = null
  }, 3000)
}

const zoomIn = () => {
  zoomLevel.value = Math.min(zoomLevel.value + 0.1, 2)
}

const zoomOut = () => {
  zoomLevel.value = Math.max(zoomLevel.value - 0.1, 0.5)
}

const resetZoom = () => {
  zoomLevel.value = 1
}

// Computed properties
const bracketPhase = computed(() => store.bracketPhase || 'SETUP')
const bracketProgress = computed(() => {
  if (!store.matchesByRound || Object.keys(store.matchesByRound).length === 0) return 0
  let total = 0
  let played = 0
  for (const matches of Object.values(store.matchesByRound)) {
    total += matches.length
    played += matches.filter(m => m.isPlayed).length
  }
  return total === 0 ? 0 : Math.round((played / total) * 100)
})

const statusText = computed(() => {
  if (store.isComplete) return 'Completed'
  if (bracketPhase.value === 'IN_PROGRESS') return 'In Progress'
  if (bracketPhase.value === 'SEEDING') return 'Seeding'
  return 'Setup'
})

const statusClass = computed(() => {
  if (store.isComplete) return 'completed'
  if (bracketPhase.value === 'IN_PROGRESS') return 'active'
  return 'pending'
})

const matchesByRound = computed(() => store.matchesByRound || {})

const leaderboardTop5 = computed(() => {
  if (!leaderboard.value?.rankings) return []
  return Object.values(leaderboard.value.rankings)
    .slice(0, 5)
    .map(entry => ({
      name: entry.name || 'Unknown',
      wins: entry.wins || 0,
      score: entry.score || 0
    }))
})

const validationErrors = computed(() => {
  const errors = []
  if (!selectedMatch.value) return errors
  if (!matchResult.value.winnerId) {
    errors.push('Select a winner')
  }
  if (
    !selectedMatch.value.contestant1Id ||
    !selectedMatch.value.contestant2Id
  ) {
    errors.push('Both contestants must be assigned')
  }
  return errors
})

const canSubmit = computed(() => {
  return validationErrors.value.length === 0
})

watch(
  () => store.bracketPhase,
  (newPhase) => {
    if (newPhase === 'COMPLETED') {
      showMessage('Tournament is complete!', 'success')
    }
  }
)
</script>

<style scoped>
.competition-bracket-container {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 1.5rem;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.bracket-header {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 2rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.bracket-header h1 {
  margin: 0 0 1rem 0;
  color: #2c3e50;
  font-size: 2rem;
}

.header-stats {
  display: flex;
  gap: 2rem;
  flex-wrap: wrap;
}

.stat {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.stat .label {
  color: #7f8c8d;
  font-weight: 600;
  font-size: 0.9rem;
}

.stat .value {
  color: #2c3e50;
  font-weight: 700;
  font-size: 1.1rem;
}

.status {
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.85rem;
}

.status.active {
  background: #d4edda;
  color: #155724;
}

.status.pending {
  background: #fff3cd;
  color: #856404;
}

.status.completed {
  background: #cfe2ff;
  color: #084298;
}

.bracket-content {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 2rem;
  margin-bottom: 2rem;
}

.bracket-viz-panel,
.recording-panel {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.viz-header,
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 2px solid #ecf0f1;
}

.viz-header h2,
.panel-header h2 {
  margin: 0;
  color: #2c3e50;
  font-size: 1.3rem;
}

.viz-controls {
  display: flex;
  gap: 0.5rem;
}

.btn-icon {
  background: #ecf0f1;
  border: none;
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.btn-icon:hover {
  background: #bdc3c7;
  transform: translateY(-2px);
}

.viz-container {
  overflow: auto;
  max-height: 600px;
  border: 1px solid #ecf0f1;
  border-radius: 8px;
  padding: 1rem;
  background: #fafbfc;
  transform-origin: top left;
  transition: transform 0.2s;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: #7f8c8d;
}

.status-indicator.connected {
  color: #27ae60;
}

.status-indicator.disconnected {
  color: #e74c3c;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

.status-indicator.connected .dot {
  background: #27ae60;
}

.status-indicator.disconnected .dot {
  background: #e74c3c;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.active-match {
  background: #f8f9fa;
  padding: 1.5rem;
  border-radius: 8px;
  border-left: 4px solid #3498db;
}

.match-title {
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 1.5rem;
  font-size: 1.1rem;
}

.bye-match {
  text-align: center;
  padding: 1.5rem;
}

.bye-match p {
  color: #7f8c8d;
  margin: 0 0 1rem 0;
}

.match-recording {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 1rem;
  align-items: start;
}

.contestant-section {
  background: white;
  padding: 1rem;
  border-radius: 6px;
  border: 2px solid #ecf0f1;
}

.contestant-header {
  font-weight: 600;
  color: #34495e;
  margin-bottom: 1rem;
  font-size: 0.9rem;
}

.contestant-info {
  margin-bottom: 1rem;
}

.player-name {
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 0.75rem;
  word-break: break-word;
}

.score-input {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.score-input label {
  font-size: 0.85rem;
  color: #7f8c8d;
  font-weight: 500;
}

.score-input input {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.95rem;
}

.score-input input:disabled {
  background: #ecf0f1;
  color: #95a5a6;
}

.btn-contestant {
  width: 100%;
  padding: 0.75rem;
  border: 2px solid #ddd;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  color: #7f8c8d;
  transition: all 0.2s;
}

.btn-contestant:hover:not(:disabled) {
  border-color: #3498db;
  color: #3498db;
}

.btn-contestant.selected {
  background: #3498db;
  color: white;
  border-color: #3498db;
}

.btn-contestant:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.vs-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #bdc3c7;
  font-size: 1.2rem;
}

.submit-section {
  margin-top: 1.5rem;
}

.validation-errors {
  background: #f8d7da;
  border: 1px solid #f5c6cb;
  color: #721c24;
  padding: 0.75rem;
  border-radius: 4px;
  margin-bottom: 1rem;
  font-size: 0.9rem;
}

.validation-errors ul {
  margin: 0;
  padding-left: 1.5rem;
}

.validation-errors li {
  margin-bottom: 0.25rem;
}

.btn-submit {
  width: 100%;
  padding: 0.875rem;
  background: #27ae60;
  color: white;
  border: none;
  border-radius: 6px;
  font-weight: 600;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.3s;
}

.btn-submit:hover:not(:disabled) {
  background: #229954;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(39, 174, 96, 0.3);
}

.btn-submit:disabled {
  background: #bdc3c7;
  cursor: not-allowed;
}

.match-completed {
  background: #d4edda;
  border: 1px solid #c3e6cb;
  color: #155724;
  padding: 1rem;
  border-radius: 6px;
  margin-top: 1rem;
}

.match-completed p {
  margin: 0 0 0.75rem 0;
  font-weight: 600;
}

.completed-info {
  font-size: 0.9rem;
}

.completed-info div {
  margin-bottom: 0.25rem;
}

.no-match-selected {
  text-align: center;
  color: #95a5a6;
  padding: 2rem 1rem;
}

.message {
  margin-top: 1rem;
  padding: 1rem;
  border-radius: 6px;
  font-weight: 500;
}

.message.success {
  background: #d4edda;
  color: #155724;
  border: 1px solid #c3e6cb;
}

.message.error {
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
}

.footer-section {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 2rem;
}

.round-progress,
.leaderboard-preview {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.round-progress h3,
.leaderboard-preview h3 {
  margin-top: 0;
  color: #2c3e50;
  font-size: 1.1rem;
  border-bottom: 2px solid #ecf0f1;
  padding-bottom: 0.75rem;
}

.progress-bars {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.progress-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.round-label {
  font-size: 0.85rem;
  color: #7f8c8d;
  min-width: 70px;
  font-weight: 600;
}

.progress-bar {
  flex: 1;
  height: 24px;
  background: #ecf0f1;
  border-radius: 12px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #3498db, #2980b9);
  transition: width 0.3s;
}

.progress-text {
  font-size: 0.8rem;
  color: #7f8c8d;
  min-width: 50px;
  text-align: right;
}

.leaderboard-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.leaderboard-table thead {
  background: #f8f9fa;
  border-bottom: 2px solid #ecf0f1;
}

.leaderboard-table th {
  padding: 0.75rem;
  text-align: left;
  font-weight: 600;
  color: #34495e;
}

.leaderboard-table td {
  padding: 0.75rem;
  border-bottom: 1px solid #ecf0f1;
  color: #2c3e50;
}

.leaderboard-table .rank {
  font-weight: 700;
  color: #3498db;
}

.export-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  justify-content: flex-end;
}

.btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s;
  text-align: center;
}

.btn-primary {
  background: #3498db;
  color: white;
}

.btn-primary:hover {
  background: #2980b9;
  transform: translateY(-2px);
}

.btn-secondary {
  background: #95a5a6;
  color: white;
  font-size: 0.9rem;
}

.btn-secondary:hover {
  background: #7f8c8d;
  transform: translateY(-2px);
}

@media (max-width: 1024px) {
  .bracket-content {
    grid-template-columns: 1fr;
  }

  .footer-section {
    grid-template-columns: 1fr;
  }
}
</style>

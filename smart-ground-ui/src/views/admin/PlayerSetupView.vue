<template>
  <div class="player-setup-container">
    <div class="setup-header">
      <h1>Schützen-Setup</h1>
      <p class="subtitle">Schützen registrieren und Gruppen zuweisen</p>
    </div>

    <div class="setup-content">
      <!-- Left Panel: Player Registration -->
      <div class="left-panel">
        <div class="section">
          <h2>Schützen</h2>

          <!-- New Player Form -->
          <div class="new-player-form">
            <h3>Neuen Schützen hinzufügen</h3>
            <div class="form-group">
              <label for="firstName">First Name</label>
              <input
                id="firstName"
                v-model="newPlayer.firstName"
                type="text"
                placeholder="First name"
              />
            </div>
            <div class="form-group">
              <label for="lastName">Last Name</label>
              <input
                id="lastName"
                v-model="newPlayer.lastName"
                type="text"
                placeholder="Last name"
              />
            </div>
            <button
              class="btn btn-primary"
              :disabled="!newPlayer.firstName || !newPlayer.lastName"
              @click="registerNewPlayer"
            >
              Schützen registrieren
            </button>
            <div v-if="message" :class="['message', message.type]">
              {{ message.text }}
            </div>
          </div>

          <!-- Existing Players List -->
          <div class="existing-players">
            <h3>Verfügbare Schützen</h3>
            <div class="players-list">
              <div
                v-for="player in availablePlayers"
                :key="player.id"
                class="player-item"
                :class="{ selected: selectedPlayers.includes(player.id) }"
                @click="selectPlayer(player)"
              >
                <div class="checkbox">
                  <input
                    type="checkbox"
                    :checked="selectedPlayers.includes(player.id)"
                    @change="togglePlayerSelection(player.id)"
                  />
                </div>
                <div class="player-info">
                  <div class="player-name">{{ player.firstName }} {{ player.lastName }}</div>
                  <div class="player-meta">ID: {{ player.id }}</div>
                </div>
              </div>
            </div>
            <p v-if="availablePlayers.length === 0" class="empty-state">
              Keine Schützen verfügbar. Registrieren Sie einen neuen Schützen.
            </p>
          </div>
        </div>
      </div>

      <!-- Right Panel: Group Assignment -->
      <div class="right-panel">
        <div class="section">
          <h2>Groups</h2>

          <!-- New Group Form -->
          <div class="new-group-form">
            <h3>Create Group</h3>
            <div class="form-group">
              <label for="groupName">Group Name</label>
              <input
                id="groupName"
                v-model="newGroup.name"
                type="text"
                placeholder="e.g., Group A, Team 1"
              />
            </div>
            <button
              class="btn btn-secondary"
              :disabled="!newGroup.name"
              @click="createGroup"
            >
              Create Group
            </button>
          </div>

          <!-- Groups and Assignments -->
          <div class="groups-list">
            <h3>Current Groups</h3>
            <div v-if="groups.length === 0" class="empty-state">
              No groups yet. Create a group to start assigning players.
            </div>
            <div v-for="group in groups" :key="group.id" class="group-card">
              <div class="group-header">
                <h4>{{ group.name }}</h4>
                <button
                  class="btn-icon"
                  title="Delete group"
                  @click="deleteGroup(group.id)"
                >
                  ✕
                </button>
              </div>
              <div class="group-players">
                <div
                  v-for="player in group.players"
                  :key="player.id"
                  class="group-player"
                >
                  <span>{{ player.firstName }} {{ player.lastName }}</span>
                  <button
                    class="btn-remove"
                    @click="removePlayerFromGroup(group.id, player.id)"
                  >
                    ✕
                  </button>
                </div>
                <div v-if="group.players.length === 0" class="no-players">
                  Keine Schützen zugewiesen
                </div>
              </div>

              <!-- Assign Selected Players to This Group -->
              <div v-if="selectedPlayers.length > 0" class="assign-section">
                <button
                  class="btn btn-sm btn-assign"
                  @click="assignPlayersToGroup(group.id)"
                >
                  {{ selectedPlayers.length }} Schützen zuweisen
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Action Buttons -->
    <div class="setup-actions">
      <button class="btn btn-secondary" @click="goBack">Back</button>
      <button
        class="btn btn-primary btn-lg"
        :disabled="!canProceed"
        @click="proceedToSession"
      >
        Proceed to Session
      </button>
    </div>

    <!-- Validation Messages -->
    <div v-if="validationErrors.length > 0" class="validation-errors">
      <h3>Setup Issues</h3>
      <ul>
        <li v-for="(error, index) in validationErrors" :key="index">
          {{ error }}
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlaySessionStore } from '@/stores/playSessionStore'
import { useGuestStore } from '@/stores/guestStore'

const router = useRouter()
const playSessionStore = usePlaySessionStore()
const guestStore = useGuestStore()

// State
const newPlayer = ref({
  firstName: '',
  lastName: ''
})

const newGroup = ref({
  name: ''
})

const selectedPlayers = ref([])
const message = ref(null)
const groups = ref([])
const availablePlayers = ref([])

// Load players from session
onMounted(async () => {
  // Get players already in session
  if (playSessionStore.currentSession?.players) {
    availablePlayers.value = playSessionStore.currentSession.players
  }

  // Get groups from session
  if (playSessionStore.currentSession?.groups) {
    groups.value = playSessionStore.currentSession.groups
  }
})

// Methods
const registerNewPlayer = () => {
  if (!newPlayer.value.firstName || !newPlayer.value.lastName) {
    showMessage('Please fill in both first and last name', 'error')
    return
  }

  const player = {
    id: `player_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    firstName: newPlayer.value.firstName,
    lastName: newPlayer.value.lastName,
    createdAt: new Date().toISOString()
  }

  availablePlayers.value.push(player)

  // Update session store
  if (!playSessionStore.currentSession.players) {
    playSessionStore.currentSession.players = []
  }
  playSessionStore.currentSession.players.push(player)

  // Reset form
  newPlayer.value = { firstName: '', lastName: '' }
  showMessage('Schütze erfolgreich registriert', 'success')
}

const selectPlayer = (player) => {
  const index = selectedPlayers.value.indexOf(player.id)
  if (index > -1) {
    selectedPlayers.value.splice(index, 1)
  } else {
    selectedPlayers.value.push(player.id)
  }
}

const togglePlayerSelection = (playerId) => {
  const index = selectedPlayers.value.indexOf(playerId)
  if (index > -1) {
    selectedPlayers.value.splice(index, 1)
  } else {
    selectedPlayers.value.push(playerId)
  }
}

const createGroup = () => {
  if (!newGroup.value.name) {
    showMessage('Please enter a group name', 'error')
    return
  }

  // Check for duplicate names
  if (groups.value.some(g => g.name === newGroup.value.name)) {
    showMessage('A group with this name already exists', 'error')
    return
  }

  const group = {
    id: `group_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    name: newGroup.value.name,
    players: [],
    createdAt: new Date().toISOString()
  }

  groups.value.push(group)

  // Update session store
  if (!playSessionStore.currentSession.groups) {
    playSessionStore.currentSession.groups = []
  }
  playSessionStore.currentSession.groups.push(group)

  newGroup.value = { name: '' }
  showMessage('Group created successfully', 'success')
}

const assignPlayersToGroup = (groupId) => {
  const group = groups.value.find(g => g.id === groupId)
  if (!group) return

  selectedPlayers.value.forEach(playerId => {
    const player = availablePlayers.value.find(p => p.id === playerId)
    if (player && !group.players.some(p => p.id === playerId)) {
      group.players.push(player)
    }
  })

  selectedPlayers.value = []
  showMessage('Schützen der Gruppe zugewiesen', 'success')
}

const removePlayerFromGroup = (groupId, playerId) => {
  const group = groups.value.find(g => g.id === groupId)
  if (group) {
    group.players = group.players.filter(p => p.id !== playerId)
  }
}

const deleteGroup = (groupId) => {
  if (confirm('Are you sure you want to delete this group?')) {
    groups.value = groups.value.filter(g => g.id !== groupId)
  }
}

const showMessage = (text, type = 'info') => {
  message.value = { text, type }
  setTimeout(() => {
    message.value = null
  }, 3000)
}

const goBack = () => {
  router.back()
}

// Computed properties
const validationErrors = computed(() => {
  const errors = []

  if (availablePlayers.value.length === 0) {
    errors.push('At least one player is required')
  }

  if (groups.value.length === 0) {
    errors.push('At least one group is required')
  }

  const assignedPlayers = new Set()
  groups.value.forEach(group => {
    group.players.forEach(player => {
      assignedPlayers.add(player.id)
    })
  })

  if (assignedPlayers.size === 0) {
    errors.push('All players must be assigned to a group')
  }

  if (assignedPlayers.size < availablePlayers.value.length) {
    const unassigned = availablePlayers.value.length - assignedPlayers.size
    errors.push(`${unassigned} player(s) not assigned to any group`)
  }

  return errors
})

const canProceed = computed(() => {
  return validationErrors.value.length === 0
})

const proceedToSession = () => {
  if (!canProceed.value) {
    showMessage('Please fix all issues before proceeding', 'error')
    return
  }

  // Update session with final player and group assignments
  playSessionStore.currentSession.players = availablePlayers.value
  playSessionStore.currentSession.groups = groups.value

  // Navigate to competition or next phase
  router.push({ name: 'CompetitionMode' })
}
</script>

<style scoped>
.player-setup-container {
  min-height: 100vh;
  background: var(--sg-bg-page);
  padding: 2rem;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.setup-header {
  text-align: center;
  margin-bottom: 2rem;
}

.setup-header h1 {
  font-size: 2.5rem;
  color: var(--sg-text-primary);
  margin: 0 0 0.5rem 0;
}

.subtitle {
  color: var(--sg-text-muted);
  font-size: 1.1rem;
  margin: 0;
}

.setup-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
  margin-bottom: 2rem;
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
}

.left-panel,
.right-panel {
  background: var(--sg-bg-card);
  border-radius: 12px;
  padding: 2rem;
  box-shadow: var(--sg-shadow-md);
}

.section h2 {
  color: var(--sg-text-primary);
  margin-top: 0;
  border-bottom: 2px solid var(--sg-border);
  padding-bottom: 1rem;
}

.new-player-form,
.new-group-form {
  background: var(--sg-bg-panel);
  padding: 1.5rem;
  border-radius: 8px;
  margin-bottom: 2rem;
}

.new-player-form h3,
.new-group-form h3 {
  margin-top: 0;
  color: var(--sg-text-muted);
  font-size: 1.1rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: var(--sg-text-muted);
  font-weight: 500;
  font-size: 0.9rem;
}

.form-group input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid var(--sg-border-input);
  border-radius: 6px;
  font-size: 0.95rem;
  transition: border-color 0.3s;
}

.form-group input:focus {
  outline: none;
  border-color: var(--sg-accent);
  box-shadow: 0 0 0 3px var(--sg-accent-tint);
}

.btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  font-size: 0.95rem;
  transition: all 0.3s ease;
}

.btn-primary {
  background: var(--sg-accent-hover);
  color: var(--sg-text-primary);
}

.btn-primary:hover:not(:disabled) {
  background: var(--sg-accent);
  transform: translateY(-2px);
  box-shadow: var(--sg-shadow-md);
}

.btn-primary:disabled {
  background: var(--sg-color-neutral-bg);
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--sg-text-faint);
  color: var(--sg-text-primary);
}

.btn-secondary:hover {
  background: var(--sg-text-muted);
  transform: translateY(-2px);
}

.btn-sm {
  padding: 0.5rem 1rem;
  font-size: 0.85rem;
}

.btn-assign {
  background: var(--sg-color-success);
  color: var(--sg-text-primary);
  width: 100%;
}

.btn-assign:hover {
  background: var(--sg-color-success-text);
}

.btn-lg {
  padding: 1rem 2rem;
  font-size: 1.1rem;
}

.btn-icon {
  background: none;
  border: none;
  color: var(--sg-color-danger);
  cursor: pointer;
  font-size: 1.5rem;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: background 0.2s;
}

.btn-icon:hover {
  background: var(--sg-danger-subtle);
}

.btn-remove {
  background: none;
  border: none;
  color: var(--sg-color-danger);
  cursor: pointer;
  font-size: 0.9rem;
  padding: 2px 6px;
}

.message {
  margin-top: 1rem;
  padding: 1rem;
  border-radius: 6px;
  text-align: center;
}

.message.success {
  background: var(--sg-color-success-bg);
  color: var(--sg-color-success-text);
  border: 1px solid color-mix(in srgb, var(--sg-color-success) 40%, transparent);
}

.message.error {
  background: var(--sg-color-danger-bg);
  color: var(--sg-color-danger-text);
  border: 1px solid color-mix(in srgb, var(--sg-color-danger) 40%, transparent);
}

.players-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.player-item {
  display: flex;
  align-items: center;
  padding: 1rem;
  background: var(--sg-bg-panel);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border: 2px solid transparent;
}

.player-item:hover {
  background: var(--sg-border);
}

.player-item.selected {
  background: var(--sg-color-success-bg);
  border-color: var(--sg-color-success);
}

.checkbox {
  margin-right: 1rem;
}

.checkbox input[type='checkbox'] {
  cursor: pointer;
  width: 18px;
  height: 18px;
}

.player-info {
  flex: 1;
}

.player-name {
  font-weight: 600;
  color: var(--sg-text-primary);
}

.player-meta {
  font-size: 0.8rem;
  color: var(--sg-text-muted);
  margin-top: 0.25rem;
}

.groups-list {
  margin-top: 2rem;
}

.groups-list h3 {
  color: var(--sg-text-muted);
  font-size: 1.1rem;
  margin-top: 0;
}

.group-card {
  background: var(--sg-bg-panel);
  padding: 1.5rem;
  border-radius: 8px;
  margin-bottom: 1rem;
  border-left: 4px solid var(--sg-accent-hover);
}

.group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.group-header h4 {
  margin: 0;
  color: var(--sg-text-primary);
}

.group-players {
  background: var(--sg-bg-card);
  padding: 1rem;
  border-radius: 6px;
  margin-bottom: 1rem;
  min-height: 40px;
}

.group-player {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--sg-border);
}

.group-player:last-child {
  border-bottom: none;
}

.no-players {
  color: var(--sg-text-faint);
  font-style: italic;
  padding: 0.5rem 0;
}

.assign-section {
  margin-top: 0.5rem;
}

.empty-state {
  color: var(--sg-text-faint);
  font-style: italic;
  text-align: center;
  padding: 1rem;
}

.setup-actions {
  display: flex;
  justify-content: center;
  gap: 1rem;
  max-width: 1400px;
  margin: 0 auto;
}

.validation-errors {
  background: var(--sg-color-danger-bg);
  border: 1px solid color-mix(in srgb, var(--sg-color-danger) 40%, transparent);
  color: var(--sg-color-danger-text);
  padding: 1.5rem;
  border-radius: 8px;
  max-width: 1400px;
  margin: 0 auto;
}

.validation-errors h3 {
  margin-top: 0;
}

.validation-errors ul {
  margin: 0;
  padding-left: 1.5rem;
}

.validation-errors li {
  margin-bottom: 0.5rem;
}

@media (max-width: 768px) {
  .setup-content {
    grid-template-columns: 1fr;
    gap: 1.5rem;
  }

  .setup-actions {
    flex-direction: column;
  }

  .btn-lg {
    width: 100%;
  }

  .setup-header h1 {
    font-size: 1.8rem;
  }
}
</style>

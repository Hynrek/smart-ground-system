<template>
  <div class="player-setup">
    <div class="setup-header">
      <h1>Schützen hinzufügen</h1>
      <p>Bestelle die Schützen für diese Runde</p>
    </div>

    <div class="player-list-container">
      <div class="player-list">
        <div v-for="(player, idx) in players" :key="player.id" class="player-item">
          <div class="player-info">
            <span class="player-index">{{ idx + 1 }}</span>
            <div class="player-details">
              <span class="player-name">{{ player.displayName }}</span>
              <span class="player-type">{{ player.type === 'user' ? 'Benutzer' : 'Gast' }}</span>
            </div>
          </div>
          <div class="player-actions">
            <button v-if="idx > 0" class="btn-up" title="Nach oben" @click="movePlayerUp(idx)">
              ↑
            </button>
            <button v-if="idx < players.length - 1" class="btn-down" title="Nach unten" @click="movePlayerDown(idx)">
              ↓
            </button>
            <button v-if="players.length > 1" class="btn-remove" title="Entfernen" @click="removePlayer(idx)">
              ×
            </button>
          </div>
        </div>
      </div>

      <div class="add-guest-section">
        <div class="input-group">
          <input
            v-model="newGuestName"
            type="text"
            placeholder="Name des Gastes..."
            @keyup.enter="addGuest"
          />
          <button class="btn-add-guest" @click="addGuest">+ Gast</button>
        </div>
      </div>
    </div>

    <div class="setup-footer">
      <button class="btn-start" :disabled="players.length < 1" @click="startGame">
        Jetzt starten
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/authStore.js';
import { useGuestStore } from '@/stores/guestStore.js';
import { usePlaySessionStore } from '@/stores/playSessionStore.js';
import { useShooterRemoteStore } from '@/stores/shooterRemoteStore.js';

const router = useRouter();
const authStore = useAuthStore();
const guestStore = useGuestStore();
const playStore = usePlaySessionStore();
const shooterStore = useShooterRemoteStore();

const newGuestName = ref('');

const players = ref([
  {
    id: authStore.user?.id || 'self',
    type: 'user',
    userId: authStore.user?.id || null,
    displayName: authStore.user?.name || 'Du',
  },
]);

const addGuest = () => {
  if (!newGuestName.value.trim()) return;
  const guest = guestStore.addGuest(newGuestName.value);
  players.value = [
    ...players.value,
    {
      id: guest.id,
      type: 'guest',
      userId: null,
      displayName: guest.displayName,
    },
  ];
  newGuestName.value = '';
};

const removePlayer = (index) => {
  if (players.value.length <= 1) return;
  players.value = players.value.filter((_, i) => i !== index);
};

const movePlayerUp = (index) => {
  if (index <= 0) return;
  const arr = [...players.value];
  [arr[index - 1], arr[index]] = [arr[index], arr[index - 1]];
  players.value = arr;
};

const movePlayerDown = (index) => {
  if (index >= players.value.length - 1) return;
  const arr = [...players.value];
  [arr[index], arr[index + 1]] = [arr[index + 1], arr[index]];
  players.value = arr;
};

const startGame = () => {
  if (players.value.length < 1) return;
  playStore.setupPlayers(players.value);
  router.push(`/remote/${shooterStore.selectedRangeId}`);
};
</script>

<style scoped>
.player-setup {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: 20px;
  background: linear-gradient(135deg, rgba(10, 10, 18, 0.98) 0%, rgba(18, 18, 28, 0.98) 100%);
  color: rgba(255, 255, 255, 0.9);
}

.setup-header {
  text-align: center;
  margin-bottom: 40px;
}

.setup-header h1 {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: #fff;
}

.setup-header p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
}

.player-list-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 500px;
  margin: 0 auto;
  width: 100%;
}

.player-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 12px;
}

.player-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.player-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.player-index {
  font-weight: 700;
  color: rgba(79, 195, 247, 0.8);
  min-width: 24px;
  text-align: center;
}

.player-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.player-name {
  font-size: 14px;
  font-weight: 500;
  color: #fff;
}

.player-type {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.player-actions {
  display: flex;
  gap: 6px;
}

.btn-up,
.btn-down,
.btn-remove {
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s;
}

.btn-up:hover,
.btn-down:hover {
  background: rgba(79, 195, 247, 0.2);
  border-color: rgba(79, 195, 247, 0.4);
  color: #4fc3f7;
}

.btn-remove:hover {
  background: rgba(252, 129, 129, 0.2);
  border-color: rgba(252, 129, 129, 0.4);
  color: #fc8181;
}

.add-guest-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-group {
  display: flex;
  gap: 8px;
}

.input-group input {
  flex: 1;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: #fff;
  font-family: inherit;
}

.input-group input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.input-group input:focus {
  outline: none;
  border-color: rgba(79, 195, 247, 0.5);
  background: rgba(255, 255, 255, 0.1);
}

.btn-add-guest {
  padding: 10px 16px;
  background: rgba(76, 175, 80, 0.2);
  border: 1px solid rgba(76, 175, 80, 0.3);
  border-radius: 8px;
  color: rgba(76, 175, 80, 0.9);
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-add-guest:hover {
  background: rgba(76, 175, 80, 0.3);
  border-color: rgba(76, 175, 80, 0.5);
}

.setup-footer {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  justify-content: center;
}

.btn-start {
  padding: 12px 40px;
  background: rgba(79, 195, 247, 0.2);
  border: 1px solid rgba(79, 195, 247, 0.4);
  border-radius: 8px;
  color: #4fc3f7;
  font-weight: 600;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.btn-start:hover:not(:disabled) {
  background: rgba(79, 195, 247, 0.3);
  border-color: rgba(79, 195, 247, 0.6);
}

.btn-start:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

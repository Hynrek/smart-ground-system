<template>
  <div class="rotte-card">
    <div class="rotte-header">
      <input
        v-model="localName"
        class="rotte-name-input"
        placeholder="Rotte benennen..."
        @blur="emit('rename', localName)"
      />
      <span class="payment-summary">{{ paidCount }}/{{ rotte.players.length }} bezahlt</span>
      <button class="icon-btn" title="Rotte löschen" @click="emit('remove')">
        <Icons icon="trash" :size="13" color="rgba(252,129,129,0.7)" />
      </button>
    </div>

    <div class="player-list">
      <div v-for="(player, idx) in rotte.players" :key="player.id" class="player-row">
        <span class="player-num">{{ idx + 1 }}.</span>
        <span class="player-name-locked">{{ player.displayName }}</span>
        <PaymentChip :paid="player.paid" @toggle="emit('toggle-paid', player.id)" />
        <button
          v-if="rotte.players.length > 1"
          class="icon-btn"
          @click="emit('remove-player', player.id)"
        >
          <Icons icon="x" :size="11" color="rgba(252,129,129,0.7)" />
        </button>
      </div>
    </div>

    <div class="add-section">
      <button
        class="add-player-btn"
        :disabled="availableUsers.length === 0"
        :title="availableUsers.length === 0 ? 'Alle Schützen bereits zugewiesen' : ''"
        @click="showPicker = true"
      >
        + Schütze hinzufügen
      </button>
      <UserPickerDropdown
        v-if="showPicker"
        :users="availableUsers"
        @select="onPickUser"
        @close="showPicker = false"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Icons from '@/components/Icons.vue'
import PaymentChip from './PaymentChip.vue'
import UserPickerDropdown from './UserPickerDropdown.vue'

const props = defineProps({
  rotte: { type: Object, required: true },
  availableUsers: { type: Array, required: true },
})

const emit = defineEmits(['rename', 'remove', 'add-player', 'remove-player', 'toggle-paid'])

const localName = ref(props.rotte.name)
const showPicker = ref(false)

watch(() => props.rotte.name, (val) => { localName.value = val })

const paidCount = computed(() => props.rotte.players.filter(p => p.paid).length)

const onPickUser = (user) => {
  emit('add-player', user)
  showPicker.value = false
}
</script>

<style scoped>
.rotte-card {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 14px;
  padding: 14px 16px;
  display: flex; flex-direction: column; gap: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.rotte-header {
  display: flex; align-items: center; gap: 10px;
}

.rotte-name-input {
  flex: 1; background: transparent;
  border: none; border-bottom: 1px solid var(--sg-border);
  color: var(--sg-text-primary); font-size: 14px; font-weight: 700; font-family: inherit;
  padding: 2px 4px; outline: none;
}
.rotte-name-input:focus { border-bottom-color: var(--sg-accent); }

.payment-summary {
  font-size: 11px; color: var(--sg-text-faint); white-space: nowrap;
}

.player-list { display: flex; flex-direction: column; gap: 6px; }

.player-row {
  display: flex; align-items: center; gap: 8px;
}

.player-num {
  font-size: 12px; color: var(--sg-text-faint); width: 20px; flex-shrink: 0;
}

.player-name-locked {
  flex: 1;
  background: var(--sg-bg-panel);
  border: 1px solid var(--sg-border);
  border-radius: 8px;
  color: var(--sg-text-primary); font-size: 13px;
  padding: 6px 10px;
}

.icon-btn {
  background: none; border: none; cursor: pointer;
  padding: 4px; border-radius: 6px;
  display: flex; align-items: center; flex-shrink: 0;
  transition: background 0.15s;
}
.icon-btn:hover { background: var(--sg-color-danger-bg); }

.add-section {
  position: relative;
}

.add-player-btn {
  width: 100%;
  background: transparent;
  border: 1px dashed color-mix(in srgb, var(--sg-accent) 35%, transparent);
  border-radius: 8px;
  color: var(--sg-color-info-text);
  font-size: 12px; font-family: inherit;
  padding: 7px; cursor: pointer; transition: all 0.15s;
}
.add-player-btn:hover:not(:disabled) {
  background: rgba(79,195,247,0.06);
  border-color: var(--sg-accent);
}
.add-player-btn:disabled {
  opacity: 0.35; cursor: not-allowed;
  border-color: var(--sg-border); color: var(--sg-text-faint);
}
</style>

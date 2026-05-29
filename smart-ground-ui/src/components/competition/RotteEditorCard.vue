<!-- src/components/competition/RotteEditorCard.vue -->
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
      <button class="icon-btn" @click="emit('remove')" title="Rotte löschen">
        <Icons icon="trash" :size="13" color="rgba(252,129,129,0.7)" />
      </button>
    </div>

    <div class="player-list">
      <div v-for="(player, idx) in rotte.players" :key="player.id" class="player-row">
        <span class="player-num">{{ idx + 1 }}.</span>
        <input
          class="player-name-input"
          :value="player.displayName"
          placeholder="Name..."
          maxlength="40"
          @input="emit('update-player-name', player.id, $event.target.value)"
        />
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

    <button class="add-player-btn" @click="emit('add-player')">
      + Schütze hinzufügen
    </button>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Icons from '@/components/Icons.vue'
import PaymentChip from './PaymentChip.vue'

const props = defineProps({
  rotte: { type: Object, required: true },
})

const emit = defineEmits(['rename', 'remove', 'add-player', 'remove-player', 'update-player-name', 'toggle-paid'])

const localName = ref(props.rotte.name)

watch(() => props.rotte.name, (val) => { localName.value = val })

const paidCount = computed(() => props.rotte.players.filter(p => p.paid).length)
</script>

<style scoped>
.rotte-card {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 14px;
  padding: 14px 16px;
  display: flex; flex-direction: column; gap: 12px;
}

.rotte-header {
  display: flex; align-items: center; gap: 10px;
}

.rotte-name-input {
  flex: 1; background: transparent;
  border: none; border-bottom: 1px solid rgba(255,255,255,0.15);
  color: #fff; font-size: 14px; font-weight: 700; font-family: inherit;
  padding: 2px 4px; outline: none;
}
.rotte-name-input:focus { border-bottom-color: rgba(79,195,247,0.5); }

.payment-summary {
  font-size: 11px; color: rgba(255,255,255,0.3); white-space: nowrap;
}

.player-list { display: flex; flex-direction: column; gap: 6px; }

.player-row {
  display: flex; align-items: center; gap: 8px;
}

.player-num {
  font-size: 12px; color: rgba(255,255,255,0.3); width: 20px; flex-shrink: 0;
}

.player-name-input {
  flex: 1;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 8px;
  color: #fff; font-size: 13px; font-family: inherit;
  padding: 6px 10px; outline: none;
}
.player-name-input:focus { border-color: rgba(79,195,247,0.3); }

.icon-btn {
  background: none; border: none; cursor: pointer;
  padding: 4px; border-radius: 6px;
  display: flex; align-items: center; flex-shrink: 0;
  transition: background 0.15s;
}
.icon-btn:hover { background: rgba(255,255,255,0.07); }

.add-player-btn {
  background: transparent;
  border: 1px dashed rgba(79,195,247,0.2);
  border-radius: 8px;
  color: rgba(79,195,247,0.5);
  font-size: 12px; font-family: inherit;
  padding: 7px; cursor: pointer; transition: all 0.15s;
}
.add-player-btn:hover {
  background: rgba(79,195,247,0.05);
  border-color: rgba(79,195,247,0.35);
  color: rgba(79,195,247,0.8);
}
</style>

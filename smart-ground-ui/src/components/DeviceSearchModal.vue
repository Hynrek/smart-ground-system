<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal" role="dialog" aria-modal="true" aria-label="Gerät zuordnen">
      <div class="modal-header">
        <h3 class="modal-title">Gerät zuordnen</h3>
        <button class="modal-close" aria-label="Schliessen" @click="$emit('close')">×</button>
      </div>

      <div class="modal-search">
        <input
          v-model="query"
          type="text"
          class="search-input"
          placeholder="Suchen…"
          aria-label="Gerät suchen"
          autofocus
        />
      </div>

      <div class="modal-list">
        <button
          v-for="device in filtered"
          :key="device.id"
          class="device-row"
          @click="$emit('select', device)"
        >
          <div class="device-row-info">
            <div class="device-row-name">{{ device.alias }}</div>
            <div class="device-row-box">{{ boxLabel(device) }}</div>
          </div>
          <TypeChip :type="device.groupName ?? device.deviceType" />
        </button>
        <p v-if="filtered.length === 0" class="empty-hint">Keine freien Geräte verfügbar</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import TypeChip from './TypeChip.vue';

const props = defineProps({
  devices: { type: Array, required: true },
});

defineEmits(['select', 'close']);

const query = ref('');

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return props.devices;
  return props.devices.filter((d) => d.alias?.toLowerCase().includes(q));
});

const boxLabel = (device) => (device.smartBoxId ? device.smartBoxId.slice(0, 8) + '…' : '–');
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: var(--sg-bg-card);
  border-radius: 14px;
  width: 420px;
  max-width: calc(100vw - 32px);
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--sg-shadow-lg);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
  border-bottom: 1px solid var(--sg-border);
}

.modal-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--sg-text-primary);
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  font-size: 20px;
  color: var(--sg-text-faint);
  cursor: pointer;
  padding: 2px 6px;
  line-height: 1;
  border-radius: 6px;
  transition: background 0.15s;
}

.modal-close:hover { background: var(--sg-bg-panel); }

.modal-search {
  padding: 12px 16px;
  border-bottom: 1px solid var(--sg-border);
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid var(--sg-border);
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.search-input:focus { border-color: var(--sg-accent); }

.modal-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.device-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 48px;
  padding: 10px 20px;
  background: none;
  border: none;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.12s;
}

.device-row:hover { background: var(--sg-bg-panel); }

.device-row:focus-visible {
  outline: 2px solid var(--sg-accent);
  outline-offset: -2px;
}

.device-row-info { min-width: 0; }

.device-row-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--sg-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-row-box {
  font-size: 11px;
  color: var(--sg-text-faint);
}

.empty-hint {
  text-align: center;
  padding: 24px 16px;
  color: var(--sg-text-faint);
  font-size: 13px;
  margin: 0;
}
</style>

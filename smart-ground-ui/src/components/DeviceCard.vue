<template>
  <div class="device-card" :class="{ fired }">
    <div class="card-header">
      <div class="device-name">
        <div class="name">{{ device.alias }}</div>
        <div class="box-id" :title="boxIdRaw || undefined">{{ boxName }}</div>
      </div>
      <!-- Status indicator: show lock pill when blocked, otherwise StatusDot -->
      <span
        v-if="device.adminBlocked"
        class="status-pill status-pill--admin"
        title="Admin-gesperrt"
      >🔒</span>
      <span
        v-else-if="device.blocked"
        class="status-pill status-pill--blocked"
        title="Gesperrt"
      >🔒</span>
      <StatusDot v-else :status="getStatus(device)" />
    </div>
    <div class="card-footer">
      <TypeChip :type="device.groupName ?? device.deviceType" />
      <div class="card-actions">
        <!-- Block/Unblock -->
        <template v-if="showBlockControls">
          <template v-if="device.adminBlocked">
            <button
              class="block-action-btn"
              :disabled="!isAdmin || blocking"
              :title="isAdmin ? 'Admin-Sperre aufheben' : 'Nur Admin kann aufheben'"
              @click="handleUnblock"
            >
              Entsperren
            </button>
          </template>
          <template v-else-if="device.blocked">
            <button class="block-action-btn" :disabled="blocking" @click="handleUnblock">
              Entsperren
            </button>
          </template>
          <template v-else>
            <button class="block-action-btn block-action-btn--block" :disabled="blocking" @click="handleBlock">
              Sperren
            </button>
          </template>
        </template>
        <!-- Fire button -->
        <button v-if="actionMode" class="fire-btn" :class="{ fired }" @click="$emit('fire')">
          <Icons icon="fire" :size="12" />
          <span>{{ fired ? 'Ausgelöst!' : 'Auslösen' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import StatusDot from './StatusDot.vue';
import TypeChip from './TypeChip.vue';
import Icons from './Icons.vue';
import { useSmartBoxStore } from '../stores/smartBoxStore.js';
import { useDeviceStore } from '../stores/deviceStore.js';
import { useAuthStore } from '../stores/authStore.js';
import { ADMIN_PERMISSION } from '../constants/deviceTypes.js';

const props = defineProps({
  device: {
    type: Object,
    required: true,
  },
  fired: Boolean,
  actionMode: Boolean,
  showBlockControls: { type: Boolean, default: true },
});

defineEmits(['fire']);

const smartBoxStore = useSmartBoxStore();
const deviceStore = useDeviceStore();
const authStore = useAuthStore();

const boxIdRaw = computed(() => props.device.smartBoxId ?? props.device.boxId ?? null);
const boxName = computed(() => {
  const id = boxIdRaw.value;
  const alias = smartBoxStore.smartboxes.find(b => b.id === id)?.alias;
  if (alias) return alias;
  if (!id) return '–';
  return id.length > 8 ? id.slice(0, 8) + '…' : id;
});

const isAdmin = computed(() => authStore.hasPermission(ADMIN_PERMISSION));
const blocking = ref(false);

const handleBlock = async () => {
  blocking.value = true;
  try {
    await deviceStore.blockDevice(props.device.id);
  } finally {
    blocking.value = false;
  }
};

const handleUnblock = async () => {
  blocking.value = true;
  try {
    await deviceStore.unblockDevice(props.device.id);
  } finally {
    blocking.value = false;
  }
};

function getStatus(device) {
  if (device.healthy === false) return 'warn';
  return 'online';
}
</script>

<style scoped>
.device-card {
  background: var(--sg-bg-card);
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.07), 0 4px 12px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid var(--sg-border);
  transition: border-color 0.2s;
}

.device-card.fired {
  border-color: var(--sg-accent);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.device-name {
  flex: 1;
  min-width: 0;
}

.name {
  font-weight: 600;
  font-size: 14.5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.box-id {
  font-size: 12px;
  color: var(--sg-text-faint);
  margin-top: 2px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.fire-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  background: var(--sg-accent);
  color: var(--sg-surface-0);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  font-family: inherit;
  transition: background 0.2s;
}

.fire-btn.fired {
  background: var(--sg-accent);
}

.fire-btn:hover {
  background: var(--sg-accent-hover);
}

.fire-btn.fired:hover {
  background: var(--sg-accent-hover);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  border-radius: 4px;
  padding: 2px 5px;
}
.status-pill--admin { background: rgba(226,75,74,.12); color: var(--sg-color-danger-text); }
.status-pill--blocked { background: rgba(237,137,54,.12); color: var(--sg-color-warning-text); }

.block-action-btn {
  font-size: 11.5px;
  font-weight: 500;
  padding: 4px 9px;
  border-radius: 6px;
  border: 1px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
  background: var(--sg-color-info-bg);
  color: var(--sg-color-info-text);
  cursor: pointer;
  font-family: inherit;
  transition: all .15s;
}
.block-action-btn--block {
  border-color: var(--sg-border);
  background: var(--sg-bg-card);
  color: var(--sg-text-muted);
}
.block-action-btn:hover:not(:disabled) { opacity: .85; }
.block-action-btn:disabled { opacity: .45; cursor: not-allowed; }
</style>

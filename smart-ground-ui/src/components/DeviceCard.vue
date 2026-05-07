<template>
  <div class="device-card" :class="{ fired }">
    <div class="card-header">
      <div class="device-name">
        <div class="name">{{ device.alias }}</div>
        <div class="box-id">{{ device.smartBoxId || device.boxId || '–' }}</div>
      </div>
      <StatusDot :status="getStatus(device)" />
    </div>
    <div class="card-footer">
      <TypeChip :type="device.groupName ?? device.deviceType" />
      <button v-if="actionMode" class="fire-btn" :class="{ fired }" @click="$emit('fire')">
        <Icons icon="fire" :size="12" />
        <span>{{ fired ? 'Ausgelöst!' : 'Auslösen' }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import StatusDot from './StatusDot.vue';
import TypeChip from './TypeChip.vue';
import Icons from './Icons.vue';

const props = defineProps({
  device: {
    type: Object,
    required: true,
  },
  fired: Boolean,
  actionMode: Boolean,
});

defineEmits(['fire']);

function getStatus(device) {
  if (device.blocked) return 'offline';
  if (device.healthy) return 'online';
  return 'warn';
}
</script>

<style scoped>
.device-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.07), 0 4px 12px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid #f0f4f8;
  transition: border-color 0.2s;
}

.device-card.fired {
  border-color: #4fc3f7;
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
  color: #a0aec0;
  margin-top: 2px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fire-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  font-family: inherit;
  transition: background 0.2s;
}

.fire-btn.fired {
  background: #4fc3f7;
}

.fire-btn:hover {
  background: #0f0f1a;
}

.fire-btn.fired:hover {
  background: #2ba4d0;
}
</style>

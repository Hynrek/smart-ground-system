<template>
  <div class="smartboxes-view">
    <div class="view-content">
      <div class="view-header">
        <div>
          <h1>SmartBoxen</h1>
          <p class="subtitle">
            {{ smartBoxStore.smartboxes.length }} Boxen · {{ deviceStore.devices.length }}
            Geräte total · automatisch erkannt
          </p>
        </div>
        <Button
          :variant="actionMode ? 'primary' : 'ghost'"
          @click="actionMode = !actionMode"
        >
          <template #icon>
            <Icons icon="bolt" :size="13" />
          </template>
          {{ actionMode ? 'Aktionsmodus aktiv' : 'Aktionsmodus' }}
        </Button>
      </div>

      <!-- Type filter -->
      <div class="filter-group">
        <button
          v-for="type in filterOptions"
          :key="type"
          :class="{ active: typeFilter === type }"
          class="filter-chip"
          @click="typeFilter = type"
        >
          {{ type }}
          <span class="filter-count">{{ getFilterCount(type) }}</span>
        </button>
      </div>

      <!-- SmartBox cards -->
      <div class="boxes-list">
        <SmartBoxCard
          v-for="box in visibleBoxes"
          :key="box.id"
          :box="box"
          :devices="getFilteredDevices(box.id)"
          :all-devices-count="
            deviceStore.devices.filter((d) => (d.smartBoxId ?? d.boxId) === box.id).length
          "
          :action-mode="actionMode"
          :fired-devices="firedDevices"
          @fire="fireDevice"
          @add-device="addDevice"
          @remove-device="removeDevice"
          @update-device="updateDevice"
          @rename-box="renameBox"
        />
      </div>

      <div v-if="visibleBoxes.length === 0" class="empty-state">
        <div class="empty-text">
          Keine Boxen mit «{{ typeFilter }}»-Geräten gefunden.
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { useSmartBoxStore } from '../stores/smartBoxStore.js';
import { useDeviceStore } from '../stores/deviceStore.js';
import * as deviceApi from '../services/deviceApi.js';
import Button from '../components/Button.vue';
import Icons from '../components/Icons.vue';
import SmartBoxCard from '../components/SmartBoxCard.vue';

const smartBoxStore = useSmartBoxStore();
const deviceStore = useDeviceStore();

watch(
  () => smartBoxStore.smartboxes,
  (boxes) => {
    boxes.forEach((box) => deviceStore.loadDevicesForBox(box.id));
  },
  { immediate: true },
);

const typeFilter = ref('Alle');
const actionMode = ref(false);
const firedDevices = ref({});

const filterOptions = computed(() => {
  const groupNames = new Set(
    deviceStore.devices
      .map((d) => d.groupName)
      .filter((name) => name)
  );
  return ['Alle', ...Array.from(groupNames).sort()];
});

const visibleBoxes = computed(() => {
  if (typeFilter.value === 'Alle') {
    return smartBoxStore.smartboxes;
  }
  return smartBoxStore.smartboxes.filter((box) => {
    const devs = deviceStore.devices.filter((d) => (d.smartBoxId ?? d.boxId) === box.id);
    return devs.some((d) => d.groupName === typeFilter.value);
  });
});

const getFilteredDevices = (boxId) => {
  const devs = deviceStore.devices.filter((d) => (d.smartBoxId ?? d.boxId) === boxId);
  if (typeFilter.value === 'Alle') {
    return devs;
  }
  return devs.filter((d) => d.groupName === typeFilter.value);
};

const getFilterCount = (type) => {
  if (type === 'Alle') {
    return deviceStore.devices.length;
  }
  return deviceStore.devices.filter((d) => d.groupName === type).length;
};

const fireDevice = async (deviceId) => {
  firedDevices.value[deviceId] = true;
  setTimeout(() => {
    delete firedDevices.value[deviceId];
  }, 1500);

  try {
    await deviceApi.sendDeviceCommand(deviceId);
  } catch (e) {
    console.error('Failed to send device command:', e);
  }
};

const addDevice = async ({ boxId, device }) => {
  await deviceStore.addDevice({
    ...device,
    boxId,
  });
};

const removeDevice = (deviceId) => {
  deviceStore.removeDevice(deviceId);
};

const updateDevice = async ({ deviceId, updates }) => {
  if ('rangeId' in updates) {
    deviceStore.updateDeviceLocal(deviceId, updates);
    try {
      if (updates.rangeId) {
        await deviceApi.assignDeviceToRange(deviceId, updates.rangeId);
      } else {
        await deviceApi.removeDeviceFromRange(deviceId);
      }
    } catch (e) {
      console.error('Failed to update device range:', e);
    }
  } else {
    await deviceStore.updateDevice(deviceId, updates);
  }
};

const renameBox = ({ boxId, alias }) => {
  smartBoxStore.updateSmartBox(boxId, { alias });
  smartBoxStore.saveSmartBox(boxId);
};
</script>

<style scoped>
.smartboxes-view {
  padding: 28px;
  flex: 1;
  overflow-y: auto;
}

.view-content {
  max-width: 960px;
}

.view-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

h1 {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.subtitle {
  font-size: 13px;
  color: #718096;
  margin-top: 4px;
}

.filter-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.filter-chip {
  padding: 5px 13px;
  border-radius: 99px;
  border: 1.5px solid #e2e8f0;
  background: #fff;
  color: #4a5568;
  cursor: pointer;
  font-size: 13px;
  font-weight: 400;
  font-family: inherit;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: all 0.15s;
}

.filter-chip.active {
  border-color: #1a1a2e;
  background: #1a1a2e;
  color: #fff;
  font-weight: 600;
}

.filter-count {
  background: #e8edf0;
  color: #718096;
  border-radius: 99px;
  font-size: 11px;
  padding: 1px 6px;
  font-weight: 600;
}

.filter-chip.active .filter-count {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
}

.boxes-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.empty-state {
  text-align: center;
  padding: 48px;
  color: #a0aec0;
}

.empty-text {
  font-size: 13px;
}

/* ── Responsive ───────────────────────────────── */
@media (min-width: 1280px) {
  .view-content {
    max-width: 1200px;
  }
}

@media (max-width: 768px) {
  .smartboxes-view {
    padding: 16px;
  }

  .view-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
}
</style>

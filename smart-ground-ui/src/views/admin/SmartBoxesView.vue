<template>
  <div class="smartboxes-view">
    <div class="view-content">
      <div class="view-header">
        <div>
          <h1>SmartBoxen</h1>
          <p v-if="activeTab === 'smartboxen'" class="subtitle">
            {{ smartBoxStore.smartboxes.length }} Boxen · {{ deviceStore.devices.length }}
            Geräte total · automatisch erkannt
          </p>
          <p v-else-if="activeTab === 'geraetetypen'" class="subtitle">
            {{ deviceTypeStore.deviceTypes.length }} Typ{{ deviceTypeStore.deviceTypes.length !== 1 ? 'en' : '' }} ·
            {{ deviceTypeStore.deviceTypeGroups.length }} Gruppe{{ deviceTypeStore.deviceTypeGroups.length !== 1 ? 'n' : '' }}
          </p>
          <p v-else class="subtitle">{{ otaStore.releases.length }} Release(s) hochgeladen</p>
        </div>
        <Button
          v-if="activeTab === 'smartboxen'"
          :variant="actionMode ? 'primary' : 'ghost'"
          @click="actionMode = !actionMode"
        >
          <template #icon>
            <Icons icon="bolt" :size="13" />
          </template>
          {{ actionMode ? 'Aktionsmodus aktiv' : 'Aktionsmodus' }}
        </Button>
      </div>

      <!-- Tab bar -->
      <div class="tab-bar">
        <button
          :class="{ 'tab--active': activeTab === 'smartboxen' }"
          class="tab"
          @click="setTab('smartboxen')"
        >
          SmartBoxen
        </button>
        <button
          :class="{ 'tab--active': activeTab === 'geraetetypen' }"
          class="tab"
          @click="setTab('geraetetypen')"
        >
          Gerätetypen
        </button>
        <button
          :class="{ 'tab--active': activeTab === 'firmware-updates' }"
          class="tab"
          @click="setTab('firmware-updates')"
        >
          Firmware-Updates
        </button>
      </div>

      <!-- SmartBoxen tab -->
      <template v-if="activeTab === 'smartboxen'">
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
            @delete-box="deleteBox"
          />
        </div>

        <div v-if="visibleBoxes.length === 0" class="empty-state">
          <div class="empty-text">
            Keine Boxen mit «{{ typeFilter }}»-Geräten gefunden.
          </div>
        </div>
      </template>

      <!-- Gerätetypen tab -->
      <DeviceConfigPanel v-else-if="activeTab === 'geraetetypen'" />

      <!-- Firmware-Updates tab -->
      <FirmwareUpdatesPanel v-else-if="activeTab === 'firmware-updates'" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useSmartBoxStore } from '@/stores/smartBoxStore.js';
import { useDeviceStore } from '@/stores/deviceStore.js';
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js';
import { useOtaStore } from '@/stores/otaStore.js';
import * as deviceApi from '@/services/deviceApi.js';
import Button from '@/components/Button.vue';
import Icons from '@/components/Icons.vue';
import SmartBoxCard from '@/components/SmartBoxCard.vue';
import DeviceConfigPanel from '@/components/DeviceConfigPanel.vue';
import FirmwareUpdatesPanel from '@/components/FirmwareUpdatesPanel.vue';
import { useUrlTab } from '@/composables/useUrlTab.js';

const smartBoxStore = useSmartBoxStore();
const deviceStore = useDeviceStore();
const deviceTypeStore = useDeviceTypeStore();
const otaStore = useOtaStore();

const { activeTab, setTab } = useUrlTab('smartboxen', ['smartboxen', 'geraetetypen', 'firmware-updates']);

watch(
  () => smartBoxStore.smartboxes,
  (boxes) => {
    boxes.forEach((box) => deviceStore.loadDevicesForBox(box.id));
  },
  { immediate: true },
);

const OTA_TERMINAL = ['APPLIED', 'FAILED', 'ROLLED_BACK'];

onMounted(() => otaStore.fetchReleases());

watch(
  () => smartBoxStore.smartboxes,
  async (boxes) => {
    for (const box of boxes) {
      const status = await otaStore.fetchStatus(box.id);
      if (status && status.phase && !OTA_TERMINAL.includes(status.phase)) {
        otaStore.startPolling(box.id);
      }
    }
  },
  { immediate: true },
);

onUnmounted(() => otaStore.stopAllPolling());

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
  await deviceStore.addDevice({ ...device, boxId });
};

const removeDevice = (deviceId) => {
  deviceStore.removeDevice(deviceId);
};

const updateDevice = async ({ deviceId, updates }) => {
  await deviceStore.updateDevice(deviceId, updates);
};

const renameBox = ({ boxId, alias }) => {
  smartBoxStore.updateSmartBox(boxId, { alias });
  smartBoxStore.saveSmartBox(boxId);
};

const deleteBox = async (boxId) => {
  try {
    await smartBoxStore.deleteSmartBox(boxId);
  } catch (e) {
    console.error('Failed to delete SmartBox:', e);
  }
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
  color: var(--sg-text-primary);
  margin: 0;
}

.subtitle {
  font-size: 13px;
  color: var(--sg-text-muted);
  margin-top: 4px;
}

/* ── Tab bar ──────────────────────────────────────── */
.tab-bar {
  display: flex;
  gap: 2px;
  border-bottom: 2px solid var(--sg-border);
  margin-bottom: 20px;
}

.tab {
  padding: 8px 16px;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  font-size: 13px;
  font-weight: 500;
  color: var(--sg-text-muted);
  cursor: pointer;
  font-family: inherit;
  transition: color 0.15s, border-color 0.15s;
}

.tab:hover {
  color: var(--sg-text-primary);
}

.tab--active {
  color: var(--sg-text-primary);
  font-weight: 700;
  border-bottom-color: var(--sg-accent);
}

/* ── Filter chips ─────────────────────────────────── */
.filter-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.filter-chip {
  padding: 5px 13px;
  border-radius: 99px;
  border: 1.5px solid var(--sg-border);
  background: var(--sg-bg-card);
  color: var(--sg-text-muted);
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
  border-color: var(--sg-accent);
  background: var(--sg-accent);
  color: var(--sg-surface-0);
  font-weight: 600;
}

.filter-count {
  background: var(--sg-bg-panel);
  color: var(--sg-text-muted);
  border-radius: 99px;
  font-size: 11px;
  padding: 1px 6px;
  font-weight: 600;
}

.filter-chip.active .filter-count {
  background: rgba(255, 255, 255, 0.25);
  color: var(--sg-text-primary);
}

.boxes-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.empty-state {
  text-align: center;
  padding: 48px;
  color: var(--sg-text-faint);
}

.empty-text {
  font-size: 13px;
}

/* ── Responsive ───────────────────────────────────── */
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

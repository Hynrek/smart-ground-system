# Gerätetypen Tab in SmartBoxen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the Gerätetypen view into SmartBoxen as a URL-persisted tab, removing the standalone sidebar entry and route.

**Architecture:** Extract DeviceTypeGroupsView content into a `DeviceTypeGroupsPanel` component, add a two-tab bar to SmartBoxesView using the existing `useUrlTab` composable, remove the sidebar nav item and route.

**Tech Stack:** Vue 3 Composition API, `useUrlTab` composable (`src/composables/useUrlTab.js`), Vue Router 4

---

## File Map

| File | Action |
|---|---|
| `src/components/DeviceTypeGroupsPanel.vue` | **Create** — groups+types content without layout wrapper |
| `src/views/SmartBoxesView.vue` | **Edit** — add tab bar, import panel, conditional render |
| `src/components/Sidebar.vue` | **Edit** — remove `device-type-groups` nav item |
| `src/router/index.js` | **Edit** — remove route, two redirect entries, and import |
| `src/views/DeviceTypeGroupsView.vue` | **Delete** |

---

### Task 1: Create `DeviceTypeGroupsPanel.vue`

Extract the groups/types content from `DeviceTypeGroupsView.vue` into a standalone panel component with no layout wrapper.

**Files:**
- Create: `src/components/DeviceTypeGroupsPanel.vue`

- [ ] **Step 1: Create the panel component**

Write `src/components/DeviceTypeGroupsPanel.vue` with the following content (this is the inner content of `DeviceTypeGroupsView` — the `.dt-view` and `.dt-content` wrappers and the `.view-header` section are intentionally omitted):

```vue
<template>
  <div>
    <div v-if="deviceTypeStore.isLoading" class="state-center">
      <p class="state-text">Lade Gerätetypen…</p>
    </div>

    <div v-else-if="deviceTypeStore.error" class="error-banner">
      {{ deviceTypeStore.error }}
    </div>

    <div v-else class="groups-stack">
      <div
        v-for="group in groups"
        :key="group.id"
        class="group-block"
      >
        <div class="group-heading">
          <span class="group-name">{{ group.name }}</span>
          <span class="group-badge">{{ group.types.length }}</span>
        </div>

        <div v-if="group.types.length === 0" class="empty-group">
          Keine Gerätetypen in dieser Gruppe
        </div>

        <div v-else class="table-wrap">
          <table class="types-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Kommando</th>
                <th>Dauer (ms)</th>
                <th>Verzögerung (ms)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              <template v-for="dt in group.types" :key="dt.id">
                <tr :class="{ 'row--editing': editingId === dt.id }">
                  <td class="td-name">{{ dt.name }}</td>
                  <td class="td-mono">{{ dt.command ?? '–' }}</td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.signalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else class="dur-val">{{ dt.signalDurationMs ?? '–' }}</span>
                  </td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.delaySignalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else class="dur-val">{{ dt.delaySignalDurationMs ?? '–' }}</span>
                  </td>
                  <td class="td-actions">
                    <template v-if="editingId === dt.id">
                      <button class="save-btn" :disabled="saving" @click="saveEdit(dt)">
                        <Icons icon="check" :size="12" />
                      </button>
                      <button class="cancel-btn" @click="editingId = null">
                        <Icons icon="x" :size="11" />
                      </button>
                    </template>
                    <button v-else class="edit-btn" @click="startEdit(dt)">
                      <Icons icon="edit" :size="13" />
                    </button>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="unassignedTypes.length > 0" class="group-block group-block--unassigned">
        <div class="group-heading">
          <span class="group-name group-name--muted">Nicht Zugeteilt</span>
          <span class="group-badge group-badge--muted">{{ unassignedTypes.length }}</span>
        </div>
        <div class="table-wrap">
          <table class="types-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Kommando</th>
                <th>Dauer (ms)</th>
                <th>Verzögerung (ms)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              <template v-for="dt in unassignedTypes" :key="dt.id">
                <tr :class="{ 'row--editing': editingId === dt.id }">
                  <td class="td-name td-name--muted">{{ dt.name }}</td>
                  <td class="td-mono">{{ dt.command ?? '–' }}</td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.signalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else class="dur-val">{{ dt.signalDurationMs ?? '–' }}</span>
                  </td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.delaySignalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else class="dur-val">{{ dt.delaySignalDurationMs ?? '–' }}</span>
                  </td>
                  <td class="td-actions">
                    <template v-if="editingId === dt.id">
                      <button class="save-btn" :disabled="saving" @click="saveEdit(dt)">
                        <Icons icon="check" :size="12" />
                      </button>
                      <button class="cancel-btn" @click="editingId = null">
                        <Icons icon="x" :size="11" />
                      </button>
                    </template>
                    <button v-else class="edit-btn" @click="startEdit(dt)">
                      <Icons icon="edit" :size="13" />
                    </button>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="groups.length === 0 && unassignedTypes.length === 0" class="state-center">
        <p class="state-text">Keine Gerätetypen vorhanden.</p>
        <p class="state-hint">Gruppen entstehen beim Registrieren einer Firmware-Konfiguration.</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js';
import Icons from '@/components/Icons.vue';

const deviceTypeStore = useDeviceTypeStore();

onMounted(() => {
  if (deviceTypeStore.deviceTypes.length === 0) {
    deviceTypeStore.loadApiData();
  }
});

const groups = computed(() =>
  deviceTypeStore.deviceTypeGroups.map((g) => ({
    ...g,
    types: deviceTypeStore.deviceTypes.filter((dt) => dt.groupId === g.id),
  })),
);

const knownGroupIds = computed(() => new Set(deviceTypeStore.deviceTypeGroups.map((g) => g.id)));

const unassignedTypes = computed(() =>
  deviceTypeStore.deviceTypes.filter((dt) => !dt.groupId || !knownGroupIds.value.has(dt.groupId)),
);

const editingId = ref(null);
const editForm = ref({ signalDurationMs: null, delaySignalDurationMs: null });
const saving = ref(false);

const startEdit = (dt) => {
  editingId.value = dt.id;
  editForm.value = {
    signalDurationMs: dt.signalDurationMs ?? null,
    delaySignalDurationMs: dt.delaySignalDurationMs ?? null,
  };
};

const saveEdit = async (dt) => {
  saving.value = true;
  try {
    await deviceTypeStore.updateDeviceType(dt.id, {
      signalDurationMs: editForm.value.signalDurationMs,
      delaySignalDurationMs: editForm.value.delaySignalDurationMs,
    });
    editingId.value = null;
  } catch {
    // error already set in store
  } finally {
    saving.value = false;
  }
};
</script>

<style scoped>
.state-center {
  padding: 48px 24px;
  text-align: center;
}
.state-text {
  font-size: 15px;
  color: #a0aec0;
  margin: 0 0 6px;
}
.state-hint {
  font-size: 13px;
  color: #c0c8d8;
  margin: 0;
}
.error-banner {
  background: #fff5f5;
  border: 1px solid #fca5a5;
  border-radius: 8px;
  padding: 12px 16px;
  color: #c53030;
  font-size: 13px;
  margin-bottom: 20px;
}
.groups-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.group-block {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #f0f4f8;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.group-block--unassigned {
  border-color: #e2e8f0;
  background: #fafbfc;
}
.group-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
  border-bottom: 1px solid #f0f4f8;
  background: #f9fafb;
}
.group-block--unassigned .group-heading {
  background: #f4f6fb;
  border-bottom-color: #e2e8f0;
}
.group-name {
  font-size: 14px;
  font-weight: 700;
  color: #1a1a2e;
}
.group-name--muted {
  color: #718096;
}
.group-badge {
  font-size: 11px;
  font-weight: 600;
  background: #e8edf5;
  color: #4a5568;
  border-radius: 20px;
  padding: 2px 8px;
}
.group-badge--muted {
  background: #f0f4f8;
  color: #a0aec0;
}
.empty-group {
  padding: 16px 20px;
  font-size: 13px;
  color: #a0aec0;
}
.table-wrap {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}
.types-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.types-table thead tr {
  background: #f9fafb;
}
.types-table th {
  padding: 8px 16px;
  text-align: left;
  font-size: 11.5px;
  font-weight: 600;
  color: #718096;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  border-bottom: 1px solid #f0f4f8;
}
.types-table th:last-child {
  text-align: right;
}
.types-table tbody tr {
  border-bottom: 1px solid #f9fafb;
  transition: background 0.1s;
}
.types-table tbody tr:last-child {
  border-bottom: none;
}
.types-table tbody tr:hover {
  background: #f9fafb;
}
.types-table tbody tr.row--editing {
  background: #f0f9ff;
}
.types-table td {
  padding: 10px 16px;
  color: #2d3748;
}
.td-name {
  font-weight: 600;
  color: #1a1a2e;
}
.td-name--muted {
  color: #718096;
  font-weight: 500;
}
.td-mono {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #555;
}
.dur-val {
  color: #4a5568;
}
.dur-input {
  width: 80px;
  padding: 5px 8px;
  border: 1.5px solid #4fc3f7;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  outline: none;
}
.td-actions {
  text-align: right;
}
.td-actions button + button {
  margin-left: 4px;
}
.edit-btn {
  background: none;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 5px 8px;
  cursor: pointer;
  color: #718096;
  display: inline-flex;
  align-items: center;
  transition: all 0.15s;
}
.edit-btn:hover {
  background: #e8f5ff;
  border-color: #4fc3f7;
  color: #1a5fa0;
}
.save-btn {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.save-btn:hover:not(:disabled) {
  background: #0f0f1a;
}
.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.cancel-btn {
  display: inline-flex;
  align-items: center;
  padding: 5px 8px;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  color: #718096;
  transition: all 0.15s;
}
.cancel-btn:hover {
  background: #f9fafb;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add src/components/DeviceTypeGroupsPanel.vue
git commit -m "[ui] Extract DeviceTypeGroupsPanel from DeviceTypeGroupsView"
```

---

### Task 2: Update `SmartBoxesView.vue`

Add a tab bar, import the panel, and conditionally render content based on the active tab.

**Files:**
- Modify: `src/views/SmartBoxesView.vue`

- [ ] **Step 1: Replace `SmartBoxesView.vue` with the tabbed version**

Replace the entire file with:

```vue
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
          <p v-else class="subtitle">
            {{ deviceTypeStore.deviceTypes.length }} Typen ·
            {{ deviceTypeStore.deviceTypeGroups.length }} Gruppen
          </p>
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
          />
        </div>

        <div v-if="visibleBoxes.length === 0" class="empty-state">
          <div class="empty-text">
            Keine Boxen mit «{{ typeFilter }}»-Geräten gefunden.
          </div>
        </div>
      </template>

      <!-- Gerätetypen tab -->
      <DeviceTypeGroupsPanel v-else-if="activeTab === 'geraetetypen'" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { useSmartBoxStore } from '../stores/smartBoxStore.js';
import { useDeviceStore } from '../stores/deviceStore.js';
import { useDeviceTypeStore } from '../stores/deviceTypeStore.js';
import * as deviceApi from '../services/deviceApi.js';
import Button from '../components/Button.vue';
import Icons from '../components/Icons.vue';
import SmartBoxCard from '../components/SmartBoxCard.vue';
import DeviceTypeGroupsPanel from '../components/DeviceTypeGroupsPanel.vue';
import { useUrlTab } from '../composables/useUrlTab.js';

const smartBoxStore = useSmartBoxStore();
const deviceStore = useDeviceStore();
const deviceTypeStore = useDeviceTypeStore();

const { activeTab, setTab } = useUrlTab('smartboxen', ['smartboxen', 'geraetetypen']);

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
  await deviceStore.addDevice({ ...device, boxId });
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

/* ── Tab bar ──────────────────────────────────────── */
.tab-bar {
  display: flex;
  gap: 2px;
  border-bottom: 2px solid #e2e8f0;
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
  color: #718096;
  cursor: pointer;
  font-family: inherit;
  transition: color 0.15s, border-color 0.15s;
}

.tab:hover {
  color: #1a1a2e;
}

.tab--active {
  color: #1a1a2e;
  font-weight: 700;
  border-bottom-color: #1a1a2e;
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
```

- [ ] **Step 2: Commit**

```bash
git add src/views/SmartBoxesView.vue
git commit -m "[ui] Add Gerätetypen tab to SmartBoxen view"
```

---

### Task 3: Remove `device-type-groups` from Sidebar

**Files:**
- Modify: `src/components/Sidebar.vue`

- [ ] **Step 1: Remove the nav item**

In `src/components/Sidebar.vue`, find `allNavItems` and remove the `device-type-groups` entry. The array should go from:

```js
const allNavItems = [
  { id: 'ranges', label: 'Plätze', icon: 'target' },
  { id: 'smartboxes', label: 'SmartBoxen', icon: 'wifi' },
  { id: 'device-type-groups', label: 'Gerätetypen', icon: 'cpu' },
  { id: 'competition', label: 'Wettkampf', icon: 'award' },
  { id: 'passen', label: 'Passen', icon: 'program' },
  { id: 'users', label: 'Benutzer', icon: 'user', adminOnly: true },
  { id: 'profile', label: 'Profil', icon: 'user' },
];
```

To:

```js
const allNavItems = [
  { id: 'ranges', label: 'Plätze', icon: 'target' },
  { id: 'smartboxes', label: 'SmartBoxen', icon: 'wifi' },
  { id: 'competition', label: 'Wettkampf', icon: 'award' },
  { id: 'passen', label: 'Passen', icon: 'program' },
  { id: 'users', label: 'Benutzer', icon: 'user', adminOnly: true },
  { id: 'profile', label: 'Profil', icon: 'user' },
];
```

- [ ] **Step 2: Commit**

```bash
git add src/components/Sidebar.vue
git commit -m "[ui] Remove Gerätetypen from sidebar (now a tab in SmartBoxen)"
```

---

### Task 4: Remove the `/device-type-groups` route and `DeviceTypeGroupsView`

**Files:**
- Modify: `src/router/index.js`
- Delete: `src/views/DeviceTypeGroupsView.vue`

- [ ] **Step 1: Update `router/index.js`**

Remove the `DeviceTypeGroupsView` import and the two route entries for it. The file goes from:

```js
import DeviceTypeGroupsView from '@/views/DeviceTypeGroupsView.vue';
// ...
{ path: '/device-type-groups', component: DeviceTypeGroupsView, meta: { layout: 'admin' } },
{ path: '/deviceTypes', redirect: '/device-type-groups' },
```

To having neither the import nor those two route objects. The relevant section of `routes` after the change:

```js
const routes = [
  { path: '/login', component: LoginView, meta: { requiresAuth: false } },
  { path: '/', redirect: '/ranges' },
  { path: '/ranges', component: RangesView, meta: { layout: 'admin' } },
  { path: '/ranges/:id', component: RangeDetailView, props: route => ({ id: route.params.id }), meta: { layout: 'admin' } },
  { path: '/smartboxes', component: SmartBoxesView, meta: { layout: 'admin' } },
  { path: '/admin/firmware-configs', component: FirmwareConfigsView, meta: { layout: 'admin', requiresAdmin: true } },
  { path: '/users', component: UsersView, meta: { layout: 'admin' } },
  { path: '/profile', component: ProfileView, meta: { layout: 'admin' } },
  { path: '/player-setup', component: PlayerSetupView, meta: { layout: 'admin' } },
  { path: '/competition', component: CompetitionManagementView, meta: { layout: 'admin' } },
  { path: '/competition/templates', component: CompetitionTemplateListView, meta: { layout: 'admin' } },
  { path: '/competition/setup', component: CompetitionSetupView, meta: { layout: 'admin' } },
  { path: '/competition/bracket', component: CompetitionBracketView, meta: { layout: 'admin' } },
  { path: '/passen', component: PassenAdminView, meta: { layout: 'admin' } },
  // ... shooter routes unchanged
];
```

- [ ] **Step 2: Delete `DeviceTypeGroupsView.vue`**

```bash
git rm src/views/DeviceTypeGroupsView.vue
```

- [ ] **Step 3: Verify lint passes**

```bash
npm run lint
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add src/router/index.js
git commit -m "[ui] Remove /device-type-groups route and DeviceTypeGroupsView"
```

---

### Task 5: Final verification

- [ ] **Step 1: Run the dev server**

```bash
npm run dev
```

- [ ] **Step 2: Verify SmartBoxen view**

Navigate to `http://localhost:5173/smartboxes`. Verify:
- "SmartBoxen" tab is active by default
- SmartBox cards render as before
- Clicking "Gerätetypen" tab switches content to the device type groups
- URL updates to `?tab=geraetetypen`
- Refreshing the page with `?tab=geraetetypen` in the URL opens directly on that tab
- The sidebar no longer shows a "Gerätetypen" entry

- [ ] **Step 3: Verify the old route is gone**

Navigate to `http://localhost:5173/device-type-groups`. Verify it does NOT redirect anywhere useful (Vue Router will fall through to a 404 or catch-all — that is correct behaviour since the route is removed).

- [ ] **Step 4: Run build**

```bash
npm run build
```

Expected: exits with code 0, no errors.

# SmartBox GPIO / Device Layer UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce the ADMIN / MANAGE_RANGES / any-authenticated permission split in the SmartBox device UI, expose Device-Config management (GPIO pin → named preset) in the Gerätetypen tab, and add per-device Block/Unblock controls.

**Architecture:** Extend the existing `SmartBoxesView` tabs and per-device card components. No new routes. `DeviceTypeGroupsPanel.vue` is replaced by `DeviceConfigPanel.vue` which groups device types by FirmwareConfig. Block/Unblock actions live in `deviceStore` and are surfaced in `SmartBoxCard`, `DeviceCard`, and `PositionCard`.

**Tech Stack:** Vue 3 `<script setup>`, Pinia 3, Vitest + @vue/test-utils, native `fetch` via `apiFetch`

**Backend dependency:** The backend plan `2026-06-30-backend-capability-registry-permissions.md` must be deployed first for `adminBlocked` and ADMIN permission guards to work end-to-end. The UI degrades gracefully until then (`adminBlocked` defaults to `false` when absent in API response).

---

## File Map

| File | Action |
|---|---|
| `src/constants/deviceTypes.js` | Add `DEBUG_GROUP_NAMES`, `ADMIN_PERMISSION` |
| `src/services/deviceApi.js` | Remove `delaySignalDurationMs` from `createDevice` |
| `src/services/deviceTypeApi.js` | Add `createSignalType`, `createDeviceType` |
| `src/mappers/DeviceMapper.js` | Add `adminBlocked`, `blocked`, `healthy`, `alias`, `groupId`, `groupName`, `deviceTypeId`, `signalDurationMs`, `smartBoxId`; remove `fireDelayMs`, `pinConfig` |
| `src/stores/deviceStore.js` | Remove `delaySignalDurationMs` params; add `blockDevice`, `unblockDevice`; update `applyDeviceEvent` for `adminBlocked` |
| `src/stores/deviceTypeStore.js` | Add `createDeviceConfig` action |
| `src/stores/__tests__/deviceStore.test.js` | New: tests for block/unblock/applyDeviceEvent |
| `src/stores/__tests__/deviceTypeStore.test.js` | New: tests for createDeviceConfig |
| `src/components/DeviceConfigPanel.vue` | New: replaces DeviceTypeGroupsPanel |
| `src/components/DeviceTypeGroupsPanel.vue` | **Delete** |
| `src/views/admin/SmartBoxesView.vue` | Swap import to DeviceConfigPanel |
| `src/components/SmartBoxCard.vue` | Remove GPIO-Pin + delay from edit; flatten add-device form; add block/unblock + status indicator |
| `src/components/DeviceCard.vue` | Add block/unblock + status indicator |
| `src/components/PositionCard.vue` | Add block/unblock + status indicator |

---

### Task 1: Remove `delaySignalDurationMs` / `fireDelayMs` everywhere

**Files:**
- Modify: `src/constants/deviceTypes.js`
- Modify: `src/services/deviceApi.js`
- Modify: `src/stores/deviceStore.js`
- Modify: `src/mappers/DeviceMapper.js`

No new tests — this is pure removal of dead code; the existing test suite must still pass.

- [ ] **Step 1: Add constants and remove dead fields from `src/constants/deviceTypes.js`**

Replace the entire file:

```js
export const STATUS_LABELS = {
  online: 'Online',
  offline: 'Offline',
  warn: 'Warnung',
};

// Permission string for ADMIN-only actions (GPIO pin assignment, admin block).
// Confirm against SecurityConfig.java when backend plan is deployed.
export const ADMIN_PERMISSION = 'MANAGE_USERS';

// DeviceTypeGroup names treated as debug-only (excluded from production device-creation flows).
export const DEBUG_GROUP_NAMES = ['LED'];
```

- [ ] **Step 2: Remove `delaySignalDurationMs` from `src/services/deviceApi.js`**

Replace `createDevice`:

```js
export async function createDevice(smartBoxId, deviceTypeId, alias, rangeId = null) {
  return apiFetch('/devices', {
    method: 'POST',
    body: JSON.stringify({ smartBoxId, deviceTypeId, alias, rangeId }),
  });
}
```

- [ ] **Step 3: Remove `delaySignalDurationMs` / `groupId` / `fireDelayMs` from `src/stores/deviceStore.js`**

Replace `createDevice`, `addDevice`, and `registerDevice`:

```js
const createDevice = async (smartBoxId, deviceTypeId, alias, rangeId = null) => {
  try {
    const created = await deviceApi.createDevice(smartBoxId, deviceTypeId, alias, rangeId);
    devices.value.push(created);
    return created;
  } catch (e) {
    console.error('Failed to create device:', e);
    error.value = e.message ?? 'Unbekannter Fehler';
    throw e;
  }
};

const addDevice = async (device) => {
  return createDevice(
    device.smartBoxId || device.boxId,
    device.deviceTypeId,
    device.name || device.alias,
    device.rangeId ?? null,
  );
};

const registerDevice = async (boxId, { deviceTypeId, alias, rangeId }) => {
  return createDevice(boxId, deviceTypeId, alias, rangeId);
};
```

Also remove `saveDevice` (calls `sendDeviceCommand` with no command arg — dead/misnamed code):

```js
// Delete the entire saveDevice function (lines 66–75).
```

Update the `return` statement to remove `saveDevice`, `registerDevice` (keep `addDevice` as the public API):

```js
return {
  devices,
  isLoading,
  error,
  addDevice,
  removeDevice,
  updateDevice,
  updateDeviceLocal,
  createDevice,
  applyDeviceEvent,
  initialize,
  loadDevices,
  loadDevicesForBox,
};
```

- [ ] **Step 4: Update `src/mappers/DeviceMapper.js`**

Replace the entire file:

```js
export function toDevice(data) {
  return {
    id: data.id,
    alias: data.alias ?? data.name ?? null,
    name: data.name ?? null,
    boxId: data.boxId ?? data.smartBoxId ?? null,
    smartBoxId: data.smartBoxId ?? data.boxId ?? null,
    type: data.type ?? null,
    pin: data.pin ?? data.command ?? null,
    status: data.status ?? 'offline',
    rangeId: data.rangeId ?? null,
    commandsProcessed: data.commandsProcessed ?? null,
    lastCommandProcessedAt: data.lastCommandProcessedAt ?? null,
    groupId: data.groupId ?? null,
    groupName: data.groupName ?? null,
    deviceTypeId: data.deviceTypeId ?? null,
    signalDurationMs: data.signalDurationMs ?? null,
    blocked: data.blocked ?? false,
    adminBlocked: data.adminBlocked ?? false,
    healthy: data.healthy ?? null,
  };
}

export function toDeviceList(data) {
  return Array.isArray(data) ? data.map(toDevice) : [];
}

export function toRegisterDeviceRequest({ deviceTypeId, alias, rangeId }) {
  return { deviceTypeId, alias, rangeId };
}

export function toDeviceCommandRequest(command) {
  return { command };
}
```

- [ ] **Step 5: Run tests and lint**

```bash
cd smart-ground-ui
npm run test
npm run lint
```

Expected: all existing tests pass, no lint errors.

- [ ] **Step 6: Commit**

```bash
git add src/constants/deviceTypes.js src/services/deviceApi.js src/stores/deviceStore.js src/mappers/DeviceMapper.js
git commit -m "[ui] Remove delaySignalDurationMs and fireDelayMs; update DeviceMapper with adminBlocked and full field set"
```

---

### Task 2: Add `blockDevice` / `unblockDevice` to `deviceStore`

**Files:**
- Modify: `src/stores/deviceStore.js`
- Create: `src/stores/__tests__/deviceStore.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/stores/__tests__/deviceStore.test.js`:

```js
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useDeviceStore } from '@/stores/deviceStore.js';
import * as deviceApi from '@/services/deviceApi.js';

vi.mock('@/services/deviceApi.js');

describe('deviceStore block/unblock', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('blockDevice sends BLOCK command and refreshes device state', async () => {
    vi.mocked(deviceApi.sendDeviceCommand).mockResolvedValue(undefined);
    vi.mocked(deviceApi.fetchDevice).mockResolvedValue({
      id: 'd1', alias: 'Werfer 1', blocked: true, adminBlocked: false,
    });

    const store = useDeviceStore();
    store.devices = [{ id: 'd1', alias: 'Werfer 1', blocked: false, adminBlocked: false }];

    await store.blockDevice('d1');

    expect(deviceApi.sendDeviceCommand).toHaveBeenCalledWith('d1', 'BLOCK');
    expect(deviceApi.fetchDevice).toHaveBeenCalledWith('d1');
    expect(store.devices[0].blocked).toBe(true);
  });

  it('unblockDevice sends UNBLOCK command and refreshes device state', async () => {
    vi.mocked(deviceApi.sendDeviceCommand).mockResolvedValue(undefined);
    vi.mocked(deviceApi.fetchDevice).mockResolvedValue({
      id: 'd1', alias: 'Werfer 1', blocked: false, adminBlocked: false,
    });

    const store = useDeviceStore();
    store.devices = [{ id: 'd1', alias: 'Werfer 1', blocked: true, adminBlocked: false }];

    await store.unblockDevice('d1');

    expect(deviceApi.sendDeviceCommand).toHaveBeenCalledWith('d1', 'UNBLOCK');
    expect(store.devices[0].blocked).toBe(false);
  });

  it('blockDevice sets error on failure', async () => {
    vi.mocked(deviceApi.sendDeviceCommand).mockRejectedValue(new Error('network error'));

    const store = useDeviceStore();
    store.devices = [{ id: 'd1', blocked: false }];

    await store.blockDevice('d1');

    expect(store.error).toBe('network error');
    expect(store.devices[0].blocked).toBe(false);
  });

  it('applyDeviceEvent updates adminBlocked', () => {
    const store = useDeviceStore();
    store.devices = [{ id: 'd1', healthy: true, blocked: false, adminBlocked: false }];

    store.applyDeviceEvent({ type: 'device.health', deviceId: 'd1', healthy: false, blocked: true, adminBlocked: true });

    expect(store.devices[0].healthy).toBe(false);
    expect(store.devices[0].blocked).toBe(true);
    expect(store.devices[0].adminBlocked).toBe(true);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
npm run test src/stores/__tests__/deviceStore.test.js
```

Expected: 4 tests FAIL — `blockDevice`, `unblockDevice` not defined.

- [ ] **Step 3: Add `blockDevice`, `unblockDevice` to `src/stores/deviceStore.js`**

Add these actions after `updateDevice`:

```js
const blockDevice = async (deviceId) => {
  try {
    await deviceApi.sendDeviceCommand(deviceId, 'BLOCK');
    const updated = await deviceApi.fetchDevice(deviceId);
    updateDeviceLocal(deviceId, updated);
  } catch (e) {
    console.error('Failed to block device:', e);
    error.value = e.message ?? 'Unbekannter Fehler';
  }
};

const unblockDevice = async (deviceId) => {
  try {
    await deviceApi.sendDeviceCommand(deviceId, 'UNBLOCK');
    const updated = await deviceApi.fetchDevice(deviceId);
    updateDeviceLocal(deviceId, updated);
  } catch (e) {
    console.error('Failed to unblock device:', e);
    error.value = e.message ?? 'Unbekannter Fehler';
  }
};
```

Update `applyDeviceEvent` to handle `adminBlocked`:

```js
const applyDeviceEvent = (event) => {
  if (event.type !== 'device.health') return;
  const device = devices.value.find((d) => d.id === event.deviceId);
  if (device) {
    device.healthy = event.healthy;
    device.blocked = event.blocked;
    device.adminBlocked = event.adminBlocked ?? device.adminBlocked ?? false;
  }
};
```

Add `blockDevice` and `unblockDevice` to the `return` statement.

- [ ] **Step 4: Run tests**

```bash
npm run test src/stores/__tests__/deviceStore.test.js
```

Expected: all 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/stores/deviceStore.js src/stores/__tests__/deviceStore.test.js
git commit -m "[ui] Add blockDevice/unblockDevice to deviceStore; update applyDeviceEvent for adminBlocked"
```

---

### Task 3: Add `createDeviceConfig` to `deviceTypeApi` and `deviceTypeStore`

**Files:**
- Modify: `src/services/deviceTypeApi.js`
- Modify: `src/stores/deviceTypeStore.js`
- Create: `src/stores/__tests__/deviceTypeStore.test.js`

- [ ] **Step 1: Write failing tests**

Create `src/stores/__tests__/deviceTypeStore.test.js`:

```js
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js';
import * as deviceTypeApi from '@/services/deviceTypeApi.js';

vi.mock('@/services/deviceTypeApi.js');

describe('deviceTypeStore createDeviceConfig', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('creates signal type then device type and appends to deviceTypes', async () => {
    vi.mocked(deviceTypeApi.createSignalType).mockResolvedValue({ id: 'st1' });
    vi.mocked(deviceTypeApi.createDeviceType).mockResolvedValue({
      id: 'dt1', name: 'Werfer 3', signalDurationMs: 500, groupId: 'g1', command: '8',
    });

    const store = useDeviceTypeStore();
    store.deviceTypes = [];

    await store.createDeviceConfig('fc1', {
      name: 'Werfer 3',
      groupId: 'g1',
      pin: 8,
      signalDurationMs: 500,
    });

    expect(deviceTypeApi.createSignalType).toHaveBeenCalledWith({
      firmwareConfigId: 'fc1',
      direction: 'OUTPUT',
      device: 'GPIO',
      command: '8',
    });
    expect(deviceTypeApi.createDeviceType).toHaveBeenCalledWith({
      name: 'Werfer 3',
      groupId: 'g1',
      signalTypeId: 'st1',
      signalDurationMs: 500,
    });
    expect(store.deviceTypes).toHaveLength(1);
    expect(store.deviceTypes[0].name).toBe('Werfer 3');
  });

  it('sets error and does not append if signal type creation fails', async () => {
    vi.mocked(deviceTypeApi.createSignalType).mockRejectedValue(new Error('forbidden'));

    const store = useDeviceTypeStore();
    store.deviceTypes = [];

    await expect(store.createDeviceConfig('fc1', {
      name: 'X', groupId: 'g1', pin: 5, signalDurationMs: 100,
    })).rejects.toThrow('forbidden');

    expect(store.deviceTypes).toHaveLength(0);
    expect(store.error).toBe('forbidden');
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
npm run test src/stores/__tests__/deviceTypeStore.test.js
```

Expected: FAIL — `createSignalType`, `createDeviceType`, `createDeviceConfig` not defined.

- [ ] **Step 3: Add `createSignalType` and `createDeviceType` to `src/services/deviceTypeApi.js`**

Append to the existing file:

```js
export async function createSignalType(payload) {
  return apiFetch('/signal-types', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function createDeviceType(payload) {
  return apiFetch('/device-types', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
```

- [ ] **Step 4: Add `createDeviceConfig` to `src/stores/deviceTypeStore.js`**

Add the import at the top of the destructured import:

```js
import {
  fetchDeviceTypes,
  fetchDeviceTypeGroups,
  fetchFirmwareConfigs,
  updateDeviceType as updateDeviceTypeApi,
  createSignalType,
  createDeviceType as createDeviceTypeApi,
} from '../services/deviceTypeApi.js';
```

Add the action inside the store definition:

```js
const createDeviceConfig = async (firmwareConfigId, { name, groupId, pin, signalDurationMs }) => {
  error.value = null;
  try {
    const signalType = await createSignalType({
      firmwareConfigId,
      direction: 'OUTPUT',
      device: 'GPIO',
      command: String(pin),
    });
    const deviceType = await createDeviceTypeApi({
      name,
      groupId,
      signalTypeId: signalType.id,
      signalDurationMs,
    });
    deviceTypes.value.push(deviceType);
    return deviceType;
  } catch (e) {
    console.error('Failed to create device config:', e);
    error.value = e.message ?? 'Unbekannter Fehler';
    throw e;
  }
};
```

Add `createDeviceConfig` to the `return` statement.

- [ ] **Step 5: Run tests**

```bash
npm run test src/stores/__tests__/deviceTypeStore.test.js
```

Expected: all 2 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/services/deviceTypeApi.js src/stores/deviceTypeStore.js src/stores/__tests__/deviceTypeStore.test.js
git commit -m "[ui] Add createSignalType/createDeviceType API functions and createDeviceConfig store action"
```

---

### Task 4: Build `DeviceConfigPanel.vue` — replace `DeviceTypeGroupsPanel`

**Files:**
- Create: `src/components/DeviceConfigPanel.vue`
- Delete: `src/components/DeviceTypeGroupsPanel.vue`
- Modify: `src/views/admin/SmartBoxesView.vue`

The panel groups device types by FirmwareConfig. It reads `deviceTypeStore.firmwareConfigs`, `deviceTypeStore.deviceTypes`, `deviceTypeStore.deviceTypeGroups`. ADMIN users see an inline create form per firmware block and can edit GPIO pin. MANAGE_RANGES users can edit name and signal duration. LED groups are visually fenced with a "Debug" badge.

The `ADMIN_PERMISSION` string is `'MANAGE_USERS'` (see `src/constants/deviceTypes.js`).

- [ ] **Step 1: Create `src/components/DeviceConfigPanel.vue`**

```vue
<template>
  <div>
    <div v-if="deviceTypeStore.isLoading" class="state-center">
      <p class="state-text">Lade Gerätekonfigurationen…</p>
    </div>

    <div v-else-if="deviceTypeStore.error" class="error-banner">
      {{ deviceTypeStore.error }}
    </div>

    <div v-else-if="deviceTypeStore.firmwareConfigs.length === 0" class="state-center">
      <p class="state-text">Keine Firmware-Konfigurationen vorhanden.</p>
      <p class="state-hint">Registriere eine SmartBox, um zu beginnen.</p>
    </div>

    <div v-else class="configs-stack">
      <div
        v-for="fc in deviceTypeStore.firmwareConfigs"
        :key="fc.id"
        class="firmware-block"
      >
        <div class="firmware-heading">
          <span class="firmware-label">v{{ fc.version }} — {{ fc.boxType }}</span>
          <button
            v-if="isAdmin"
            class="new-config-btn"
            @click="toggleCreate(fc.id)"
          >
            <Icons icon="plus" :size="11" />
            Neue Konfiguration
          </button>
        </div>

        <!-- Device type table -->
        <div class="table-wrap">
          <table class="types-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Gruppe</th>
                <th>GPIO-Pin</th>
                <th>Dauer (ms)</th>
                <th />
              </tr>
            </thead>
            <tbody>
              <template v-for="dt in typesForFirmware(fc.id)" :key="dt.id">
                <tr :class="{ 'row--editing': editingId === dt.id }">
                  <td class="td-name">{{ dt.name }}</td>
                  <td>
                    <span v-if="isDebugGroup(dt.groupId)" class="debug-badge">
                      <Icons icon="settings" :size="11" />
                      Debug
                    </span>
                    <span v-else class="group-label">{{ groupName(dt.groupId) }}</span>
                  </td>
                  <td class="td-mono">
                    <template v-if="editingId === dt.id && isAdmin">
                      <input
                        v-model="editForm.command"
                        type="number"
                        min="0"
                        max="40"
                        class="pin-input"
                        :disabled="isDebugGroup(dt.groupId)"
                      />
                    </template>
                    <span v-else>{{ isDebugGroup(dt.groupId) ? '— (onboard)' : (dt.command ?? '–') }}</span>
                  </td>
                  <td>
                    <template v-if="editingId === dt.id">
                      <input
                        v-model.number="editForm.signalDurationMs"
                        type="number"
                        min="0"
                        class="dur-input"
                      />
                    </template>
                    <span v-else>{{ dt.signalDurationMs ?? '–' }}</span>
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
              <tr v-if="typesForFirmware(fc.id).length === 0">
                <td colspan="5" class="empty-row">Keine Konfigurationen</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Inline create form (ADMIN only) -->
        <div v-if="isAdmin && creatingForFirmware === fc.id" class="create-form">
          <p v-if="createError" class="create-error">{{ createError }}</p>
          <div class="form-row">
            <div class="form-group">
              <label>Name *</label>
              <input v-model="createForm.name" placeholder="z.B. Werfer 3" />
            </div>
            <div class="form-group">
              <label>Gruppe *</label>
              <select v-model="createForm.groupId" @change="onGroupChange">
                <option value="" disabled>Gruppe wählen</option>
                <option v-for="g in deviceTypeStore.deviceTypeGroups" :key="g.id" :value="g.id">
                  {{ g.name }}{{ isDebugGroupName(g.name) ? ' (Debug)' : '' }}
                </option>
              </select>
            </div>
            <div class="form-group">
              <label>GPIO-Pin *</label>
              <input
                v-model.number="createForm.pin"
                type="number"
                min="0"
                max="40"
                :disabled="isDebugGroup(createForm.groupId)"
                :placeholder="isDebugGroup(createForm.groupId) ? '— (onboard)' : 'z.B. 8'"
              />
            </div>
            <div class="form-group">
              <label>Dauer (ms) *</label>
              <input v-model.number="createForm.signalDurationMs" type="number" min="0" placeholder="z.B. 500" />
            </div>
          </div>
          <div class="form-actions">
            <button
              class="save-btn"
              :disabled="creating || !createForm.name.trim() || !createForm.groupId || (createForm.signalDurationMs === null)"
              @click="submitCreate(fc.id)"
            >
              <Icons icon="check" :size="12" />
              Erstellen
            </button>
            <button class="cancel-btn" @click="creatingForFirmware = null">Abbrechen</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import { ADMIN_PERMISSION, DEBUG_GROUP_NAMES } from '@/constants/deviceTypes.js';
import Icons from '@/components/Icons.vue';

const deviceTypeStore = useDeviceTypeStore();
const authStore = useAuthStore();

const isAdmin = computed(() => authStore.hasPermission(ADMIN_PERMISSION));

const isDebugGroupName = (name) => DEBUG_GROUP_NAMES.includes(name?.toUpperCase?.() ?? '');
const isDebugGroup = (groupId) => {
  if (!groupId) return false;
  const group = deviceTypeStore.deviceTypeGroups.find((g) => g.id === groupId);
  return isDebugGroupName(group?.name);
};

const groupName = (groupId) =>
  deviceTypeStore.deviceTypeGroups.find((g) => g.id === groupId)?.name ?? '–';

const typesForFirmware = (firmwareConfigId) =>
  deviceTypeStore.deviceTypes.filter((dt) => dt.firmwareConfigId === firmwareConfigId);

// ── Inline edit ──────────────────────────────────────────────────────────────
const editingId = ref(null);
const editForm = ref({ name: '', command: '', signalDurationMs: null });
const saving = ref(false);

const startEdit = (dt) => {
  editingId.value = dt.id;
  editForm.value = {
    name: dt.name ?? '',
    command: dt.command ?? '',
    signalDurationMs: dt.signalDurationMs ?? null,
  };
};

const saveEdit = async (dt) => {
  saving.value = true;
  try {
    const payload = { name: editForm.value.name, signalDurationMs: editForm.value.signalDurationMs };
    if (isAdmin.value && !isDebugGroup(dt.groupId)) {
      payload.command = String(editForm.value.command);
    }
    await deviceTypeStore.updateDeviceType(dt.id, payload);
    editingId.value = null;
  } catch {
    // error surfaced via deviceTypeStore.error
  } finally {
    saving.value = false;
  }
};

// ── Create form ───────────────────────────────────────────────────────────────
const creatingForFirmware = ref(null);
const createForm = ref({ name: '', groupId: '', pin: null, signalDurationMs: null });
const createError = ref(null);
const creating = ref(false);

const toggleCreate = (firmwareConfigId) => {
  creatingForFirmware.value = creatingForFirmware.value === firmwareConfigId ? null : firmwareConfigId;
  createForm.value = { name: '', groupId: '', pin: null, signalDurationMs: null };
  createError.value = null;
};

const onGroupChange = () => {
  if (isDebugGroup(createForm.value.groupId)) {
    createForm.value.pin = null;
  }
};

const submitCreate = async (firmwareConfigId) => {
  createError.value = null;
  creating.value = true;
  try {
    await deviceTypeStore.createDeviceConfig(firmwareConfigId, {
      name: createForm.value.name.trim(),
      groupId: createForm.value.groupId,
      pin: isDebugGroup(createForm.value.groupId) ? 0 : createForm.value.pin,
      signalDurationMs: createForm.value.signalDurationMs,
    });
    creatingForFirmware.value = null;
  } catch (e) {
    createError.value = `Erstellen fehlgeschlagen: ${e.message}`;
  } finally {
    creating.value = false;
  }
};
</script>

<style scoped>
.state-center { padding: 48px 24px; text-align: center; }
.state-text { font-size: 15px; color: #a0aec0; margin: 0 0 6px; }
.state-hint { font-size: 13px; color: #c0c8d8; margin: 0; }
.error-banner { background: #fff5f5; border: 1px solid #fca5a5; border-radius: 8px; padding: 12px 16px; color: #c53030; font-size: 13px; margin-bottom: 20px; }

.configs-stack { display: flex; flex-direction: column; gap: 20px; }

.firmware-block { background: #fff; border-radius: 12px; border: 1px solid #f0f4f8; box-shadow: 0 1px 3px rgba(0,0,0,.06); overflow: hidden; }

.firmware-heading { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid #f0f4f8; background: #f9fafb; }

.firmware-label { font-size: 13px; font-weight: 700; color: #1a1a2e; font-family: 'Courier New', monospace; }

.new-config-btn { display: flex; align-items: center; gap: 5px; padding: 5px 10px; background: #1a1a2e; color: #fff; border: none; border-radius: 6px; font-size: 12px; font-weight: 500; font-family: inherit; cursor: pointer; }
.new-config-btn:hover { background: #0f0f1a; }

.table-wrap { overflow-x: auto; }

.types-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.types-table th { padding: 8px 16px; text-align: left; font-size: 11.5px; font-weight: 600; color: #718096; text-transform: uppercase; letter-spacing: 0.4px; border-bottom: 1px solid #f0f4f8; background: #f9fafb; }
.types-table th:last-child { text-align: right; }
.types-table td { padding: 10px 16px; color: #2d3748; border-bottom: 1px solid #f9fafb; }
.types-table tbody tr:last-child td { border-bottom: none; }
.types-table tbody tr:hover { background: #f9fafb; }
.types-table tbody tr.row--editing { background: #f0f9ff; }

.td-name { font-weight: 600; color: #1a1a2e; }
.td-mono { font-family: 'Courier New', monospace; font-size: 12px; color: #555; }
.empty-row { text-align: center; color: #a0aec0; font-size: 13px; padding: 16px; }

.debug-badge { display: inline-flex; align-items: center; gap: 4px; background: rgba(239,159,39,.15); color: #c67c10; border-radius: 4px; padding: 2px 6px; font-size: 11.5px; font-weight: 600; }
.group-label { color: #4a5568; }

.pin-input, .dur-input { width: 72px; padding: 4px 8px; border: 1.5px solid #4fc3f7; border-radius: 6px; font-size: 13px; font-family: inherit; outline: none; }

.td-actions { text-align: right; }
.td-actions button + button { margin-left: 4px; }

.edit-btn { background: none; border: 1px solid #e2e8f0; border-radius: 6px; padding: 5px 8px; cursor: pointer; color: #718096; display: inline-flex; align-items: center; transition: all .15s; }
.edit-btn:hover { background: #e8f5ff; border-color: #4fc3f7; color: #1a5fa0; }

.save-btn { display: inline-flex; align-items: center; gap: 4px; padding: 5px 9px; background: #1a1a2e; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 12px; font-family: inherit; }
.save-btn:hover:not(:disabled) { background: #0f0f1a; }
.save-btn:disabled { opacity: .5; cursor: not-allowed; }

.cancel-btn { display: inline-flex; align-items: center; padding: 5px 8px; background: transparent; border: 1px solid #e2e8f0; border-radius: 6px; cursor: pointer; color: #718096; font-size: 12px; font-family: inherit; }
.cancel-btn:hover { background: #f9fafb; }

.create-form { padding: 16px 20px; border-top: 1px solid #e2e8f0; background: #f9fafb; }
.create-error { color: #c53030; font-size: 13px; margin: 0 0 10px; }
.form-row { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group label { font-size: 11.5px; font-weight: 600; color: #718096; text-transform: uppercase; letter-spacing: 0.4px; }
.form-group input, .form-group select { padding: 6px 10px; border: 1.5px solid #e2e8f0; border-radius: 6px; font-size: 13px; font-family: inherit; outline: none; min-width: 120px; }
.form-group input:focus, .form-group select:focus { border-color: #4fc3f7; }
.form-actions { display: flex; gap: 8px; }
</style>
```

- [ ] **Step 2: Delete `src/components/DeviceTypeGroupsPanel.vue`**

```bash
rm "smart-ground-ui/src/components/DeviceTypeGroupsPanel.vue"
```

- [ ] **Step 3: Update `src/views/admin/SmartBoxesView.vue`**

Replace the import line:
```js
// Before
import DeviceTypeGroupsPanel from '@/components/DeviceTypeGroupsPanel.vue';
// After
import DeviceConfigPanel from '@/components/DeviceConfigPanel.vue';
```

Replace the template usage:
```html
<!-- Before -->
<DeviceTypeGroupsPanel v-else-if="activeTab === 'geraetetypen'" />
<!-- After -->
<DeviceConfigPanel v-else-if="activeTab === 'geraetetypen'" />
```

- [ ] **Step 4: Run dev server and verify tab renders**

```bash
cd smart-ground-ui
npm run dev
```

Open `http://localhost:5173`, log in as admin, navigate to SmartBoxen → Gerätetypen tab. Verify:
- Firmware blocks appear (one per FirmwareConfig)
- Device types listed per block with Name / Gruppe / GPIO-Pin / Dauer columns
- LED group rows show the amber "Debug" badge
- "Neue Konfiguration" button visible for admin, hidden for MANAGE_RANGES-only user

- [ ] **Step 5: Run tests and lint**

```bash
npm run test
npm run lint
```

Expected: all tests pass, no lint errors.

- [ ] **Step 6: Commit**

```bash
git add src/components/DeviceConfigPanel.vue src/views/admin/SmartBoxesView.vue
git rm src/components/DeviceTypeGroupsPanel.vue
git commit -m "[ui] Replace DeviceTypeGroupsPanel with DeviceConfigPanel — FirmwareConfig-grouped device configs, ADMIN-gated create/pin-edit"
```

---

### Task 5: Simplify SmartBoxCard "Add device" form

**Files:**
- Modify: `src/components/SmartBoxCard.vue`

Remove the Gruppe → Typ cascade. Show a single flat Typ dropdown. Exclude LED/debug types by default, with ADMIN toggle.

- [ ] **Step 1: Update `<script setup>` in `SmartBoxCard.vue`**

Add the new imports at the top of `<script setup>`:

```js
import { useAuthStore } from '../stores/authStore.js';
import { ADMIN_PERMISSION, DEBUG_GROUP_NAMES } from '../constants/deviceTypes.js';
```

Add new computed properties after `editFilteredDeviceTypes`:

```js
const authStore = useAuthStore();
const isAdmin = computed(() => authStore.hasPermission(ADMIN_PERMISSION));
const showDebugTypes = ref(false);

const isDebugGroup = (groupId) => {
  if (!groupId) return false;
  const group = deviceTypeGroupStore.groups.find((g) => g.id === groupId);
  return DEBUG_GROUP_NAMES.includes(group?.name?.toUpperCase?.() ?? '');
};

const availableDeviceTypes = computed(() => {
  return deviceTypeStore.deviceTypes.filter((t) => {
    if (isDebugGroup(t.groupId)) return showDebugTypes.value && isAdmin.value;
    return true;
  });
});

const groupNameForType = (t) => {
  return deviceTypeGroupStore.groups.find((g) => g.id === t.groupId)?.name ?? '';
};
```

Update `newDeviceForm` initial value (remove `groupId`):

```js
const newDeviceForm = ref({ name: '', deviceTypeId: '' });
```

Update `resetNewDeviceForm`:

```js
const resetNewDeviceForm = () => {
  newDeviceForm.value = { name: '', deviceTypeId: '' };
  showDebugTypes.value = false;
};
```

Update `handleAddDevice` (remove `groupId`):

```js
const handleAddDevice = () => {
  if (!newDeviceForm.value.name.trim() || !newDeviceForm.value.deviceTypeId) return;
  emit('add-device', {
    boxId: props.box.id,
    device: {
      name: newDeviceForm.value.name,
      deviceTypeId: newDeviceForm.value.deviceTypeId,
    },
  });
  resetNewDeviceForm();
  isAdding.value = false;
};
```

- [ ] **Step 2: Update the add-device form template in `SmartBoxCard.vue`**

Replace the add-device form `<div v-if="isAdding" class="add-form">` section (lines ~209–250) with:

```html
<div v-if="isAdding" class="add-form">
  <div class="form-group">
    <label>Name *</label>
    <input
      v-model="newDeviceForm.name"
      placeholder="z.B. Werfer 3"
      @keydown.enter="handleAddDevice"
    />
  </div>
  <div class="form-group">
    <label>Typ *</label>
    <select v-model="newDeviceForm.deviceTypeId">
      <option value="" disabled>Typ wählen</option>
      <option v-for="t in availableDeviceTypes" :key="t.id" :value="t.id">
        {{ t.name }}{{ groupNameForType(t) ? ` (${groupNameForType(t)})` : '' }}
      </option>
    </select>
  </div>
  <button
    v-if="isAdmin"
    type="button"
    class="debug-toggle"
    @click="showDebugTypes = !showDebugTypes"
  >
    {{ showDebugTypes ? 'Debug-Geräte ausblenden' : 'Debug-Geräte anzeigen' }}
  </button>
  <div class="form-actions">
    <button
      class="add-btn"
      :disabled="!newDeviceForm.name.trim() || !newDeviceForm.deviceTypeId"
      @click="handleAddDevice"
    >
      <Icons icon="plus" :size="12" />
      Erfassen
    </button>
    <button class="cancel-btn" @click="toggleAddMode">Abbrechen</button>
  </div>
</div>
```

Add the `debug-toggle` style to the component's `<style scoped>`:

```css
.debug-toggle {
  background: none;
  border: none;
  color: #718096;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 0;
  text-decoration: underline;
}
```

- [ ] **Step 3: Remove the Gruppe edit field from the inline edit row**

In the inline edit row (`<tr v-if="editingId === device.id" ...>`), remove the Gruppe `<div class="form-group">` block (the select with `v-model="editForm.groupId"`). Keep Name, Signaldauer, SmartBox selector.

Also remove `editForm.groupId` from `startEdit`:

```js
const startEdit = (device) => {
  editingId.value = device.id;
  editForm.value = {
    alias: device.alias ?? device.name ?? '',
    deviceTypeId: device.deviceTypeId ?? '',
    signalDurationMs: device.signalDurationMs ?? '',
    smartBoxId: device.smartBoxId ?? device.boxId ?? props.box.id,
  };
};
```

Update `saveEdit` to remove groupId and pin from the updates:

```js
const saveEdit = (deviceId) => {
  const updates = {
    alias: editForm.value.alias.trim(),
    smartBoxId: editForm.value.smartBoxId,
  };
  if (editForm.value.deviceTypeId) {
    updates.deviceTypeId = editForm.value.deviceTypeId;
  }
  if (editForm.value.signalDurationMs !== '') {
    updates.signalDurationMs = parseInt(editForm.value.signalDurationMs);
  }
  emit('update-device', { deviceId, updates });
  editingId.value = null;
};
```

Also remove the `Typ` select from the edit row (since changing device type changes pin — that's ADMIN territory, done via Device-Config page). The edit row keeps only: Name, Signaldauer, SmartBox.

- [ ] **Step 4: Run tests and lint**

```bash
npm run test
npm run lint
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/components/SmartBoxCard.vue
git commit -m "[ui] Simplify add-device form to flat type list; remove GPIO-pin and delay from edit row"
```

---

### Task 6: Block/Unblock UI in SmartBoxCard

**Files:**
- Modify: `src/components/SmartBoxCard.vue`

Add a status indicator column and Block/Unblock button to each device row in the table. ADMIN confirmation dialog for "Dauerhaft sperren".

- [ ] **Step 1: Add device status helper and block actions to `<script setup>`**

Add imports at top of `<script setup>`:

```js
import { useDeviceStore } from '../stores/deviceStore.js';
```

Add after existing store declarations:

```js
const deviceStore = useDeviceStore();

const deviceStatus = (device) => {
  if (device.adminBlocked) return 'admin-blocked';
  if (device.blocked) return 'user-blocked';
  if (device.healthy === false) return 'unhealthy';
  return 'ok';
};

const blockingId = ref(null);

const handleBlock = async (device) => {
  blockingId.value = device.id;
  try {
    await deviceStore.blockDevice(device.id);
  } finally {
    blockingId.value = null;
  }
};

const handleUnblock = async (device) => {
  blockingId.value = device.id;
  try {
    await deviceStore.unblockDevice(device.id);
  } finally {
    blockingId.value = null;
  }
};
```

- [ ] **Step 2: Add a status cell and Block/Unblock to the device table template**

In the `<table class="device-table">` thead, add a new `<th>Status</th>` before the final `<th />`.

In the device row `<tr>`, add a status cell and block/unblock cell before `<td class="actions-cell">`:

```html
<!-- Status cell -->
<td class="status-cell">
  <span
    v-if="deviceStatus(device) === 'admin-blocked'"
    class="status-pill status-pill--admin"
    title="Admin-gesperrt"
  >
    <Icons icon="lock" :size="11" /> Admin-gesperrt
  </span>
  <span
    v-else-if="deviceStatus(device) === 'user-blocked'"
    class="status-pill status-pill--blocked"
    title="Gesperrt (Nutzer)"
  >
    <Icons icon="lock" :size="11" /> Gesperrt
  </span>
  <span
    v-else-if="deviceStatus(device) === 'unhealthy'"
    class="status-pill status-pill--warn"
    title="Nicht reagiert"
  >
    Nicht reagiert
  </span>
</td>

<!-- Block/Unblock cell -->
<td class="block-cell">
  <template v-if="device.adminBlocked">
    <button
      class="unblock-btn"
      :disabled="!isAdmin || blockingId === device.id"
      :title="isAdmin ? 'Admin-Sperre aufheben' : 'Nur Admin kann aufheben'"
      @click="handleUnblock(device)"
    >
      Entsperren
    </button>
  </template>
  <template v-else-if="device.blocked">
    <button
      class="unblock-btn"
      :disabled="blockingId === device.id"
      @click="handleUnblock(device)"
    >
      Entsperren
    </button>
  </template>
  <template v-else>
    <button
      class="block-btn"
      :disabled="blockingId === device.id"
      @click="handleBlock(device)"
    >
      Sperren
    </button>
  </template>
</td>
```

- [ ] **Step 3: Add styles to `<style scoped>`**

```css
.status-cell { white-space: nowrap; }
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11.5px;
  font-weight: 600;
  border-radius: 4px;
  padding: 2px 7px;
}
.status-pill--admin {
  background: rgba(226,75,74,.12);
  color: #c53030;
}
.status-pill--blocked {
  background: rgba(237,137,54,.12);
  color: #c05621;
}
.status-pill--warn {
  background: rgba(237,137,54,.12);
  color: #c05621;
}
.block-cell { white-space: nowrap; }
.block-btn, .unblock-btn {
  font-size: 11.5px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid;
  cursor: pointer;
  font-family: inherit;
  transition: all .15s;
}
.block-btn {
  border-color: #e2e8f0;
  background: #fff;
  color: #718096;
}
.block-btn:hover:not(:disabled) {
  border-color: #c05621;
  color: #c05621;
  background: rgba(237,137,54,.06);
}
.unblock-btn {
  border-color: #c3dafe;
  background: #ebf4ff;
  color: #2b6cb0;
}
.unblock-btn:hover:not(:disabled) {
  background: #bee3f8;
}
.block-btn:disabled, .unblock-btn:disabled {
  opacity: .45;
  cursor: not-allowed;
}
```

- [ ] **Step 4: Run dev server and verify**

Log in as admin and as a regular user. Verify:
- Blocked device shows orange "Gesperrt" pill + "Entsperren" button
- Admin-blocked device shows red "Admin-gesperrt" pill + disabled "Entsperren" for regular user
- Admin sees enabled "Entsperren" on admin-blocked device
- "Sperren" works and pill appears immediately after re-fetch

- [ ] **Step 5: Run tests and lint**

```bash
npm run test
npm run lint
```

- [ ] **Step 6: Commit**

```bash
git add src/components/SmartBoxCard.vue
git commit -m "[ui] Add Block/Unblock controls and device status indicator to SmartBoxCard"
```

---

### Task 7: Block/Unblock UI in `DeviceCard` and `PositionCard`

**Files:**
- Modify: `src/components/DeviceCard.vue`
- Modify: `src/components/PositionCard.vue`

Both components call `deviceStore.blockDevice`/`unblockDevice` directly (no emit needed — stores are fine to call from any component per the convention).

- [ ] **Step 1: Update `DeviceCard.vue`**

Replace the entire file:

```vue
<template>
  <div class="device-card" :class="{ fired, blocked: device.blocked || device.adminBlocked }">
    <div class="card-header">
      <div class="device-name">
        <div class="name">{{ device.alias }}</div>
        <div class="box-id" :title="boxIdRaw || undefined">{{ boxName }}</div>
      </div>
      <span
        v-if="device.adminBlocked"
        class="status-pill status-pill--admin"
        title="Admin-gesperrt"
      >
        <Icons icon="lock" :size="10" />
      </span>
      <span
        v-else-if="device.blocked"
        class="status-pill status-pill--blocked"
        title="Gesperrt"
      >
        <Icons icon="lock" :size="10" />
      </span>
      <StatusDot v-else :status="getStatus(device)" />
    </div>
    <div class="card-footer">
      <TypeChip :type="device.groupName ?? device.deviceType" />
      <div class="card-actions">
        <button v-if="actionMode" class="fire-btn" :class="{ fired }" @click="$emit('fire')">
          <Icons icon="fire" :size="12" />
          <span>{{ fired ? 'Ausgelöst!' : 'Auslösen' }}</span>
        </button>
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
  device: { type: Object, required: true },
  fired: Boolean,
  actionMode: Boolean,
});

defineEmits(['fire']);

const smartBoxStore = useSmartBoxStore();
const deviceStore = useDeviceStore();
const authStore = useAuthStore();

const isAdmin = computed(() => authStore.hasPermission(ADMIN_PERMISSION));
const blocking = ref(false);

const boxIdRaw = computed(() => props.device.smartBoxId ?? props.device.boxId ?? null);
const boxName = computed(() => {
  const id = boxIdRaw.value;
  const alias = smartBoxStore.smartboxes.find((b) => b.id === id)?.alias;
  if (alias) return alias;
  if (!id) return '–';
  return id.length > 8 ? id.slice(0, 8) + '…' : id;
});

function getStatus(device) {
  if (device.healthy === false) return 'warn';
  return 'online';
}

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
</script>

<style scoped>
.device-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,.07), 0 4px 12px rgba(0,0,0,.04);
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid #f0f4f8;
  transition: border-color 0.2s;
}
.device-card.fired { border-color: #4fc3f7; }
.device-card.blocked { border-color: rgba(226,75,74,.25); }

.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.device-name { flex: 1; min-width: 0; }
.name { font-weight: 600; font-size: 14.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.box-id { font-size: 12px; color: #a0aec0; margin-top: 2px; }

.status-pill { display: inline-flex; align-items: center; border-radius: 4px; padding: 2px 5px; }
.status-pill--admin { background: rgba(226,75,74,.12); color: #c53030; }
.status-pill--blocked { background: rgba(237,137,54,.12); color: #c05621; }

.card-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.card-actions { display: flex; gap: 6px; align-items: center; }

.fire-btn {
  display: flex; align-items: center; gap: 5px; padding: 5px 10px;
  background: #1a1a2e; color: #fff; border: none; border-radius: 6px;
  cursor: pointer; font-size: 12px; font-weight: 500; font-family: inherit; transition: background .2s;
}
.fire-btn.fired { background: #4fc3f7; }
.fire-btn:hover { background: #0f0f1a; }
.fire-btn.fired:hover { background: #2ba4d0; }

.block-action-btn {
  font-size: 11.5px; font-weight: 500; padding: 4px 9px;
  border-radius: 6px; border: 1px solid #c3dafe;
  background: #ebf4ff; color: #2b6cb0; cursor: pointer; font-family: inherit;
}
.block-action-btn--block {
  border-color: #e2e8f0; background: #fff; color: #718096;
}
.block-action-btn:disabled { opacity: .45; cursor: not-allowed; }
</style>
```

- [ ] **Step 2: Add block/unblock to `PositionCard.vue`**

In the `<script setup>` of `PositionCard.vue`, add:

```js
import { ref, computed } from 'vue'; // already imported — merge
import { useDeviceStore } from '@/stores/deviceStore.js';
import { useAuthStore } from '@/stores/authStore.js';
import { ADMIN_PERMISSION } from '@/constants/deviceTypes.js';

const deviceStore = useDeviceStore();
const authStore = useAuthStore();
const isAdmin = computed(() => authStore.hasPermission(ADMIN_PERMISSION));
const blocking = ref(false);

const handleBlockDevice = async (deviceId) => {
  blocking.value = true;
  try { await deviceStore.blockDevice(deviceId); }
  finally { blocking.value = false; }
};

const handleUnblockDevice = async (device) => {
  blocking.value = true;
  try { await deviceStore.unblockDevice(device.id); }
  finally { blocking.value = false; }
};
```

In the template, in the section that renders a device card inside the position (find where the device name/card is shown — wherever the device is rendered), add a Block/Unblock indicator. Locate the device display area and add after the device name/chip:

```html
<!-- Block/Unblock for position device -->
<template v-if="position.device">
  <template v-if="position.device.adminBlocked">
    <span class="pos-lock pos-lock--admin" title="Admin-gesperrt">🔒</span>
    <button
      v-if="isAdmin"
      class="pos-unblock-btn"
      :disabled="blocking"
      @click.stop="handleUnblockDevice(position.device)"
    >
      Entsperren
    </button>
  </template>
  <template v-else-if="position.device.blocked">
    <span class="pos-lock" title="Gesperrt">🔒</span>
    <button
      class="pos-unblock-btn"
      :disabled="blocking"
      @click.stop="handleUnblockDevice(position.device)"
    >
      Entsperren
    </button>
  </template>
  <template v-else>
    <button
      class="pos-block-btn"
      :disabled="blocking"
      @click.stop="handleBlockDevice(position.device.id)"
    >
      Sperren
    </button>
  </template>
</template>
```

Add styles to PositionCard's `<style scoped>`:

```css
.pos-lock { font-size: 13px; }
.pos-lock--admin { filter: hue-rotate(330deg); }
.pos-block-btn, .pos-unblock-btn {
  font-size: 11px; padding: 3px 8px; border-radius: 5px;
  border: 1px solid; cursor: pointer; font-family: inherit;
}
.pos-block-btn { border-color: #e2e8f0; background: #fff; color: #718096; }
.pos-unblock-btn { border-color: #c3dafe; background: #ebf4ff; color: #2b6cb0; }
.pos-block-btn:disabled, .pos-unblock-btn:disabled { opacity: .45; cursor: not-allowed; }
```

**Note:** The exact template insertion point in PositionCard depends on the current component structure. Find the device display area (where `DeviceCard` or device name is rendered) and insert the block/unblock controls adjacent to it. Read `src/components/PositionCard.vue` at implementation time to find the right anchor.

- [ ] **Step 3: Run tests and lint**

```bash
npm run test
npm run lint
```

Expected: all tests pass, no lint errors.

- [ ] **Step 4: Commit**

```bash
git add src/components/DeviceCard.vue src/components/PositionCard.vue
git commit -m "[ui] Add Block/Unblock controls and status indicators to DeviceCard and PositionCard"
```

---

### Task 8: Full test run and smoke verification

- [ ] **Step 1: Run all tests**

```bash
cd smart-ground-ui
npm run test
```

Expected: all tests pass.

- [ ] **Step 2: Run lint**

```bash
npm run lint:check
```

Expected: no errors.

- [ ] **Step 3: Build**

```bash
npm run build
```

Expected: build succeeds with no warnings about unused imports or missing modules.

- [ ] **Step 4: Smoke test (dev server)**

```bash
npm run dev
```

Verify with admin login:
- SmartBoxen → Gerätetypen tab: firmware blocks visible, LED rows show Debug badge, "Neue Konfiguration" button appears
- SmartBoxen → SmartBoxen tab → SmartBoxCard: add-device form shows single Typ dropdown (`Name (Gruppe)` format), "Debug-Geräte anzeigen" toggle visible for admin
- SmartBoxCard device row: block/unblock buttons present, status pills appear correctly
- DeviceCard: block/unblock visible
- Ranges → Range detail → PositionCard: block/unblock visible on positioned devices

Verify with MANAGE_RANGES-only login:
- Gerätetypen tab: no "Neue Konfiguration" button, pin column is read-only text
- SmartBoxCard: no "Debug-Geräte anzeigen" toggle

---

## Self-Review

**Spec coverage:**
- ✅ Create Device-Config (name + GPIO pin + signalDurationMs scoped to FirmwareConfig) — Task 3 + Task 4
- ✅ Edit pin (ADMIN) / name + duration (MANAGE_RANGES) — Task 4 `DeviceConfigPanel` inline edit
- ✅ Add device: flat type list, no group cascade — Task 5
- ✅ LED/debug group visually fenced with Debug badge; excluded from add-device by default — Task 4 + Task 5
- ✅ `delaySignalDurationMs` removed completely — Task 1
- ✅ GPIO-Pin removed from SmartBoxCard edit row — Task 5
- ✅ `adminBlocked` field in DeviceMapper — Task 1
- ✅ `blockDevice`/`unblockDevice` store actions — Task 2
- ✅ Block/Unblock buttons in SmartBoxCard, DeviceCard, PositionCard — Task 6 + Task 7
- ✅ Status indicator (admin-blocked vs user-blocked vs unhealthy) — Task 6 + Task 7
- ✅ Admin-blocked device: Entsperren disabled for non-ADMIN — Task 6 + Task 7
- ✅ Device state re-fetched after block/unblock (no optimistic update) — Task 2 `blockDevice`/`unblockDevice` always calls `fetchDevice` after command

**Placeholder scan:**
- `ADMIN_PERMISSION = 'MANAGE_USERS'` — flagged with comment in `deviceTypes.js`; verify against `SecurityConfig.java` when backend plan is deployed
- PositionCard Step 2 note: "Find the device display area at implementation time" — intentional guidance, PositionCard's structure is deep (377 lines) and the exact anchor point should be read fresh

**Type consistency:**
- `blockDevice(deviceId: string)` / `unblockDevice(deviceId: string)` used consistently in Task 2 tests and Task 6/7 call sites ✅
- `createDeviceConfig(firmwareConfigId, { name, groupId, pin, signalDurationMs })` consistent across Task 3 tests and Task 4 call site ✅
- `ADMIN_PERMISSION` and `DEBUG_GROUP_NAMES` imported from `@/constants/deviceTypes.js` in Tasks 4, 5, 6, 7 ✅
- `availableDeviceTypes` computed in Task 5 uses `deviceTypeStore.deviceTypes` (already loaded by the existing `deviceTypeStore.loadApiData` call in SmartBoxesView `onMounted`) ✅

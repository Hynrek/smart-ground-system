# SmartBox OTA — UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Vue 3 admin UI the OTA surface: a dedicated "Firmware-Updates" page to upload and list releases, plus a per-box "update now" trigger with a live-polling progress display on the SmartBox views.

**Architecture:** Follows the project's store → service → mapper → view chain. A new `otaApi.js` service wraps the backend OTA endpoints (multipart upload bypasses the JSON content-type). A new `otaStore` (Pinia, Composition API, **api-mode only**, like `smartBoxStore`) holds releases + a per-box OTA-status map and owns the polling lifecycle (poll every 3 s until a terminal phase). A new `FirmwareUpdatesView` page handles upload + listing; a reusable `OtaUpdatePanel` embedded in `SmartBoxCard` does the per-box trigger + live status.

**Tech Stack:** Vue 3 (`<script setup>`, Composition API), Pinia, Vue Router, native `fetch` via `apiClient`, Vitest + @vue/test-utils. German display labels, English identifiers. Project-scoped `ui-ux-pro-max` design skill is available — match existing card/page styling and CSS tokens (`--sg-brand`, `--sg-text-muted`, `--sg-border`, `--sg-bg-card`, …).

**Backend contract (already implemented & merged):**
- `GET /api/ota/releases` → `[{ id, type, version, sha256, sizeBytes, createdAt }]`
- `POST /api/ota/releases` (multipart: `type`, `version`, `file`) → `201` `OtaReleaseResponse`
- `POST /api/smart-boxes/{id}/ota` (JSON `{ type, version }`) → `202` (empty body)
- `GET /api/smart-boxes/{id}/ota` → `{ version, phase, progress, detail, updatedAt }`
- The SPA hits `/api` (same origin / Vite proxy); base URL handled by `apiClient`.

**Input spec:** `docs/superpowers/specs/2026-06-27-smartbox-ota-design.md`

---

## ⚠️ Backend dependency (read first)

`SmartBoxResponse` (from `GET /api/smart-boxes`) currently exposes `firmwareVersion` (now the MicroPython kernel string, e.g. `"micropython-1.23.0"`) but **not** `appVersion` (the App-Code version the discovery handler stores on `SmartBox.appVersion`). The UI wants to show the *current installed App-Code version* per box.

- **This UI plan degrades gracefully:** it reads `box.appVersion` and shows `—` when absent, so it works today.
- **Recommended 1-line backend follow-up (separate task, backend repo):** add `appVersion` to the `SmartBoxResponse` OpenAPI schema and to `SmartBoxController.toResponse(...)`. Once shipped, the UI shows the real value with no further change.

This dependency is noted, not blocking. Do not add backend code in this (UI) plan.

## Conventions for this plan (read once)

- Work in `smart-ground-ui/`. Run tests: `npm run test`. Single file: `npm run test src/path/to/x.test.js`. Lint: `npm run lint:check`.
- `<script setup>` only; `@/` import alias; no direct API calls in components (store → service chain); `storeToRefs()` when destructuring store state in components.
- German UI text, English identifiers. Reuse existing components (`Button`, `Icons`, `StatusDot`, `Badge`, `FormField`) and CSS variables.
- Stores: `defineStore('name', () => { … })` Composition API. Store tests mock the service with `vi.mock()`; component tests `mount()` with a fresh Pinia.
- Commit messages: `[ui] short description`.

## File map (what each new/changed file owns)

- Modify `src/services/apiClient.js` — add `apiUpload(path, formData)` (multipart, auth-only headers); make `handleResponse` treat `202` as no-content.
- Create `src/services/otaApi.js` — `fetchReleases`, `uploadRelease`, `triggerOta`, `fetchOtaStatus`.
- Create `src/constants/ota.js` — phase labels (German), terminal-phase set, type constants.
- Create `src/stores/otaStore.js` — releases + per-box status map + polling lifecycle.
- Create `src/views/admin/FirmwareUpdatesView.vue` — upload form + releases list page.
- Create `src/components/OtaUpdatePanel.vue` — per-box trigger + live status widget.
- Modify `src/components/SmartBoxCard.vue` — embed `OtaUpdatePanel`; surface `appVersion`.
- Modify `src/views/admin/SmartBoxesView.vue` — provide releases to cards + own polling lifecycle.
- Modify `src/router/index.js` — add `/admin/firmware-updates` route (admin layout).
- Modify `src/components/Sidebar.vue` — nav link to the new page.
- Tests under `src/services/__tests__/`, `src/stores/__tests__/`, `src/components/__tests__/`.

## Shared shapes (keep identical across tasks)

- **Release:** `{ id, type: 'APP'|'FIRMWARE', version, sha256, sizeBytes, createdAt }`.
- **OTA status:** `{ version, phase, progress, detail, updatedAt }`, phase ∈ `DOWNLOADING|VERIFYING|APPLYING|APPLIED|FAILED|ROLLED_BACK`.
- **otaStore public surface:** `releases`, `statusByBox`, `isLoading`, `error`, `uploading`, `fetchReleases()`, `uploadRelease({type,version,file})`, `triggerUpdate(boxId,type,version)`, `fetchStatus(boxId)`, `startPolling(boxId)`, `stopPolling(boxId)`, `stopAllPolling()`.

---

## Task 1: `apiClient` — multipart upload + 202 handling

**Files:**
- Modify: `src/services/apiClient.js`
- Create: `src/services/__tests__/apiClient.test.js`

- [ ] **Step 1: Write the failing test**

```javascript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { apiUpload, handleResponse } from '@/services/apiClient.js';

describe('apiClient upload + 202', () => {
  beforeEach(() => {
    localStorage.setItem('sg_token', 'tok123');
    vi.stubGlobal('fetch', vi.fn());
  });
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('handleResponse returns null for 202 (empty accepted body)', async () => {
    const res = { ok: true, status: 202, json: () => Promise.reject(new Error('no body')) };
    await expect(handleResponse(res)).resolves.toBeNull();
  });

  it('apiUpload posts FormData with auth header and NO content-type', async () => {
    fetch.mockResolvedValue({ ok: true, status: 201, json: () => Promise.resolve({ id: 'r1' }) });
    const fd = new FormData();
    fd.append('version', '0.7');
    const out = await apiUpload('/ota/releases', fd);

    expect(out).toEqual({ id: 'r1' });
    const [url, opts] = fetch.mock.calls[0];
    expect(url).toContain('/ota/releases');
    expect(opts.method).toBe('POST');
    expect(opts.body).toBe(fd);
    expect(opts.headers.Authorization).toBe('Bearer tok123');
    // Browser must set the multipart boundary itself → we must NOT set Content-Type
    expect(opts.headers['Content-Type']).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test src/services/__tests__/apiClient.test.js`
Expected: FAIL — `apiUpload` not exported; 202 test fails (current `handleResponse` calls `response.json()` for non-204 ok → rejects).

- [ ] **Step 3: Implement the changes**

In `src/services/apiClient.js`, change the early `ok` branch of `handleResponse` and add `apiUpload`:

```javascript
export async function handleResponse(response) {
  if (response.ok) {
    // 204 No Content and 202 Accepted carry no body (e.g. push-config, OTA trigger)
    if (response.status === 204 || response.status === 202) return null;
    return response.json();
  }
  // … unchanged 401/403/error handling …
```

Add at the end of the file:

```javascript
// Multipart upload: send FormData with the auth token but WITHOUT a Content-Type
// header, so the browser sets multipart/form-data + boundary automatically.
export async function apiUpload(path, formData) {
  const token = localStorage.getItem('sg_token');
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers,
    body: formData,
  });
  return handleResponse(response);
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `npm run test src/services/__tests__/apiClient.test.js`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/services/apiClient.js src/services/__tests__/apiClient.test.js
git commit -m "[ui] Add multipart apiUpload and treat 202 as no-content"
```

---

## Task 2: `otaApi` service

**Files:**
- Create: `src/services/otaApi.js`
- Create: `src/services/__tests__/otaApi.test.js`

- [ ] **Step 1: Write the failing test**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as apiClient from '@/services/apiClient.js';
import * as otaApi from '@/services/otaApi.js';

vi.mock('@/services/apiClient.js');

describe('otaApi', () => {
  beforeEach(() => vi.clearAllMocks());

  it('fetchReleases hits GET /ota/releases', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue([{ id: 'r1' }]);
    const out = await otaApi.fetchReleases();
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/ota/releases');
    expect(out).toEqual([{ id: 'r1' }]);
  });

  it('uploadRelease posts multipart with type, version, file', async () => {
    vi.mocked(apiClient.apiUpload).mockResolvedValue({ id: 'r2' });
    const file = new File(['x'], 'bundle.zip');
    await otaApi.uploadRelease('APP', '0.7', file);
    const [path, fd] = vi.mocked(apiClient.apiUpload).mock.calls[0];
    expect(path).toBe('/ota/releases');
    expect(fd.get('type')).toBe('APP');
    expect(fd.get('version')).toBe('0.7');
    expect(fd.get('file')).toBe(file);
  });

  it('triggerOta posts JSON {type, version} to the box', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(null);
    await otaApi.triggerOta('box-1', 'APP', '0.7');
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/smart-boxes/box-1/ota', {
      method: 'POST',
      body: JSON.stringify({ type: 'APP', version: '0.7' }),
    });
  });

  it('fetchOtaStatus hits GET /smart-boxes/{id}/ota', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ phase: 'APPLIED' });
    const out = await otaApi.fetchOtaStatus('box-1');
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/smart-boxes/box-1/ota');
    expect(out).toEqual({ phase: 'APPLIED' });
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test src/services/__tests__/otaApi.test.js`
Expected: FAIL — `otaApi.js` does not exist.

- [ ] **Step 3: Implement `otaApi.js`**

```javascript
import { apiFetch, apiUpload } from './apiClient.js';

export async function fetchReleases() {
  return apiFetch('/ota/releases');
}

export async function uploadRelease(type, version, file) {
  const formData = new FormData();
  formData.append('type', type);
  formData.append('version', version);
  formData.append('file', file);
  return apiUpload('/ota/releases', formData);
}

export async function triggerOta(boxId, type, version) {
  return apiFetch(`/smart-boxes/${boxId}/ota`, {
    method: 'POST',
    body: JSON.stringify({ type, version }),
  });
}

export async function fetchOtaStatus(boxId) {
  return apiFetch(`/smart-boxes/${boxId}/ota`);
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `npm run test src/services/__tests__/otaApi.test.js`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/services/otaApi.js src/services/__tests__/otaApi.test.js
git commit -m "[ui] Add otaApi service (releases, upload, trigger, status)"
```

---

## Task 3: `constants/ota.js` — phase labels + helpers

**Files:**
- Create: `src/constants/ota.js`
- Create: `src/constants/__tests__/ota.test.js`

- [ ] **Step 1: Write the failing test**

```javascript
import { describe, it, expect } from 'vitest';
import { OTA_PHASE_LABELS, OTA_TERMINAL_PHASES, isTerminalPhase, OTA_TYPE } from '@/constants/ota.js';

describe('ota constants', () => {
  it('labels every phase in German', () => {
    ['DOWNLOADING', 'VERIFYING', 'APPLYING', 'APPLIED', 'FAILED', 'ROLLED_BACK']
      .forEach(p => expect(OTA_PHASE_LABELS[p]).toBeTruthy());
  });

  it('terminal phases are APPLIED, FAILED, ROLLED_BACK', () => {
    expect(OTA_TERMINAL_PHASES).toEqual(['APPLIED', 'FAILED', 'ROLLED_BACK']);
    expect(isTerminalPhase('APPLIED')).toBe(true);
    expect(isTerminalPhase('DOWNLOADING')).toBe(false);
    expect(isTerminalPhase(null)).toBe(false);
  });

  it('exposes APP/FIRMWARE type constants', () => {
    expect(OTA_TYPE.APP).toBe('APP');
    expect(OTA_TYPE.FIRMWARE).toBe('FIRMWARE');
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test src/constants/__tests__/ota.test.js`
Expected: FAIL — `@/constants/ota.js` does not exist.

- [ ] **Step 3: Implement `constants/ota.js`**

```javascript
// OTA phase → German display label (shown next to the progress bar).
export const OTA_PHASE_LABELS = {
  DOWNLOADING: 'Lädt herunter',
  VERIFYING: 'Verifiziert',
  APPLYING: 'Wird angewendet',
  APPLIED: 'Aktualisiert',
  FAILED: 'Fehlgeschlagen',
  ROLLED_BACK: 'Zurückgerollt',
};

// Phases at which polling stops (the update reached a final outcome).
export const OTA_TERMINAL_PHASES = ['APPLIED', 'FAILED', 'ROLLED_BACK'];

export function isTerminalPhase(phase) {
  return OTA_TERMINAL_PHASES.includes(phase);
}

export const OTA_TYPE = { APP: 'APP', FIRMWARE: 'FIRMWARE' };

export const OTA_TYPE_LABELS = { APP: 'App-Code', FIRMWARE: 'Firmware (Kernel)' };
```

- [ ] **Step 4: Run it to verify it passes**

Run: `npm run test src/constants/__tests__/ota.test.js`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/constants/ota.js src/constants/__tests__/ota.test.js
git commit -m "[ui] Add OTA phase labels and helpers"
```

---

## Task 4: `otaStore` — releases (fetch + upload)

**Files:**
- Create: `src/stores/otaStore.js`
- Create: `src/stores/__tests__/otaStore.test.js`

- [ ] **Step 1: Write the failing test**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useOtaStore } from '@/stores/otaStore.js';
import * as otaApi from '@/services/otaApi.js';

vi.mock('@/services/otaApi.js');

describe('otaStore releases', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('fetchReleases loads releases into state', async () => {
    vi.mocked(otaApi.fetchReleases).mockResolvedValue([{ id: 'r1', type: 'APP', version: '0.7' }]);
    const store = useOtaStore();
    await store.fetchReleases();
    expect(store.releases).toHaveLength(1);
    expect(store.error).toBe(null);
    expect(store.isLoading).toBe(false);
  });

  it('fetchReleases records error message on failure', async () => {
    vi.mocked(otaApi.fetchReleases).mockRejectedValue(new Error('boom'));
    const store = useOtaStore();
    await store.fetchReleases();
    expect(store.error).toBe('boom');
    expect(store.releases).toEqual([]);
  });

  it('uploadRelease uploads then refetches releases', async () => {
    vi.mocked(otaApi.uploadRelease).mockResolvedValue({ id: 'r2' });
    vi.mocked(otaApi.fetchReleases).mockResolvedValue([{ id: 'r2', type: 'APP', version: '0.8' }]);
    const store = useOtaStore();
    const file = new File(['x'], 'b.zip');
    await store.uploadRelease({ type: 'APP', version: '0.8', file });
    expect(otaApi.uploadRelease).toHaveBeenCalledWith('APP', '0.8', file);
    expect(store.releases.some(r => r.version === '0.8')).toBe(true);
    expect(store.uploading).toBe(false);
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test src/stores/__tests__/otaStore.test.js`
Expected: FAIL — `otaStore.js` does not exist.

- [ ] **Step 3: Implement `otaStore.js` (releases part)**

```javascript
import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as otaApi from '@/services/otaApi.js';

export const useOtaStore = defineStore('ota', () => {
  // ── State ──
  const releases = ref([]);
  const statusByBox = ref({});      // boxId -> { version, phase, progress, detail, updatedAt }
  const isLoading = ref(false);
  const uploading = ref(false);
  const error = ref(null);

  // Non-reactive interval handles (boxId -> intervalId); not part of the public state.
  const pollers = new Map();

  // ── Actions: releases ──
  const fetchReleases = async () => {
    isLoading.value = true;
    error.value = null;
    try {
      releases.value = await otaApi.fetchReleases();
    } catch (e) {
      error.value = e.message ?? 'Unbekannter Fehler';
      releases.value = [];
    } finally {
      isLoading.value = false;
    }
  };

  const uploadRelease = async ({ type, version, file }) => {
    uploading.value = true;
    error.value = null;
    try {
      await otaApi.uploadRelease(type, version, file);
      await fetchReleases();
    } catch (e) {
      error.value = e.message ?? 'Upload fehlgeschlagen';
      throw e;
    } finally {
      uploading.value = false;
    }
  };

  return {
    releases,
    statusByBox,
    isLoading,
    uploading,
    error,
    fetchReleases,
    uploadRelease,
    // status + polling actions added in Task 5
    _pollers: pollers,
  };
});
```

> The status/polling actions are added in Task 5; `statusByBox` and `pollers` are declared now so the store shape is stable.

- [ ] **Step 4: Run it to verify it passes**

Run: `npm run test src/stores/__tests__/otaStore.test.js`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/stores/otaStore.js src/stores/__tests__/otaStore.test.js
git commit -m "[ui] Add otaStore with release fetch + upload"
```

---

## Task 5: `otaStore` — per-box status + polling

**Files:**
- Modify: `src/stores/otaStore.js`
- Modify: `src/stores/__tests__/otaStore.test.js`

- [ ] **Step 1: Write the failing tests (append a new describe block)**

```javascript
import { afterEach } from 'vitest';
import { isTerminalPhase } from '@/constants/ota.js';

describe('otaStore status + polling', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('fetchStatus stores status under the box id', async () => {
    vi.mocked(otaApi.fetchOtaStatus).mockResolvedValue({ phase: 'DOWNLOADING', progress: 40, version: '0.7' });
    const store = useOtaStore();
    await store.fetchStatus('box-1');
    expect(store.statusByBox['box-1'].phase).toBe('DOWNLOADING');
  });

  it('startPolling fetches repeatedly until a terminal phase, then stops', async () => {
    vi.mocked(otaApi.fetchOtaStatus)
      .mockResolvedValueOnce({ phase: 'DOWNLOADING', progress: 20 })
      .mockResolvedValueOnce({ phase: 'APPLYING', progress: 80 })
      .mockResolvedValue({ phase: 'APPLIED', progress: 100 });
    const store = useOtaStore();

    store.startPolling('box-1', 1000);
    await vi.advanceTimersByTimeAsync(0);      // immediate first fetch
    expect(store.statusByBox['box-1'].phase).toBe('DOWNLOADING');
    await vi.advanceTimersByTimeAsync(1000);
    expect(store.statusByBox['box-1'].phase).toBe('APPLYING');
    await vi.advanceTimersByTimeAsync(1000);
    expect(store.statusByBox['box-1'].phase).toBe('APPLIED');

    const callsAtTerminal = vi.mocked(otaApi.fetchOtaStatus).mock.calls.length;
    await vi.advanceTimersByTimeAsync(3000);   // no further polling after terminal
    expect(vi.mocked(otaApi.fetchOtaStatus).mock.calls.length).toBe(callsAtTerminal);
  });

  it('triggerUpdate posts the command then starts polling', async () => {
    vi.mocked(otaApi.triggerOta).mockResolvedValue(null);
    vi.mocked(otaApi.fetchOtaStatus).mockResolvedValue({ phase: 'DOWNLOADING', progress: 0 });
    const store = useOtaStore();
    await store.triggerUpdate('box-1', 'APP', '0.7');
    expect(otaApi.triggerOta).toHaveBeenCalledWith('box-1', 'APP', '0.7');
    await vi.advanceTimersByTimeAsync(0);
    expect(store.statusByBox['box-1'].phase).toBe('DOWNLOADING');
    store.stopAllPolling();
  });

  it('stopPolling clears the interval for a box', async () => {
    vi.mocked(otaApi.fetchOtaStatus).mockResolvedValue({ phase: 'DOWNLOADING', progress: 10 });
    const store = useOtaStore();
    store.startPolling('box-1', 1000);
    await vi.advanceTimersByTimeAsync(0);
    const calls = vi.mocked(otaApi.fetchOtaStatus).mock.calls.length;
    store.stopPolling('box-1');
    await vi.advanceTimersByTimeAsync(5000);
    expect(vi.mocked(otaApi.fetchOtaStatus).mock.calls.length).toBe(calls);
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test src/stores/__tests__/otaStore.test.js`
Expected: FAIL — `fetchStatus` / `startPolling` / `triggerUpdate` / `stopPolling` not defined.

- [ ] **Step 3: Implement the status + polling actions**

In `src/stores/otaStore.js`, add the import and the actions, and export them. Add at the top:

```javascript
import { isTerminalPhase } from '@/constants/ota.js';
```

Insert these actions before the `return`:

```javascript
  // ── Actions: per-box status + polling ──
  const fetchStatus = async (boxId) => {
    try {
      const status = await otaApi.fetchOtaStatus(boxId);
      statusByBox.value = { ...statusByBox.value, [boxId]: status };
      return status;
    } catch (e) {
      // A box that has never reported leaves no status; don't surface as a page error
      console.error('OTA-Status konnte nicht geladen werden:', e);
      return null;
    }
  };

  const stopPolling = (boxId) => {
    const handle = pollers.get(boxId);
    if (handle !== undefined) {
      clearInterval(handle);
      pollers.delete(boxId);
    }
  };

  const startPolling = (boxId, intervalMs = 3000) => {
    stopPolling(boxId);              // never double-poll the same box
    const tick = async () => {
      const status = await fetchStatus(boxId);
      if (status && isTerminalPhase(status.phase)) {
        stopPolling(boxId);
      }
    };
    tick();                          // immediate first fetch
    pollers.set(boxId, setInterval(tick, intervalMs));
  };

  const stopAllPolling = () => {
    for (const handle of pollers.values()) clearInterval(handle);
    pollers.clear();
  };

  const triggerUpdate = async (boxId, type, version) => {
    error.value = null;
    try {
      await otaApi.triggerOta(boxId, type, version);
      startPolling(boxId);
    } catch (e) {
      error.value = e.message ?? 'Update konnte nicht gestartet werden';
      throw e;
    }
  };
```

Add them to the returned object:

```javascript
    fetchStatus,
    startPolling,
    stopPolling,
    stopAllPolling,
    triggerUpdate,
```

- [ ] **Step 4: Run it to verify it passes**

Run: `npm run test src/stores/__tests__/otaStore.test.js`
Expected: PASS (all release + status/polling tests).

- [ ] **Step 5: Commit**

```bash
git add src/stores/otaStore.js src/stores/__tests__/otaStore.test.js
git commit -m "[ui] Add per-box OTA status polling to otaStore"
```

---

## Task 6: `FirmwareUpdatesView` — upload + releases list page

**Files:**
- Create: `src/views/admin/FirmwareUpdatesView.vue`
- Create: `src/components/__tests__/FirmwareUpdatesView.test.js`

- [ ] **Step 1: Write the failing test**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import FirmwareUpdatesView from '@/views/admin/FirmwareUpdatesView.vue';
import { useOtaStore } from '@/stores/otaStore.js';

describe('FirmwareUpdatesView', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('lists releases from the store', async () => {
    const store = useOtaStore();
    store.fetchReleases = vi.fn();
    store.releases = [
      { id: 'r1', type: 'APP', version: '0.7', sha256: 'ab', sizeBytes: 1024, createdAt: '2026-06-29T10:00:00Z' },
    ];
    const wrapper = mount(FirmwareUpdatesView);
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain('0.7');
    expect(wrapper.text()).toContain('App-Code');
  });

  it('calls uploadRelease with the form values on submit', async () => {
    const store = useOtaStore();
    store.fetchReleases = vi.fn();
    store.uploadRelease = vi.fn().mockResolvedValue();
    const wrapper = mount(FirmwareUpdatesView);

    await wrapper.find('[data-testid="ota-version"]').setValue('0.9');
    // Simulate a chosen file by setting the component's file ref through the input event
    const file = new File(['x'], 'bundle.zip');
    const input = wrapper.find('[data-testid="ota-file"]');
    Object.defineProperty(input.element, 'files', { value: [file] });
    await input.trigger('change');

    await wrapper.find('[data-testid="ota-upload-btn"]').trigger('click');
    expect(store.uploadRelease).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'APP', version: '0.9', file }),
    );
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test src/components/__tests__/FirmwareUpdatesView.test.js`
Expected: FAIL — view does not exist.

- [ ] **Step 3: Implement `FirmwareUpdatesView.vue`**

```vue
<template>
  <div class="ota-view">
    <div class="view-content">
      <div class="view-header">
        <div>
          <h1>Firmware-Updates</h1>
          <p class="subtitle">{{ otaStore.releases.length }} Release(s) hochgeladen</p>
        </div>
      </div>

      <!-- Upload form -->
      <form class="upload-card" @submit.prevent="submit">
        <div class="form-row">
          <label class="form-group">
            <span>Typ</span>
            <select v-model="form.type" data-testid="ota-type">
              <option :value="OTA_TYPE.APP">{{ OTA_TYPE_LABELS.APP }}</option>
              <option :value="OTA_TYPE.FIRMWARE">{{ OTA_TYPE_LABELS.FIRMWARE }}</option>
            </select>
          </label>
          <label class="form-group">
            <span>Version</span>
            <input v-model="form.version" data-testid="ota-version" placeholder="z.B. 0.7" />
          </label>
          <label class="form-group file-group">
            <span>{{ form.type === OTA_TYPE.APP ? 'App-Code (ZIP)' : 'Firmware (.bin)' }}</span>
            <input
              ref="fileInput"
              type="file"
              data-testid="ota-file"
              :accept="form.type === OTA_TYPE.APP ? '.zip' : '.bin'"
              @change="onFile"
            />
          </label>
          <Button
            type="submit"
            variant="primary"
            data-testid="ota-upload-btn"
            :disabled="!canSubmit || otaStore.uploading"
          >
            {{ otaStore.uploading ? 'Lädt hoch…' : 'Hochladen' }}
          </Button>
        </div>
        <p v-if="otaStore.error" class="form-error">{{ otaStore.error }}</p>
      </form>

      <!-- Releases list -->
      <div v-if="otaStore.releases.length" class="release-table-wrap">
        <table class="release-table">
          <thead>
            <tr><th>Typ</th><th>Version</th><th>Grösse</th><th>SHA-256</th><th>Hochgeladen</th></tr>
          </thead>
          <tbody>
            <tr v-for="r in otaStore.releases" :key="r.id">
              <td><Badge :color="r.type === OTA_TYPE.APP ? 'blue' : 'amber'">{{ OTA_TYPE_LABELS[r.type] }}</Badge></td>
              <td class="mono">{{ r.version }}</td>
              <td>{{ formatSize(r.sizeBytes) }}</td>
              <td class="mono sha">{{ r.sha256.slice(0, 12) }}…</td>
              <td>{{ formatDate(r.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">Noch keine Releases hochgeladen.</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useOtaStore } from '@/stores/otaStore.js';
import { OTA_TYPE, OTA_TYPE_LABELS } from '@/constants/ota.js';
import Button from '@/components/Button.vue';
import Badge from '@/components/Badge.vue';

const otaStore = useOtaStore();
const fileInput = ref(null);
const form = reactive({ type: OTA_TYPE.APP, version: '', file: null });

const canSubmit = computed(() => !!form.version.trim() && !!form.file);

const onFile = (e) => {
  form.file = e.target.files?.[0] ?? null;
};

const submit = async () => {
  if (!canSubmit.value) return;
  try {
    await otaStore.uploadRelease({ type: form.type, version: form.version.trim(), file: form.file });
    form.version = '';
    form.file = null;
    if (fileInput.value) fileInput.value.value = '';
  } catch {
    // error surfaced via otaStore.error
  }
};

const formatSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};

const formatDate = (iso) =>
  new Date(iso).toLocaleString('de-CH', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

onMounted(() => otaStore.fetchReleases());
</script>

<style scoped>
.ota-view { padding: 28px; flex: 1; overflow-y: auto; }
.view-content { max-width: 960px; }
.view-header { margin-bottom: 20px; }
h1 { font-size: 22px; font-weight: 700; color: var(--sg-brand); margin: 0; }
.subtitle { font-size: 13px; color: var(--sg-text-muted); margin-top: 4px; }
.upload-card { background: var(--sg-bg-card); border: 1px solid var(--sg-border); border-radius: 12px; padding: 18px; margin-bottom: 24px; }
.form-row { display: flex; gap: 14px; align-items: flex-end; flex-wrap: wrap; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group span { font-size: 11.5px; color: var(--sg-text-muted); font-weight: 500; }
.form-group select, .form-group input { padding: 7px 10px; border: 1.5px solid var(--sg-border); border-radius: 7px; font-size: 13px; font-family: inherit; }
.form-error { color: #e05252; font-size: 12px; margin-top: 10px; }
.release-table-wrap { border: 1px solid var(--sg-border); border-radius: 12px; overflow: hidden; }
.release-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.release-table th { text-align: left; padding: 9px 14px; background: var(--sg-bg-panel); color: var(--sg-text-muted); font-size: 11.5px; text-transform: uppercase; letter-spacing: 0.4px; }
.release-table td { padding: 9px 14px; border-top: 1px solid var(--sg-border); }
.mono { font-family: monospace; }
.sha { color: var(--sg-text-muted); }
.empty-state { text-align: center; padding: 40px; color: var(--sg-text-faint); font-size: 13px; }
@media (max-width: 768px) { .ota-view { padding: 16px; } .form-row { flex-direction: column; align-items: stretch; } }
</style>
```

- [ ] **Step 4: Run it to verify it passes**

Run: `npm run test src/components/__tests__/FirmwareUpdatesView.test.js`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/views/admin/FirmwareUpdatesView.vue src/components/__tests__/FirmwareUpdatesView.test.js
git commit -m "[ui] Add Firmware-Updates page (upload + release list)"
```

---

## Task 7: Route + sidebar nav

**Files:**
- Modify: `src/router/index.js`
- Modify: `src/components/Sidebar.vue`
- Create: `src/router/__tests__/otaRoute.test.js`

- [ ] **Step 1: Inspect the existing pattern**

Open `src/router/index.js` and find the `FirmwareConfigsView` route (path `/admin/firmware-configs`). Copy its exact shape — `meta` keys (`layout: 'admin'`, any role/`requiresAuth`), and whether it uses a lazy `() => import(...)` or a static import. Match it.

- [ ] **Step 2: Write the failing test**

```javascript
import { describe, it, expect } from 'vitest';
import router from '@/router/index.js';

describe('OTA route', () => {
  it('registers /admin/firmware-updates with admin layout', () => {
    const match = router.resolve('/admin/firmware-updates');
    expect(match.matched.length).toBeGreaterThan(0);
    expect(match.meta.layout).toBe('admin');
  });
});
```

- [ ] **Step 3: Run it to verify it fails**

Run: `npm run test src/router/__tests__/otaRoute.test.js`
Expected: FAIL — route not found (`match.matched.length === 0`).

- [ ] **Step 4: Add the route + nav link**

In `src/router/index.js`, add a route mirroring the `firmware-configs` entry exactly (same `meta`), e.g.:

```javascript
  {
    path: '/admin/firmware-updates',
    name: 'firmware-updates',
    component: () => import('@/views/admin/FirmwareUpdatesView.vue'),
    meta: { layout: 'admin' },   // copy the EXACT meta keys used by firmware-configs
  },
```

In `src/components/Sidebar.vue`, add a navigation entry next to the SmartBoxen / Firmware-Configs links, following the existing link markup (same component/icon pattern). Label it **"Firmware-Updates"**, route `/admin/firmware-updates`, using whatever icon convention the sidebar uses (e.g. `download` or `refresh`; pick an existing icon name from `Icons.vue`).

- [ ] **Step 5: Run it to verify it passes**

Run: `npm run test src/router/__tests__/otaRoute.test.js`
Expected: PASS. Also run `npm run lint:check` to confirm no lint errors in the edited files.

- [ ] **Step 6: Commit**

```bash
git add src/router/index.js src/components/Sidebar.vue src/router/__tests__/otaRoute.test.js
git commit -m "[ui] Add Firmware-Updates route and sidebar nav"
```

---

## Task 8: `OtaUpdatePanel` — per-box trigger + live status

**Files:**
- Create: `src/components/OtaUpdatePanel.vue`
- Create: `src/components/__tests__/OtaUpdatePanel.test.js`

- [ ] **Step 1: Write the failing test**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import OtaUpdatePanel from '@/components/OtaUpdatePanel.vue';
import { useOtaStore } from '@/stores/otaStore.js';

const box = { id: 'box-1', alias: 'Box 1', appVersion: '0.6' };

describe('OtaUpdatePanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('shows the current app version and APP releases in the picker', () => {
    const store = useOtaStore();
    store.releases = [
      { id: 'r1', type: 'APP', version: '0.7' },
      { id: 'r2', type: 'FIRMWARE', version: 'mp-1.24' },
    ];
    const wrapper = mount(OtaUpdatePanel, { props: { box } });
    expect(wrapper.text()).toContain('0.6');               // current appVersion
    const options = wrapper.findAll('[data-testid="ota-version-option"]');
    expect(options).toHaveLength(1);                        // only APP releases by default
    expect(options[0].text()).toContain('0.7');
  });

  it('calls triggerUpdate with the selected version', async () => {
    const store = useOtaStore();
    store.releases = [{ id: 'r1', type: 'APP', version: '0.7' }];
    store.triggerUpdate = vi.fn().mockResolvedValue();
    const wrapper = mount(OtaUpdatePanel, { props: { box } });
    await wrapper.find('[data-testid="ota-version-select"]').setValue('0.7');
    await wrapper.find('[data-testid="ota-trigger-btn"]').trigger('click');
    expect(store.triggerUpdate).toHaveBeenCalledWith('box-1', 'APP', '0.7');
  });

  it('renders a progress bar with the German phase label while updating', () => {
    const store = useOtaStore();
    store.releases = [];
    store.statusByBox = { 'box-1': { phase: 'DOWNLOADING', progress: 40, version: '0.7' } };
    const wrapper = mount(OtaUpdatePanel, { props: { box } });
    expect(wrapper.text()).toContain('Lädt herunter');
    expect(wrapper.find('[data-testid="ota-progress-bar"]').attributes('style')).toContain('40%');
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test src/components/__tests__/OtaUpdatePanel.test.js`
Expected: FAIL — component does not exist.

- [ ] **Step 3: Implement `OtaUpdatePanel.vue`**

```vue
<template>
  <div class="ota-panel">
    <div class="ota-row">
      <div class="ota-version">
        <span class="ota-label">App-Code</span>
        <span class="ota-current mono">v{{ box.appVersion ?? '—' }}</span>
      </div>

      <!-- Active update: live progress -->
      <div v-if="status && !isTerminalPhase(status.phase)" class="ota-progress">
        <div class="ota-progress-head">
          <span>{{ phaseLabel(status.phase) }} → v{{ status.version }}</span>
          <span class="mono">{{ status.progress ?? 0 }}%</span>
        </div>
        <div class="ota-bar-track">
          <div
            class="ota-bar-fill"
            data-testid="ota-progress-bar"
            :style="{ width: (status.progress ?? 0) + '%' }"
          />
        </div>
      </div>

      <!-- Terminal result badge -->
      <Badge v-else-if="status" :color="resultColor(status.phase)">
        {{ phaseLabel(status.phase) }}<template v-if="status.version"> · v{{ status.version }}</template>
      </Badge>

      <!-- Trigger -->
      <div class="ota-trigger">
        <select v-model="selectedVersion" data-testid="ota-version-select" :disabled="updating || !appReleases.length">
          <option value="" disabled>Version wählen</option>
          <option
            v-for="r in appReleases"
            :key="r.id"
            :value="r.version"
            data-testid="ota-version-option"
          >v{{ r.version }}</option>
        </select>
        <Button
          variant="primary"
          data-testid="ota-trigger-btn"
          :disabled="!selectedVersion || updating"
          @click="trigger"
        >
          <template #icon><Icons icon="download" :size="12" /></template>
          Update
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { storeToRefs } from 'pinia';
import { useOtaStore } from '@/stores/otaStore.js';
import { OTA_PHASE_LABELS, isTerminalPhase, OTA_TYPE } from '@/constants/ota.js';
import Button from '@/components/Button.vue';
import Badge from '@/components/Badge.vue';
import Icons from '@/components/Icons.vue';

const props = defineProps({ box: { type: Object, required: true } });

const otaStore = useOtaStore();
const { releases, statusByBox } = storeToRefs(otaStore);

const selectedVersion = ref('');
const updating = computed(() => {
  const s = statusByBox.value[props.box.id];
  return !!s && !isTerminalPhase(s.phase);
});
const status = computed(() => statusByBox.value[props.box.id] ?? null);
const appReleases = computed(() => releases.value.filter((r) => r.type === OTA_TYPE.APP));

const phaseLabel = (p) => OTA_PHASE_LABELS[p] ?? p;
const resultColor = (p) => (p === 'APPLIED' ? 'green' : p === 'ROLLED_BACK' ? 'amber' : 'red');

const trigger = async () => {
  if (!selectedVersion.value) return;
  try {
    await otaStore.triggerUpdate(props.box.id, OTA_TYPE.APP, selectedVersion.value);
  } catch {
    // surfaced via otaStore.error
  }
};
</script>

<style scoped>
.ota-panel { padding: 10px 18px; border-top: 1px dashed var(--sg-border, #e2e8f0); }
.ota-row { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.ota-version { display: flex; flex-direction: column; }
.ota-label { font-size: 11px; color: var(--sg-text-muted, #718096); }
.ota-current { font-size: 13px; font-weight: 600; }
.ota-progress { flex: 1; min-width: 160px; }
.ota-progress-head { display: flex; justify-content: space-between; font-size: 11.5px; color: var(--sg-text-muted, #718096); margin-bottom: 3px; }
.ota-bar-track { height: 6px; border-radius: 99px; background: var(--sg-bg-panel, #f0f4f8); overflow: hidden; }
.ota-bar-fill { height: 100%; background: var(--sg-brand, #4fc3f7); transition: width 0.3s; }
.ota-trigger { display: flex; gap: 8px; align-items: center; margin-left: auto; }
.ota-trigger select { padding: 6px 10px; border: 1.5px solid var(--sg-border, #e2e8f0); border-radius: 7px; font-size: 13px; font-family: inherit; }
.mono { font-family: monospace; }
</style>
```

> The panel reads only `box.appVersion` and the store's `statusByBox`/`releases` — it owns no polling. Polling is driven by the trigger action and the parent view (Task 9).

- [ ] **Step 4: Run it to verify it passes**

Run: `npm run test src/components/__tests__/OtaUpdatePanel.test.js`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/components/OtaUpdatePanel.vue src/components/__tests__/OtaUpdatePanel.test.js
git commit -m "[ui] Add OtaUpdatePanel (per-box trigger + live progress)"
```

---

## Task 9: Integrate panel into `SmartBoxCard` + own polling in `SmartBoxesView`

**Files:**
- Modify: `src/components/SmartBoxCard.vue`
- Modify: `src/views/admin/SmartBoxesView.vue`
- Modify: `src/components/__tests__/` — add `SmartBoxCardOta.test.js`

- [ ] **Step 1: Write the failing test**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import SmartBoxCard from '@/components/SmartBoxCard.vue';

const box = {
  id: 'box-1', alias: 'Box 1', macAddress: 'aabbccddeeff',
  status: 'ONLINE', firmwareVersion: 'micropython-1.23.0', appVersion: '0.6',
};

describe('SmartBoxCard OTA panel', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('renders the OTA panel for the box', () => {
    const wrapper = mount(SmartBoxCard, {
      props: { box, devices: [], allDevicesCount: 0 },
      global: { stubs: { TypeChip: true } },
    });
    // OtaUpdatePanel surfaces the current app version label
    expect(wrapper.findComponent({ name: 'OtaUpdatePanel' }).exists()).toBe(true);
  });
});
```

> If `findComponent({ name: 'OtaUpdatePanel' })` doesn't resolve (component name inference), assert on a stable selector the panel renders, e.g. `wrapper.find('.ota-panel').exists()`.

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test src/components/__tests__/SmartBoxCardOta.test.js`
Expected: FAIL — panel not present in the card.

- [ ] **Step 3: Embed the panel in `SmartBoxCard.vue`**

Add the import in `<script setup>`:

```javascript
import OtaUpdatePanel from './OtaUpdatePanel.vue';
```

In the template, place the panel just above the `add-device-section` (so OTA controls sit at the bottom of the card, below the device table):

```vue
    <!-- OTA update controls -->
    <OtaUpdatePanel :box="box" />

    <!-- Add device form / button -->
    <div class="add-device-section">
```

Optional polish (keeps the firmware badge meaningful now that `firmwareVersion` is the kernel string): in the box header, leave the existing `firmware-badge` as-is — it shows the kernel; the panel shows the App-Code version. No change required.

- [ ] **Step 4: Own the polling lifecycle in `SmartBoxesView.vue`**

In `src/views/admin/SmartBoxesView.vue` `<script setup>`, add:

```javascript
import { onMounted, onUnmounted } from 'vue';
import { useOtaStore } from '@/stores/otaStore.js';

const otaStore = useOtaStore();

onMounted(async () => {
  await otaStore.fetchReleases();
  // Load each box's last OTA status; resume polling for any mid-update box.
  for (const box of smartBoxStore.smartboxes) {
    const status = await otaStore.fetchStatus(box.id);
    if (status && status.phase && !['APPLIED', 'FAILED', 'ROLLED_BACK'].includes(status.phase)) {
      otaStore.startPolling(box.id);
    }
  }
});

onUnmounted(() => otaStore.stopAllPolling());
```

> `smartBoxStore.smartboxes` may load asynchronously; if the view already triggers box loading elsewhere, fetch statuses inside the existing `watch(() => smartBoxStore.smartboxes, …)` instead — match whatever ordering the view already uses so statuses are fetched once boxes exist. The key requirements: fetch releases on mount, resume polling for in-flight boxes, and `stopAllPolling()` on unmount.

- [ ] **Step 5: Run it to verify it passes**

Run: `npm run test src/components/__tests__/SmartBoxCardOta.test.js`
Expected: PASS. Then run the full suite: `npm run test` — all green. Then `npm run lint:check`.

- [ ] **Step 6: Commit**

```bash
git add src/components/SmartBoxCard.vue src/views/admin/SmartBoxesView.vue src/components/__tests__/SmartBoxCardOta.test.js
git commit -m "[ui] Embed OTA panel in SmartBoxCard and drive polling from the view"
```

---

## Task 10: Build, full suite, docs

**Files:**
- Modify: `smart-ground-ui/CLAUDE.md`

- [ ] **Step 1: Full verification**

Run: `npm run test` (all green), `npm run lint:check` (no errors), `npm run build` (no warnings/errors).

- [ ] **Step 2: Update `CLAUDE.md`**

Make these edits to `smart-ground-ui/CLAUDE.md`:

1. **Service Layer table** — add a row: `otaApi.js` | OTA releases + per-box trigger/status | `GET/POST /ota/releases`, `POST/GET /smart-boxes/{id}/ota`.
2. **Core Pinia Stores** — add `otaStore` with a one-paragraph description (releases + per-box `statusByBox` map + polling lifecycle that stops at terminal phases; api-mode only, like `smartBoxStore`).
3. **Routing → Admin routes** — add `/admin/firmware-updates` (Firmware-Updates page; ADMIN).
4. **Implementation Status** — move "OTA firmware updates" from ❌ Not Yet Implemented to ✅ Fully Implemented, noting the upload page + per-box live-polling trigger, and the standing dependency that `SmartBoxResponse` should expose `appVersion` (UI degrades gracefully until then).

- [ ] **Step 3: Commit**

```bash
git add smart-ground-ui/CLAUDE.md
git commit -m "[ui] Document OTA UI (otaApi, otaStore, Firmware-Updates page)"
```

---

## Out of scope (this plan)

- Firmware (kernel) OTA from the UI — the `OtaUpdatePanel` triggers App-Code updates only. Firmware releases still upload + list on the page; per-box firmware triggering can be a follow-up (the store/`triggerUpdate` already accept `type`).
- Real-time status via STOMP — polling is used deliberately (STOMP isn't wired; see CLAUDE.md). When the backend emits OTA events, the polling in `otaStore` can be replaced by a subscription with no view changes.
- The backend `SmartBoxResponse.appVersion` addition (see the dependency note) — a separate 1-line backend task.

## Backend ⇄ UI contract check

- `GET /ota/releases` → list page + per-box version picker — ✅ Tasks 2, 6, 8.
- `POST /ota/releases` (multipart) → upload form — ✅ Tasks 1, 2, 6.
- `POST /smart-boxes/{id}/ota` → per-box trigger (202 handled) — ✅ Tasks 1, 2, 5, 8.
- `GET /smart-boxes/{id}/ota` → live status polling until terminal — ✅ Tasks 2, 5, 8, 9.
- `appVersion` shown per box — ✅ Task 8 (graceful `—` until backend exposes it).

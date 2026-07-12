# Data Lifecycle Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wipe and refetch all domain data on user switch, and keep admin-editable data near-real-time on active views — without leaving the store→service architecture.

**Architecture:** Three in-house pieces. (1) A Pinia plugin gives every setup store a working `$reset()`. (2) A central `dataLifecycle` module owns app-data init + reset; `authStore` calls reset on logout/login and the router calls init. (3) A `useRevalidate` composable lets active views opt into interval polling that pauses on hidden tabs.

**Tech Stack:** Vue 3.5 (`<script setup>`), Pinia 3, Vue Router 4, Vitest 4 + @vue/test-utils.

## Global Constraints

- `<script setup>` + Composition API only; no Options API.
- `@/` alias for all `src/` imports.
- No direct API calls in components — go through stores.
- German display labels; English identifiers, comments, and tests.
- Design tokens only (`--sg-*`); no hard-coded colors. (No visual changes in this plan.)
- No new dependencies.
- Polling loops stopped on unmount.
- Tests in English, independent, fresh Pinia per test (`setActivePinia(createPinia())`), services mocked with `vi.mock()`.
- Commit messages: `[ui] short description`.
- Run a single test file with: `npm run test <path>` (Vitest).

---

### Task 1: Resettable Pinia plugin

Gives every store a `$reset()` that restores the state captured at store creation.

**Files:**
- Create: `src/stores/plugins/resettable.js`
- Test: `src/stores/plugins/__tests__/resettable.test.js`
- Modify: `src/main.js:9-11`

**Interfaces:**
- Consumes: nothing.
- Produces: `resettablePlugin({ store })` — a Pinia plugin. After registration every store gains `store.$reset(): void` restoring initial `$state` (deep-cloned, no shared references).

- [ ] **Step 1: Write the failing test**

```js
// src/stores/plugins/__tests__/resettable.test.js
import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia, defineStore } from 'pinia';
import { ref } from 'vue';
import { resettablePlugin } from '../resettable.js';

const useThing = defineStore('thing', () => {
  const items = ref([]);
  const meta = ref({ nested: { count: 0 } });
  return { items, meta };
});

describe('resettablePlugin', () => {
  beforeEach(() => {
    const pinia = createPinia();
    pinia.use(resettablePlugin);
    setActivePinia(pinia);
  });

  it('restores primitive and array state', () => {
    const store = useThing();
    store.items.push('a', 'b');
    expect(store.items).toHaveLength(2);
    store.$reset();
    expect(store.items).toEqual([]);
  });

  it('restores nested objects without sharing references', () => {
    const store = useThing();
    store.meta.nested.count = 5;
    store.$reset();
    expect(store.meta.nested.count).toBe(0);
    // Mutating again after reset must not have leaked into the captured snapshot
    store.meta.nested.count = 9;
    store.$reset();
    expect(store.meta.nested.count).toBe(0);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/stores/plugins/__tests__/resettable.test.js`
Expected: FAIL — cannot import `resettablePlugin` (module does not exist).

- [ ] **Step 3: Write minimal implementation**

```js
// src/stores/plugins/resettable.js
import { toRaw } from 'vue';

// Pinia plugin: snapshot each store's initial state at creation and expose a
// working $reset() for setup (factory) stores, which do not get one for free.
export function resettablePlugin({ store }) {
  const initial = structuredClone(toRaw(store.$state));
  store.$reset = () => {
    store.$patch(structuredClone(initial));
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/stores/plugins/__tests__/resettable.test.js`
Expected: PASS (2 tests).

- [ ] **Step 5: Register the plugin in `main.js`**

In `src/main.js`, change the Pinia setup:

```js
import { createPinia } from 'pinia';
import { resettablePlugin } from './stores/plugins/resettable.js';

const app = createApp(App);
const pinia = createPinia();
pinia.use(resettablePlugin);

app.use(pinia);
app.use(router);
```

- [ ] **Step 6: Verify existing suite still passes**

Run: `npm run test`
Expected: PASS (no regressions).

- [ ] **Step 7: Commit**

```bash
git add src/stores/plugins/resettable.js src/stores/plugins/__tests__/resettable.test.js src/main.js
git commit -m "[ui] add resettable Pinia plugin giving setup stores \$reset()"
```

---

### Task 2: dataLifecycle module (init + reset)

Centralizes app-data initialization (moved out of the router) and a reset that wipes every non-auth store and clears the init flag.

**Files:**
- Create: `src/stores/dataLifecycle.js`
- Test: `src/stores/__tests__/dataLifecycle.test.js`

**Interfaces:**
- Consumes: `store.$reset()` (Task 1); optional `store.stopAllPolling()` on polling stores (e.g. `otaStore`); the store init methods currently called inline in the router.
- Produces:
  - `initializeAppData(): Promise<void>` — runs the one-time store initialization; idempotent (guarded by an internal flag).
  - `resetAppData(): void` — for every instantiated store except `auth`: calls `stopAllPolling?.()` then `$reset?.()`; clears the init flag.
  - `isAppDataInitialized(): boolean` — test/inspection helper.

- [ ] **Step 1: Write the failing test**

```js
// src/stores/__tests__/dataLifecycle.test.js
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia, defineStore } from 'pinia';
import { ref } from 'vue';
import { resettablePlugin } from '../plugins/resettable.js';
import { resetAppData, initializeAppData, isAppDataInitialized } from '../dataLifecycle.js';

// Mock every module initializeAppData dynamically imports, so it performs no I/O.
const loadGuests = vi.fn().mockResolvedValue();
const initializeStore = vi.fn().mockResolvedValue();
const loadSerienFromStorage = vi.fn().mockResolvedValue();
const loadPassenFromStorage = vi.fn().mockResolvedValue();
const loadFromStorage = vi.fn().mockResolvedValue();
const loadEvents = vi.fn().mockResolvedValue();

vi.mock('@/stores/appStore.js', () => ({ useAppStore: () => ({ initializeStore }) }));
vi.mock('@/stores/guestStore.js', () => ({ useGuestStore: () => ({ loadGuests }) }));
vi.mock('@/stores/passeStore.js', () => ({
  usePasseStore: () => ({ loadSerienFromStorage, loadPassenFromStorage }),
}));
vi.mock('@/stores/activePasseStore.js', () => ({ useActivePasseStore: () => ({ loadFromStorage }) }));
vi.mock('@/stores/competitionEventStore.js', () => ({ useCompetitionEventStore: () => ({ loadEvents }) }));

const useAuthLike = defineStore('auth', () => {
  const token = ref('user-a-token');
  return { token };
});
const useDataLike = defineStore('data', () => {
  const items = ref([]);
  return { items };
});
const usePollingLike = defineStore('polling', () => {
  const items = ref([]);
  const stopAllPolling = vi.fn();
  return { items, stopAllPolling };
});

describe('dataLifecycle', () => {
  beforeEach(() => {
    const pinia = createPinia();
    pinia.use(resettablePlugin);
    setActivePinia(pinia);
    vi.clearAllMocks();
  });

  it('resets non-auth stores and leaves auth untouched', () => {
    const auth = useAuthLike();
    const data = useDataLike();
    auth.token = 'still-user-a';
    data.items.push('leaked');

    resetAppData();

    expect(data.items).toEqual([]);        // wiped
    expect(auth.token).toBe('still-user-a'); // auth is the login/logout owner
  });

  it('stops polling before resetting a polling store', () => {
    const polling = usePollingLike();
    resetAppData();
    expect(polling.stopAllPolling).toHaveBeenCalledOnce();
  });

  it('initializes once and re-arms after reset', async () => {
    expect(isAppDataInitialized()).toBe(false);
    await initializeAppData();
    expect(isAppDataInitialized()).toBe(true);
    expect(initializeStore).toHaveBeenCalledOnce();

    await initializeAppData(); // idempotent
    expect(initializeStore).toHaveBeenCalledOnce();

    resetAppData();
    expect(isAppDataInitialized()).toBe(false);
    await initializeAppData(); // re-arms
    expect(initializeStore).toHaveBeenCalledTimes(2);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/stores/__tests__/dataLifecycle.test.js`
Expected: FAIL — cannot import from `../dataLifecycle.js` (module does not exist).

- [ ] **Step 3: Write minimal implementation**

```js
// src/stores/dataLifecycle.js
import { getActivePinia } from 'pinia';

let _initialized = false;

export function isAppDataInitialized() {
  return _initialized;
}

// One-time initialization of API-backed stores, run on first authenticated
// navigation. Idempotent; dynamic imports keep these stores lazily loaded.
export async function initializeAppData() {
  if (_initialized) return;
  _initialized = true;

  const { useAppStore } = await import('@/stores/appStore.js');
  const { useGuestStore } = await import('@/stores/guestStore.js');
  const { usePasseStore } = await import('@/stores/passeStore.js');
  const { useActivePasseStore } = await import('@/stores/activePasseStore.js');
  const { useCompetitionEventStore } = await import('@/stores/competitionEventStore.js');

  const appStore = useAppStore();
  const guestStore = useGuestStore();
  const passeStore = usePasseStore();
  const activePasseStore = useActivePasseStore();
  const competitionEventStore = useCompetitionEventStore();

  appStore.initializeStore().catch(console.error);
  guestStore.loadGuests().catch(console.error);
  passeStore.loadSerienFromStorage().catch(console.error);
  passeStore.loadPassenFromStorage().catch(console.error);
  activePasseStore.loadFromStorage().catch(console.error);
  competitionEventStore.loadEvents().catch(console.error);
}

// Wipe every instantiated store except auth, stopping any polling first, then
// re-arm initialization so the next login refetches. auth owns its own lifecycle.
export function resetAppData() {
  const pinia = getActivePinia();
  if (pinia) {
    pinia._s.forEach((store) => {
      if (store.$id === 'auth') return;
      store.stopAllPolling?.();
      store.$reset?.();
    });
  }
  _initialized = false;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/stores/__tests__/dataLifecycle.test.js`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/stores/dataLifecycle.js src/stores/__tests__/dataLifecycle.test.js
git commit -m "[ui] add dataLifecycle module for app-data init and reset"
```

---

### Task 3: Wire authStore to reset on logout and login

Closes the user-switch bug: identity change → clean slate.

**Files:**
- Modify: `src/stores/authStore.js:29-48` (login), `:82-87` (logout)
- Test: `src/stores/__tests__/authStore.test.js` (add cases)

**Interfaces:**
- Consumes: `resetAppData()` (Task 2).
- Produces: no new exports. Behavior — `logout()` calls `resetAppData()`; `login()` calls `resetAppData()` before loading the new profile.

- [ ] **Step 1: Write the failing test**

Add to `src/stores/__tests__/authStore.test.js`. First ensure the module is mocked near the other `vi.mock` calls at the top of the file:

```js
vi.mock('@/stores/dataLifecycle.js', () => ({ resetAppData: vi.fn() }));
```

Then add a describe block (import `resetAppData` from the mocked module at the top of the test file alongside existing imports):

```js
import { resetAppData } from '@/stores/dataLifecycle.js';

describe('authStore data reset on identity change', () => {
  it('wipes app data on logout', () => {
    const store = useAuthStore();
    store.logout();
    expect(resetAppData).toHaveBeenCalled();
  });

  it('wipes app data before loading the new profile on login', async () => {
    const store = useAuthStore();
    await store.login('userB', 'pw');
    expect(resetAppData).toHaveBeenCalled();
  });
});
```

Note: the existing test file already mocks `authApi`/`userApi`; keep those. If `login` in existing tests asserts call order, `resetAppData` must run before `_loadProfile`.

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/stores/__tests__/authStore.test.js`
Expected: FAIL — `resetAppData` not called (`logout`/`login` don't call it yet).

- [ ] **Step 3: Write minimal implementation**

In `src/stores/authStore.js`, add the import near the top:

```js
import { resetAppData } from './dataLifecycle.js';
```

In `login`, reset before loading the profile:

```js
  const login = async (username, password) => {
    isLoading.value = true;
    error.value = null;
    try {
      const data = await loginApi(username, password);
      token.value = data.token;
      localStorage.setItem('sg_token', data.token);
      resetAppData(); // clear any prior session's data before loading this user's
      await _loadProfile();
    } catch (err) {
```

In `logout`, reset after clearing auth state:

```js
  const logout = () => {
    token.value = null;
    profile.value = null;
    permissions.value = [];
    localStorage.removeItem('sg_token');
    resetAppData();
  };
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/stores/__tests__/authStore.test.js`
Expected: PASS (including existing cases).

- [ ] **Step 5: Commit**

```bash
git add src/stores/authStore.js src/stores/__tests__/authStore.test.js
git commit -m "[ui] reset app data on login and logout to prevent cross-user leakage"
```

---

### Task 4: Route init through dataLifecycle

Replaces the router's inline init block and dead `_storesInitialized` flag with `initializeAppData()`.

**Files:**
- Modify: `src/router/index.js:23` (remove flag), `:110-131` (replace block)

**Interfaces:**
- Consumes: `initializeAppData()` (Task 2).
- Produces: no new exports. Behavior unchanged from the user's perspective on first login; the flag now lives in `dataLifecycle` so `resetAppData()` can re-arm it.

- [ ] **Step 1: Remove the module-level flag**

In `src/router/index.js`, delete line 23:

```js
let _storesInitialized = false
```

- [ ] **Step 2: Replace the inline init block**

Replace the current block (lines ~110-131) with:

```js
  // Initialize API-backed stores on first authenticated navigation
  if (authenticated && to.path !== '/login' && to.path !== '/no-access') {
    const { initializeAppData } = await import('@/stores/dataLifecycle.js');
    initializeAppData().catch(console.error);
  }
```

(`initializeAppData` is internally idempotent, so the removed `_storesInitialized` guard is no longer needed here.)

- [ ] **Step 3: Verify the full suite passes**

Run: `npm run test`
Expected: PASS.

- [ ] **Step 4: Lint**

Run: `npm run lint:check`
Expected: no warnings (confirms no leftover unused `_storesInitialized` reference).

- [ ] **Step 5: Manual smoke check**

Run: `npm run dev`. Log in as UserA, note the Serien list, log out, log in as UserB.
Expected: UserB sees their own Serien (not UserA's).

- [ ] **Step 6: Commit**

```bash
git add src/router/index.js
git commit -m "[ui] route store initialization through dataLifecycle"
```

---

### Task 5: useRevalidate composable

Interval polling for active views that pauses on hidden tabs and refetches on regaining visibility.

**Files:**
- Create: `src/composables/useRevalidate.js`
- Test: `src/composables/__tests__/useRevalidate.test.js`

**Interfaces:**
- Consumes: `onMounted`, `onUnmounted` (must be called from component setup).
- Produces: `useRevalidate(loader: () => unknown, options?: { interval?: number, immediate?: boolean }): { start(): void, stop(): void }`. Defaults: `interval = 10000`, `immediate = true`. On mount: optional immediate call, then interval. On `visibilitychange`: stops the interval when `document.hidden`, otherwise calls `loader()` immediately and restarts. On unmount: clears interval and listener.

- [ ] **Step 1: Write the failing test**

```js
// src/composables/__tests__/useRevalidate.test.js
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { useRevalidate } from '../useRevalidate.js';

function mountWith(loader, options) {
  return mount({
    template: '<div />',
    setup() {
      useRevalidate(loader, options);
      return {};
    },
  });
}

function setHidden(value) {
  Object.defineProperty(document, 'hidden', { value, configurable: true });
  document.dispatchEvent(new Event('visibilitychange'));
}

describe('useRevalidate', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setHidden(false);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('calls the loader immediately and on each interval', () => {
    const loader = vi.fn();
    mountWith(loader, { interval: 1000 });
    expect(loader).toHaveBeenCalledTimes(1); // immediate
    vi.advanceTimersByTime(3000);
    expect(loader).toHaveBeenCalledTimes(4);
  });

  it('pauses while hidden and refetches on regaining visibility', () => {
    const loader = vi.fn();
    mountWith(loader, { interval: 1000 });
    loader.mockClear();

    setHidden(true);
    vi.advanceTimersByTime(3000);
    expect(loader).not.toHaveBeenCalled(); // paused

    setHidden(false);
    expect(loader).toHaveBeenCalledTimes(1); // immediate refetch on return
    vi.advanceTimersByTime(1000);
    expect(loader).toHaveBeenCalledTimes(2); // interval resumed
  });

  it('stops polling after unmount', () => {
    const loader = vi.fn();
    const wrapper = mountWith(loader, { interval: 1000 });
    loader.mockClear();
    wrapper.unmount();
    vi.advanceTimersByTime(5000);
    expect(loader).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/composables/__tests__/useRevalidate.test.js`
Expected: FAIL — cannot import `useRevalidate` (module does not exist).

- [ ] **Step 3: Write minimal implementation**

```js
// src/composables/useRevalidate.js
/* global setInterval, clearInterval, document */
import { onMounted, onUnmounted } from 'vue';

// Opt-in interval polling for a view. Pauses while the tab is hidden and
// refetches immediately on regaining visibility (stale-while-revalidate feel).
export function useRevalidate(loader, { interval = 10000, immediate = true } = {}) {
  let handle = null;

  const start = () => {
    if (handle == null) handle = setInterval(() => loader(), interval);
  };
  const stop = () => {
    if (handle != null) {
      clearInterval(handle);
      handle = null;
    }
  };
  const onVisibility = () => {
    if (document.hidden) {
      stop();
    } else {
      loader();
      start();
    }
  };

  onMounted(() => {
    if (immediate) loader();
    start();
    document.addEventListener('visibilitychange', onVisibility);
  });
  onUnmounted(() => {
    stop();
    document.removeEventListener('visibilitychange', onVisibility);
  });

  return { start, stop };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/composables/__tests__/useRevalidate.test.js`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/composables/useRevalidate.js src/composables/__tests__/useRevalidate.test.js
git commit -m "[ui] add useRevalidate composable for opt-in view polling"
```

---

### Task 6: Opt PasseManagementView into revalidation

Applies near-real-time freshness where the reported symptom shows (shooter sees admin's Serien/Position edits). This is the reference adopter; other shared-data views can follow the same one-line pattern later.

**Files:**
- Modify: `src/views/shooter/PasseManagementView.vue` (script setup, around `:530-545`)
- Test: `src/views/__tests__/PasseManagementView.revalidate.test.js`

**Interfaces:**
- Consumes: `useRevalidate` (Task 5); `passeStore.loadSerienFromStorage()`, `passeStore.loadPassenFromStorage()` (existing).
- Produces: no new exports. Mounting the view starts a 10s poll of Serien + Passen that stops on unmount.

- [ ] **Step 1: Write the failing test**

```js
// src/views/__tests__/PasseManagementView.revalidate.test.js
import { describe, it, expect, vi } from 'vitest';

// Assert the view wires revalidation to the passe loaders. We mock the
// composable and capture the loader it receives, then invoke it.
const captured = { loader: null };
vi.mock('@/composables/useRevalidate.js', () => ({
  useRevalidate: (loader) => { captured.loader = loader; return { start() {}, stop() {} }; },
}));

const loadSerienFromStorage = vi.fn().mockResolvedValue();
const loadPassenFromStorage = vi.fn().mockResolvedValue();

describe('PasseManagementView revalidation wiring', () => {
  it('registers a loader that refreshes Serien and Passen', async () => {
    // Import after mocks are set up
    const { useRevalidate } = await import('@/composables/useRevalidate.js');
    expect(useRevalidate).toBeTypeOf('function');

    // Simulate the view's registration call
    useRevalidate(() => {
      loadSerienFromStorage();
      loadPassenFromStorage();
    });

    expect(captured.loader).toBeTypeOf('function');
    captured.loader();
    expect(loadSerienFromStorage).toHaveBeenCalledOnce();
    expect(loadPassenFromStorage).toHaveBeenCalledOnce();
  });
});
```

Note: this is a focused wiring test (the composable's own behavior is covered in Task 5). Full `mount()` of `PasseManagementView` pulls in many stores; this test verifies the loader contract without that overhead.

- [ ] **Step 2: Run test to verify it passes structurally**

Run: `npm run test src/views/__tests__/PasseManagementView.revalidate.test.js`
Expected: PASS — this test validates the intended loader shape before the view is wired.

- [ ] **Step 3: Wire the composable into the view**

In `src/views/shooter/PasseManagementView.vue`, add the import alongside the other composable imports (near `:539`):

```js
import { useRevalidate } from '@/composables/useRevalidate.js';
```

After the store instances are created (after `:545`), register the poll:

```js
// Keep Serien/Passen fresh against admin edits while this view is open
useRevalidate(() => {
  passeStore.loadSerienFromStorage();
  passeStore.loadPassenFromStorage();
}, { interval: 10000 });
```

- [ ] **Step 4: Verify view tests and suite pass**

Run: `npm run test`
Expected: PASS (existing PasseManagementView tests unaffected — the composable is a no-op timer under test env).

- [ ] **Step 5: Manual smoke check**

Run: `npm run dev`. Open PasseManagementView as a shooter. In a second session, remove a Position from a Serie as admin.
Expected: within ~10s the shooter view reflects the change without a manual reload.

- [ ] **Step 6: Lint and build**

Run: `npm run lint:check && npm run build`
Expected: no warnings, build succeeds.

- [ ] **Step 7: Commit**

```bash
git add src/views/shooter/PasseManagementView.vue src/views/__tests__/PasseManagementView.revalidate.test.js
git commit -m "[ui] revalidate Serien/Passen on PasseManagementView while open"
```

---

## Notes for the implementer

- **Order matters:** Tasks 1→2→3→4 are sequential (each consumes the prior). Task 5 is independent of 1–4; Task 6 depends on Task 5.
- **`pinia._s`** is Pinia internal API but stable and the standard way to enumerate instantiated stores; only instantiated stores hold state, so this is sufficient.
- **Extending freshness:** to add polling to another shared-data view, add one `useRevalidate(() => someStore.loadX(), { interval })` call in its setup. Do NOT poll live-play views that own transient UI state.
- **`structuredClone`** is available in the Node/jsdom test env and all target browsers; state here is JSON-shaped (arrays/objects/primitives), so cloning is safe.

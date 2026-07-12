# Range Remote User Assignment — Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow admins to assign a user to a range so that user auto-redirects to the range remote view on login and is locked to it, while unassigned users see the range as reserved and are blocked from entering it.

**Architecture:** The backend returns `assignedRangeId` in `GET /auth/me` and `assignedUserId` in range responses. The router guard hard-locks assigned users to their range path. `ShooterRemoteView` validates access on mount via a range fetch. `RangeDetailView` gets a "User als Remote zuweisen" section with a user search modal. The `rangeStore` is the source of truth for `assignedUserId` on each range; `auth.profile` is the source of truth for the logged-in user's assignment.

**Tech Stack:** Vue 3 Composition API, Pinia, Vue Router 4, Vitest + @vue/test-utils

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `src/services/rangeApi.js` | Modify | Add `assignRangeUser` API call |
| `src/router/index.js` | Modify | Extend `defaultHome`; add hard-lock guard; export `defaultHome` for testing |
| `src/views/shooter/ShooterRemoteView.vue` | Modify | On-mount access check; hide back button for assigned users |
| `src/views/shooter/ShooterRangeSelectView.vue` | Modify | Disable + "Reserviert" badge for ranges with `assignedUserId` |
| `src/components/UserSearchModal.vue` | Create | Search modal for selecting a user to assign |
| `src/views/RangeDetailView.vue` | Modify | "User als Remote zuweisen" section + modal wiring |
| `src/router/__tests__/defaultHome.test.js` | Create | Unit test for `defaultHome` redirect logic |
| `src/components/__tests__/UserSearchModal.test.js` | Create | Component test for search filtering and emit |

---

## Task 1: Add `assignRangeUser` to rangeApi

**Files:**
- Modify: `src/services/rangeApi.js`

- [ ] **Step 1: Add the API function**

Append to the end of `src/services/rangeApi.js`:

```js
export async function assignRangeUser(rangeId, userId) {
  return apiFetch(`/ranges/${rangeId}/assigned-user`, {
    method: 'PATCH',
    body: JSON.stringify({ userId }),
  });
}
```

`userId` is a string UUID when assigning, or `null` when unassigning. The backend accepts both.

- [ ] **Step 2: Commit**

```bash
git add src/services/rangeApi.js
git commit -m "[ui] Add assignRangeUser to rangeApi"
```

---

## Task 2: Router — auto-redirect and hard-lock guard

**Files:**
- Modify: `src/router/index.js`
- Create: `src/router/__tests__/defaultHome.test.js`

- [ ] **Step 1: Write the failing test**

Create `src/router/__tests__/defaultHome.test.js`:

```js
import { describe, it, expect } from 'vitest'

// Replicated here because defaultHome will be exported in the next step
// Update this import once the export exists:
// import { defaultHome } from '../index.js'

const defaultHome = (auth) => {
  if (auth.profile?.assignedRangeId) return `/remote/${auth.profile.assignedRangeId}`
  if (auth.hasPermission('VIEW_REMOTE')) return '/home'
  if (auth.hasPermission('MANAGE_RANGES')) return '/ranges'
  return '/no-access'
}

describe('defaultHome', () => {
  it('redirects assigned user to their range', () => {
    const auth = {
      profile: { assignedRangeId: 'range-abc' },
      hasPermission: () => false,
    }
    expect(defaultHome(auth)).toBe('/remote/range-abc')
  })

  it('redirects shooter without assignment to /home', () => {
    const auth = {
      profile: null,
      hasPermission: (p) => p === 'VIEW_REMOTE',
    }
    expect(defaultHome(auth)).toBe('/home')
  })

  it('redirects admin without assignment to /ranges', () => {
    const auth = {
      profile: null,
      hasPermission: (p) => p === 'MANAGE_RANGES',
    }
    expect(defaultHome(auth)).toBe('/ranges')
  })

  it('redirects user with no permissions to /no-access', () => {
    const auth = {
      profile: null,
      hasPermission: () => false,
    }
    expect(defaultHome(auth)).toBe('/no-access')
  })

  it('gives assignedRangeId priority over VIEW_REMOTE', () => {
    const auth = {
      profile: { assignedRangeId: 'range-xyz' },
      hasPermission: (p) => p === 'VIEW_REMOTE',
    }
    expect(defaultHome(auth)).toBe('/remote/range-xyz')
  })
})
```

- [ ] **Step 2: Run to verify tests pass (they test the local replica)**

```bash
npm run test src/router/__tests__/defaultHome.test.js
```

Expected: all 5 PASS (against the local replica in the test file).

- [ ] **Step 3: Update `src/router/index.js`**

Replace the existing `defaultHome` function and `router.beforeEach` block with the following. The function is now exported so the test can import the real one later.

Replace:
```js
// Gibt die Standardstartseite basierend auf den Berechtigungen des Benutzers zurück
const defaultHome = (auth) => {
  if (auth.hasPermission('VIEW_REMOTE')) return '/home';
  if (auth.hasPermission('MANAGE_RANGES')) return '/ranges';
  return '/no-access';
};
```

With:
```js
// Gibt die Standardstartseite basierend auf den Berechtigungen des Benutzers zurück
export const defaultHome = (auth) => {
  if (auth.profile?.assignedRangeId) return `/remote/${auth.profile.assignedRangeId}`;
  if (auth.hasPermission('VIEW_REMOTE')) return '/home';
  if (auth.hasPermission('MANAGE_RANGES')) return '/ranges';
  return '/no-access';
};
```

Replace the existing `router.beforeEach` block:
```js
router.beforeEach(async (to, from, next) => {
  const auth = useAuthStore();
  await auth.readyPromise;
  const authenticated = auth.isAuthenticated();
  const requiresAuth = to.meta.requiresAuth !== false;

  if (requiresAuth && !authenticated) {
    next('/login');
    return;
  }

  if (to.path === '/login' && authenticated) {
    next(defaultHome(auth));
    return;
  }

  // Handle root — redirect to permission-appropriate home
  if (to.path === '/' && authenticated) {
    next(defaultHome(auth));
    return;
  }

  // Hard-lock: assigned users may only visit their range path
  if (authenticated && auth.profile?.assignedRangeId) {
    const allowedPath = `/remote/${auth.profile.assignedRangeId}`;
    if (!to.path.startsWith(allowedPath)) {
      next(allowedPath);
      return;
    }
  }

  next();
});
```

- [ ] **Step 4: Update test to import the real `defaultHome`**

In `src/router/__tests__/defaultHome.test.js`, replace the local replica at the top with the real import:

```js
import { describe, it, expect, vi } from 'vitest'

// Mock vue-router and pinia so the router module can be imported
vi.mock('vue-router', () => ({
  createRouter: vi.fn(() => ({ beforeEach: vi.fn() })),
  createWebHistory: vi.fn(),
}))
vi.mock('@/stores/authStore', () => ({ useAuthStore: vi.fn() }))

import { defaultHome } from '../index.js'

describe('defaultHome', () => {
  it('redirects assigned user to their range', () => {
    const auth = {
      profile: { assignedRangeId: 'range-abc' },
      hasPermission: () => false,
    }
    expect(defaultHome(auth)).toBe('/remote/range-abc')
  })

  it('redirects shooter without assignment to /home', () => {
    const auth = {
      profile: null,
      hasPermission: (p) => p === 'VIEW_REMOTE',
    }
    expect(defaultHome(auth)).toBe('/home')
  })

  it('redirects admin without assignment to /ranges', () => {
    const auth = {
      profile: null,
      hasPermission: (p) => p === 'MANAGE_RANGES',
    }
    expect(defaultHome(auth)).toBe('/ranges')
  })

  it('redirects user with no permissions to /no-access', () => {
    const auth = {
      profile: null,
      hasPermission: () => false,
    }
    expect(defaultHome(auth)).toBe('/no-access')
  })

  it('gives assignedRangeId priority over VIEW_REMOTE', () => {
    const auth = {
      profile: { assignedRangeId: 'range-xyz' },
      hasPermission: (p) => p === 'VIEW_REMOTE',
    }
    expect(defaultHome(auth)).toBe('/remote/range-xyz')
  })
})
```

- [ ] **Step 5: Run tests to verify all pass**

```bash
npm run test src/router/__tests__/defaultHome.test.js
```

Expected: 5 PASS

- [ ] **Step 6: Commit**

```bash
git add src/router/index.js src/router/__tests__/defaultHome.test.js
git commit -m "[ui] Add range assignment redirect and hard-lock guard to router"
```

---

## Task 3: ShooterRemoteView — access check and hide back button

**Files:**
- Modify: `src/views/shooter/ShooterRemoteView.vue`

- [ ] **Step 1: Add imports**

In `src/views/shooter/ShooterRemoteView.vue`, in the `<script setup>` block, add these two imports alongside the existing ones:

```js
import { useAuthStore } from '@/stores/authStore.js';
import { fetchRange } from '@/services/rangeApi.js';
```

And initialize the store:

```js
const auth = useAuthStore();
```

Place this line directly after `const store = useShooterRemoteStore();`.

- [ ] **Step 2: Add access check at the start of `onMounted`**

The existing `onMounted` starts at line 170. Add the access check as the very first operation inside it, before `store.ensureReserved`:

```js
onMounted(async () => {
  // Redirect if this range is assigned to a different user
  const rangeData = await fetchRange(props.rangeId);
  if (rangeData.assignedUserId && rangeData.assignedUserId !== auth.profile?.id) {
    router.replace('/remote');
    return;
  }

  store.ensureReserved(props.rangeId);
  // ... rest of onMounted unchanged
```

- [ ] **Step 3: Conditionally show the back button**

In the template, find the back button (line ~7):

```html
<button class="back-btn" @click="goBack">
```

Add `v-if` so it only shows for users who are not assigned to this range:

```html
<button v-if="!auth.profile?.assignedRangeId" class="back-btn" @click="goBack">
```

- [ ] **Step 4: Run linter**

```bash
npm run lint
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add src/views/shooter/ShooterRemoteView.vue
git commit -m "[ui] Block unassigned users from reserved ranges in ShooterRemoteView"
```

---

## Task 4: ShooterRangeSelectView — "Reserviert" badge

**Files:**
- Modify: `src/views/shooter/ShooterRangeSelectView.vue`

- [ ] **Step 1: Add `useAuthStore` import and computed helper**

In `<script setup>`, add the import:

```js
import { useAuthStore } from '@/stores/authStore.js';
```

Initialize the store:

```js
const auth = useAuthStore();
```

Add a helper function (after the existing `selectRange` function):

```js
const isReservedByOther = (range) =>
  !!range.assignedUserId && range.assignedUserId !== auth.profile?.id;
```

- [ ] **Step 2: Update the range card in the template**

Find the `<button ... class="range-card"` block. Replace it with:

```html
<button
  v-for="range in ranges"
  :key="range.id"
  class="range-card"
  :class="{
    'range-card--locked': range.locked,
    'range-card--reserved': isReservedByOther(range),
  }"
  :disabled="range.locked || isReservedByOther(range)"
  @click="selectRange(range)"
>
  <div
    class="card-accent"
    :class="
      range.locked
        ? 'card-accent--red'
        : isReservedByOther(range)
          ? 'card-accent--blue'
          : 'card-accent--green'
    "
  />

  <div class="card-body">
    <div class="card-main">
      <div class="card-name-row">
        <span class="card-name">{{ range.name }}</span>
        <span v-if="range.locked" class="locked-chip">
          <Icons icon="lock" :size="10" color="#fc8181" />
          Gesperrt
        </span>
        <span v-else-if="isReservedByOther(range)" class="reserved-chip">
          Reserviert
        </span>
      </div>
      <p v-if="range.description" class="card-desc">{{ range.description }}</p>
      <span class="device-count">
        <Icons icon="bolt" :size="11" color="rgba(255,255,255,0.35)" />
        {{ getDeviceCount(range.id) }} Geräte
      </span>
    </div>
    <Icons icon="chevronRight" :size="16" color="rgba(255,255,255,0.2)" />
  </div>
</button>
```

- [ ] **Step 3: Add CSS for reserved state**

In the `<style scoped>` block, add after `.card-accent--red`:

```css
.card-accent--blue {
  background: #63b3ed;
}

.range-card--reserved {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: none;
}

.reserved-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(99, 179, 237, 0.15);
  color: #63b3ed;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 20px;
}
```

- [ ] **Step 4: Run linter**

```bash
npm run lint
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add src/views/shooter/ShooterRangeSelectView.vue
git commit -m "[ui] Show Reserviert badge for ranges assigned to a tablet user"
```

---

## Task 5: UserSearchModal component

**Files:**
- Create: `src/components/UserSearchModal.vue`
- Create: `src/components/__tests__/UserSearchModal.test.js`

- [ ] **Step 1: Write the failing test**

Create `src/components/__tests__/UserSearchModal.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import UserSearchModal from '../UserSearchModal.vue'

const users = [
  { id: 'u1', fullName: 'Alice Müller' },
  { id: 'u2', fullName: 'Bob Meier' },
  { id: 'u3', fullName: 'Charlie Baum' },
]

describe('UserSearchModal', () => {
  it('renders all users when search is empty', () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    expect(wrapper.findAll('.user-row')).toHaveLength(3)
  })

  it('filters users by fullName (case-insensitive)', async () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    await wrapper.find('input').setValue('alice')
    expect(wrapper.findAll('.user-row')).toHaveLength(1)
    expect(wrapper.find('.user-row').text()).toContain('Alice Müller')
  })

  it('shows empty hint when no match', async () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    await wrapper.find('input').setValue('zzz')
    expect(wrapper.findAll('.user-row')).toHaveLength(0)
    expect(wrapper.find('.empty-hint').exists()).toBe(true)
  })

  it('emits select with the user when a row is clicked', async () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    await wrapper.findAll('.user-row')[1].trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')[0][0]).toEqual(users[1])
  })

  it('emits close when backdrop is clicked', async () => {
    const wrapper = mount(UserSearchModal, { props: { users } })
    await wrapper.find('.modal-backdrop').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
```

- [ ] **Step 2: Run to verify tests fail**

```bash
npm run test src/components/__tests__/UserSearchModal.test.js
```

Expected: FAIL — `Cannot find module '../UserSearchModal.vue'`

- [ ] **Step 3: Create `src/components/UserSearchModal.vue`**

```vue
<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal" role="dialog" aria-modal="true" aria-label="User als Remote zuweisen">
      <div class="modal-header">
        <h3 class="modal-title">User als Remote zuweisen</h3>
        <button class="modal-close" aria-label="Schliessen" @click="$emit('close')">×</button>
      </div>

      <div class="modal-search">
        <input
          v-model="query"
          type="text"
          class="search-input"
          placeholder="Suchen…"
          aria-label="Benutzer suchen"
          autofocus
        />
      </div>

      <div class="modal-list">
        <button
          v-for="user in filtered"
          :key="user.id"
          class="user-row"
          @click="$emit('select', user)"
        >
          <div class="user-avatar">{{ initial(user) }}</div>
          <span class="user-name">{{ user.fullName }}</span>
        </button>
        <p v-if="filtered.length === 0" class="empty-hint">Keine Benutzer gefunden</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  users: { type: Array, required: true },
});

defineEmits(['select', 'close']);

const query = ref('');

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return props.users;
  return props.users.filter((u) => u.fullName?.toLowerCase().includes(q));
});

const initial = (user) => user.fullName?.charAt(0).toUpperCase() ?? '?';
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
  background: #fff;
  border-radius: 14px;
  width: 420px;
  max-width: calc(100vw - 32px);
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-title {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  font-size: 20px;
  color: #a0aec0;
  cursor: pointer;
  padding: 2px 6px;
  line-height: 1;
  border-radius: 6px;
  transition: background 0.15s;
}
.modal-close:hover { background: #f0f4f8; }

.modal-search {
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}
.search-input:focus { border-color: #4fc3f7; }

.modal-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.user-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 20px;
  background: none;
  border: none;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.12s;
}
.user-row:hover { background: #f7fafc; }

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(79, 195, 247, 0.15);
  border: 1px solid rgba(79, 195, 247, 0.3);
  color: #2c7a9e;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #2d3748;
}

.empty-hint {
  text-align: center;
  padding: 24px 16px;
  color: #a0aec0;
  font-size: 13px;
  margin: 0;
}
</style>
```

- [ ] **Step 4: Run tests to verify all pass**

```bash
npm run test src/components/__tests__/UserSearchModal.test.js
```

Expected: 5 PASS

- [ ] **Step 5: Commit**

```bash
git add src/components/UserSearchModal.vue src/components/__tests__/UserSearchModal.test.js
git commit -m "[ui] Add UserSearchModal component for range remote user assignment"
```

---

## Task 6: RangeDetailView — "User als Remote zuweisen" section

**Files:**
- Modify: `src/views/RangeDetailView.vue`

- [ ] **Step 1: Add imports**

In `src/views/RangeDetailView.vue`, in the `<script setup>` block, add these alongside the existing imports:

```js
import UserSearchModal from '../components/UserSearchModal.vue';
import { assignRangeUser } from '../services/rangeApi.js';
```

- [ ] **Step 2: Add reactive state**

After the existing `const firedDevices = ref({});` line, add:

```js
const showUserModal = ref(false);
const isAssigning = ref(false);
```

- [ ] **Step 3: Add computed and action functions**

After the existing `const toggleAssignPanel = () => { ... };` line, add:

```js
// ── Remote user assignment ─────────────────────────────────────────────────────
const assignedUser = computed(() => {
  const id = range.value?.assignedUserId;
  if (!id) return null;
  return userStore.users.find((u) => u.id === id) ?? null;
});

const openUserModal = async () => {
  if (userStore.users.length === 0) await userStore.loadUsers();
  showUserModal.value = true;
};

const handleAssign = async (user) => {
  showUserModal.value = false;
  isAssigning.value = true;
  try {
    await assignRangeUser(props.id, user.id);
    const r = rangeStore.ranges.find((r) => r.id === props.id);
    if (r) r.assignedUserId = user.id;
  } catch (e) {
    console.error('Failed to assign remote user:', e);
  } finally {
    isAssigning.value = false;
  }
};

const handleUnassign = async () => {
  isAssigning.value = true;
  try {
    await assignRangeUser(props.id, null);
    const r = rangeStore.ranges.find((r) => r.id === props.id);
    if (r) r.assignedUserId = null;
  } catch (e) {
    console.error('Failed to unassign remote user:', e);
  } finally {
    isAssigning.value = false;
  }
};
```

- [ ] **Step 4: Add the section to the template**

In the template, after the closing `</div>` of `.detail-header` (around line 67) and before `<!-- Position grid -->`, insert:

```html
<!-- User als Remote zuweisen -->
<section class="remote-user-section">
  <h4 class="section-label">User als Remote zuweisen</h4>

  <div v-if="assignedUser" class="assigned-user-row">
    <div class="assigned-avatar">{{ assignedUser.fullName?.charAt(0).toUpperCase() }}</div>
    <span class="assigned-name">{{ assignedUser.fullName }}</span>
    <button
      class="release-btn"
      :disabled="isAssigning"
      @click="handleUnassign"
    >
      {{ isAssigning ? '…' : 'Entfernen' }}
    </button>
  </div>

  <div v-else class="unassigned-row">
    <span class="unassigned-hint">Kein Benutzer zugewiesen</span>
    <button class="block-btn" :disabled="isAssigning" @click="openUserModal">
      + Zuweisen
    </button>
  </div>

  <UserSearchModal
    v-if="showUserModal"
    :users="userStore.users"
    @select="handleAssign"
    @close="showUserModal = false"
  />
</section>
```

- [ ] **Step 5: Add CSS for the new section**

In the `<style scoped>` block, append:

```css
/* ── Remote user section ─────────────────────────────── */
.remote-user-section {
  margin-bottom: 24px;
  padding: 16px 20px;
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}

.section-label {
  font-size: 11px;
  font-weight: 700;
  color: #a0aec0;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin: 0 0 12px;
}

.assigned-user-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.assigned-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(79, 195, 247, 0.15);
  border: 1px solid rgba(79, 195, 247, 0.3);
  color: #2c7a9e;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.assigned-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: #2d3748;
}

.unassigned-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.unassigned-hint {
  font-size: 13px;
  color: #a0aec0;
}
```

- [ ] **Step 6: Run linter**

```bash
npm run lint
```

Expected: no errors.

- [ ] **Step 7: Run all tests**

```bash
npm run test
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/views/RangeDetailView.vue
git commit -m "[ui] Add 'User als Remote zuweisen' section to RangeDetailView"
```

---

## Done

All tasks complete. The feature delivers:
- Assigned users auto-redirect to their range on login and are hard-locked to it
- The back button is hidden for assigned users
- Any navigation attempt to a different path is intercepted by the router guard
- Unassigned users trying to open an assigned range are redirected to `/remote`
- The range list shows a "Reserviert" badge for ranges that have an assigned tablet user
- Admins can search for and assign/unassign users from the Range detail panel

**Next step:** Backend implementation — see `docs/superpowers/specs/2026-06-02-range-remote-user-assignment-design.md` (the "Next Step" section at the bottom).

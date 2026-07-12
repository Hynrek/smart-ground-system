# User Role Assignment UI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add toggle chips to the user detail panel so admins can assign and revoke roles (ADMIN, SHOOTER, OWNER) on existing users without leaving the view.

**Architecture:** The backend already supports multi-role users via `POST /users/{id}/roles` (assign) and `DELETE /users/{id}/roles/{roleName}` (revoke). The frontend will add a `revokeRole` API call, a `toggleRole` store action, and a "Rollen" section with interactive chips in the detail panel. No modal or extra page needed.

**Tech Stack:** Vue 3 (Composition API, `<script setup>`), Pinia, Vitest + @vue/test-utils

---

## Files

| File | Change |
|---|---|
| `src/services/userApi.js` | Add `revokeRole(userId, roleName)` |
| `src/stores/userStore.js` | Add `toggleRole(userId, roleName)` action |
| `src/views/UsersView.vue` | Add "Rollen" section with toggle chips to detail panel |
| `src/stores/__tests__/userStore.test.js` | Tests for `toggleRole` |

---

### Task 1: Add `revokeRole` to userApi

**Files:**
- Modify: `src/services/userApi.js`

- [ ] **Step 1: Add `revokeRole` export**

Open `src/services/userApi.js` and append at the bottom:

```js
export async function revokeRole(userId, roleName) {
  return apiFetch(`/users/${userId}/roles/${roleName}`, {
    method: 'DELETE',
  })
}
```

- [ ] **Step 2: Verify no lint errors**

```bash
npm run lint
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add src/services/userApi.js
git commit -m "[ui] Add revokeRole API call"
```

---

### Task 2: Add `toggleRole` action to userStore

**Files:**
- Modify: `src/stores/userStore.js`
- Test: `src/stores/__tests__/userStore.test.js`

- [ ] **Step 1: Write failing test**

Create (or open if it exists) `src/stores/__tests__/userStore.test.js` and add:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../userStore.js'
import * as userApi from '../../services/userApi.js'

vi.mock('../../services/userApi.js')

describe('useUserStore — toggleRole', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('assigns role when user does not have it', async () => {
    const store = useUserStore()
    store.userRolesMap = { 'u1': [{ roleName: 'SHOOTER' }] }
    userApi.assignRole.mockResolvedValue({ roleName: 'ADMIN' })
    userApi.fetchUserRoles.mockResolvedValue([{ roleName: 'SHOOTER' }, { roleName: 'ADMIN' }])

    await store.toggleRole('u1', 'ADMIN')

    expect(userApi.assignRole).toHaveBeenCalledWith('u1', 'ADMIN')
    expect(userApi.revokeRole).not.toHaveBeenCalled()
    expect(store.userRolesMap['u1']).toEqual([{ roleName: 'SHOOTER' }, { roleName: 'ADMIN' }])
  })

  it('revokes role when user already has it', async () => {
    const store = useUserStore()
    store.userRolesMap = { 'u1': [{ roleName: 'SHOOTER' }, { roleName: 'ADMIN' }] }
    userApi.revokeRole.mockResolvedValue(undefined)
    userApi.fetchUserRoles.mockResolvedValue([{ roleName: 'SHOOTER' }])

    await store.toggleRole('u1', 'ADMIN')

    expect(userApi.revokeRole).toHaveBeenCalledWith('u1', 'ADMIN')
    expect(userApi.assignRole).not.toHaveBeenCalled()
    expect(store.userRolesMap['u1']).toEqual([{ roleName: 'SHOOTER' }])
  })

  it('sets error and rethrows on API failure', async () => {
    const store = useUserStore()
    store.userRolesMap = { 'u1': [] }
    userApi.assignRole.mockRejectedValue(new Error('Network error'))

    await expect(store.toggleRole('u1', 'ADMIN')).rejects.toThrow('Network error')
    expect(store.error).toBe('Network error')
  })
})
```

- [ ] **Step 2: Run test — verify it fails**

```bash
npm run test src/stores/__tests__/userStore.test.js
```

Expected: FAIL — `store.toggleRole is not a function`

- [ ] **Step 3: Implement `toggleRole` in userStore**

Open `src/stores/userStore.js`. Add `revokeRole` to the import at the top:

```js
import * as userApi from '../services/userApi.js'
```

(already imported — no change needed there)

Add this action inside the `defineStore` body, after `deleteUser`:

```js
async function toggleRole(userId, roleName) {
  isLoading.value = true
  error.value = null
  try {
    const current = userRolesMap.value[userId] ?? []
    const hasRole = current.some((r) => r.roleName === roleName)
    if (hasRole) {
      await userApi.revokeRole(userId, roleName)
    } else {
      await userApi.assignRole(userId, roleName)
    }
    userRolesMap.value[userId] = await userApi.fetchUserRoles(userId)
  } catch (err) {
    error.value = err.message || 'Failed to update role'
    throw err
  } finally {
    isLoading.value = false
  }
}
```

Also add `toggleRole` to the `return` object at the bottom of the store.

- [ ] **Step 4: Run tests — verify they pass**

```bash
npm run test src/stores/__tests__/userStore.test.js
```

Expected: all 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/stores/userStore.js src/stores/__tests__/userStore.test.js
git commit -m "[ui] Add toggleRole action to userStore"
```

---

### Task 3: Add role toggle chips to the detail panel

**Files:**
- Modify: `src/views/UsersView.vue`

The detail panel already has sections (Kontakt, Adresse, Mitgliedschaft, System). Add a "Rollen" section after the detail header — above the other sections so it's immediately visible.

The three roles to show: `ADMIN`, `SHOOTER`, `OWNER`. A chip is active (filled) when the user has that role. Clicking it calls `toggleRole`. Chips are disabled while `userStore.isLoading` is true.

- [ ] **Step 1: Add the `ROLES` constant and `hasRole` helper to `<script setup>`**

In `src/views/UsersView.vue`, inside `<script setup>`, add after the existing constants (`STATUS_LABELS`, etc.):

```js
const ROLES = [
  { name: 'ADMIN', label: 'Admin' },
  { name: 'SHOOTER', label: 'Schütze' },
  { name: 'OWNER', label: 'Bereichsleiter' },
]

function hasRole(userId, roleName) {
  return (userStore.userRolesMap[userId] ?? []).some((r) => r.roleName === roleName)
}

async function handleToggleRole(userId, roleName) {
  await userStore.toggleRole(userId, roleName)
}
```

- [ ] **Step 2: Add the "Rollen" section to the template**

In `src/views/UsersView.vue`, inside the `<div class="detail-sections">`, add a new section as the **first child** (before the "Kontakt" section):

```html
<section class="detail-section">
  <h4 class="section-label">Rollen</h4>
  <div class="role-chips">
    <button
      v-for="role in ROLES"
      :key="role.name"
      :class="['role-chip', { 'role-chip--active': hasRole(selectedUser.id, role.name) }]"
      :disabled="userStore.isLoading"
      :aria-pressed="hasRole(selectedUser.id, role.name)"
      :aria-label="`Rolle ${role.label} ${hasRole(selectedUser.id, role.name) ? 'entfernen' : 'zuweisen'}`"
      @click="handleToggleRole(selectedUser.id, role.name)"
    >
      {{ role.label }}
    </button>
  </div>
</section>
```

- [ ] **Step 3: Add chip styles to `<style scoped>`**

Append to the `<style scoped>` block in `UsersView.vue`:

```css
/* ── Role chips ── */
.role-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.role-chip {
  padding: 0.3rem 0.9rem;
  border-radius: 20px;
  border: 1.5px solid #d1d5db;
  background: #f9fafb;
  color: #6b7280;
  font-size: 0.82rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.role-chip:hover:not(:disabled) {
  border-color: #2563eb;
  color: #2563eb;
  background: #eff6ff;
}

.role-chip--active {
  background: #2563eb;
  border-color: #2563eb;
  color: white;
}

.role-chip--active:hover:not(:disabled) {
  background: #1d4ed8;
  border-color: #1d4ed8;
  color: white;
}

.role-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
```

- [ ] **Step 4: Verify lint**

```bash
npm run lint
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add src/views/UsersView.vue
git commit -m "[ui] Add role toggle chips to user detail panel"
```

---

## Self-Review

**Spec coverage:**
- ✅ Toggle chips for ADMIN, SHOOTER, OWNER in detail panel
- ✅ Active state reflects current roles from `userRolesMap`
- ✅ Assign via `POST /users/{id}/roles`, revoke via `DELETE /users/{id}/roles/{roleName}`
- ✅ Disabled while loading
- ✅ Multi-role supported (chips are independent)
- ✅ Scoped roles deferred (not in scope)

**Placeholder scan:** None found.

**Type consistency:**
- `toggleRole(userId, roleName)` — consistent across store, test, and view
- `userRolesMap[userId]` — array of `{ roleName }` objects — consistent with existing usage in `UsersView.vue` and `userStore.js`
- `revokeRole(userId, roleName)` — consistent between `userApi.js` usage in store and the API function signature

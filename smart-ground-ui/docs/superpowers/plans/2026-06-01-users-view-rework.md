# UsersView Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the minimal admin UsersView with a Master-Detail layout that exposes all backend user fields, a tabbed create/edit modal, and role badges in the user list.

**Architecture:** Four focused changes: extend `userApi.js` with three new functions, extend `userStore.js` with role-map state and two new actions, build a reusable `UserFormModal.vue` component, and rewrite `UsersView.vue` with the master-detail layout. The modal is self-contained and emits `close`/`saved` — the view owns no form logic.

**Tech Stack:** Vue 3 Composition API (`<script setup>`), Pinia, Vitest + @vue/test-utils, `apiFetch` from `src/services/apiClient.js`

---

## File Map

| Status | File | Responsibility |
|---|---|---|
| Modify | `src/services/userApi.js` | Add `updateUser`, `assignRole`, `fetchUserRoles` |
| Create | `src/services/__tests__/userApi.test.js` | Test new API functions |
| Modify | `src/stores/userStore.js` | Add `selectedUser`, `userRolesMap`, `selectUser`, `updateUser`; update `loadUsers` and `createUser` |
| Create | `src/stores/__tests__/userStore.test.js` | Test store additions |
| Create | `src/components/UserFormModal.vue` | Two-tab create/edit modal |
| Create | `src/components/__tests__/UserFormModal.test.js` | Modal unit tests |
| Modify | `src/views/UsersView.vue` | Full rewrite — master-detail layout |

---

## Task 1: Extend `userApi.js`

**Files:**
- Modify: `src/services/userApi.js`
- Create: `src/services/__tests__/userApi.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/services/__tests__/userApi.test.js`:

```js
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../apiClient.js', () => ({
  apiFetch: vi.fn(),
}))

describe('userApi — new functions', () => {
  beforeEach(() => vi.clearAllMocks())

  it('updateUser calls PATCH /users/{id}', async () => {
    const { apiFetch } = await import('../apiClient.js')
    vi.mocked(apiFetch).mockResolvedValue({ id: 'u1', vorname: 'Hans' })

    const { updateUser } = await import('../userApi.js')
    const data = { vorname: 'Hans', nachname: 'Müller' }
    await updateUser('u1', data)

    expect(apiFetch).toHaveBeenCalledWith('/users/u1', {
      method: 'PATCH',
      body: JSON.stringify(data),
    })
  })

  it('assignRole calls POST /users/{id}/roles with roleName', async () => {
    const { apiFetch } = await import('../apiClient.js')
    vi.mocked(apiFetch).mockResolvedValue({})

    const { assignRole } = await import('../userApi.js')
    await assignRole('u1', 'SHOOTER')

    expect(apiFetch).toHaveBeenCalledWith('/users/u1/roles', {
      method: 'POST',
      body: JSON.stringify({ roleName: 'SHOOTER' }),
    })
  })

  it('fetchUserRoles calls GET /users/{id}/roles', async () => {
    const { apiFetch } = await import('../apiClient.js')
    vi.mocked(apiFetch).mockResolvedValue([{ roleName: 'SHOOTER' }])

    const { fetchUserRoles } = await import('../userApi.js')
    const result = await fetchUserRoles('u1')

    expect(apiFetch).toHaveBeenCalledWith('/users/u1/roles')
    expect(result).toEqual([{ roleName: 'SHOOTER' }])
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```
npm run test src/services/__tests__/userApi.test.js
```

Expected: 3 failures — `updateUser`, `assignRole`, `fetchUserRoles` are not exported.

- [ ] **Step 3: Add the three functions to `userApi.js`**

Open `src/services/userApi.js` and append at the end:

```js
export async function updateUser(userId, data) {
  return apiFetch(`/users/${userId}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

export async function assignRole(userId, roleName) {
  return apiFetch(`/users/${userId}/roles`, {
    method: 'POST',
    body: JSON.stringify({ roleName }),
  })
}

export async function fetchUserRoles(userId) {
  return apiFetch(`/users/${userId}/roles`)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
npm run test src/services/__tests__/userApi.test.js
```

Expected: 3 passing.

- [ ] **Step 5: Commit**

```
git add src/services/userApi.js src/services/__tests__/userApi.test.js
git commit -m "[ui] Add updateUser, assignRole, fetchUserRoles to userApi"
```

---

## Task 2: Extend `userStore.js`

**Files:**
- Modify: `src/stores/userStore.js`
- Create: `src/stores/__tests__/userStore.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/stores/__tests__/userStore.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '../userStore'
import * as userApi from '../../services/userApi'

vi.mock('../../services/userApi')

const mockUser1 = {
  id: 'u1',
  email: 'hans@test.ch',
  vorname: 'Hans',
  nachname: 'Müller',
  fullName: 'Hans Müller',
  status: 'ACTIVE',
  erstelltAm: '2024-01-01T00:00:00Z',
  aktualisiertAm: '2024-01-01T00:00:00Z',
}
const mockUser2 = {
  id: 'u2',
  email: 'anna@test.ch',
  vorname: 'Anna',
  nachname: 'Schmidt',
  fullName: 'Anna Schmidt',
  status: 'ACTIVE',
  erstelltAm: '2024-01-02T00:00:00Z',
  aktualisiertAm: '2024-01-02T00:00:00Z',
}
const mockRoles = [{ roleName: 'SHOOTER', roleId: 'r1', description: 'Schütze', assignedAt: '2024-01-01T00:00:00Z' }]

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadUsers fetches users and their roles in parallel', async () => {
    vi.mocked(userApi.fetchUsers).mockResolvedValue([mockUser1, mockUser2])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue(mockRoles)

    const store = useUserStore()
    await store.loadUsers()

    expect(store.users).toEqual([mockUser1, mockUser2])
    expect(store.userRolesMap['u1']).toEqual(mockRoles)
    expect(store.userRolesMap['u2']).toEqual(mockRoles)
    expect(userApi.fetchUserRoles).toHaveBeenCalledTimes(2)
  })

  it('loadUsers tolerates individual role-fetch failures gracefully', async () => {
    vi.mocked(userApi.fetchUsers).mockResolvedValue([mockUser1])
    vi.mocked(userApi.fetchUserRoles).mockRejectedValue(new Error('403'))

    const store = useUserStore()
    await store.loadUsers()

    expect(store.users).toEqual([mockUser1])
    expect(store.userRolesMap['u1']).toEqual([])
  })

  it('selectUser sets selectedUser', () => {
    const store = useUserStore()
    store.selectUser(mockUser1)
    expect(store.selectedUser).toEqual(mockUser1)
  })

  it('createUser creates user and assigns role, then reloads', async () => {
    vi.mocked(userApi.createUser).mockResolvedValue({ ...mockUser1, id: 'new-id' })
    vi.mocked(userApi.assignRole).mockResolvedValue({})
    vi.mocked(userApi.fetchUsers).mockResolvedValue([mockUser1])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue(mockRoles)

    const store = useUserStore()
    const userData = { vorname: 'Hans', nachname: 'Müller', email: 'hans@test.ch', password: 'secret' }
    await store.createUser(userData, 'ADMIN')

    expect(userApi.createUser).toHaveBeenCalledWith(userData)
    expect(userApi.assignRole).toHaveBeenCalledWith('new-id', 'ADMIN')
    expect(userApi.fetchUsers).toHaveBeenCalled()
  })

  it('createUser defaults role to SHOOTER when not provided', async () => {
    vi.mocked(userApi.createUser).mockResolvedValue({ ...mockUser1, id: 'new-id' })
    vi.mocked(userApi.assignRole).mockResolvedValue({})
    vi.mocked(userApi.fetchUsers).mockResolvedValue([])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue([])

    const store = useUserStore()
    await store.createUser({ vorname: 'Hans', nachname: 'Müller', email: 'h@t.ch', password: 'x' })

    expect(userApi.assignRole).toHaveBeenCalledWith('new-id', 'SHOOTER')
  })

  it('updateUser calls userApi.updateUser and reloads', async () => {
    vi.mocked(userApi.updateUser).mockResolvedValue({ ...mockUser1, vorname: 'Johannes' })
    vi.mocked(userApi.fetchUsers).mockResolvedValue([mockUser1])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue(mockRoles)

    const store = useUserStore()
    await store.updateUser('u1', { vorname: 'Johannes' })

    expect(userApi.updateUser).toHaveBeenCalledWith('u1', { vorname: 'Johannes' })
    expect(userApi.fetchUsers).toHaveBeenCalled()
  })

  it('deleteUser clears selectedUser if deleted user was selected', async () => {
    vi.mocked(userApi.deleteUser).mockResolvedValue(null)
    vi.mocked(userApi.fetchUsers).mockResolvedValue([])
    vi.mocked(userApi.fetchUserRoles).mockResolvedValue([])

    const store = useUserStore()
    store.users = [mockUser1]
    store.selectUser(mockUser1)

    await store.deleteUser('u1')

    expect(store.selectedUser).toBeNull()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```
npm run test src/stores/__tests__/userStore.test.js
```

Expected: all fail — `userRolesMap`, `selectedUser`, `selectUser`, `updateUser` not present; `createUser` doesn't assign roles.

- [ ] **Step 3: Replace `src/stores/userStore.js` with the updated implementation**

```js
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as userApi from '../services/userApi.js'

export const useUserStore = defineStore('user', () => {
  const users = ref([])
  const selectedUser = ref(null)
  const userRolesMap = ref({})
  const currentUser = ref(null)
  const isLoading = ref(false)
  const error = ref(null)

  async function loadUsers() {
    isLoading.value = true
    error.value = null
    try {
      const data = await userApi.fetchUsers()
      const list = data.content ?? data
      users.value = list

      const entries = await Promise.all(
        list.map(async (u) => {
          const roles = await userApi.fetchUserRoles(u.id).catch(() => [])
          return [u.id, roles]
        })
      )
      userRolesMap.value = Object.fromEntries(entries)
    } catch (err) {
      error.value = err.message || 'Failed to load users'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  function selectUser(user) {
    selectedUser.value = user
  }

  async function createUser(userData, role = 'SHOOTER') {
    isLoading.value = true
    error.value = null
    try {
      const created = await userApi.createUser(userData)
      await userApi.assignRole(created.id, role)
      await loadUsers()
    } catch (err) {
      error.value = err.message || 'Failed to create user'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function updateUser(userId, data) {
    isLoading.value = true
    error.value = null
    try {
      await userApi.updateUser(userId, data)
      await loadUsers()
    } catch (err) {
      error.value = err.message || 'Failed to update user'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function deleteUser(userId) {
    isLoading.value = true
    error.value = null
    try {
      await userApi.deleteUser(userId)
      if (selectedUser.value?.id === userId) {
        selectedUser.value = null
      }
      await loadUsers()
    } catch (err) {
      error.value = err.message || 'Failed to delete user'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function getCurrentUser() {
    isLoading.value = true
    error.value = null
    try {
      const data = await userApi.getCurrentUser()
      currentUser.value = data
      return data
    } catch (err) {
      error.value = err.message || 'Failed to load user profile'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function changePassword(oldPassword, newPassword) {
    isLoading.value = true
    error.value = null
    try {
      await userApi.changePassword(oldPassword, newPassword)
    } catch (err) {
      error.value = err.message || 'Failed to change password'
      throw err
    } finally {
      isLoading.value = false
    }
  }

  return {
    users,
    selectedUser,
    userRolesMap,
    currentUser,
    isLoading,
    error,
    loadUsers,
    selectUser,
    createUser,
    updateUser,
    deleteUser,
    getCurrentUser,
    changePassword,
  }
})
```

- [ ] **Step 4: Run tests to verify they pass**

```
npm run test src/stores/__tests__/userStore.test.js
```

Expected: 7 passing.

- [ ] **Step 5: Commit**

```
git add src/stores/userStore.js src/stores/__tests__/userStore.test.js
git commit -m "[ui] Extend userStore with selectedUser, userRolesMap, updateUser, role-aware createUser"
```

---

## Task 3: Build `UserFormModal.vue`

**Files:**
- Create: `src/components/UserFormModal.vue`
- Create: `src/components/__tests__/UserFormModal.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/components/__tests__/UserFormModal.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const mockCreate = vi.fn().mockResolvedValue(undefined)
const mockUpdate = vi.fn().mockResolvedValue(undefined)

vi.mock('@/stores/userStore.js', () => ({
  useUserStore: () => ({
    createUser: mockCreate,
    updateUser: mockUpdate,
    isLoading: false,
    error: null,
  }),
}))

async function importModal() {
  const { default: UserFormModal } = await import('../UserFormModal.vue')
  return UserFormModal
}

describe('UserFormModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders Basisdaten tab active by default', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    expect(wrapper.find('[data-testid="tab-base"]').classes()).toContain('active')
    expect(wrapper.find('[data-testid="tab-extended"]').classes()).not.toContain('active')
  })

  it('switches to Erweitert tab on click', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="tab-extended"]').trigger('click')
    expect(wrapper.find('[data-testid="tab-extended"]').classes()).toContain('active')
    expect(wrapper.find('[data-testid="tab-base"]').classes()).not.toContain('active')
  })

  it('shows validation errors when required fields empty on submit', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="error-vorname"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-email"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="error-password"]').exists()).toBe(true)
  })

  it('switches back to Basisdaten tab when validation fails while on Erweitert', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="tab-extended"]').trigger('click')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    expect(wrapper.find('[data-testid="tab-base"]').classes()).toContain('active')
  })

  it('emits close when Abbrechen is clicked', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="cancel-btn"]').trigger('click')
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('calls createUser and emits saved after valid create submit', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, { props: { mode: 'create' } })
    await wrapper.find('[data-testid="input-vorname"]').setValue('Hans')
    await wrapper.find('[data-testid="input-nachname"]').setValue('Müller')
    await wrapper.find('[data-testid="input-email"]').setValue('hans@test.ch')
    await wrapper.find('[data-testid="input-password"]').setValue('secret123')
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(mockCreate).toHaveBeenCalledOnce()
    expect(wrapper.emitted('saved')).toBeTruthy()
  })

  it('does not show password field in edit mode', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, {
      props: { mode: 'edit', initialUser: { id: 'u1', vorname: 'Anna', nachname: 'Schmidt', email: 'anna@test.ch' } },
    })
    expect(wrapper.find('[data-testid="input-password"]').exists()).toBe(false)
  })

  it('does not show role dropdown in edit mode', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, {
      props: { mode: 'edit', initialUser: { id: 'u1', vorname: 'Anna', nachname: 'Schmidt', email: 'anna@test.ch' } },
    })
    expect(wrapper.find('[data-testid="input-role"]').exists()).toBe(false)
  })

  it('pre-populates form with initialUser data in edit mode', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, {
      props: {
        mode: 'edit',
        initialUser: { id: 'u1', vorname: 'Anna', nachname: 'Schmidt', email: 'anna@test.ch', telefonnummer: '+41 79 111' },
      },
    })
    expect(wrapper.find('[data-testid="input-vorname"]').element.value).toBe('Anna')
    expect(wrapper.find('[data-testid="input-nachname"]').element.value).toBe('Schmidt')
  })

  it('calls updateUser and emits saved after valid edit submit', async () => {
    const UserFormModal = await importModal()
    const wrapper = mount(UserFormModal, {
      props: { mode: 'edit', initialUser: { id: 'u1', vorname: 'Anna', nachname: 'Schmidt', email: 'anna@test.ch' } },
    })
    await wrapper.find('[data-testid="submit-btn"]').trigger('click')
    await wrapper.vm.$nextTick()
    expect(mockUpdate).toHaveBeenCalledWith('u1', expect.objectContaining({ vorname: 'Anna', nachname: 'Schmidt' }))
    expect(wrapper.emitted('saved')).toBeTruthy()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```
npm run test src/components/__tests__/UserFormModal.test.js
```

Expected: all fail — `UserFormModal.vue` does not exist.

- [ ] **Step 3: Create `src/components/UserFormModal.vue`**

```vue
<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-container">
      <div class="modal-header">
        <h2>{{ mode === 'create' ? 'Neuer Benutzer' : 'Benutzer bearbeiten' }}</h2>
        <button class="close-btn" aria-label="Schließen" @click="$emit('close')">✕</button>
      </div>

      <div class="modal-tabs" role="tablist">
        <button
          data-testid="tab-base"
          role="tab"
          :class="['tab', { active: activeTab === 'base' }]"
          :aria-selected="activeTab === 'base'"
          @click="activeTab = 'base'"
        >Basisdaten</button>
        <button
          data-testid="tab-extended"
          role="tab"
          :class="['tab', { active: activeTab === 'extended' }]"
          :aria-selected="activeTab === 'extended'"
          @click="activeTab = 'extended'"
        >Erweitert</button>
      </div>

      <!-- Tab 1: Basisdaten -->
      <div v-show="activeTab === 'base'" class="tab-content" role="tabpanel">
        <div class="form-row">
          <div class="form-group">
            <label for="input-vorname">Vorname *</label>
            <input
              id="input-vorname"
              data-testid="input-vorname"
              v-model="form.vorname"
              type="text"
              :class="{ 'input-error': errors.vorname }"
              :disabled="userStore.isLoading"
            />
            <span v-if="errors.vorname" data-testid="error-vorname" class="error-msg">{{ errors.vorname }}</span>
          </div>
          <div class="form-group">
            <label for="input-nachname">Nachname *</label>
            <input
              id="input-nachname"
              data-testid="input-nachname"
              v-model="form.nachname"
              type="text"
              :class="{ 'input-error': errors.nachname }"
              :disabled="userStore.isLoading"
            />
            <span v-if="errors.nachname" data-testid="error-nachname" class="error-msg">{{ errors.nachname }}</span>
          </div>
        </div>

        <div class="form-group">
          <label for="input-email">E-Mail *</label>
          <input
            id="input-email"
            data-testid="input-email"
            v-model="form.email"
            type="email"
            :class="{ 'input-error': errors.email }"
            :disabled="mode === 'edit' || userStore.isLoading"
          />
          <span v-if="errors.email" data-testid="error-email" class="error-msg">{{ errors.email }}</span>
        </div>

        <div v-if="mode === 'create'" class="form-group">
          <label for="input-password">Passwort *</label>
          <input
            id="input-password"
            data-testid="input-password"
            v-model="form.password"
            type="password"
            :class="{ 'input-error': errors.password }"
            :disabled="userStore.isLoading"
          />
          <span v-if="errors.password" data-testid="error-password" class="error-msg">{{ errors.password }}</span>
        </div>

        <div v-if="mode === 'create'" class="form-group">
          <label for="input-role">Rolle *</label>
          <select
            id="input-role"
            data-testid="input-role"
            v-model="form.role"
            :disabled="userStore.isLoading"
          >
            <option value="SHOOTER">Schütze</option>
            <option value="ADMIN">Admin</option>
          </select>
          <span class="field-hint">Standard: Schütze</span>
        </div>
      </div>

      <!-- Tab 2: Erweitert -->
      <div v-show="activeTab === 'extended'" class="tab-content" role="tabpanel">
        <div class="form-row">
          <div class="form-group">
            <label for="input-geburtsdatum">Geburtsdatum</label>
            <input
              id="input-geburtsdatum"
              data-testid="input-geburtsdatum"
              v-model="form.geburtsdatum"
              type="date"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group">
            <label for="input-geschlecht">Geschlecht</label>
            <select
              id="input-geschlecht"
              data-testid="input-geschlecht"
              v-model="form.geschlecht"
              :disabled="userStore.isLoading"
            >
              <option value="">—</option>
              <option value="MAENNLICH">Männlich</option>
              <option value="WEIBLICH">Weiblich</option>
              <option value="DIVERS">Divers</option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label for="input-telefonnummer">Telefonnummer</label>
          <input
            id="input-telefonnummer"
            data-testid="input-telefonnummer"
            v-model="form.telefonnummer"
            type="tel"
            placeholder="+41 79 ..."
            :disabled="userStore.isLoading"
          />
        </div>

        <div class="form-row form-row--address">
          <div class="form-group form-group--street">
            <label for="input-strasse">Strasse</label>
            <input
              id="input-strasse"
              data-testid="input-strasse"
              v-model="form.strasse"
              type="text"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group form-group--housenr">
            <label for="input-hausnummer">Nr.</label>
            <input
              id="input-hausnummer"
              data-testid="input-hausnummer"
              v-model="form.hausnummer"
              type="text"
              :disabled="userStore.isLoading"
            />
          </div>
        </div>

        <div class="form-row form-row--city">
          <div class="form-group form-group--plz">
            <label for="input-plz">PLZ</label>
            <input
              id="input-plz"
              data-testid="input-plz"
              v-model="form.plz"
              type="text"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group form-group--city-name">
            <label for="input-stadt">Stadt</label>
            <input
              id="input-stadt"
              data-testid="input-stadt"
              v-model="form.stadt"
              type="text"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group form-group--land">
            <label for="input-land">Land</label>
            <input
              id="input-land"
              data-testid="input-land"
              v-model="form.land"
              type="text"
              placeholder="CH"
              :disabled="userStore.isLoading"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="input-mitgliedsnummer">Mitgliedsnummer</label>
            <input
              id="input-mitgliedsnummer"
              data-testid="input-mitgliedsnummer"
              v-model="form.mitgliedsnummer"
              type="text"
              placeholder="SG-0001"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group">
            <label for="input-sprache">Sprache</label>
            <select
              id="input-sprache"
              data-testid="input-sprache"
              v-model="form.sprache"
              :disabled="userStore.isLoading"
            >
              <option value="de">Deutsch</option>
              <option value="fr">Français</option>
              <option value="it">Italiano</option>
              <option value="en">English</option>
            </select>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <span class="field-hint">* Pflichtfelder</span>
        <div class="footer-actions">
          <button
            data-testid="cancel-btn"
            class="btn btn-secondary"
            :disabled="userStore.isLoading"
            @click="$emit('close')"
          >Abbrechen</button>
          <button
            data-testid="submit-btn"
            class="btn btn-primary"
            :disabled="userStore.isLoading"
            @click="submit"
          >{{ userStore.isLoading ? 'Wird gespeichert...' : (mode === 'create' ? 'Erstellen' : 'Speichern') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/userStore.js'

const props = defineProps({
  mode: { type: String, default: 'create' },
  initialUser: { type: Object, default: null },
})
const emit = defineEmits(['close', 'saved'])

const userStore = useUserStore()
const activeTab = ref('base')

const form = reactive({
  vorname: '',
  nachname: '',
  email: '',
  password: '',
  role: 'SHOOTER',
  geburtsdatum: '',
  geschlecht: '',
  telefonnummer: '',
  strasse: '',
  hausnummer: '',
  plz: '',
  stadt: '',
  land: '',
  mitgliedsnummer: '',
  sprache: 'de',
})

const errors = reactive({})

onMounted(() => {
  if (props.mode === 'edit' && props.initialUser) {
    Object.assign(form, {
      vorname: props.initialUser.vorname ?? '',
      nachname: props.initialUser.nachname ?? '',
      email: props.initialUser.email ?? '',
      geburtsdatum: props.initialUser.geburtsdatum ?? '',
      geschlecht: props.initialUser.geschlecht ?? '',
      telefonnummer: props.initialUser.telefonnummer ?? '',
      strasse: props.initialUser.strasse ?? '',
      hausnummer: props.initialUser.hausnummer ?? '',
      plz: props.initialUser.plz ?? '',
      stadt: props.initialUser.stadt ?? '',
      land: props.initialUser.land ?? '',
      mitgliedsnummer: props.initialUser.mitgliedsnummer ?? '',
      sprache: props.initialUser.sprache ?? 'de',
    })
  }
})

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.vorname.trim()) errors.vorname = 'Pflichtfeld'
  if (!form.nachname.trim()) errors.nachname = 'Pflichtfeld'
  if (!form.email.trim()) errors.email = 'Pflichtfeld'
  if (props.mode === 'create' && !form.password) errors.password = 'Pflichtfeld'
  return Object.keys(errors).length === 0
}

function cleanPayload(obj) {
  return Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== '' && v !== null && v !== undefined)
  )
}

async function submit() {
  if (!validate()) {
    activeTab.value = 'base'
    return
  }
  try {
    if (props.mode === 'create') {
      const { role, ...raw } = { ...form }
      await userStore.createUser(cleanPayload(raw), role)
    } else {
      const { password, role, email, ...raw } = { ...form }
      await userStore.updateUser(props.initialUser.id, cleanPayload(raw))
    }
    emit('saved')
  } catch {
    // error surfaced via userStore.error
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-container {
  background: white;
  border-radius: 8px;
  width: 540px;
  max-width: 95vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem 0;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.1rem;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.1rem;
  cursor: pointer;
  color: #6b7280;
  padding: 0.25rem;
}

.modal-tabs {
  display: flex;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 1.5rem;
  margin-top: 1rem;
}

.tab {
  padding: 0.6rem 1.25rem;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.9rem;
  color: #6b7280;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tab.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
  font-weight: 600;
}

.tab-content {
  padding: 1.25rem 1.5rem;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.85rem;
}

.form-row--address {
  grid-template-columns: 2fr 1fr;
}

.form-row--city {
  grid-template-columns: 1fr 2fr 1fr;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.form-group label {
  font-size: 0.78rem;
  color: #6b7280;
  font-weight: 500;
}

.form-group input,
.form-group select {
  padding: 0.55rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.9rem;
}

.form-group input:disabled,
.form-group select:disabled {
  background: #f9fafb;
  color: #9ca3af;
}

.input-error {
  border-color: #ef4444 !important;
}

.error-msg {
  font-size: 0.75rem;
  color: #ef4444;
}

.field-hint {
  font-size: 0.75rem;
  color: #9ca3af;
}

.modal-footer {
  padding: 0.85rem 1.5rem;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f9fafb;
  border-radius: 0 0 8px 8px;
}

.footer-actions {
  display: flex;
  gap: 0.5rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: #2563eb;
  color: white;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}
</style>
```

- [ ] **Step 4: Run tests to verify they pass**

```
npm run test src/components/__tests__/UserFormModal.test.js
```

Expected: 10 passing.

- [ ] **Step 5: Commit**

```
git add src/components/UserFormModal.vue src/components/__tests__/UserFormModal.test.js
git commit -m "[ui] Add UserFormModal with two-tab create/edit form"
```

---

## Task 4: Rewrite `UsersView.vue`

**Files:**
- Modify: `src/views/UsersView.vue`

> No separate test file for this view — the store and modal are already unit-tested. A smoke-render test is included below.

- [ ] **Step 1: Write a basic smoke test**

Create `src/views/__tests__/UsersView.test.js`:

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/stores/userStore.js', () => ({
  useUserStore: () => ({
    users: [
      { id: 'u1', fullName: 'Hans Müller', email: 'hans@test.ch', status: 'ACTIVE' },
      { id: 'u2', fullName: 'Anna Schmidt', email: 'anna@test.ch', status: 'ACTIVE' },
    ],
    selectedUser: null,
    userRolesMap: { u1: [{ roleName: 'SHOOTER' }], u2: [{ roleName: 'ADMIN' }] },
    isLoading: false,
    error: null,
    loadUsers: vi.fn().mockResolvedValue(undefined),
    selectUser: vi.fn(),
    deleteUser: vi.fn(),
  }),
}))

vi.mock('@/components/UserFormModal.vue', () => ({
  default: { template: '<div />' },
}))

describe('UsersView', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders all users in the list', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    expect(wrapper.text()).toContain('Hans Müller')
    expect(wrapper.text()).toContain('Anna Schmidt')
  })

  it('renders role badges in the list', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    expect(wrapper.text()).toContain('Schütze')
    expect(wrapper.text()).toContain('Admin')
  })

  it('filters list by search term', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    await wrapper.find('[data-testid="search-input"]').setValue('anna')
    expect(wrapper.text()).not.toContain('Hans Müller')
    expect(wrapper.text()).toContain('Anna Schmidt')
  })

  it('shows empty state when no user is selected', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(true)
  })

  it('opens create modal when + Neu is clicked', async () => {
    const { default: UsersView } = await import('../UsersView.vue')
    const wrapper = mount(UsersView)
    await wrapper.find('[data-testid="btn-create"]').trigger('click')
    expect(wrapper.find('[data-testid="user-form-modal"]').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: Run smoke tests to verify they fail**

```
npm run test src/views/__tests__/UsersView.test.js
```

Expected: all fail — `UsersView.vue` doesn't have the new structure yet.

- [ ] **Step 3: Replace `src/views/UsersView.vue`**

```vue
<template>
  <div class="users-view">
    <!-- Left: User List Panel -->
    <aside class="list-panel">
      <div class="list-header">
        <input
          data-testid="search-input"
          v-model="search"
          type="text"
          placeholder="Suchen..."
          class="search-input"
          aria-label="Benutzer suchen"
        />
        <button
          data-testid="btn-create"
          class="btn btn-primary btn-sm"
          @click="openCreate"
        >+ Neu</button>
      </div>

      <div v-if="userStore.error" class="error-banner">{{ userStore.error }}</div>

      <div class="list-body">
        <p v-if="filteredUsers.length === 0" class="list-empty">Keine Benutzer gefunden</p>
        <button
          v-for="user in filteredUsers"
          :key="user.id"
          :class="['user-item', { selected: selectedUser?.id === user.id }]"
          @click="userStore.selectUser(user)"
        >
          <span class="user-item-name">{{ user.fullName }}</span>
          <span class="user-item-badges">
            <span :class="['badge', statusBadgeClass(user.status)]">{{ statusLabel(user.status) }}</span>
            <span
              v-for="role in (userStore.userRolesMap[user.id] ?? [])"
              :key="role.roleName"
              :class="['badge', roleBadgeClass(role.roleName)]"
            >{{ roleLabel(role.roleName) }}</span>
          </span>
        </button>
      </div>
    </aside>

    <!-- Right: Detail Panel -->
    <main class="detail-panel">
      <div v-if="!selectedUser" data-testid="empty-state" class="empty-state">
        <p>Benutzer aus der Liste auswählen</p>
      </div>

      <template v-else>
        <div class="detail-header">
          <div class="detail-title">
            <span class="detail-name">{{ selectedUser.fullName }}</span>
            <span :class="['badge', statusBadgeClass(selectedUser.status)]">{{ statusLabel(selectedUser.status) }}</span>
            <span
              v-for="role in (userStore.userRolesMap[selectedUser.id] ?? [])"
              :key="role.roleName"
              :class="['badge', roleBadgeClass(role.roleName)]"
            >{{ roleLabel(role.roleName) }}</span>
          </div>
          <div class="detail-actions">
            <button class="btn btn-secondary btn-sm" @click="openEdit">Bearbeiten</button>
            <button class="btn btn-danger btn-sm" :disabled="userStore.isLoading" @click="deletingUser = selectedUser">Löschen</button>
          </div>
        </div>

        <div class="detail-sections">
          <section class="detail-section">
            <h4 class="section-label">Kontakt</h4>
            <div class="detail-grid">
              <div class="detail-field">
                <span class="field-label">E-Mail</span>
                <span>{{ selectedUser.email }}</span>
              </div>
              <div class="detail-field">
                <span class="field-label">Telefon</span>
                <span>{{ selectedUser.telefonnummer ?? '—' }}</span>
              </div>
              <div class="detail-field">
                <span class="field-label">Geburtsdatum</span>
                <span>{{ formatDate(selectedUser.geburtsdatum) ?? '—' }}</span>
              </div>
            </div>
          </section>

          <section class="detail-section">
            <h4 class="section-label">Adresse</h4>
            <div class="detail-field">
              <span>{{ formatAddress(selectedUser) }}</span>
            </div>
          </section>

          <section class="detail-section">
            <h4 class="section-label">Mitgliedschaft</h4>
            <div class="detail-grid">
              <div class="detail-field">
                <span class="field-label">Mitgliedsnummer</span>
                <span>{{ selectedUser.mitgliedsnummer ?? '—' }}</span>
              </div>
              <div class="detail-field">
                <span class="field-label">Schiesslizenz</span>
                <span>{{ selectedUser.schiessLizenz ?? '—' }}</span>
              </div>
              <div class="detail-field">
                <span class="field-label">Lizenz Ablauf</span>
                <span :class="licenseExpiryClass(selectedUser.schiessLizenzVerfallsdatum)">
                  {{ formatDate(selectedUser.schiessLizenzVerfallsdatum) ?? '—' }}
                  <span v-if="isLicenseExpiringSoon(selectedUser.schiessLizenzVerfallsdatum)"> ⚠</span>
                </span>
              </div>
            </div>
          </section>

          <section class="detail-section">
            <h4 class="section-label">System</h4>
            <div class="detail-grid">
              <div class="detail-field">
                <span class="field-label">Erstellt am</span>
                <span>{{ formatInstant(selectedUser.erstelltAm) }}</span>
              </div>
              <div class="detail-field">
                <span class="field-label">Zuletzt aktualisiert</span>
                <span>{{ formatInstant(selectedUser.aktualisiertAm) }}</span>
              </div>
            </div>
          </section>
        </div>
      </template>
    </main>

    <!-- Create / Edit Modal -->
    <UserFormModal
      v-if="showModal"
      data-testid="user-form-modal"
      :mode="modalMode"
      :initial-user="modalUser"
      @close="showModal = false"
      @saved="onSaved"
    />

    <!-- Delete Confirmation -->
    <div v-if="deletingUser" class="modal-overlay" @click.self="deletingUser = null">
      <div class="confirm-modal">
        <h3>Benutzer löschen?</h3>
        <p>Möchten Sie „{{ deletingUser.fullName }}" wirklich löschen? Diese Aktion kann nicht rückgängig gemacht werden.</p>
        <div class="confirm-actions">
          <button class="btn btn-secondary" @click="deletingUser = null">Abbrechen</button>
          <button class="btn btn-danger" :disabled="userStore.isLoading" @click="handleDelete">
            {{ userStore.isLoading ? 'Wird gelöscht...' : 'Löschen' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/userStore.js'
import UserFormModal from '@/components/UserFormModal.vue'

const userStore = useUserStore()
const search = ref('')
const showModal = ref(false)
const modalMode = ref('create')
const modalUser = ref(null)
const deletingUser = ref(null)

const selectedUser = computed(() => userStore.selectedUser)

const filteredUsers = computed(() => {
  const q = search.value.toLowerCase()
  if (!q) return userStore.users
  return userStore.users.filter(
    (u) =>
      u.fullName?.toLowerCase().includes(q) ||
      u.email?.toLowerCase().includes(q)
  )
})

onMounted(() => userStore.loadUsers())

function openCreate() {
  modalMode.value = 'create'
  modalUser.value = null
  showModal.value = true
}

function openEdit() {
  modalMode.value = 'edit'
  modalUser.value = selectedUser.value
  showModal.value = true
}

async function onSaved() {
  showModal.value = false
  await userStore.loadUsers()
}

async function handleDelete() {
  if (!deletingUser.value) return
  await userStore.deleteUser(deletingUser.value.id)
  deletingUser.value = null
}

const STATUS_LABELS = { ACTIVE: 'Aktiv', INACTIVE: 'Inaktiv' }
const STATUS_BADGE_CLASSES = { ACTIVE: 'badge-green', INACTIVE: 'badge-gray' }
const ROLE_LABELS = { ADMIN: 'Admin', SHOOTER: 'Schütze', OWNER: 'Bereichsleiter' }
const ROLE_BADGE_CLASSES = { ADMIN: 'badge-purple', SHOOTER: 'badge-blue', OWNER: 'badge-yellow' }

const statusLabel = (s) => STATUS_LABELS[s] ?? s
const statusBadgeClass = (s) => STATUS_BADGE_CLASSES[s] ?? 'badge-gray'
const roleLabel = (r) => ROLE_LABELS[r] ?? r
const roleBadgeClass = (r) => ROLE_BADGE_CLASSES[r] ?? 'badge-gray'

const formatDate = (val) => {
  if (!val) return null
  const d = new Date(val)
  return isNaN(d.getTime()) ? null : d.toLocaleDateString('de-DE')
}

const formatInstant = (val) => {
  if (!val) return '—'
  return new Date(val).toLocaleDateString('de-DE', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const formatAddress = (u) => {
  const parts = [
    u.strasse && u.hausnummer ? `${u.strasse} ${u.hausnummer}` : (u.strasse || null),
    u.plz && u.stadt ? `${u.plz} ${u.stadt}` : (u.plz || u.stadt || null),
    u.land || null,
  ].filter(Boolean)
  return parts.length ? parts.join(', ') : '—'
}

const isLicenseExpiringSoon = (dateStr) => {
  if (!dateStr) return false
  return (new Date(dateStr) - new Date()) < 90 * 24 * 60 * 60 * 1000
}

const licenseExpiryClass = (dateStr) =>
  isLicenseExpiringSoon(dateStr) ? 'text-warning' : ''
</script>

<style scoped>
.users-view {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ── Left panel ── */
.list-panel {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.list-header {
  padding: 0.75rem;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.search-input {
  flex: 1;
  padding: 0.4rem 0.6rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.85rem;
}

.list-body {
  flex: 1;
  overflow-y: auto;
}

.list-empty {
  padding: 2rem;
  text-align: center;
  color: #9ca3af;
  font-size: 0.9rem;
}

.user-item {
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  border-left: 3px solid transparent;
  padding: 0.65rem 0.75rem;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.user-item:hover {
  background: #f9fafb;
}

.user-item.selected {
  background: #eff6ff;
  border-left-color: #2563eb;
}

.user-item-name {
  font-size: 0.875rem;
  font-weight: 500;
}

.user-item-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

/* ── Right panel ── */
.detail-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 0.95rem;
}

.detail-header {
  padding: 0.85rem 1.25rem;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f9fafb;
  flex-shrink: 0;
}

.detail-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.detail-name {
  font-weight: 600;
  font-size: 1rem;
}

.detail-actions {
  display: flex;
  gap: 0.5rem;
}

.detail-sections {
  flex: 1;
  overflow-y: auto;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.detail-section .section-label {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #6b7280;
  margin: 0 0 0.6rem;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}

.detail-field {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.field-label {
  font-size: 0.72rem;
  color: #9ca3af;
}

/* ── Badges ── */
.badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 0.75rem;
  font-weight: 500;
}

.badge-green { background: #d1fae5; color: #065f46; }
.badge-gray  { background: #f3f4f6; color: #6b7280; }
.badge-blue  { background: #dbeafe; color: #1d4ed8; }
.badge-purple{ background: #f3e8ff; color: #7e22ce; }
.badge-yellow{ background: #fef3c7; color: #92400e; }

/* ── Warnings ── */
.text-warning { color: #d97706; font-weight: 500; }

.error-banner {
  background: #fee2e2;
  color: #991b1b;
  padding: 0.5rem 0.75rem;
  font-size: 0.85rem;
}

/* ── Buttons ── */
.btn {
  padding: 0.45rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
}

.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-sm { padding: 0.3rem 0.75rem; font-size: 0.82rem; }
.btn-primary { background: #2563eb; color: white; }
.btn-secondary { background: #f3f4f6; color: #374151; }
.btn-danger { background: #dc2626; color: white; }

/* ── Delete confirm modal ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.confirm-modal {
  background: white;
  border-radius: 8px;
  padding: 1.75rem;
  max-width: 420px;
  width: 90%;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.confirm-modal h3 { margin: 0 0 0.5rem; }
.confirm-modal p  { color: #6b7280; margin: 0 0 1.5rem; font-size: 0.9rem; }

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

@media (max-width: 768px) {
  .users-view { flex-direction: column; }
  .list-panel { width: 100%; border-right: none; border-bottom: 1px solid #e5e7eb; max-height: 240px; }
  .detail-grid { grid-template-columns: 1fr 1fr; }
}
</style>
```

- [ ] **Step 4: Run all tests**

```
npm run test src/views/__tests__/UsersView.test.js
```

Expected: 5 passing.

Then run the full suite to check for regressions:

```
npm run test
```

Expected: all existing tests still pass.

- [ ] **Step 5: Lint check**

```
npm run lint
```

Fix any issues before committing.

- [ ] **Step 6: Commit**

```
git add src/views/UsersView.vue src/views/__tests__/UsersView.test.js
git commit -m "[ui] Rewrite UsersView with master-detail layout and full user fields"
```

---

## Final Verification

- [ ] Run full test suite: `npm run test` — all passing
- [ ] Run lint: `npm run lint` — no warnings
- [ ] Manual smoke test with dev server (`npm run dev`):
  - Log in as admin (`admin@smartground.local` / `admin123`)
  - Navigate to Benutzerverwaltung
  - User list shows names + status + role badges
  - Click a user → detail panel shows all sections
  - Click "+ Neu" → modal opens on Basisdaten tab, role defaults to Schütze
  - Switch to Erweitert tab → all optional fields present
  - Submit empty form → validation errors appear, tab snaps back to Basisdaten
  - Create a user → appears in list with correct role badge
  - Select user → click Bearbeiten → modal pre-filled, no password field
  - Edit a field → save → detail panel reflects change
  - Delete a user → confirm → user removed, detail panel clears

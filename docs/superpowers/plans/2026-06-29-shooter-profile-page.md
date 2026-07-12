# Shooter Profile Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `UsernameEditModal` with a dedicated `/profil` subpage in the shooter layout showing and editing the full user profile in 3 tabs (Profil, Kontakt, Mitgliedschaft).

**Architecture:** `authStore.updateOwnUsername` is replaced by the more general `updateProfile(data)` action that PATCHes `PATCH /api/users/{id}` with any subset of profile fields and refreshes `profile` via `_loadProfile()`. `ShooterProfilView.vue` is a new full-page view with local reactive form copies per tab — no new store needed. `UsernameEditModal.vue` is deleted entirely.

**Tech Stack:** Vue 3 Composition API (`<script setup>`), Pinia, Vue Router 4, Vitest + `@vue/test-utils`, native `fetch` via `userApi.js`

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `src/stores/authStore.js` | Modify | Replace `updateOwnUsername` with `updateProfile(data)` |
| `src/stores/__tests__/authStore.test.js` | Create | Tests for `updateProfile` |
| `src/router/index.js` | Modify | Add `/profil` shooter route |
| `src/views/shooter/ShooterProfilView.vue` | Create | 3-tab profile page |
| `src/views/shooter/ShooterHomeView.vue` | Modify | Remove modal state/import, navigate to `/profil` |
| `src/components/UsernameEditModal.vue` | Delete | No longer used |

---

## Task 1: Replace `updateOwnUsername` with `updateProfile` in authStore

**Files:**
- Modify: `src/stores/authStore.js`
- Create: `src/stores/__tests__/authStore.test.js`

- [ ] **Step 1: Create the test file**

```js
// src/stores/__tests__/authStore.test.js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../authStore'
import * as userApi from '@/services/userApi'
import * as authApi from '@/services/authApi'

vi.mock('@/services/userApi')
vi.mock('@/services/authApi')

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('updateProfile', () => {
    it('calls updateUser with profile id and data, then reloads profile', async () => {
      vi.mocked(authApi.login).mockResolvedValue({ token: 'tok' })
      vi.mocked(authApi.getMe)
        .mockResolvedValueOnce({
          id: 'user-1',
          vorname: 'Hans',
          nachname: 'Alt',
          username: 'hans',
          email: 'hans@test.com',
          permissions: [],
        })
        .mockResolvedValueOnce({
          id: 'user-1',
          vorname: 'Hans',
          nachname: 'Neu',
          username: 'hans',
          email: 'hans@test.com',
          permissions: [],
        })
      vi.mocked(userApi.updateUser).mockResolvedValue({})

      const store = useAuthStore()
      await store.login('hans@test.com', 'pw')

      await store.updateProfile({ nachname: 'Neu' })

      expect(userApi.updateUser).toHaveBeenCalledWith('user-1', { nachname: 'Neu' })
      expect(store.profile.nachname).toBe('Neu')
    })

    it('sets error and rethrows when updateUser fails', async () => {
      vi.mocked(authApi.getMe).mockResolvedValue({
        id: 'user-1', vorname: 'Hans', nachname: 'Alt',
        username: 'hans', email: 'hans@test.com', permissions: [],
      })
      vi.mocked(userApi.updateUser).mockRejectedValue(new Error('Network error'))

      const store = useAuthStore()
      store.profile = { id: 'user-1', vorname: 'Hans', nachname: 'Alt', username: 'hans', email: 'hans@test.com' }

      await expect(store.updateProfile({ nachname: 'Neu' })).rejects.toThrow('Network error')
      expect(store.error).toBe('Network error')
    })
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd smart-ground-ui && npm run test src/stores/__tests__/authStore.test.js
```

Expected: FAIL — `store.updateProfile is not a function`

- [ ] **Step 3: Replace `updateOwnUsername` with `updateProfile` in authStore**

In `src/stores/authStore.js`, replace the `updateOwnUsername` action (lines ~50–62) with:

```js
const updateProfile = async (data) => {
  isLoading.value = true
  error.value = null
  try {
    await updateUserApi(profile.value.id, data)
    await _loadProfile()
  } catch (err) {
    error.value = err.message
    throw err
  } finally {
    isLoading.value = false
  }
}
```

Also update the `return` statement — remove `updateOwnUsername`, add `updateProfile`:

```js
return {
  token,
  profile,
  permissions,
  displayName,
  isLoading,
  error,
  ready,
  readyPromise,
  isAuthenticated,
  hasPermission,
  login,
  updateProfile,
  init,
  logout,
}
```

- [ ] **Step 4: Run the test again — expect PASS**

```bash
npm run test src/stores/__tests__/authStore.test.js
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/stores/authStore.js src/stores/__tests__/authStore.test.js
git commit -m "[ui] Replace updateOwnUsername with updateProfile in authStore"
```

---

## Task 2: Add `/profil` route

**Files:**
- Modify: `src/router/index.js`

- [ ] **Step 1: Add the import and route entry**

At the top of `src/router/index.js`, add the import after the existing shooter imports:

```js
import ShooterProfilView from '@/views/shooter/ShooterProfilView.vue'
```

In the `routes` array, after the `/meine-passen` route, add:

```js
{ path: '/profil', component: ShooterProfilView, meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
```

- [ ] **Step 2: Commit**

```bash
git add src/router/index.js
git commit -m "[ui] Add /profil route to shooter layout"
```

Note: The build will fail until `ShooterProfilView.vue` exists — that's fine, Task 3 creates it.

---

## Task 3: Create ShooterProfilView.vue

**Files:**
- Create: `src/views/shooter/ShooterProfilView.vue`

- [ ] **Step 1: Create the component**

```vue
<!-- src/views/shooter/ShooterProfilView.vue -->
<template>
  <div class="profil-view">
    <!-- Header -->
    <div class="profil-header">
      <button class="back-btn" aria-label="Zurück" @click="router.back()">
        <Icons icon="chevron-left" :size="20" />
      </button>
      <div class="avatar">{{ initials }}</div>
      <div class="header-info">
        <div class="full-name">{{ fullName }}</div>
        <div class="username">@{{ authStore.profile?.username }}</div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tab-bar" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        role="tab"
        :aria-selected="activeTab === tab.id"
        :class="['tab-btn', { active: activeTab === tab.id }]"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab: Profil -->
    <div v-if="activeTab === 'profil'" class="tab-content" role="tabpanel">
      <div class="form-section">
        <div class="field-group">
          <label for="vorname">Vorname</label>
          <input id="vorname" v-model="profilForm.vorname" type="text" />
        </div>
        <div class="field-group">
          <label for="nachname">Nachname</label>
          <input id="nachname" v-model="profilForm.nachname" type="text" />
        </div>
        <div class="field-group">
          <label for="username">Benutzername</label>
          <input id="username" v-model="profilForm.username" type="text" />
          <span v-if="usernameError" class="field-error">{{ usernameError }}</span>
        </div>
        <div class="field-group">
          <label for="geburtsdatum">Geburtsdatum</label>
          <input id="geburtsdatum" v-model="profilForm.geburtsdatum" type="date" />
        </div>
        <div class="field-group">
          <label for="geschlecht">Geschlecht</label>
          <select id="geschlecht" v-model="profilForm.geschlecht">
            <option value="">— keine Angabe —</option>
            <option value="MAENNLICH">Männlich</option>
            <option value="WEIBLICH">Weiblich</option>
            <option value="DIVERS">Divers</option>
            <option value="UNBEKANNT">Unbekannt</option>
          </select>
        </div>
        <div class="field-group">
          <label for="sprache">Sprache</label>
          <select id="sprache" v-model="profilForm.sprache">
            <option value="">— keine Angabe —</option>
            <option value="DE">Deutsch</option>
            <option value="EN">English</option>
            <option value="FR">Français</option>
            <option value="IT">Italiano</option>
          </select>
        </div>
        <div class="field-group">
          <label for="biographie">Über mich</label>
          <textarea id="biographie" v-model="profilForm.biographie" maxlength="500" rows="3" />
          <span class="char-count">{{ profilForm.biographie?.length ?? 0 }} / 500</span>
        </div>
      </div>
      <div v-if="saveError" class="save-error">{{ saveError }}</div>
      <button class="save-btn" :disabled="authStore.isLoading" @click="saveProfilTab">
        {{ profilSaved ? 'Gespeichert ✓' : 'Speichern' }}
      </button>
    </div>

    <!-- Tab: Kontakt -->
    <div v-if="activeTab === 'kontakt'" class="tab-content" role="tabpanel">
      <div class="form-section">
        <div class="field-group">
          <label for="email">E-Mail</label>
          <input id="email" v-model="kontaktForm.email" type="email" />
        </div>
        <div class="field-group readonly">
          <label>E-Mail-Status</label>
          <span :class="['status-badge', authStore.profile?.emailBestaetigt ? 'badge-ok' : 'badge-pending']">
            {{ authStore.profile?.emailBestaetigt ? 'Bestätigt' : 'Ausstehend' }}
          </span>
        </div>
        <div class="field-group">
          <label for="telefonnummer">Telefon</label>
          <input id="telefonnummer" v-model="kontaktForm.telefonnummer" type="tel" />
        </div>
        <div class="field-group two-col">
          <div>
            <label for="strasse">Strasse</label>
            <input id="strasse" v-model="kontaktForm.strasse" type="text" />
          </div>
          <div>
            <label for="hausnummer">Nr.</label>
            <input id="hausnummer" v-model="kontaktForm.hausnummer" type="text" style="width: 70px" />
          </div>
        </div>
        <div class="field-group two-col">
          <div>
            <label for="plz">PLZ</label>
            <input id="plz" v-model="kontaktForm.plz" type="text" style="width: 80px" />
          </div>
          <div>
            <label for="stadt">Stadt</label>
            <input id="stadt" v-model="kontaktForm.stadt" type="text" />
          </div>
        </div>
        <div class="field-group">
          <label for="land">Land</label>
          <input id="land" v-model="kontaktForm.land" type="text" />
        </div>
      </div>
      <div v-if="saveError" class="save-error">{{ saveError }}</div>
      <button class="save-btn" :disabled="authStore.isLoading" @click="saveKontaktTab">
        {{ kontaktSaved ? 'Gespeichert ✓' : 'Speichern' }}
      </button>
    </div>

    <!-- Tab: Mitgliedschaft -->
    <div v-if="activeTab === 'mitgliedschaft'" class="tab-content" role="tabpanel">
      <div class="form-section">
        <div class="field-group readonly">
          <label>Mitgliedsnummer</label>
          <span class="field-value">{{ authStore.profile?.mitgliedsnummer ?? '—' }}</span>
        </div>
        <div class="field-group readonly">
          <label>Schiesslizenz</label>
          <span class="field-value">{{ authStore.profile?.schiessLizenz ?? '—' }}</span>
        </div>
        <div class="field-group readonly">
          <label>Lizenz gültig bis</label>
          <span class="field-value">{{ formatDate(authStore.profile?.schiessLizenzVerfallsdatum) }}</span>
        </div>
        <div class="field-group readonly">
          <label>Lizenz-Status</label>
          <span :class="['status-badge', authStore.profile?.schiessLizenzGueltig ? 'badge-ok' : 'badge-expired']">
            {{ authStore.profile?.schiessLizenzGueltig ? 'Gültig' : 'Abgelaufen / nicht vorhanden' }}
          </span>
        </div>
        <div class="field-group readonly">
          <label>Mitglied seit</label>
          <span class="field-value">{{ formatDate(authStore.profile?.erstelltAm) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore.js'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const authStore = useAuthStore()

const USERNAME_RE = /^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$/

const tabs = [
  { id: 'profil', label: 'Profil' },
  { id: 'kontakt', label: 'Kontakt' },
  { id: 'mitgliedschaft', label: 'Mitgliedschaft' },
]
const activeTab = ref('profil')

const profilSaved = ref(false)
const kontaktSaved = ref(false)
const saveError = ref('')
const usernameError = ref('')

const p = authStore.profile

const profilForm = reactive({
  vorname: p?.vorname ?? '',
  nachname: p?.nachname ?? '',
  username: p?.username ?? '',
  geburtsdatum: p?.geburtsdatum ?? '',
  geschlecht: p?.geschlecht ?? '',
  sprache: p?.sprache ?? '',
  biographie: p?.biographie ?? '',
})

const kontaktForm = reactive({
  email: p?.email ?? '',
  telefonnummer: p?.telefonnummer ?? '',
  strasse: p?.strasse ?? '',
  hausnummer: p?.hausnummer ?? '',
  plz: p?.plz ?? '',
  stadt: p?.stadt ?? '',
  land: p?.land ?? '',
})

const fullName = computed(() => `${authStore.profile?.vorname ?? ''} ${authStore.profile?.nachname ?? ''}`.trim())
const initials = computed(() => {
  const v = authStore.profile?.vorname?.[0] ?? ''
  const n = authStore.profile?.nachname?.[0] ?? ''
  return (v + n).toUpperCase() || '?'
})

function buildPayload(form) {
  // Only send fields that have a non-empty value — backend skips null fields (NON_NULL)
  return Object.fromEntries(
    Object.entries(form).filter(([, v]) => v !== '' && v !== null && v !== undefined)
  )
}

async function saveProfilTab() {
  usernameError.value = ''
  saveError.value = ''
  if (!USERNAME_RE.test(profilForm.username.trim())) {
    usernameError.value = '3–30 Zeichen, Buchstaben/Ziffern/._-'
    return
  }
  try {
    await authStore.updateProfile(buildPayload(profilForm))
    profilSaved.value = true
    setTimeout(() => { profilSaved.value = false }, 1500)
  } catch {
    saveError.value = authStore.error ?? 'Speichern fehlgeschlagen'
  }
}

async function saveKontaktTab() {
  saveError.value = ''
  try {
    await authStore.updateProfile(buildPayload(kontaktForm))
    kontaktSaved.value = true
    setTimeout(() => { kontaktSaved.value = false }, 1500)
  } catch {
    saveError.value = authStore.error ?? 'Speichern fehlgeschlagen'
  }
}

function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('de-CH', { day: '2-digit', month: '2-digit', year: 'numeric' })
}
</script>

<style scoped>
.profil-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.profil-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.back-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.5);
  padding: 4px;
  display: flex;
  align-items: center;
  border-radius: 6px;
  transition: background 0.15s;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.07);
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
  color: var(--sg-accent);
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.full-name {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.username {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding: 0 20px;
}

.tab-btn {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  margin-bottom: -1px;
  padding: 12px 14px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  border-bottom-color: var(--sg-accent);
  color: var(--sg-accent);
}

.tab-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.field-group.two-col {
  flex-direction: row;
  gap: 10px;
}

.field-group.two-col > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  flex: 1;
}

.field-group label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.field-group input,
.field-group select,
.field-group textarea {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  padding: 9px 12px;
  transition: border-color 0.15s;
  width: 100%;
}

.field-group input:focus,
.field-group select:focus,
.field-group textarea:focus {
  border-color: var(--sg-accent);
  outline: none;
}

.field-group select option {
  background: #1a2340;
}

.field-group.readonly {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 8px;
  padding: 10px 12px;
}

.field-group.readonly label {
  text-transform: uppercase;
  margin: 0;
}

.field-value {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.status-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 20px;
}

.badge-ok {
  background: rgba(74, 222, 128, 0.15);
  color: #4ade80;
}

.badge-pending {
  background: rgba(251, 191, 36, 0.15);
  color: #fbbf24;
}

.badge-expired {
  background: rgba(248, 113, 113, 0.15);
  color: #f87171;
}

.char-count {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  text-align: right;
}

.field-error {
  color: var(--sg-color-danger, #f87171);
  font-size: 12px;
}

.save-error {
  color: var(--sg-color-danger, #f87171);
  font-size: 13px;
  margin-top: 8px;
}

.save-btn {
  margin-top: 20px;
  width: 100%;
  padding: 12px;
  background: var(--sg-accent);
  border: none;
  border-radius: 10px;
  color: var(--sg-brand, #0f1a2e);
  cursor: pointer;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  transition: opacity 0.15s;
}

.save-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.save-btn:not(:disabled):hover {
  opacity: 0.9;
}
</style>
```

- [ ] **Step 2: Verify the build compiles**

```bash
cd smart-ground-ui && npm run build 2>&1 | tail -20
```

Expected: no errors (warnings about unused vars are ok)

- [ ] **Step 3: Commit**

```bash
git add src/views/shooter/ShooterProfilView.vue
git commit -m "[ui] Add ShooterProfilView with 3-tab profile page"
```

---

## Task 4: Update ShooterHomeView — remove modal, navigate to /profil

**Files:**
- Modify: `src/views/shooter/ShooterHomeView.vue`
- Delete: `src/components/UsernameEditModal.vue`

- [ ] **Step 1: Update ShooterHomeView.vue**

Replace the entire `<script setup>` block with:

```js
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/authStore.js';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const authStore = useAuthStore();

const displayName = computed(() => authStore.displayName ?? 'Schütze');
const firstName = computed(() => displayName.value.split(/[\s._@]/)[0]);
const userInitial = computed(() => firstName.value.charAt(0).toUpperCase());

const canManage = computed(() => authStore.hasPermission('MANAGE_RANGES'));

const handleLogout = () => {
  authStore.logout();
  router.push('/login');
};
```

In the `<template>`, make two changes:

1. The "Konto" button in the top bar — change from `@click="showAccount = true"` to `@click="router.push('/profil')"`:

```html
<button class="account-edit-btn" data-testid="open-account" @click="router.push('/profil')">Konto</button>
```

2. The "Mein Profil" tile — change from `@click="showAccount = true"` to `@click="router.push('/profil')"`:

```html
<button class="app-tile app-tile--available" data-testid="open-profile" @click="router.push('/profil')">
```

3. Remove the `<UsernameEditModal>` line at the bottom of the template:

```html
<!-- DELETE THIS LINE: -->
<UsernameEditModal v-if="showAccount" @close="showAccount = false" />
```

- [ ] **Step 2: Delete UsernameEditModal.vue**

```bash
rm "smart-ground-ui/src/components/UsernameEditModal.vue"
```

- [ ] **Step 3: Verify no remaining references to UsernameEditModal**

```bash
grep -r "UsernameEditModal\|showAccount\|updateOwnUsername" smart-ground-ui/src/
```

Expected: no output (zero matches)

- [ ] **Step 4: Run lint**

```bash
cd smart-ground-ui && npm run lint
```

Expected: no errors

- [ ] **Step 5: Run full test suite**

```bash
npm run test
```

Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
git add src/views/shooter/ShooterHomeView.vue
git rm src/components/UsernameEditModal.vue
git commit -m "[ui] Remove UsernameEditModal, navigate to /profil from ShooterHomeView"
```

---

## Self-Review

**Spec coverage:**
- ✅ `UsernameEditModal` removed
- ✅ "Mein Profil" tile navigates to `/profil`
- ✅ "Konto" button navigates to `/profil`
- ✅ `/profil` route added with `VIEW_REMOTE` permission and shooter layout
- ✅ Header: avatar initials, full name, username, back button
- ✅ Tab 1 Profil: vorname, nachname, username, geburtsdatum, geschlecht, sprache, biographie — all editable
- ✅ Tab 2 Kontakt: email, telefonnummer, adresse fields editable; E-Mail-Status read-only badge
- ✅ Tab 3 Mitgliedschaft: mitgliedsnummer, schiessLizenz, lizenz bis, lizenz-Status, mitglied seit — all read-only
- ✅ `authStore.updateProfile(data)` replaces `updateOwnUsername`
- ✅ `updateOwnUsername` fully removed
- ✅ Save per tab, success feedback (1.5s label change), inline error display
- ✅ Username validation reused from deleted modal

**Placeholder scan:** None found.

**Type consistency:** `updateProfile(data)` used consistently in authStore and view. `buildPayload` produces plain object — matches what `updateUser(id, data)` expects.

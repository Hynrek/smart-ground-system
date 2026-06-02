<template>
  <div class="users-view">
    <!-- Left: User List Panel -->
    <aside class="list-panel">
      <div class="list-header">
        <input
          v-model="search"
          data-testid="search-input"
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
            <h4 class="section-label">Rollen</h4>
            <div class="role-chips">
              <button
                v-for="role in userStore.availableRoles"
                :key="role.name"
                :class="['role-chip', { 'role-chip--active': hasRole(selectedUser.id, role.name) }]"
                :disabled="userStore.isLoading"
                :aria-pressed="hasRole(selectedUser.id, role.name)"
                :aria-label="`Rolle ${ROLE_LABELS[role.name] || role.name} ${hasRole(selectedUser.id, role.name) ? 'entfernen' : 'zuweisen'}`"
                @click="handleToggleRole(selectedUser.id, role.name)"
              >
                {{ ROLE_LABELS[role.name] || role.name }}
              </button>
            </div>
          </section>

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

onMounted(() => {
  userStore.loadUsers()
  userStore.loadAvailableRoles()
})

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

function hasRole(userId, roleName) {
  return (userStore.userRolesMap[userId] ?? []).some((r) => r.roleName === roleName)
}

async function handleToggleRole(userId, roleName) {
  try {
    await userStore.toggleRole(userId, roleName)
  } catch {
    // userStore.error is already set; the error banner surfaces it
  }
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
  border-right: 1px solid var(--sg-border);
  display: flex;
  flex-direction: column;
}

.list-header {
  padding: 0.75rem;
  border-bottom: 1px solid var(--sg-border);
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.search-input {
  flex: 1;
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--sg-border-input);
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
  color: var(--sg-text-faint);
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
  background: var(--sg-bg-panel);
}

.user-item.selected {
  background: var(--sg-color-info-bg);
  border-left-color: var(--sg-accent-hover);
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
  color: var(--sg-text-faint);
  font-size: 0.95rem;
}

.detail-header {
  padding: 0.85rem 1.25rem;
  border-bottom: 1px solid var(--sg-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--sg-bg-panel);
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
  color: var(--sg-text-muted);
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
  color: var(--sg-text-faint);
}

/* ── Badges ── */
.badge {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 0.75rem;
  font-weight: 500;
}

.badge-green  { background: var(--sg-color-success-bg); color: var(--sg-color-success-text); }
.badge-gray   { background: var(--sg-color-neutral-bg); color: var(--sg-color-neutral-text); }
.badge-blue   { background: var(--sg-color-info-bg); color: var(--sg-color-info-text); }
.badge-purple { background: var(--sg-color-purple-bg); color: var(--sg-color-purple-text); }
.badge-yellow { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); }

/* ── Warnings ── */
.text-warning { color: var(--sg-color-warning); font-weight: 500; }

.error-banner {
  background: var(--sg-color-danger-bg);
  color: var(--sg-color-danger-text);
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
.btn-primary  { background: var(--sg-accent-hover); color: #fff; }
.btn-secondary { background: var(--sg-bg-panel); color: var(--sg-text-muted); }
.btn-danger   { background: var(--sg-color-danger); color: #fff; }

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
  background: var(--sg-bg-card);
  border-radius: 8px;
  padding: 1.75rem;
  max-width: 420px;
  width: 90%;
  box-shadow: var(--sg-shadow-lg);
}

.confirm-modal h3 { margin: 0 0 0.5rem; }
.confirm-modal p  { color: var(--sg-text-muted); margin: 0 0 1.5rem; font-size: 0.9rem; }

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

/* ── Role chips ── */
.role-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.role-chip {
  padding: 0.3rem 0.9rem;
  border-radius: 20px;
  border: 1.5px solid var(--sg-border-input);
  background: var(--sg-bg-panel);
  color: var(--sg-text-muted);
  font-size: 0.82rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.role-chip:hover:not(:disabled) {
  border-color: var(--sg-accent-hover);
  color: var(--sg-accent-hover);
  background: var(--sg-color-info-bg);
}

.role-chip--active {
  background: var(--sg-accent-hover);
  border-color: var(--sg-accent-hover);
  color: #fff;
}

.role-chip--active:hover:not(:disabled) {
  background: var(--sg-accent);
  border-color: var(--sg-accent);
  color: #fff;
}

.role-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .users-view { flex-direction: column; }
  .list-panel { width: 100%; border-right: none; border-bottom: 1px solid var(--sg-border); max-height: 240px; }
  .detail-grid { grid-template-columns: 1fr 1fr; }
}
</style>

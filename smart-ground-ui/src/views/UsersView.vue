<template>
  <div class="users-view">
    <h1>Benutzerverwaltung</h1>

    <!-- Error Banner -->
    <div v-if="userStore.error" class="error-banner">
      {{ userStore.error }}
    </div>

    <!-- Success Banner -->
    <div v-if="successMessage" class="success-banner">
      {{ successMessage }}
    </div>

    <!-- Create User Card -->
    <div class="create-card">
      <h2>Neuer Benutzer</h2>
      <div class="form-group">
        <input
          v-model="newUser.vorname"
          type="text"
          placeholder="Vorname"
          class="form-input"
          :disabled="userStore.isLoading"
        />
      </div>
      <div class="form-group">
        <input
          v-model="newUser.nachname"
          type="text"
          placeholder="Nachname"
          class="form-input"
          :disabled="userStore.isLoading"
        />
      </div>
      <div class="form-group">
        <input
          v-model="newUser.email"
          type="email"
          placeholder="E-Mail"
          class="form-input"
          :disabled="userStore.isLoading"
        />
      </div>
      <div class="form-group">
        <input
          v-model="newUser.password"
          type="password"
          placeholder="Passwort"
          class="form-input"
          :disabled="userStore.isLoading"
        />
      </div>
      <div class="form-actions">
        <button
          class="btn btn-primary"
          :disabled="!newUser.email || !newUser.password || !newUser.vorname || !newUser.nachname || userStore.isLoading"
          @click="handleCreateUser"
        >
          {{ userStore.isLoading ? 'Wird erstellt...' : 'Erstellen' }}
        </button>
      </div>
    </div>

    <!-- Users List -->
    <div class="users-grid">
      <div v-if="userStore.users.length === 0" class="empty-state">
        <p>Keine Benutzer gefunden</p>
      </div>

      <div v-for="user in userStore.users" :key="user.id" class="user-card">
        <div class="user-info">
          <h3>{{ user.fullName }}</h3>
          <div class="meta">
            <span class="status-badge" :class="user.status?.toLowerCase()">
              {{ user.email }}
            </span>
            <span class="created-date">
              {{ formatDate(user.erstelltAm) }}
            </span>
          </div>
        </div>

        <div class="user-actions">
          <button
            class="btn btn-danger btn-sm"
            :disabled="userStore.isLoading"
            @click="openDeleteConfirm(user)"
          >
            Löschen
          </button>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <div v-if="deletingUser" class="modal-overlay" @click="closeDeleteConfirm">
      <div class="modal" @click.stop>
        <h2>Benutzer löschen?</h2>
        <p>Möchten Sie den Benutzer "{{ deletingUser.username }}" wirklich löschen?</p>
        <div class="modal-actions">
          <button class="btn btn-danger" :disabled="userStore.isLoading" @click="handleDeleteUser">
            Löschen
          </button>
          <button class="btn btn-secondary" :disabled="userStore.isLoading" @click="closeDeleteConfirm">
            Abbrechen
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useUserStore } from '../stores/userStore.js'

const userStore = useUserStore()
const newUser = ref({ vorname: '', nachname: '', email: '', password: '' })
const deletingUser = ref(null)
const successMessage = ref(null)

onMounted(async () => {
  try {
    await userStore.loadUsers()
  } catch (err) {
    // Error is already in userStore.error
  }
})

const formatDate = (epochSeconds) => {
  return new Date(epochSeconds * 1000).toLocaleDateString('de-DE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

const handleCreateUser = async () => {
  if (!newUser.value.email || !newUser.value.password || !newUser.value.vorname || !newUser.value.nachname) {
    userStore.error = 'Alle Felder sind erforderlich'
    return
  }

  try {
    await userStore.createUser(newUser.value)
    newUser.value = { vorname: '', nachname: '', email: '', password: '' }
    successMessage.value = 'Benutzer erfolgreich erstellt'
    setTimeout(() => {
      successMessage.value = null
    }, 3000)
  } catch (err) {
    // Error is already in userStore.error
  }
}

const openDeleteConfirm = (user) => {
  deletingUser.value = user
}

const closeDeleteConfirm = () => {
  deletingUser.value = null
}

const handleDeleteUser = async () => {
  if (!deletingUser.value) return
  try {
    await userStore.deleteUser(deletingUser.value.id)
    successMessage.value = 'Benutzer erfolgreich gelöscht'
    closeDeleteConfirm()
    setTimeout(() => {
      successMessage.value = null
    }, 3000)
  } catch (err) {
    // Error is already in userStore.error
  }
}
</script>

<style scoped>
.users-view {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
}

h1 {
  margin-bottom: 2rem;
  color: #333;
}

.error-banner {
  background-color: #fee;
  color: #c33;
  padding: 1rem;
  border-radius: 4px;
  margin-bottom: 1rem;
}

.success-banner {
  background-color: #efe;
  color: #3c3;
  padding: 1rem;
  border-radius: 4px;
  margin-bottom: 1rem;
}

.create-card {
  background: white;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 2rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.create-card h2 {
  margin: 0 0 1rem 0;
  font-size: 1.1rem;
  color: #333;
}

.form-group {
  margin-bottom: 1rem;
}

.form-input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.form-input:disabled {
  background-color: #f5f5f5;
  color: #999;
}

.form-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 1.5rem;
}

.users-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.5rem;
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 3rem;
  color: #999;
}

.user-card {
  background: white;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.user-info h3 {
  margin: 0 0 0.5rem 0;
  color: #333;
}

.meta {
  display: flex;
  gap: 1rem;
  align-items: center;
  font-size: 0.9rem;
  color: #666;
}

.status-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: 500;
  background-color: #ddd;
  color: #333;
}

.status-badge.active {
  background-color: #87ceeb;
  color: white;
}

.created-date {
  color: #999;
  font-size: 0.85rem;
}

.user-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 1rem;
  flex-wrap: wrap;
}

.admin-note {
  color: #999;
  font-size: 0.9rem;
  align-self: center;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background-color: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #0056b3;
}

.btn-secondary {
  background-color: #6c757d;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background-color: #545b62;
}

.btn-danger {
  background-color: #dc3545;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background-color: #c82333;
}

.btn-sm {
  padding: 0.25rem 0.75rem;
  font-size: 0.85rem;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: white;
  border-radius: 8px;
  padding: 2rem;
  max-width: 400px;
  width: 90%;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.modal h2 {
  margin: 0 0 0.5rem 0;
  color: #333;
}

.modal p {
  margin: 0.5rem 0 1.5rem 0;
  color: #666;
}

.modal-actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 2rem;
}

@media (max-width: 768px) {
  .users-view {
    padding: 1rem;
  }

  .users-grid {
    grid-template-columns: 1fr;
  }

  .user-actions {
    flex-direction: column;
  }

  .btn {
    width: 100%;
  }
}
</style>

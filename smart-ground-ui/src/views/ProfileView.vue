<template>
  <div class="profile-view">
    <div class="profile-container">
      <h1>Mein Profil</h1>

      <!-- Error Banner -->
      <div v-if="userStore.error" class="error-banner">
        {{ userStore.error }}
      </div>

      <!-- Success Banner -->
      <div v-if="successMessage" class="success-banner">
        {{ successMessage }}
      </div>

      <!-- User Info Card -->
      <div v-if="userStore.currentUser" class="info-card">
        <h2>Benutzerdaten</h2>
        <div class="info-row">
          <label>Benutzername</label>
          <span>{{ userStore.currentUser.username }}</span>
        </div>
        <div class="info-row">
          <label>Rolle</label>
          <span class="role-badge" :class="userStore.currentUser.role.toLowerCase()">
            {{ userStore.currentUser.role === 'ADMIN' ? 'Administrator' : 'Schütze' }}
          </span>
        </div>
        <div class="info-row">
          <label>Erstellt am</label>
          <span>{{ formatDate(userStore.currentUser.createdAt) }}</span>
        </div>
      </div>

      <!-- Password Change Card -->
      <div class="password-card">
        <h2>Passwort ändern</h2>
        <div class="form-group">
          <label for="old-password">Aktuelles Passwort</label>
          <input
            id="old-password"
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="Aktuelles Passwort"
            class="form-input"
            :disabled="userStore.isLoading"
          />
        </div>
        <div class="form-group">
          <label for="new-password">Neues Passwort</label>
          <input
            id="new-password"
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="Neues Passwort"
            class="form-input"
            :disabled="userStore.isLoading"
          />
        </div>
        <div class="form-group">
          <label for="confirm-password">Passwort wiederholen</label>
          <input
            id="confirm-password"
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="Passwort wiederholen"
            class="form-input"
            :disabled="userStore.isLoading"
          />
        </div>
        <div class="form-actions">
          <button
            class="btn btn-primary"
            :disabled="!isPasswordFormValid || userStore.isLoading"
            @click="handleChangePassword"
          >
            {{ userStore.isLoading ? 'Wird geändert...' : 'Passwort ändern' }}
          </button>
          <button class="btn btn-secondary" :disabled="userStore.isLoading" @click="resetPasswordForm">
            Abbrechen
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, computed } from 'vue'
import { useUserStore } from '../stores/userStore.js'

const userStore = useUserStore()
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const successMessage = ref(null)

const isPasswordFormValid = computed(() => {
  return (
    passwordForm.value.oldPassword &&
    passwordForm.value.newPassword &&
    passwordForm.value.confirmPassword &&
    passwordForm.value.newPassword === passwordForm.value.confirmPassword
  )
})

onMounted(async () => {
  try {
    await userStore.getCurrentUser()
  } catch (err) {
    // Error is already in userStore.error
  }
})

const formatDate = (date) => {
  return new Date(date).toLocaleDateString('de-DE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const handleChangePassword = async () => {
  if (!isPasswordFormValid.value) {
    userStore.error = 'Alle Felder müssen ausgefüllt sein und die Passwörter müssen übereinstimmen'
    return
  }

  try {
    await userStore.changePassword(passwordForm.value.oldPassword, passwordForm.value.newPassword)
    resetPasswordForm()
    successMessage.value = 'Passwort erfolgreich geändert'
    setTimeout(() => {
      successMessage.value = null
    }, 3000)
  } catch (err) {
    // Error is already in userStore.error
  }
}

const resetPasswordForm = () => {
  passwordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  }
}
</script>

<style scoped>
.profile-view {
  padding: 2rem;
  max-width: 600px;
  margin: 0 auto;
}

.profile-container {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

h1 {
  margin: 0 0 1rem 0;
  color: #333;
}

.error-banner {
  background-color: #fee;
  color: #c33;
  padding: 1rem;
  border-radius: 4px;
}

.success-banner {
  background-color: #efe;
  color: #3c3;
  padding: 1rem;
  border-radius: 4px;
}

.info-card,
.password-card {
  background: white;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 2rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.info-card h2,
.password-card h2 {
  margin: 0 0 1.5rem 0;
  color: #333;
  font-size: 1.1rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 0;
  border-bottom: 1px solid #f0f0f0;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row label {
  color: #666;
  font-weight: 500;
}

.info-row span {
  color: #333;
}

.role-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: 500;
  background-color: #ddd;
  color: #333;
}

.role-badge.admin {
  background-color: #ffd700;
  color: #333;
}

.role-badge.shooter {
  background-color: #87ceeb;
  color: white;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: #333;
  font-weight: 500;
}

.form-input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  transition: border-color 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.1);
}

.form-input:disabled {
  background-color: #f5f5f5;
  color: #999;
}

.form-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 2rem;
}

.btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1rem;
  transition: all 0.2s;
  flex: 1;
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

@media (max-width: 768px) {
  .profile-view {
    padding: 1rem;
  }

  .info-card,
  .password-card {
    padding: 1.5rem;
  }
}
</style>

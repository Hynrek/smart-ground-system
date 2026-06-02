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
import { useUserStore } from '@/stores/userStore.js'

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
  color: var(--sg-brand);
}

.error-banner {
  background-color: var(--sg-color-danger-bg);
  color: var(--sg-color-danger-text);
  padding: 1rem;
  border-radius: 4px;
}

.success-banner {
  background-color: var(--sg-color-success-bg);
  color: var(--sg-color-success-text);
  padding: 1rem;
  border-radius: 4px;
}

.info-card,
.password-card {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 8px;
  padding: 2rem;
  box-shadow: var(--sg-shadow-sm);
}

.info-card h2,
.password-card h2 {
  margin: 0 0 1.5rem 0;
  color: var(--sg-brand);
  font-size: 1.1rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 0;
  border-bottom: 1px solid var(--sg-border);
}

.info-row:last-child {
  border-bottom: none;
}

.info-row label {
  color: var(--sg-text-muted);
  font-weight: 500;
}

.info-row span {
  color: var(--sg-text-primary);
}

.role-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: 500;
  background-color: var(--sg-color-neutral-bg);
  color: var(--sg-color-neutral-text);
}

.role-badge.admin {
  background-color: var(--sg-color-warning-bg);
  color: var(--sg-color-warning-text);
}

.role-badge.shooter {
  background-color: var(--sg-color-info-bg);
  color: var(--sg-color-info-text);
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: var(--sg-text-primary);
  font-weight: 500;
}

.form-input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid var(--sg-border-input);
  border-radius: 4px;
  font-size: 1rem;
  transition: border-color 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: var(--sg-accent);
  box-shadow: 0 0 0 3px var(--sg-accent-tint);
}

.form-input:disabled {
  background-color: var(--sg-bg-panel);
  color: var(--sg-text-faint);
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
  background-color: var(--sg-accent-hover);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background-color: var(--sg-accent);
}

.btn-secondary {
  background-color: var(--sg-text-muted);
  color: #fff;
}

.btn-secondary:hover:not(:disabled) {
  background-color: var(--sg-brand);
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

<template>
  <div class="shooter-profile">
    <!-- Header -->
    <div class="profile-header">
      <button class="back-btn" @click="goBack">
        <Icons icon="chevronLeft" :size="20" color="rgba(255,255,255,0.8)" />
      </button>
      <h1 class="profile-title">Mein Profil</h1>
    </div>

    <!-- Error Banner -->
    <Transition name="slide">
      <div v-if="userStore.error" class="error-banner">
        {{ userStore.error }}
      </div>
    </Transition>

    <!-- Success Banner -->
    <Transition name="slide">
      <div v-if="successMessage" class="success-banner">
        {{ successMessage }}
      </div>
    </Transition>

    <!-- User Info Card -->
    <div v-if="userStore.currentUser" class="info-card">
      <h2>Benutzerdaten</h2>
      <div class="info-row">
        <span class="info-label">Benutzername</span>
        <span class="info-value">{{ userStore.currentUser.username }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">Rolle</span>
        <span class="role-badge">Schütze</span>
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
          placeholder="Geben Sie Ihr aktuelles Passwort ein"
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
          placeholder="Geben Sie Ihr neues Passwort ein"
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
          placeholder="Wiederholen Sie Ihr neues Passwort"
          class="form-input"
          :class="{ 'form-input--error': passwordMismatch }"
          :disabled="userStore.isLoading"
        />
        <div v-if="passwordMismatch" class="form-error">
          ⚠ Passwörter stimmen nicht überein
        </div>
      </div>

      <div class="form-actions">
        <button
          class="btn btn-primary"
          :disabled="!isPasswordFormValid || userStore.isLoading"
          @click="handleChangePassword"
        >
          {{ userStore.isLoading ? 'Wird geändert...' : 'Passwort ändern' }}
        </button>
        <button
          class="btn btn-secondary"
          :disabled="userStore.isLoading"
          @click="resetPasswordForm"
        >
          Abbrechen
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/userStore.js';
import Icons from '@/components/Icons.vue';

const router = useRouter();
const userStore = useUserStore();

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});
const successMessage = ref(null);

const isPasswordFormValid = computed(() => {
  return (
    passwordForm.value.oldPassword &&
    passwordForm.value.newPassword &&
    passwordForm.value.confirmPassword &&
    passwordForm.value.newPassword === passwordForm.value.confirmPassword
  );
});

const passwordMismatch = computed(() => {
  return (
    passwordForm.value.newPassword &&
    passwordForm.value.confirmPassword &&
    passwordForm.value.newPassword !== passwordForm.value.confirmPassword
  );
});

onMounted(async () => {
  try {
    await userStore.getCurrentUser();
  } catch (err) {
    // Error is handled by userStore
  }
});

const handleChangePassword = async () => {
  if (!isPasswordFormValid.value) {
    userStore.error = 'Bitte füllen Sie alle Felder korrekt aus';
    return;
  }

  try {
    await userStore.changePassword(
      passwordForm.value.oldPassword,
      passwordForm.value.newPassword
    );
    resetPasswordForm();
    successMessage.value = 'Passwort erfolgreich geändert';
    setTimeout(() => {
      successMessage.value = null;
    }, 3000);
  } catch (err) {
    // Error is already in userStore.error
  }
};

const resetPasswordForm = () => {
  passwordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  };
};

const goBack = () => {
  router.back();
};
</script>

<style scoped>
.shooter-profile {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: env(safe-area-inset-top, 0) 16px calc(env(safe-area-inset-bottom, 0px) + 16px);
  min-height: 0;
  gap: 16px;
}

/* ── Header ──────────────────────────────────────── */
.profile-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}

.back-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 8px;
  margin-left: -8px;
  display: flex;
  align-items: center;
  border-radius: 10px;
  flex-shrink: 0;
  transition: background 0.15s;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.07);
}

.profile-title {
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
  margin: 0;
  letter-spacing: -0.3px;
}

/* ── Banners ─────────────────────────────────────── */
.error-banner {
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(252, 129, 129, 0.12);
  border: 1px solid rgba(252, 129, 129, 0.35);
  color: #fc8181;
  font-size: 14px;
  font-weight: 500;
}

.success-banner {
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(72, 187, 120, 0.12);
  border: 1px solid rgba(72, 187, 120, 0.35);
  color: #68d391;
  font-size: 14px;
  font-weight: 500;
}

.slide-enter-active,
.slide-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}

.slide-enter-from {
  opacity: 0;
  transform: translateY(-4px);
}

.slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ── Cards ───────────────────────────────────────── */
.info-card,
.password-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  padding: 20px;
}

.info-card h2,
.password-card h2 {
  font-size: 16px;
  font-weight: 700;
  color: #ffffff;
  margin: 0 0 16px 0;
  letter-spacing: -0.2px;
}

/* ── Info Card ───────────────────────────────────── */
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-value {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
}

.role-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 12px;
  background: rgba(79, 195, 247, 0.15);
  border: 1px solid rgba(79, 195, 247, 0.3);
  color: #4fc3f7;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

/* ── Form ────────────────────────────────────────── */
.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: #ffffff;
  font-size: 14px;
  font-family: inherit;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.form-input::placeholder {
  color: rgba(255, 255, 255, 0.3);
}

.form-input:focus {
  outline: none;
  border-color: rgba(79, 195, 247, 0.5);
  box-shadow: 0 0 0 3px rgba(79, 195, 247, 0.15);
}

.form-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.form-input--error {
  border-color: rgba(252, 129, 129, 0.5);
  box-shadow: 0 0 0 3px rgba(252, 129, 129, 0.1);
}

.form-error {
  margin-top: 6px;
  font-size: 12px;
  color: #fc8181;
  font-weight: 500;
}

/* ── Buttons ─────────────────────────────────────── */
.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}

.btn {
  flex: 1;
  padding: 11px 16px;
  border: none;
  border-radius: 10px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: rgba(79, 195, 247, 0.18);
  border: 1px solid rgba(79, 195, 247, 0.4);
  color: #4fc3f7;
}

.btn-primary:hover:not(:disabled) {
  background: rgba(79, 195, 247, 0.25);
  border-color: rgba(79, 195, 247, 0.6);
  box-shadow: 0 0 12px rgba(79, 195, 247, 0.2);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.6);
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.8);
}

/* ── Responsive ──────────────────────────────────── */
@media (max-width: 600px) {
  .shooter-profile {
    padding: env(safe-area-inset-top, 0) 12px calc(env(safe-area-inset-bottom, 0px) + 12px);
    gap: 12px;
  }

  .info-card,
  .password-card {
    padding: 16px;
  }

  .profile-title {
    font-size: 20px;
  }
}
</style>

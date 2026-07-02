<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/authStore';
import Button from '@/components/Button.vue';
import Logo from '@/components/Logo.vue';

const router = useRouter();
const authStore = useAuthStore();

const username = ref('');
const password = ref('');
const errorMessage = ref('');

const handleLogin = async () => {
  errorMessage.value = '';
  try {
    await authStore.login(username.value, password.value);
    router.push('/welcome');
  } catch (err) {
    errorMessage.value = err.message;
  }
};

const handleKeydown = (e) => {
  if (e.key === 'Enter') handleLogin();
};
</script>

<template>
  <div class="login-container">
    <div class="login-box">
      <Logo variant="reversed" :size="100" class="login-logo" />

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="username">E-Mail oder Benutzername</label>
          <input
            id="username"
            v-model="username"
            type="text"
            placeholder="E-Mail oder Benutzername eingeben"
            required
            @keydown="handleKeydown"
          />
        </div>

        <div class="form-group">
          <label for="password">Password</label>
          <input
            id="password"
            v-model="password"
            type="password"
            placeholder="Enter your password"
            required
            @keydown="handleKeydown"
          />
        </div>

        <div v-if="errorMessage" class="error-message">
          {{ errorMessage }}
        </div>

        <Button
          type="submit"
          :disabled="authStore.isLoading"
          class="login-button"
        >
          {{ authStore.isLoading ? 'Logging in...' : 'Login' }}
        </Button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #1a1a2e;
  font-family: system-ui, -apple-system, sans-serif;
}

.login-box {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px;
  padding: 40px;
  width: 100%;
  max-width: 460px;
  text-align: center;
  color: #fff;
}

.login-logo {
  margin: 0 auto 30px;
  color: #fff;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  text-align: left;
}

label {
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.7);
}

input {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
  color: #fff;
}

input::placeholder {
  color: rgba(255, 255, 255, 0.3);
}

input:focus {
  outline: none;
  border-color: var(--sg-accent);
  box-shadow: 0 0 0 3px rgba(79, 195, 247, 0.15);
}

.error-message {
  padding: 10px;
  background: rgba(224, 82, 82, 0.15);
  color: var(--sg-color-danger-bg);
  border-radius: 4px;
  font-size: 13px;
}

.login-button {
  margin-top: 8px;
  width: 100%;
  background: var(--sg-accent);
  color: #1a1a2e;
}

.login-button:hover:not(:disabled) {
  background: var(--sg-accent-hover);
}
</style>

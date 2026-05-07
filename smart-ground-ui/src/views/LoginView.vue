<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/authStore';
import Button from '@/components/Button.vue';

const router = useRouter();
const authStore = useAuthStore();

const username = ref('');
const password = ref('');
const errorMessage = ref('');

const handleLogin = async () => {
  errorMessage.value = '';
  try {
    await authStore.login(username.value, password.value);
    router.push('/ranges');
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
      <h1>Smart Ground</h1>
      <p class="subtitle">Management System</p>

      <form class="login-form" @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="username">Username</label>
          <input
            id="username"
            v-model="username"
            type="text"
            placeholder="Enter your username"
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  font-family: system-ui, -apple-system, sans-serif;
}

.login-box {
  background: white;
  border-radius: 8px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  padding: 40px;
  width: 100%;
  max-width: 400px;
  text-align: center;
}

h1 {
  margin: 0 0 8px 0;
  color: #1a1a2e;
  font-size: 28px;
  font-weight: 600;
}

.subtitle {
  margin: 0 0 30px 0;
  color: #666;
  font-size: 14px;
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
  color: #1a1a2e;
}

input {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
}

input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.error-message {
  padding: 10px;
  background: #fee;
  color: #c33;
  border-radius: 4px;
  font-size: 13px;
}

.login-button {
  margin-top: 8px;
  width: 100%;
}
</style>

<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/authStore';
import { defaultHome } from '@/router';
import Logo from '@/components/Logo.vue';

const router = useRouter();
const authStore = useAuthStore();

const REDIRECT_DELAY_MS = 2500;

const proceed = () => {
  router.replace(defaultHome(authStore));
};

let timeoutId;
onMounted(() => {
  timeoutId = setTimeout(proceed, REDIRECT_DELAY_MS);
});
</script>

<template>
  <div class="welcome-container" @click="proceed">
    <div class="welcome-box">
      <Logo variant="reversed" :size="100" class="welcome-logo" />
      <h1 class="welcome-title">Willkommen bei Smart Ground</h1>
      <p v-if="authStore.displayName" class="welcome-subtitle">
        Schön, dich zu sehen, {{ authStore.displayName }}!
      </p>
      <div class="welcome-progress"><span class="welcome-progress-bar" /></div>
    </div>
  </div>
</template>

<style scoped>
.welcome-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #1a1a2e;
  font-family: system-ui, -apple-system, sans-serif;
}

.welcome-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 40px;
  color: #fff;
  animation: welcome-fade-in 0.4s ease-out;
}

.welcome-logo {
  margin-bottom: 28px;
  color: #fff;
}

.welcome-title {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: #fff;
}

.welcome-subtitle {
  margin: 0;
  font-size: 15px;
  color: rgba(255, 255, 255, 0.45);
}

.welcome-progress {
  margin-top: 32px;
  width: 120px;
  height: 3px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.1);
  overflow: hidden;
}

.welcome-progress-bar {
  display: block;
  height: 100%;
  background: var(--sg-accent);
  border-radius: 2px;
  animation: welcome-progress 2.5s ease-in-out forwards;
}

@keyframes welcome-fade-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes welcome-progress {
  from {
    width: 0%;
  }
  to {
    width: 100%;
  }
}
</style>

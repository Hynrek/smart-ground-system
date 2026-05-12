<script setup>
import { computed, onMounted } from 'vue';
import MainLayout from './layouts/MainLayout.vue';
import ShooterLayout from './layouts/ShooterLayout.vue';
import { useRoute, useRouter } from 'vue-router';
import { useAppStore } from './stores/appStore.js';
import { useAuthStore } from './stores/authStore.js';

const route = useRoute();
const router = useRouter();
const appStore = useAppStore();
const authStore = useAuthStore();

const navMap = {
  '/ranges': 'ranges',
  '/smartboxes': 'smartboxes',
  '/device-type-groups': 'device-type-groups',
  '/competition': 'competition',
  '/programme': 'programme',
  '/users': 'users',
  '/profile': 'profile',
};

const activeNav = computed(() => navMap[route.path] || 'ranges');
const layout = computed(() => route.meta.layout ?? 'admin');

const handleNav = (navId) => {
  const routeMap = {
    ranges: '/ranges',
    smartboxes: '/smartboxes',
    'device-type-groups': '/device-type-groups',
    templates: '/deviceTypes',
    users: '/users',
  };
  router.push(routeMap[navId] || '/ranges');
};

onMounted(() => {
  if (authStore.isAuthenticated()) {
    appStore.initializeStore();
  }
});
</script>

<template>
  <!-- No layout: login page or legacy full-screen Werfer remote -->
  <router-view v-if="layout === 'legacy-fullscreen' || route.path === '/login'" />

  <!-- Shooter layout: mobile-first, no sidebar -->
  <ShooterLayout v-else-if="layout === 'shooter'">
    <router-view />
  </ShooterLayout>

  <!-- Admin layout: sidebar + main content -->
  <MainLayout v-else :active-nav="activeNav" @nav="handleNav">
    <router-view />
  </MainLayout>
</template>

<style scoped>
:deep(body) {
  font-family: system-ui, -apple-system, sans-serif;
  background: #f7f8fc;
  color: #1a1a2e;
  margin: 0;
  padding: 0;
}

:deep(html),
:deep(body),
:deep(#app) {
  height: 100%;
  margin: 0;
  padding: 0;
}
</style>

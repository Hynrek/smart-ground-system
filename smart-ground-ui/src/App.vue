<script setup>
import { computed } from 'vue';
import MainLayout from './layouts/MainLayout.vue';
import ShooterLayout from './layouts/ShooterLayout.vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

const navMap = {
  '/ranges': 'ranges',
  '/smartboxes': 'smartboxes',
  '/device-type-groups': 'device-type-groups',
  '/admin/wettkampf': 'competition',
  '/passen': 'passen',
  '/users': 'users',
  '/profile': 'profile',
  '/testing': 'testing',
};

const activeNav = computed(() => {
  if (route.path.startsWith('/admin/wettkampf')) return 'competition'
  return navMap[route.path] || 'ranges'
});
const layout = computed(() => route.meta.layout ?? 'admin');

const handleNav = (navId) => {
  const routeMap = {
    ranges: '/ranges',
    smartboxes: '/smartboxes',
    'device-type-groups': '/device-type-groups',
    competition: '/admin/wettkampf',
    templates: '/deviceTypes',
    users: '/users',
  };
  router.push(routeMap[navId] || '/ranges');
};
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
  background: var(--sg-bg-page);
  color: var(--sg-text-primary);
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

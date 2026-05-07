<template>
  <div class="main-layout">
    <Sidebar :active-nav="activeNav" @nav="onNav" />
    <main class="layout-main">
      <slot />
    </main>
  </div>
</template>

<script setup>
import Sidebar from '../components/Sidebar.vue';

const props = defineProps({
  activeNav: {
    type: String,
    default: 'ranges',
  },
});

const emit = defineEmits(['nav']);

const onNav = (navId) => {
  emit('nav', navId);
};
</script>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: #f7f8fc;
}

.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
</style>

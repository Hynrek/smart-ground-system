<template>
  <div class="shooter-layout">
    <slot />
  </div>
</template>

<style scoped>
.shooter-layout {
  min-height: 100dvh;
  background: #1a1a2e;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
</style>

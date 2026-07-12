<template>
  <span class="badge" :class="`badge-${color}`">
    <slot />
  </span>
</template>

<script setup>
defineProps({
  color: {
    type: String,
    default: 'gray',
    validator: (v) => ['blue', 'green', 'red', 'gray', 'warn'].includes(v),
  },
});
</script>

<style scoped>
.badge {
  display: inline-block;
  border-radius: 99px;
  padding: 2px 9px;
  font-size: 11.5px;
  font-weight: 600;
}

.badge-blue {
  background: var(--sg-color-info-bg);
  color: var(--sg-color-info-text);
}

.badge-green {
  background: var(--sg-color-success-bg);
  color: var(--sg-color-success-text);
}

.badge-red {
  background: var(--sg-color-danger-bg);
  color: var(--sg-color-danger-text);
}

.badge-gray {
  background: var(--sg-color-neutral-bg);
  color: var(--sg-color-neutral-text);
}

.badge-warn {
  background: var(--sg-color-warning-bg);
  color: var(--sg-color-warning-text);
}
</style>

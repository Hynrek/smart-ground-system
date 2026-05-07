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
  background: #dbeffe;
  color: #1a5fa0;
}

.badge-green {
  background: #d4f5e2;
  color: #1e6640;
}

.badge-red {
  background: #fde0e0;
  color: #9b2c2c;
}

.badge-gray {
  background: #e8edf0;
  color: #555;
}

.badge-warn {
  background: #fff3d4;
  color: #8a5a00;
}
</style>

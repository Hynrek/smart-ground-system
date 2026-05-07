<template>
  <span
    :style="dotStyle"
    class="status-dot"
    :aria-label="`Status: ${statusLabel}`"
    role="img"
  />
</template>

<script setup>
import { computed } from 'vue';
import { STATUS_COLORS } from '../constants/deviceTypes.js';

const props = defineProps({
  status: {
    type: String,
    default: 'online',
  },
});

const dotStyle = computed(() => ({
  display: 'inline-block',
  width: '7px',
  height: '7px',
  borderRadius: '50%',
  background: STATUS_COLORS[props.status] || '#ccc',
  flexShrink: 0,
}));

const statusLabel = computed(() => {
  const labels = {
    online: 'Online',
    offline: 'Offline',
    error: 'Error',
    warning: 'Warning',
  };
  return labels[props.status] || props.status;
});
</script>

<style scoped>
.status-dot {
  flex-shrink: 0;
}
</style>

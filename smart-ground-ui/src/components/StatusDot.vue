<template>
  <span
    class="status-dot"
    :class="`dot-${status}`"
    :aria-label="`Status: ${statusLabel}`"
    role="img"
  />
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  status: {
    type: String,
    default: 'online',
  },
});

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
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-online {
  background: var(--sg-color-success);
}

.dot-offline {
  background: var(--sg-color-danger);
}

.dot-warning {
  background: var(--sg-color-warning);
}

.dot-error {
  background: var(--sg-color-danger);
}
</style>

<template>
  <span class="type-chip" :style="chipStyle">
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue';
import { DEVICE_COLORS, DIRECTION_COLORS } from '../constants/deviceTypes.js';

const props = defineProps({
  device: {
    type: String,
    default: null,
  },
  direction: {
    type: String,
    default: null,
  },
  type: {
    type: String,
    default: null,
  },
});

const label = computed(() => {
  if (props.device && props.direction) {
    return `${props.device} / ${props.direction}`;
  }
  return props.type || 'Unknown';
});

const chipStyle = computed(() => {
  let color = { bg: '#e8edf0', text: '#555' };

  if (props.device) {
    color = DEVICE_COLORS[props.device] || color;
  } else if (props.type) {
    color = DIRECTION_COLORS[props.type] || color;
  }

  return {
    background: color.bg,
    color: color.text,
  };
});
</script>

<style scoped>
.type-chip {
  display: inline-block;
  border-radius: 5px;
  padding: 2px 8px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.2px;
}
</style>

<template>
  <span class="type-chip" :class="chipClass">
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue';

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

const chipClass = computed(() => {
  if (props.device) {
    return `chip-${props.device.toLowerCase()}`;
  }
  if (props.type) {
    return `chip-${props.type.toLowerCase()}`;
  }
  return 'chip-default';
});
</script>

<style scoped>
/* Neutral base — chip classes are derived from arbitrary device/type names
   (e.g. chip-wurfmaschine), so unknown types must still render as a chip. */
.type-chip {
  display: inline-block;
  border-radius: 5px;
  padding: 2px 8px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.2px;
  background: var(--sg-color-neutral-bg);
  color: var(--sg-color-neutral-text);
}

.chip-wurfmaschine {
  background: var(--sg-color-info-bg);
  color: var(--sg-color-info-text);
}

.chip-gpio {
  background: var(--sg-color-info-bg);
  color: var(--sg-color-info-text);
}

.chip-led {
  background: var(--sg-color-purple-bg);
  color: var(--sg-color-purple-text);
}

.chip-input {
  background: var(--sg-color-success-bg);
  color: var(--sg-color-success-text);
}

.chip-output {
  background: var(--sg-color-warning-bg);
  color: var(--sg-color-warning-text);
}

.chip-default {
  background: var(--sg-color-neutral-bg);
  color: var(--sg-color-neutral-text);
}
</style>

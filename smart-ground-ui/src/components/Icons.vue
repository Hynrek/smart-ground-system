<template>
  <svg
    :width="size"
    :height="size"
    :viewBox="`0 0 ${viewBoxSize} ${viewBoxSize}`"
    :fill="fill"
    :stroke="color"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    v-bind="$attrs"
  >
    <path :d="iconPath" />
  </svg>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  icon: {
    type: String,
    required: true,
  },
  size: {
    type: Number,
    default: 16,
  },
  color: {
    type: String,
    default: 'currentColor',
  },
  fill: {
    type: String,
    default: 'none',
  },
  strokeWidth: {
    type: Number,
    default: 1.6,
  },
  viewBoxSize: {
    type: Number,
    default: 16,
  },
});

const iconPaths = {
  ranges: 'M8 2a5 5 0 100 10A5 5 0 008 2zm0 3.5a1.5 1.5 0 110 3 1.5 1.5 0 010-3zM8 1v2M8 13v2M1 8h2M13 8h2',
  boxes: 'M3 3h10a1 1 0 011 1v8a1 1 0 01-1 1H3a1 1 0 01-1-1V4a1 1 0 011-1zM5 8h6M8 5v6',
  templates: 'M2 2h5a1 1 0 011 1v5a1 1 0 01-1 1H2a1 1 0 01-1-1V3a1 1 0 011-1zM9 2h5a1 1 0 011 1v5a1 1 0 01-1 1H9a1 1 0 01-1-1V3a1 1 0 011-1zM2 9h5a1 1 0 011 1v5a1 1 0 01-1 1H2a1 1 0 01-1-1v-5a1 1 0 011-1zM9 9h5a1 1 0 011 1v5a1 1 0 01-1 1H9a1 1 0 01-1-1v-5a1 1 0 011-1z',
  users: 'M8 7a2.5 2.5 0 100-5 2.5 2.5 0 000 5zM2.5 14c0-3 2.5-5 5.5-5s5.5 2 5.5 5',
  chevron: 'M4.5 2.5L8 6l-3.5 3.5',
  plus: 'M7 2v10M2 7h10',
  trash: 'M2 4h10M5 4V2.5h4V4M5.5 6.5v4M8.5 6.5v4M3 4l.8 8h6.4L11 4',
  fire: 'M7 13c-3 0-5-2-5-4.5C2 5 5 3 5 3c0 2 2 2.5 2 2.5C7 4 9 2.5 9 1c2 1.5 3 3.5 3 5.5C12 10.5 10 13 7 13z',
  x: 'M2 2l8 8M10 2L2 10',
  grip: 'M4.5 3.5a1.1 1.1 0 110 2.2 1.1 1.1 0 010-2.2zM9.5 3.5a1.1 1.1 0 110 2.2 1.1 1.1 0 010-2.2zM4.5 7a1.1 1.1 0 110 2.2 1.1 1.1 0 010-2.2zM9.5 7a1.1 1.1 0 110 2.2 1.1 1.1 0 010-2.2zM4.5 10.5a1.1 1.1 0 110 2.2 1.1 1.1 0 010-2.2zM9.5 10.5a1.1 1.1 0 110 2.2 1.1 1.1 0 010-2.2z',
  arrowR: 'M2 7h10M8 3l4 4-4 4',
  wifi: 'M1 5.5a9 9 0 0112 0M3.5 8a5.5 5.5 0 017 0M6 10.5a2 2 0 012 0',
  edit: 'M9.5 2l2.5 2.5-7 7H2.5V9l7-7z',
  check: 'M2 7l4 4 6-6',
  bolt: 'M8.5 1L3 8h5l-2.5 5L13 6H8L8.5 1',
  lock: 'M3 7h10a1 1 0 011 1v5a1 1 0 01-1 1H3a1 1 0 01-1-1V8a1 1 0 011-1zm2-3V4a3 3 0 116 0v0M8 10v2',
  unlock: 'M3 7h10a1 1 0 011 1v5a1 1 0 01-1 1H3a1 1 0 01-1-1V8a1 1 0 011-1zm6-3V4a3 3 0 10-6 0M8 10v2',
  program: 'M2 2h12a1 1 0 011 1v10a1 1 0 01-1 1H2a1 1 0 01-1-1V3a1 1 0 011-1zM2 5h12M4 8l3 2 3-4',
  chevronLeft: 'M9.5 2.5L6 6l3.5 3.5',
  chevronRight: 'M6.5 2.5L10 6l-3.5 3.5',
  chevronDown: 'M2.5 4.5L6 8l3.5-3.5',
  download: 'M8 2v7M5 6l3 3 3-3M3 12h10',
  stop: 'M3 3h10a1 1 0 011 1v8a1 1 0 01-1 1H3a1 1 0 01-1-1V4a1 1 0 011-1z',
  alert: 'M8 1l6 11H2L8 1zm0 8v1M8 5v2',
  target: 'M8 2a6 6 0 100 12A6 6 0 008 2zm0 2a4 4 0 110 8 4 4 0 010-8zm0 2a2 2 0 100 4 2 2 0 000-4z',
  cpu: 'M3 3h10a1 1 0 011 1v8a1 1 0 01-1 1H3a1 1 0 01-1-1V4a1 1 0 011-1zM5 1v2M11 1v2M5 13v2M11 13v2M1 5h2M13 5h2M1 11h2M13 11h2M7 7h2v2H7z',
  user: 'M8 7a2.5 2.5 0 100-5 2.5 2.5 0 000 5zM3 14c0-2.5 2-4 5-4s5 1.5 5 4',
  smartground: 'M8 1C4.5 1 2 3.5 2 7c0 1.5.5 2.9 1.3 4L2 14h3.5l.9-1.5c.6.1 1.2.2 1.8.2 3.5 0 6-2.5 6-6S11.5 1 8 1zm0 10c-2.2 0-4-1.8-4-4s1.8-4 4-4 4 1.8 4 4-1.8 4-4 4zm1-5H7v2h2z',
  logout: 'M10 2H6a2 2 0 00-2 2v8a2 2 0 002 2h4M11 6l3-3m0 0l-3-3m3 3H7',
  record: 'M8 4.5a3.5 3.5 0 100 7 3.5 3.5 0 000-7z',
};

const iconPath = computed(() => iconPaths[props.icon] || '');
</script>

<style scoped>
svg {
  display: inline-block;
  flex-shrink: 0;
}
</style>

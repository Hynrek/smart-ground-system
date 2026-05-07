<template>
  <Badge :color="colorMap[props.status] || 'gray'">
    <slot>{{ statusLabels[props.status] }}</slot>
  </Badge>
</template>

<script setup>
import Badge from './Badge.vue';

const props = defineProps({
  status: {
    type: String,
    required: true,
    validator: (v) => ['online', 'offline', 'warn', 'blocked'].includes(v),
  },
});

const colorMap = {
  online: 'green',
  offline: 'red',
  warn: 'warn',
  blocked: 'red',
};

const statusLabels = {
  online: 'Online',
  offline: 'Offline',
  warn: 'Warnung',
  blocked: 'Gesperrt',
};
</script>

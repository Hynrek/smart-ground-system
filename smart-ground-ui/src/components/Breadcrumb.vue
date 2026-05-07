<template>
  <div class="breadcrumb">
    <template v-for="(item, i) in items" :key="i">
      <Icons v-if="i > 0" icon="chevron" :size="11" />
      <button
        v-if="item.onClick"
        class="breadcrumb-link"
        @click="item.onClick"
      >
        {{ item.label }}
      </button>
      <span v-else class="breadcrumb-text" :class="{ active: i === items.length - 1 }">
        {{ item.label }}
      </span>
    </template>
  </div>
</template>

<script setup>
import Icons from './Icons.vue';

defineProps({
  items: {
    type: Array,
    required: true,
  },
});
</script>

<style scoped>
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  margin-bottom: 6px;
}

.breadcrumb-link {
  background: none;
  border: none;
  color: #4fc3f7;
  cursor: pointer;
  padding: 0;
  font-size: 13px;
  font-family: inherit;
  transition: color 0.15s;
}

.breadcrumb-link:hover {
  color: #2ba4d0;
}

.breadcrumb-text {
  color: #718096;
  font-weight: 400;
}

.breadcrumb-text.active {
  color: #1a1a2e;
  font-weight: 600;
}
</style>

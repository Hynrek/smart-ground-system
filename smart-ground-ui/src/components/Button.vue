<template>
  <button class="btn" :class="`btn-${variant} btn-${size}`" :disabled="disabled" @click="$emit('click')">
    <span v-if="icon" class="btn-icon" v-html="icon"></span>
    <slot />
  </button>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (v) => ['primary', 'ghost', 'danger'].includes(v),
  },
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'icon-only'].includes(v),
  },
  icon: String,
  disabled: Boolean,
});

defineEmits(['click']);
</script>

<style scoped>
.btn {
  display: flex;
  align-items: center;
  gap: 5px;
  border: none;
  cursor: pointer;
  font-family: inherit;
  font-weight: 500;
  transition: all 0.15s;
  border-radius: 7px;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-sm {
  padding: 5px 11px;
  font-size: 12px;
}

.btn-md {
  padding: 8px 16px;
  font-size: 13px;
}

.btn-icon-only {
  padding: 10px;
  width: 44px;
  height: 44px;
  justify-content: center;
}

.btn-primary {
  background: #1a1a2e;
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #0f0f1a;
}

.btn-ghost {
  background: transparent;
  border: 1px solid #e2e8f0;
  color: #4a5568;
}

.btn-ghost:hover:not(:disabled) {
  background: #f9fafb;
}

.btn-danger {
  background: transparent;
  border: 1px solid #fca5a5;
  color: #e05252;
}

.btn-danger:hover:not(:disabled) {
  background: #fde0e0;
}

.btn-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>

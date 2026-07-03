<template>
  <button
    class="btn"
    :class="`btn-${variant} btn-${size}`"
    :disabled="disabled"
    :aria-disabled="disabled || undefined"
    :aria-label="ariaLabel || undefined"
    @click="$emit('click')"
  >
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
  ariaLabel: String,
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
  border-radius: var(--sg-radius-btn);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-sm {
  padding: 5px 11px;
  font-size: var(--sg-text-xs);
}

.btn-md {
  padding: 8px 16px;
  font-size: var(--sg-text-sm);
}

.btn-icon-only {
  padding: 10px;
  width: 44px;
  height: 44px;
  justify-content: center;
}

/* Solid accent fill with dark glyph — same contrast move as the shooter icon chips */
.btn-primary {
  background: var(--sg-accent);
  color: var(--sg-surface-0);
  font-weight: 600;
}

.btn-primary:hover:not(:disabled) {
  background: var(--sg-accent-hover);
}

.btn-ghost {
  background: transparent;
  border: 1px solid var(--sg-border-input);
  color: var(--sg-text-muted);
}

.btn-ghost:hover:not(:disabled) {
  background: var(--sg-bg-panel);
}

.btn-danger {
  background: transparent;
  border: 1px solid var(--sg-color-danger-bg);
  color: var(--sg-color-danger);
}

.btn-danger:hover:not(:disabled) {
  background: var(--sg-color-danger-bg);
}

.btn-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>

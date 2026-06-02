<template>
  <div class="form-field">
    <label v-if="label" class="form-label">{{ label }}</label>
    <slot />
    <span v-if="hint" class="form-hint">{{ hint }}</span>
  </div>
</template>

<script setup>
defineProps({
  label: String,
  hint: String,
});
</script>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.form-label {
  font-size: var(--sg-text-sm);
  font-weight: 500;
  color: var(--sg-text-muted);
}

.form-hint {
  font-size: var(--sg-text-xs);
  color: var(--sg-text-faint);
  margin-top: 0.2rem;
}
</style>

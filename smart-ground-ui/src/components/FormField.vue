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
  font-size: 0.85rem;
  font-weight: 500;
  color: #4a5568;
}

.form-hint {
  font-size: 0.78rem;
  color: #a0aec0;
  margin-top: 0.2rem;
}
</style>

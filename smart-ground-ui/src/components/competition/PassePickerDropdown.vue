<template>
  <div ref="wrapperRef" class="picker-wrapper">
    <div class="picker-list">
      <div v-if="passen.length === 0" class="picker-empty">Keine Passen verfügbar</div>
      <div
        v-for="passe in passen"
        :key="passe.id"
        class="picker-item"
        @click="emit('select', passe)"
      >
        <span class="picker-name">{{ passe.name }}</span>
        <span class="picker-meta">{{ passe.serien?.length ?? 0 }} Serien</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

defineOptions({ name: 'PassePickerDropdown' })

defineProps({
  passen: { type: Array, required: true },
})
const emit = defineEmits(['select', 'close'])

const wrapperRef = ref(null)

const onOutsideClick = (e) => {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target)) {
    emit('close')
  }
}

onMounted(() => document.addEventListener('mousedown', onOutsideClick))
onUnmounted(() => document.removeEventListener('mousedown', onOutsideClick))
</script>

<style scoped>
.picker-wrapper {
  position: absolute;
  z-index: 50;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
  box-shadow: var(--sg-shadow-lg);
  min-width: 220px;
  overflow: hidden;
}

.picker-list {
  max-height: 200px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.picker-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 14px;
  cursor: pointer;
  transition: background 0.1s;
}
.picker-item:hover { background: var(--sg-accent-tint); }

.picker-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--sg-brand);
}

.picker-meta {
  font-size: 11px;
  color: var(--sg-text-faint);
}

.picker-empty {
  padding: 12px;
  font-size: 12px;
  color: var(--sg-text-faint);
  text-align: center;
}
</style>

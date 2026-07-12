<template>
  <div ref="wrapperRef" class="picker-wrapper">
    <input
      v-model="search"
      class="picker-search"
      placeholder="Schütze suchen..."
      autofocus
      @keydown.escape="emit('close')"
    />
    <div class="picker-list">
      <div v-if="filtered.length === 0" class="picker-empty">Keine Schützen verfügbar</div>
      <div
        v-for="user in filtered"
        :key="user.id"
        class="picker-item"
        @click="emit('select', user)"
      >
        {{ user.displayName }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

defineOptions({ name: 'UserPickerDropdown' })

const props = defineProps({
  users: { type: Array, required: true },
})
const emit = defineEmits(['select', 'close'])

const search = ref('')
const wrapperRef = ref(null)

const filtered = computed(() =>
  props.users.filter(u =>
    u.displayName?.toLowerCase().includes(search.value.toLowerCase())
  )
)

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
  border: 1px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  min-width: 200px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.picker-search {
  border: none;
  border-bottom: 1px solid var(--sg-border);
  padding: 9px 12px;
  font-size: 13px;
  font-family: inherit;
  color: var(--sg-text-primary);
  outline: none;
  background: var(--sg-bg-panel);
}
.picker-search:focus { background: var(--sg-bg-card); }

.picker-list {
  max-height: 180px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.picker-item {
  padding: 9px 12px;
  font-size: 13px;
  color: var(--sg-text-primary);
  cursor: pointer;
  transition: background 0.1s;
}
.picker-item:hover { background: rgba(79, 195, 247, 0.08); color: var(--sg-color-info-text); }

.picker-empty {
  padding: 12px;
  font-size: 12px;
  color: var(--sg-text-faint);
  text-align: center;
}
</style>

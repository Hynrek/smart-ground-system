<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal" role="dialog" aria-modal="true" aria-label="User als Remote zuweisen">
      <div class="modal-header">
        <h3 class="modal-title">User als Remote zuweisen</h3>
        <button class="modal-close" aria-label="Schliessen" @click="$emit('close')">×</button>
      </div>

      <div class="modal-search">
        <input
          v-model="query"
          type="text"
          class="search-input"
          placeholder="Suchen…"
          aria-label="Benutzer suchen"
          autofocus
        />
      </div>

      <div class="modal-list">
        <button
          v-for="user in filtered"
          :key="user.id"
          class="user-row"
          @click="$emit('select', user)"
        >
          <div class="user-avatar">{{ initial(user) }}</div>
          <span class="user-name">{{ user.fullName }}</span>
        </button>
        <p v-if="filtered.length === 0" class="empty-hint">Keine Benutzer gefunden</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  users: { type: Array, required: true },
})

defineEmits(['select', 'close'])

const query = ref('')

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return props.users
  return props.users.filter((u) => u.fullName?.toLowerCase().includes(q))
})

const initial = (user) => user.fullName?.charAt(0).toUpperCase() ?? '?'
</script>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: #fff;
  border-radius: 14px;
  width: 420px;
  max-width: calc(100vw - 32px);
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-title {
  font-size: 15px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  font-size: 20px;
  color: #a0aec0;
  cursor: pointer;
  padding: 2px 6px;
  line-height: 1;
  border-radius: 6px;
  transition: background 0.15s;
}

.modal-close:hover {
  background: #f0f4f8;
}

.modal-search {
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1.5px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.search-input:focus {
  border-color: #4fc3f7;
}

.modal-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.user-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 20px;
  background: none;
  border: none;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.12s;
}

.user-row:hover {
  background: #f7fafc;
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(79, 195, 247, 0.15);
  border: 1px solid rgba(79, 195, 247, 0.3);
  color: #2c7a9e;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #2d3748;
}

.empty-hint {
  text-align: center;
  padding: 24px 16px;
  color: #a0aec0;
  font-size: 13px;
  margin: 0;
}
</style>

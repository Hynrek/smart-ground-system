<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-box">
      <h2>Benutzername ändern</h2>
      <div class="form-group">
        <label for="edit-username">Benutzername</label>
        <input
          id="edit-username"
          v-model="username"
          data-testid="edit-username"
          type="text"
          :disabled="authStore.isLoading"
          @keydown.enter="save"
        />
        <span v-if="localError" data-testid="username-error" class="error">{{ localError }}</span>
        <span v-else-if="authStore.error" data-testid="username-error" class="error">{{ authStore.error }}</span>
      </div>
      <div class="actions">
        <button data-testid="cancel" :disabled="authStore.isLoading" @click="$emit('close')">Abbrechen</button>
        <button data-testid="save" :disabled="authStore.isLoading" @click="save">Speichern</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'

const emit = defineEmits(['close', 'saved'])
const authStore = useAuthStore()
const USERNAME_RE = /^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$/

const username = ref(authStore.profile?.username ?? '')
const localError = ref('')

async function save() {
  localError.value = ''
  if (!USERNAME_RE.test(username.value.trim())) {
    localError.value = '3–30 Zeichen, Buchstaben/Ziffern/._-'
    return
  }
  try {
    await authStore.updateOwnUsername(username.value.trim())
    emit('saved')
    emit('close')
  } catch {
    // Fehler wird über authStore.error angezeigt
  }
}
</script>

<style scoped>
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.modal-box { background: var(--sg-bg-card, #fff); border-radius: 8px; padding: 24px; width: 360px; max-width: 92vw; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin: 12px 0; }
.error { color: var(--sg-color-danger, #ef4444); font-size: .8rem; }
.actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>

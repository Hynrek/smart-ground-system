<template>
  <div class="modal-overlay" @click.self="cancel">
    <div class="start-modal">
      <div class="modal-header">
        <h3 class="modal-title">Punkte aufnehmen?</h3>
        <p class="modal-subtitle">Solo-Serie starten</p>
      </div>

      <div class="shooter-row">
        <span class="shooter-label">Schütze</span>
        <span class="shooter-name">{{ shooter.displayName }}</span>
      </div>

      <button class="change-shooter-btn" @click="openQrScan">
        Schütze ändern
      </button>

      <label class="record-toggle-row" for="record-toggle">
        <input
          id="record-toggle"
          v-model="record"
          data-testid="record-toggle"
          type="checkbox"
          class="record-toggle-input"
        />
        <span class="record-toggle-label">Punkte aufzeichnen</span>
      </label>

      <p v-if="error" class="start-modal-error" role="alert">{{ error }}</p>

      <div class="modal-actions">
        <button class="action-btn action-btn--cancel" @click="cancel">
          Abbrechen
        </button>
        <button class="action-btn action-btn--start" data-testid="confirm" @click="confirm">
          Bestätigen
        </button>
      </div>
    </div>

    <QrScanModal v-if="showQr" @close="showQr = false" @resolved="handleResolved" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '@/stores/authStore.js'
import QrScanModal from '@/components/shooter/QrScanModal.vue'

defineProps({
  // Surfaces a failure from the parent's persisted-start attempt (e.g. startSerie
  // rejecting) so the shooter sees why the modal is still open instead of it
  // silently closing as if the run had been recorded.
  error: { type: String, default: '' },
})

const emit = defineEmits(['confirm', 'cancel'])

const authStore = useAuthStore()

const record = ref(false)
const showQr = ref(false)

const profileDisplayName = () => {
  const profile = authStore.profile
  if (!profile) return ''
  return `${profile.vorname ?? ''} ${profile.nachname ?? ''}`.trim()
}

const shooter = ref({
  userId: authStore.profile?.id ?? null,
  displayName: profileDisplayName(),
})

const openQrScan = () => {
  showQr.value = true
}

// QrScanModal already resolves the QR token via userApi.resolveUserByQr internally
// (through profileStore.resolveCheckinToken) and only emits 'resolved' once the
// backend lookup succeeds — invalid/unreachable codes are handled inside the modal.
const handleResolved = (user) => {
  shooter.value = { userId: user.userId, displayName: user.displayName }
  record.value = true // changing shooter implies recording
  showQr.value = false
}

const confirm = () => {
  emit('confirm', { record: record.value, shooter: shooter.value })
}

const cancel = () => {
  emit('cancel')
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  /* Scrim alpha has no --sg-* token in this codebase; matches the same
     rgba(0, 0, 0, ...) convention used by sibling overlays (QrScanModal,
     UserFormModal, StechenPanel, PlayerHandoverScreen). */
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: var(--sg-space-6);
}

.start-modal {
  background: var(--sg-bg-card);
  border: 1.5px solid var(--sg-border);
  border-radius: 20px;
  padding: var(--sg-space-6);
  width: 100%;
  max-width: 380px;
  display: flex;
  flex-direction: column;
  gap: var(--sg-space-4);
}

.modal-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.modal-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--sg-text-primary);
  margin: 0;
  text-align: center;
}

.modal-subtitle {
  font-size: 12px;
  color: var(--sg-text-muted);
  margin: 0;
  text-align: center;
}

.shooter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--sg-color-neutral-bg);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  padding: var(--sg-space-3) var(--sg-space-4);
}

.shooter-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--sg-text-muted);
}

.shooter-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--sg-text-primary);
}

.change-shooter-btn {
  min-height: 48px;
  border-radius: 10px;
  border: 1px dashed var(--sg-border-input);
  background: transparent;
  color: var(--sg-text-faint);
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}

.change-shooter-btn:hover {
  background: var(--sg-color-neutral-bg);
  color: var(--sg-text-muted);
}

.record-toggle-row {
  display: flex;
  align-items: center;
  gap: var(--sg-space-3);
  min-height: 48px;
  cursor: pointer;
}

.record-toggle-input {
  width: 24px;
  height: 24px;
  accent-color: var(--sg-accent);
  cursor: pointer;
}

.record-toggle-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--sg-text-primary);
}

.start-modal-error {
  margin: 0;
  color: var(--sg-color-danger-text);
  font-weight: 600;
  text-align: center;
  font-size: 13px;
}

.modal-actions {
  display: flex;
  gap: var(--sg-space-2);
  padding-top: var(--sg-space-2);
  border-top: 1px solid var(--sg-border);
}

.action-btn {
  flex: 1;
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
  border: 1px solid transparent;
}

.action-btn--start {
  background: var(--sg-accent-tint);
  border-color: color-mix(in srgb, var(--sg-accent) 30%, transparent);
  color: var(--sg-accent);
}

.action-btn--start:hover {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
}

.action-btn--cancel {
  background: transparent;
  border-color: var(--sg-border);
  color: var(--sg-text-faint);
}

.action-btn--cancel:hover {
  background: var(--sg-color-neutral-bg);
}
</style>

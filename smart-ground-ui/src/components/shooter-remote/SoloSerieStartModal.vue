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
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 24px;
}

.start-modal {
  background: rgba(24, 24, 40, 0.98);
  border: 1.5px solid var(--sg-border);
  border-radius: 20px;
  padding: 24px;
  width: 100%;
  max-width: 380px;
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  padding: 12px 16px;
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
  background: rgba(255, 255, 255, 0.04);
  color: var(--sg-text-muted);
}

.record-toggle-row {
  display: flex;
  align-items: center;
  gap: 12px;
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

.modal-actions {
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
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
  background: rgba(79, 195, 247, 0.12);
  border-color: rgba(79, 195, 247, 0.3);
  color: var(--sg-accent);
}

.action-btn--start:hover {
  background: rgba(79, 195, 247, 0.2);
}

.action-btn--cancel {
  background: transparent;
  border-color: var(--sg-border);
  color: var(--sg-text-faint);
}

.action-btn--cancel:hover {
  background: rgba(255, 255, 255, 0.05);
}
</style>

<template>
  <div class="gsm-overlay" @click.self="emit('cancel')">
    <div class="gsm-modal">
      <h2 class="gsm-title">{{ title }}</h2>
      <p v-if="subtitle" class="gsm-subtitle">{{ subtitle }}</p>

      <div class="gsm-player-list">
        <div
          v-for="(player, i) in players"
          :key="player.id"
          class="gsm-player-row"
          :class="{ 'is-starter-row': allowStarter && starterId === player.id }"
        >
          <span class="gsm-player-num">{{ i + 1 }}:</span>

          <input
            v-if="editable"
            v-model="player.displayName"
            class="gsm-player-input"
            type="text"
            :placeholder="`Schütze ${i + 1}`"
            maxlength="30"
          />
          <template v-else>
            <span class="gsm-player-name">{{ player.displayName }}</span>
            <span
              v-if="player.userId"
              class="gsm-player-account-badge"
              title="Mit Account verknüpft — Ergebnis wird gespeichert"
            >
              <Icons icon="check" :size="12" color="var(--sg-color-success)" />
            </span>
          </template>

          <template v-if="allowReorder">
            <button
              class="gsm-order-btn gsm-move-up"
              :disabled="i === 0"
              title="Nach oben"
              @click="moveUp(i)"
            >
              <Icons icon="chevronDown" :size="11" color="rgba(255,255,255,0.6)" class="gsm-rot-180" />
            </button>
            <button
              class="gsm-order-btn gsm-move-down"
              :disabled="i === players.length - 1"
              title="Nach unten"
              @click="moveDown(i)"
            >
              <Icons icon="chevronDown" :size="11" color="rgba(255,255,255,0.6)" />
            </button>
          </template>

          <label
            v-if="allowStarter"
            class="gsm-starter-radio"
            :class="{ 'is-starter': starterId === player.id }"
            :title="starterId === player.id ? 'Startet' : 'Als Starter markieren'"
          >
            <input
              v-model="starterId"
              type="radio"
              class="gsm-radio-input"
              :name="`${idPrefix}-starter`"
              :value="player.id"
            />
            <span class="gsm-radio-dot" aria-hidden="true"></span>
            <span class="gsm-radio-label">Start</span>
          </label>

          <button
            v-if="allowRemove && players.length > 1"
            class="gsm-remove-btn"
            @click="removePlayer(i)"
          >
            <Icons icon="x" :size="12" color="var(--sg-color-danger-text)" />
          </button>
        </div>
      </div>

      <button v-if="allowAdd" class="gsm-add-btn" @click="addPlayer">
        + Schütze hinzufügen
      </button>
      <button v-if="allowQr" class="gsm-add-btn gsm-add-qr-btn" @click="emit('qr-scan')">
        + Schütze per QR
      </button>
      <p v-if="qrNotice" class="gsm-qr-notice">{{ qrNotice }}</p>

      <!-- Surfaces a failed confirm attempt (e.g. persisted Serie start rejecting)
           so the caller can keep this modal open instead of it closing silently. -->
      <p v-if="errorMessage" class="gsm-qr-notice" role="alert">{{ errorMessage }}</p>

      <label v-if="allowPreview" class="gsm-preview-check">
        <input v-model="previewFirst" type="checkbox" class="gsm-checkbox-input" />
        <span class="gsm-checkbox-box" aria-hidden="true">
          <Icons icon="check" :size="11" color="var(--sg-surface-0)" />
        </span>
        <span class="gsm-checkbox-label">Serie zuerst anschauen</span>
      </label>

      <div class="gsm-actions">
        <button class="gsm-btn gsm-btn--cancel" @click="emit('cancel')">
          <Icons icon="x" :size="14" class="gsm-btn-icon" />
          <span class="gsm-btn-label">Abbrechen</span>
        </button>
        <button
          class="gsm-btn gsm-btn--primary"
          :disabled="players.length === 0"
          @click="onConfirm"
        >
          <Icons icon="play" :size="14" class="gsm-btn-icon" />
          <span class="gsm-btn-label">{{ confirmLabel }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import Icons from '@/components/Icons.vue'

// Shared "group / Passe start" setup modal — used by ShooterPlayPage (group setup,
// with reorder/starter/QR) and PasseManagementView (simple guest-name entry).
const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  confirmLabel: { type: String, default: 'Starten' },
  qrNotice: { type: String, default: '' },
  errorMessage: { type: String, default: '' },
  editable: { type: Boolean, default: true },
  allowAdd: { type: Boolean, default: true },
  allowRemove: { type: Boolean, default: true },
  allowReorder: { type: Boolean, default: false },
  allowStarter: { type: Boolean, default: false },
  allowQr: { type: Boolean, default: false },
  allowPreview: { type: Boolean, default: false },
  idPrefix: { type: String, default: 'p' },
  playerDefaults: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['cancel', 'confirm', 'qr-scan'])

const players = defineModel('players', { default: () => [] })
const starterId = defineModel('starterId', { default: null })
const previewFirst = defineModel('previewFirst', { default: false })

const onConfirm = () => {
  emit('confirm', previewFirst.value)
}

const addPlayer = () => {
  const n = players.value.length + 1
  players.value.push({
    id: `${props.idPrefix}-${crypto.randomUUID()}`,
    displayName: `Schütze ${n}`,
    ...props.playerDefaults,
  })
}

const removePlayer = (index) => {
  players.value.splice(index, 1)
}

const moveUp = (i) => {
  if (i === 0) return
  const list = players.value
  ;[list[i - 1], list[i]] = [list[i], list[i - 1]]
}

const moveDown = (i) => {
  const list = players.value
  if (i === list.length - 1) return
  ;[list[i], list[i + 1]] = [list[i + 1], list[i]]
}
</script>

<style scoped>
.gsm-overlay {
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

.gsm-modal {
  background: rgba(24, 24, 40, 0.98);
  border: 1.5px solid var(--sg-border);
  border-radius: 20px;
  padding: 28px 24px;
  width: 100%;
  max-width: 360px;
  max-height: 90vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.gsm-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--sg-text-primary);
  margin: 0;
  text-align: center;
}

.gsm-subtitle {
  font-size: 12px;
  font-weight: 600;
  color: var(--sg-text-faint);
  text-align: center;
  margin: -12px 0 0;
}

.gsm-player-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.gsm-player-row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--sg-border);
  border-radius: 10px;
  padding: 10px 12px;
}

.gsm-player-row.is-starter-row {
  border-color: color-mix(in srgb, var(--sg-accent) 35%, transparent);
}

.gsm-player-num {
  font-size: 13px;
  font-weight: 700;
  color: color-mix(in srgb, var(--sg-accent) 70%, transparent);
  min-width: 22px;
}

.gsm-player-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.85);
}

.gsm-player-input {
  flex: 1;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--sg-border);
  border-radius: 8px;
  color: var(--sg-text-primary);
  font-size: 13px;
  font-family: inherit;
  padding: 8px 10px;
  outline: none;
  transition: border-color 0.15s;
}

.gsm-player-input:focus {
  border-color: color-mix(in srgb, var(--sg-accent) 30%, transparent);
}

.gsm-player-account-badge {
  color: var(--sg-color-success);
  font-weight: 700;
  margin-left: 6px;
}

.gsm-order-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  min-height: 30px;
  padding: 4px;
  border-radius: 8px;
  transition: background 0.15s;
}

.gsm-order-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.07);
}

.gsm-order-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.gsm-rot-180 {
  transform: rotate(180deg);
}

.gsm-starter-radio {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 48px;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid var(--sg-border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--sg-text-faint);
  font-family: inherit;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}

.gsm-starter-radio.is-starter {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border-color: color-mix(in srgb, var(--sg-accent) 40%, transparent);
  color: var(--sg-accent);
}

.gsm-radio-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.gsm-radio-dot {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1.5px solid var(--sg-border-input);
  background: rgba(255, 255, 255, 0.05);
  transition: all 0.15s;
  position: relative;
}

.gsm-starter-radio.is-starter .gsm-radio-dot {
  border-color: var(--sg-accent);
}

.gsm-starter-radio.is-starter .gsm-radio-dot::after {
  content: '';
  position: absolute;
  inset: 3px;
  border-radius: 50%;
  background: var(--sg-accent);
}

.gsm-radio-input:focus-visible ~ .gsm-radio-dot {
  outline: 2px solid var(--sg-accent);
  outline-offset: 2px;
}

.gsm-radio-label {
  white-space: nowrap;
}

.gsm-remove-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 3px;
  border-radius: 6px;
  transition: background 0.15s;
}

.gsm-remove-btn:hover {
  background: rgba(252, 129, 129, 0.1);
}

.gsm-add-btn {
  width: 100%;
  padding: 11px;
  background: rgba(255, 255, 255, 0.04);
  border: 1.5px dashed var(--sg-border-input);
  border-radius: 10px;
  color: var(--sg-text-faint);
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}

.gsm-add-btn:hover {
  background: rgba(255, 255, 255, 0.07);
  border-color: rgba(255, 255, 255, 0.25);
  color: var(--sg-text-muted);
}

.gsm-qr-notice {
  color: var(--sg-color-danger-text);
  font-size: 0.9rem;
  text-align: center;
  margin: 4px 0 0;
}

.gsm-preview-check {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 2px;
}

.gsm-checkbox-input {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.gsm-checkbox-box {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  border: 1.5px solid var(--sg-border-input);
  background: rgba(255, 255, 255, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.gsm-checkbox-box :deep(svg) {
  opacity: 0;
  transition: opacity 0.15s;
}

.gsm-checkbox-input:checked ~ .gsm-checkbox-box {
  background: var(--sg-accent);
  border-color: var(--sg-accent);
}

.gsm-checkbox-input:checked ~ .gsm-checkbox-box :deep(svg) {
  opacity: 1;
}

.gsm-checkbox-input:focus-visible ~ .gsm-checkbox-box {
  outline: 2px solid var(--sg-accent);
  outline-offset: 2px;
}

.gsm-checkbox-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--sg-text-muted);
}

.gsm-actions {
  display: flex;
  gap: 10px;
}

.gsm-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 16px;
  border-radius: 12px;
  border: none;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.gsm-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.gsm-btn-icon {
  display: none;
}

@media (max-width: 480px) {
  .gsm-btn-icon {
    display: inline-flex;
  }

  .gsm-btn-label {
    display: none;
  }
}

.gsm-btn--cancel {
  border: 1px solid rgba(252, 129, 129, 0.35);
  background: rgba(252, 129, 129, 0.1);
  color: var(--sg-color-danger-text);
}

.gsm-btn--cancel:hover {
  background: rgba(252, 129, 129, 0.18);
}

.gsm-btn--primary {
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  color: var(--sg-accent);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
}

.gsm-btn--primary:hover:not(:disabled) {
  background: color-mix(in srgb, var(--sg-accent) 28%, transparent);
}
</style>

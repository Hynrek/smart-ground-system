<template>
  <div v-if="open" class="modal-overlay" @click.self="emit('close')">
    <div class="start-modal">

      <!-- ── Step 1: Rotten definieren ── -->
      <template v-if="step === 1">
        <div class="modal-header">
          <h3 class="modal-title">Wettkampf starten</h3>
          <p class="modal-subtitle">"{{ template?.name }}"</p>
        </div>

        <div class="rotten-counter-row">
          <span class="counter-label">Anzahl Rotten</span>
          <div class="counter-controls">
            <button
              class="counter-btn"
              :disabled="rotten.length <= 1"
              @click="removeLastRotte"
            >
              <Icons icon="x" :size="11" color="rgba(255,255,255,0.6)" />
            </button>
            <span class="counter-value">{{ rotten.length }}</span>
            <button
              class="counter-btn"
              :disabled="rotten.length >= 8"
              @click="addRotte"
            >
              <Icons icon="plus" :size="11" color="rgba(255,255,255,0.6)" />
            </button>
          </div>
        </div>

        <div class="rotten-list">
          <div v-for="(rotte, ri) in rotten" :key="rotte._key" class="rotte-block">
            <div class="rotte-block-header">
              <span class="rotte-label">Rotte {{ ri + 1 }}</span>
            </div>

            <div class="modal-players">
              <div
                v-for="(player, pi) in rotte.players"
                :key="player.id"
                class="modal-player-row"
              >
                <span class="modal-player-num">{{ pi + 1 }}:</span>
                <input
                  v-model="player.displayName"
                  class="modal-player-input"
                  type="text"
                  :placeholder="`Schütze ${pi + 1}`"
                  maxlength="30"
                />
                <button
                  v-if="rotte.players.length > 1"
                  class="icon-btn icon-btn--danger"
                  @click="removePlayer(ri, pi)"
                >
                  <Icons icon="x" :size="11" color="#fc8181" />
                </button>
              </div>
            </div>

            <button class="add-player-btn" @click="addPlayer(ri)">
              + Schütze hinzufügen
            </button>
          </div>
        </div>

        <div class="modal-actions">
          <button class="action-btn action-btn--cancel" @click="emit('close')">
            Abbrechen
          </button>
          <button
            class="action-btn action-btn--start"
            :disabled="!canProceed"
            @click="step = 2"
          >
            Weiter
            <Icons icon="chevronRight" :size="13" color="#4fc3f7" />
          </button>
        </div>
      </template>

      <!-- ── Step 2: Bestätigen ── -->
      <template v-else>
        <div class="modal-header">
          <h3 class="modal-title">Bestätigen</h3>
        </div>

        <div class="confirm-summary">
          <div class="summary-chip">
            <span class="summary-num">{{ rotten.length }}</span>
            <span class="summary-lbl">Rotten</span>
          </div>
          <span class="summary-dot">·</span>
          <div class="summary-chip">
            <span class="summary-num">{{ passenCount }}</span>
            <span class="summary-lbl">Passen</span>
          </div>
          <span class="summary-dot">·</span>
          <div class="summary-chip">
            <span class="summary-num">{{ serienTotal }}</span>
            <span class="summary-lbl">Serien total</span>
          </div>
        </div>

        <div class="rotten-preview">
          <div v-for="(rotte, ri) in rotten" :key="rotte._key" class="rotte-preview-row">
            <span class="rotte-preview-label">Rotte {{ ri + 1 }}</span>
            <span class="rotte-preview-players">
              {{ rotte.players.map(p => p.displayName || `Schütze ${rotte.players.indexOf(p) + 1}`).join(', ') }}
            </span>
          </div>
        </div>

        <div class="modal-actions">
          <button class="action-btn action-btn--cancel" @click="step = 1">
            <Icons icon="chevronLeft" :size="13" color="rgba(255,255,255,0.4)" />
            Zurück
          </button>
          <button class="action-btn action-btn--start" @click="confirmStart">
            <Icons icon="check" :size="13" color="#4fc3f7" />
            Starten
          </button>
        </div>
      </template>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import Icons from '@/components/Icons.vue'

// ── UUID helper (same pattern as activePasseStore) ──────────────────────────
const generateUUID = () =>
  typeof globalThis !== 'undefined' && globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

// ── Props & emits ──────────────────────────────────────────────────────────
const props = defineProps({
  template: {
    type: Object,
    default: null,
  },
  open: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close', 'confirm'])

// ── Internal state ─────────────────────────────────────────────────────────
const step = ref(1)
let _playerCounter = 1

const makePlayer = () => ({
  id: `sp-${_playerCounter++}`,
  displayName: '',
  type: 'guest',
})

const makeRotte = (index) => ({
  _key: generateUUID(),
  name: `Rotte ${index + 1}`,
  players: [makePlayer()],
})

const rotten = ref([makeRotte(0), makeRotte(1)])

// ── Reset when modal opens ─────────────────────────────────────────────────
watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      step.value = 1
      _playerCounter = 1
      rotten.value = [makeRotte(0), makeRotte(1)]
    }
  },
)

// ── Rotte management ───────────────────────────────────────────────────────
const addRotte = () => {
  if (rotten.value.length >= 8) return
  rotten.value.push(makeRotte(rotten.value.length))
}

const removeLastRotte = () => {
  if (rotten.value.length <= 1) return
  rotten.value.pop()
}

// ── Player management ──────────────────────────────────────────────────────
const addPlayer = (rotteIndex) => {
  rotten.value[rotteIndex].players.push(makePlayer())
}

const removePlayer = (rotteIndex, playerIndex) => {
  rotten.value[rotteIndex].players.splice(playerIndex, 1)
}

// ── Validation ─────────────────────────────────────────────────────────────
const canProceed = computed(() =>
  rotten.value.every(
    (r) =>
      r.players.length > 0 &&
      r.players.every((p) => p.displayName.trim().length > 0),
  ),
)

// ── Summary computation ────────────────────────────────────────────────────
const passenCount = computed(() => props.template?.passen?.length ?? 0)

const serienTotal = computed(() => {
  const serienPerPasse = (props.template?.passen ?? []).reduce(
    (sum, passe) => sum + (passe.serien?.length ?? 0),
    0,
  )
  return serienPerPasse * rotten.value.length
})

// ── Confirm ────────────────────────────────────────────────────────────────
const confirmStart = () => {
  const result = rotten.value.map((r, i) => ({
    rotteId: generateUUID(),
    name: `Rotte ${i + 1}`,
    players: r.players.map((p) => ({
      id: p.id,
      displayName: p.displayName.trim(),
      type: 'guest',
    })),
  }))
  emit('confirm', result)
}
</script>

<style scoped>
/* ── Overlay & shell (matches TrainingManagementView modal style) ── */
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
  border: 1.5px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  padding: 24px;
  width: 100%;
  max-width: 380px;
  max-height: 90vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ── Header ── */
.modal-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.modal-title {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  margin: 0;
  text-align: center;
}

.modal-subtitle {
  font-size: 12px;
  color: rgba(79, 195, 247, 0.7);
  margin: 0;
  text-align: center;
  font-style: italic;
}

/* ── Rotten counter ── */
.rotten-counter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 10px 14px;
}

.counter-label {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.7);
}

.counter-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.counter-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
}

.counter-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.14);
}

.counter-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.counter-value {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  min-width: 20px;
  text-align: center;
}

/* ── Rotten list (scrollable area) ── */
.rotten-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rotte-block {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 14px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rotte-block-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rotte-label {
  font-size: 11px;
  font-weight: 700;
  color: rgba(79, 195, 247, 0.7);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* ── Player rows (matches reference exactly) ── */
.modal-players {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.modal-player-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.modal-player-num {
  font-size: 13px;
  font-weight: 700;
  color: rgba(79, 195, 247, 0.7);
  min-width: 20px;
}

.modal-player-input {
  flex: 1;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: #fff;
  font-size: 13px;
  font-family: inherit;
  padding: 8px 10px;
  outline: none;
  transition: border-color 0.15s;
}

.modal-player-input:focus {
  border-color: rgba(79, 195, 247, 0.3);
}

/* ── Add player button (matches reference) ── */
.add-player-btn {
  background: transparent;
  border: 1px dashed rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
  font-family: inherit;
  padding: 7px;
  cursor: pointer;
  transition: all 0.15s;
  text-align: center;
}

.add-player-btn:hover {
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.6);
}

/* ── Icon buttons ── */
.icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  transition: background 0.15s;
  flex-shrink: 0;
}

.icon-btn:hover {
  background: rgba(255, 255, 255, 0.07);
}

.icon-btn--danger:hover {
  background: rgba(252, 129, 129, 0.1);
}

/* ── Modal actions (matches reference) ── */
.modal-actions {
  display: flex;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s, opacity 0.15s;
  border: 1px solid transparent;
}

.action-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.action-btn--start {
  background: rgba(79, 195, 247, 0.12);
  border-color: rgba(79, 195, 247, 0.3);
  color: #4fc3f7;
}

.action-btn--start:hover:not(:disabled) {
  background: rgba(79, 195, 247, 0.2);
}

.action-btn--cancel {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.4);
}

.action-btn--cancel:hover {
  background: rgba(255, 255, 255, 0.05);
}

/* ── Step 2: Confirmation ── */
.confirm-summary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(79, 195, 247, 0.06);
  border: 1px solid rgba(79, 195, 247, 0.15);
  border-radius: 14px;
  padding: 16px;
}

.summary-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.summary-num {
  font-size: 22px;
  font-weight: 700;
  color: #4fc3f7;
  line-height: 1;
}

.summary-lbl {
  font-size: 10px;
  font-weight: 600;
  color: rgba(79, 195, 247, 0.5);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.summary-dot {
  font-size: 18px;
  color: rgba(79, 195, 247, 0.2);
  margin-top: -8px;
}

.rotten-preview {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rotte-preview-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 10px;
}

.rotte-preview-label {
  font-size: 11px;
  font-weight: 700;
  color: rgba(79, 195, 247, 0.6);
  text-transform: uppercase;
  letter-spacing: 0.4px;
  min-width: 54px;
  flex-shrink: 0;
}

.rotte-preview-players {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

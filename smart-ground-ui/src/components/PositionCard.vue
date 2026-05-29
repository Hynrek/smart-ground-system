<template>
  <!-- Ghost card: click to create next position -->
  <div
    v-if="!position"
    class="position-card position-card--ghost"
    role="button"
    :aria-label="`Position ${nextLabel} hinzufügen`"
    @click="$emit('add-position')"
  >
    <div class="pos-label pos-label--ghost">{{ nextLabel }}</div>
    <div class="ghost-body">
      <Icons icon="plus" :size="18" color="#a0aec0" />
      <span>Position hinzufügen</span>
    </div>
  </div>

  <!-- Real card -->
  <div
    v-else
    class="position-card"
    :class="{
      'position-card--empty': !position.device,
      'position-card--filled': !!position.device,
      'drag-over': dragOver,
    }"
    @dragover.prevent="dragOver = true"
    @dragleave="dragOver = false"
    @drop.prevent="onDrop"
  >
    <!-- Label row -->
    <div class="pos-label-row">
      <div
        class="pos-label"
        :title="isAdmin ? 'Klicken zum Umbenennen' : undefined"
        @click="isAdmin && startRename()"
      >
        <template v-if="renaming">
          <input
            ref="renameInput"
            v-model="renameValue"
            class="label-input"
            @keydown.enter.prevent="commitRename"
            @keydown.escape.prevent="cancelRename"
            @blur="commitRename"
            @click.stop
          />
        </template>
        <template v-else>{{ position.label }}</template>
      </div>

      <!-- Kebab menu: delete position (admin only) -->
      <button
        v-if="isAdmin"
        class="kebab-btn"
        title="Position löschen"
        aria-label="Position löschen"
        @click.stop="$emit('delete-position')"
      >
        <Icons icon="trash" :size="12" color="#a0aec0" />
      </button>
    </div>

    <!-- Filled: device card -->
    <template v-if="position.device">
      <div class="device-area">
        <DeviceCard
          :device="position.device"
          :fired="fired"
          :action-mode="false"
        />
        <div
          v-if="isAdmin"
          class="device-overlay"
          role="button"
          aria-label="Gerät entfernen"
          tabindex="0"
          @click.stop="$emit('remove-device')"
          @keydown.enter.stop="$emit('remove-device')"
          @keydown.space.prevent.stop="$emit('remove-device')"
        >
          <Icons icon="x" :size="14" color="#fff" />
          <span>Gerät entfernen</span>
        </div>
      </div>
      <!-- Fire button in action mode -->
      <button
        v-if="actionMode"
        class="fire-btn"
        :class="{ fired }"
        @click="$emit('fire', position.device.id)"
      >
        <Icons icon="fire" :size="12" />
        <span>{{ fired ? 'Ausgelöst!' : 'Auslösen' }}</span>
      </button>
    </template>

    <!-- Empty slot -->
    <template v-else>
      <div class="empty-slot">
        <Icons icon="plus" :size="16" color="#cbd5e0" />
        <span>Gerät hierher ziehen</span>
      </div>
      <!-- Disabled fire button in action mode -->
      <button v-if="actionMode" class="fire-btn fire-btn--disabled" disabled>
        <Icons icon="lock" :size="12" color="#cbd5e0" />
        <span>Kein Gerät</span>
      </button>
    </template>
  </div>
</template>

<script setup>
import { ref, nextTick, computed } from 'vue';
import DeviceCard from './DeviceCard.vue';
import Icons from './Icons.vue';
import { useAuthStore } from '../stores/authStore.js';

const props = defineProps({
  /** null = ghost card */
  position: { type: Object, default: null },
  /** Label preview for the ghost card, e.g. "D" */
  nextLabel: { type: String, default: '+' },
  actionMode: { type: Boolean, default: false },
  fired: { type: Boolean, default: false },
});

const emit = defineEmits([
  'add-position',
  'drop',
  'remove-device',
  'rename',
  'delete-position',
  'fire',
]);

const authStore = useAuthStore();
const isAdmin = computed(() => authStore.isAdminOrOwner());

// ── Drag & drop ───────────────────────────────────────────────────────────────
const dragOver = ref(false);

function onDrop(event) {
  dragOver.value = false;
  const deviceId = event.dataTransfer?.getData('deviceId');
  if (deviceId) emit('drop', deviceId);
}

// ── Inline rename ─────────────────────────────────────────────────────────────
const renaming = ref(false);
const renameValue = ref('');
const renameInput = ref(null);

function startRename() {
  if (!props.position) return;
  renameValue.value = props.position.label;
  renaming.value = true;
  nextTick(() => renameInput.value?.select());
}

function commitRename() {
  const trimmed = renameValue.value.trim();
  if (trimmed && trimmed !== props.position?.label) {
    emit('rename', trimmed);
  }
  renaming.value = false;
}

function cancelRename() {
  renaming.value = false;
}
</script>

<style scoped>
.position-card {
  background: #fff;
  border-radius: 12px;
  border: 1.5px solid #e2e8f0;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 180px;
  transition: border-color 0.15s, box-shadow 0.15s;
  position: relative;
}

.position-card--filled {
  box-shadow: 0 1px 3px rgba(0,0,0,0.07), 0 4px 12px rgba(0,0,0,0.04);
}

.position-card--empty {
  border-style: dashed;
  background: #fafbfc;
}

.position-card--ghost {
  border: 1.5px dashed #cbd5e0;
  background: #fafbfc;
  cursor: pointer;
  align-items: center;
  justify-content: center;
  min-height: 140px;
  opacity: 0.7;
  transition: opacity 0.15s, border-color 0.15s;
}

.position-card--ghost:hover {
  opacity: 1;
  border-color: #a0aec0;
}

.drag-over {
  border-color: #4fc3f7 !important;
  background: rgba(79, 195, 247, 0.05) !important;
  box-shadow: 0 0 0 3px rgba(79, 195, 247, 0.2);
}

/* ── Label ── */
.pos-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pos-label {
  font-size: 22px;
  font-weight: 800;
  color: #1a1a2e;
  letter-spacing: -0.5px;
  line-height: 1;
  cursor: default;
  user-select: none;
}

.pos-label--ghost {
  font-size: 22px;
  font-weight: 800;
  color: #cbd5e0;
  margin-bottom: 6px;
}

[title="Klicken zum Umbenennen"] {
  cursor: text;
}

.label-input {
  font-size: 22px;
  font-weight: 800;
  color: #1a1a2e;
  border: none;
  border-bottom: 2px solid #4fc3f7;
  outline: none;
  background: transparent;
  width: 60px;
  font-family: inherit;
  padding: 0;
}

/* ── Ghost body ── */
.ghost-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  color: #a0aec0;
  font-size: 12px;
  font-weight: 500;
  text-align: center;
}

/* ── Device area ── */
.device-area {
  position: relative;
  border-radius: 10px;
}

/* ── Flatten nested DeviceCard ── */
:deep(.device-card) {
  box-shadow: none;
  border-color: transparent;
  background: #f8fafc;
}

/* ── Device remove overlay ── */
.device-overlay {
  position: absolute;
  inset: 0;
  border-radius: 10px;
  background: rgba(229, 62, 62, 0.88);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}

.device-area:hover .device-overlay {
  opacity: 1;
}

.device-overlay:focus-visible {
  opacity: 1;
  outline: 2px solid #fff;
  outline-offset: -3px;
}

/* ── Empty slot ── */
.empty-slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 18px 0;
  color: #cbd5e0;
  font-size: 12px;
  font-weight: 500;
}

/* ── Kebab (hover-reveal) ── */
.kebab-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s;
}

.position-card:hover .kebab-btn,
.kebab-btn:focus-visible {
  opacity: 1;
}

.kebab-btn:hover { background: #fee2e2; }

.kebab-btn:focus-visible {
  outline: 2px solid #4fc3f7;
  outline-offset: 2px;
}

/* ── Fire button ── */
.fire-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 100%;
  padding: 7px 10px;
  background: #1a1a2e;
  color: #fff;
  border: none;
  border-radius: 7px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  font-family: inherit;
  transition: background 0.2s;
}
.fire-btn.fired { background: #4fc3f7; }
.fire-btn:hover:not(:disabled) { background: #0f0f1a; }

.fire-btn--disabled {
  background: #f1f5f9;
  color: #cbd5e0;
  cursor: not-allowed;
}
</style>

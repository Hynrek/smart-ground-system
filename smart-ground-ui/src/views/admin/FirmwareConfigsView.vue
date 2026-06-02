<script setup>
import { ref, computed } from 'vue'
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js'
import { registerFirmwareConfig } from '@/services/deviceTypeApi.js'

const deviceTypeStore = useDeviceTypeStore()

const firmwareConfigs = computed(() => deviceTypeStore.firmwareConfigs)
const loading = computed(() => deviceTypeStore.isLoading)

const showForm = ref(false)
const form = ref(emptyForm())
const registering = ref(false)
const error = ref(null)
const successMessage = ref(null)

function emptyForm() {
  return {
    version: '',
    box_type: 'pico',
    signal_types: [
      { direction: 'INPUT', device: 'GPIO', command: '', group_name: '', name: '', signal_duration_ms: 500, delay_signal_duration_ms: null }
    ]
  }
}

function isFormValid() {
  if (!form.value.version.trim() || !form.value.box_type.trim()) return false
  return form.value.signal_types.every(st =>
    st.command.trim() && st.group_name.trim() && st.name.trim() && st.signal_duration_ms > 0
  )
}

function addSignalType() {
  form.value.signal_types.push({
    direction: 'INPUT',
    device: 'GPIO',
    command: '',
    group_name: '',
    name: '',
    signal_duration_ms: 500,
    delay_signal_duration_ms: null
  })
}

function removeSignalType(index) {
  form.value.signal_types.splice(index, 1)
}

async function submitForm() {
  if (!isFormValid()) return
  registering.value = true
  error.value = null
  successMessage.value = null
  try {
    await registerFirmwareConfig(form.value)
    successMessage.value = `FirmwareConfig v${form.value.version} erfolgreich registriert!`
    form.value = emptyForm()
    await deviceTypeStore.loadApiData()
    setTimeout(() => {
      showForm.value = false
      successMessage.value = null
    }, 2000)
  } catch (e) {
    error.value = e.message
  } finally {
    registering.value = false
  }
}
</script>

<template>
  <div class="view">
    <div class="view-container">
      <div class="view-header">
        <h1 class="view-title">Firmware-Konfigurationen</h1>
        <button class="btn-primary" :disabled="showForm" @click="showForm = true">
          + Neue Firmware
        </button>
      </div>

      <div v-if="successMessage" class="success-banner">
        ✓ {{ successMessage }}
      </div>

      <div v-if="showForm" class="form-card">
        <h3 class="form-title">Neue Firmware-Konfiguration registrieren</h3>
        <div class="form-content">
          <div class="form-section">
            <h4 class="section-title">Firmware-Details</h4>
            <div class="form-row">
              <div class="form-field">
                <label class="form-label">Version</label>
                <input
                  v-model="form.version"
                  type="text"
                  class="form-input"
                  placeholder="z.B. 0.5"
                />
              </div>
              <div class="form-field">
                <label class="form-label">Box-Type</label>
                <select v-model="form.box_type" class="form-input">
                  <option value="pico">Pico 2W</option>
                </select>
              </div>
            </div>
          </div>

          <div class="form-section">
            <h4 class="section-title">Signal-Typen</h4>
            <div
              v-for="(st, idx) in form.signal_types"
              :key="idx"
              class="signal-type-block"
            >
              <div class="signal-type-header">
                <span>Signal-Typ {{ idx + 1 }}</span>
                <button
                  v-if="form.signal_types.length > 1"
                  type="button"
                  class="btn-remove"
                  @click="removeSignalType(idx)"
                >
                  ✕
                </button>
              </div>
              <div class="form-row">
                <div class="form-field">
                  <label class="form-label">Richtung</label>
                  <select v-model="st.direction" class="form-input">
                    <option value="INPUT">INPUT</option>
                    <option value="OUTPUT">OUTPUT</option>
                  </select>
                </div>
                <div class="form-field">
                  <label class="form-label">Gerät</label>
                  <select v-model="st.device" class="form-input">
                    <option value="GPIO">GPIO</option>
                    <option value="LED">LED</option>
                  </select>
                </div>
              </div>
              <div class="form-row">
                <div class="form-field">
                  <label class="form-label">Befehl</label>
                  <input
                    v-model="st.command"
                    type="text"
                    class="form-input"
                    placeholder="z.B. 15 oder OFF"
                  />
                </div>
                <div class="form-field">
                  <label class="form-label">Gruppen-Name</label>
                  <input
                    v-model="st.group_name"
                    type="text"
                    class="form-input"
                    placeholder="z.B. Wurfmaschine"
                  />
                </div>
              </div>
              <div class="form-row">
                <div class="form-field">
                  <label class="form-label">Gerätetyp-Name</label>
                  <input
                    v-model="st.name"
                    type="text"
                    class="form-input"
                    placeholder="z.B. Werfer V0.5"
                  />
                </div>
                <div class="form-field">
                  <label class="form-label">Signal-Dauer (ms)</label>
                  <input
                    v-model.number="st.signal_duration_ms"
                    type="number"
                    min="1"
                    class="form-input"
                  />
                </div>
              </div>
              <div class="form-row">
                <div class="form-field">
                  <label class="form-label">Verzögerung (ms, optional)</label>
                  <input
                    v-model.number="st.delay_signal_duration_ms"
                    type="number"
                    min="0"
                    class="form-input"
                    placeholder="Leer für keine Verzögerung"
                  />
                </div>
              </div>
            </div>
            <button type="button" class="btn-add-signal" @click="addSignalType">
              + Signal-Typ hinzufügen
            </button>
          </div>
        </div>

        <div v-if="error" class="inline-error">{{ error }}</div>
        <div class="form-actions">
          <button class="btn-save" :disabled="registering || !isFormValid()" @click="submitForm">
            {{ registering ? 'Registrieren…' : 'Registrieren' }}
          </button>
          <button class="btn-cancel" @click="showForm = false">Abbrechen</button>
        </div>
      </div>

      <div v-if="loading && firmwareConfigs.length === 0" class="loading-state">
        Lade Firmware-Konfigurationen…
      </div>

      <div v-else-if="firmwareConfigs.length === 0 && !loading" class="empty-state">
        <p>Keine Firmware-Konfigurationen registriert.</p>
      </div>

      <div v-else class="firmware-grid">
        <div v-for="fc in firmwareConfigs" :key="fc.id" class="firmware-card">
          <div class="firmware-header">
            <h3 class="firmware-version">v{{ fc.version }}</h3>
            <span class="firmware-badge">{{ fc.boxType }}</span>
          </div>
          <div class="signal-types-list">
            <div v-for="st in fc.signalTypes" :key="st.id" class="signal-type-item">
              <div class="signal-type-label">{{ st.device }} - {{ st.communicationDirection }}</div>
              <div class="signal-type-command">{{ st.command }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.view {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.view-container {
  max-width: 1000px;
}

.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--sg-border);
}

.view-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--sg-brand);
  margin: 0;
}

.btn-primary {
  padding: 0.5rem 1rem;
  background: var(--sg-brand);
  color: #fff;
  border: none;
  border-radius: 7px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 500;
  font-family: inherit;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.success-banner {
  background: var(--sg-color-success-bg);
  border: 1px solid color-mix(in srgb, var(--sg-color-success) 40%, transparent);
  color: var(--sg-color-success-text);
  padding: 0.75rem 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.form-card {
  background: var(--sg-bg-card);
  border-radius: 10px;
  box-shadow: var(--sg-shadow-sm);
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  border: 1px solid var(--sg-border);
}

.form-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--sg-brand);
  margin: 0 0 1.5rem 0;
}

.form-content {
  margin-bottom: 1.5rem;
}

.form-section {
  margin-bottom: 1.5rem;
}

.form-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--sg-text-muted);
  margin: 0 0 1rem 0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--sg-text-muted);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-label {
  font-size: 0.85rem;
  color: var(--sg-text-muted);
  font-weight: 500;
}

.form-input {
  padding: 0.5rem 0.75rem;
  border: 1.5px solid var(--sg-border);
  border-radius: 6px;
  font-size: 0.9rem;
  font-family: inherit;
}

.form-input:focus {
  outline: none;
  border-color: var(--sg-accent);
}

.signal-type-block {
  background: var(--sg-bg-panel);
  border: 1px solid var(--sg-border);
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.signal-type-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--sg-text-muted);
}

.btn-remove {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-color-danger-bg);
  color: var(--sg-color-danger);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
}

.btn-remove:hover {
  background: var(--sg-color-danger-bg);
}

.btn-add-signal {
  padding: 0.5rem 1rem;
  background: var(--sg-bg-panel);
  color: var(--sg-text-muted);
  border: 1px solid var(--sg-border-input);
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 500;
  font-family: inherit;
}

.btn-add-signal:hover {
  background: var(--sg-border);
}

.inline-error {
  color: var(--sg-color-danger);
  font-size: 0.85rem;
  margin-bottom: 0.75rem;
  padding: 0.5rem;
  background: var(--sg-color-danger-bg);
  border-left: 3px solid var(--sg-color-danger-bg);
  padding-left: 0.75rem;
}

.form-actions {
  display: flex;
  gap: 8px;
}

.btn-save {
  padding: 0.5rem 1rem;
  background: var(--sg-brand);
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 500;
  font-family: inherit;
}

.btn-save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-cancel {
  padding: 0.5rem 1rem;
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--sg-text-muted);
  font-family: inherit;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 4rem 2rem;
  color: var(--sg-text-muted);
}

.firmware-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
}

.firmware-card {
  background: var(--sg-bg-card);
  border-radius: 10px;
  box-shadow: var(--sg-shadow-sm);
  padding: 1.25rem;
  border: 1px solid var(--sg-border);
}

.firmware-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--sg-border);
}

.firmware-version {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--sg-brand);
  margin: 0;
}

.firmware-badge {
  font-size: 0.75rem;
  font-weight: 600;
  background: var(--sg-color-purple-bg);
  color: var(--sg-color-purple-text);
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  text-transform: uppercase;
}

.signal-types-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.signal-type-item {
  background: var(--sg-bg-panel);
  padding: 0.75rem;
  border-radius: 6px;
  border-left: 3px solid var(--sg-accent);
}

.signal-type-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--sg-text-muted);
  margin-bottom: 0.25rem;
}

.signal-type-command {
  font-size: 0.8rem;
  color: var(--sg-text-muted);
  font-family: 'Courier New', monospace;
}
</style>

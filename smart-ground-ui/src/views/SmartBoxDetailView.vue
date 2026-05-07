<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import Button from '@/components/Button.vue'
import { useSmartBoxStore } from '@/stores/smartBoxStore.js'
import { useDeviceStore } from '@/stores/deviceStore.js'
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js'

const props = defineProps({ id: { type: String, required: true } })
const router = useRouter()

const smartBoxStore = useSmartBoxStore()
const deviceStore = useDeviceStore()
const deviceTypeStore = useDeviceTypeStore()

const box = computed(() => smartBoxStore.smartboxes.find(b => b.id === props.id) ?? null)
const devices = computed(() => deviceStore.devices.filter(d => (d.boxId ?? d.smartBoxId) === props.id))
const deviceCount = computed(() => devices.value.length)
const deviceTypeGroups = computed(() => deviceTypeStore.deviceTypeGroups || [])
const deviceTypesForGroup = computed(() => {
  if (!selectedGroupId.value) return []
  return deviceTypeStore.deviceTypes.filter(dt => dt.groupId === selectedGroupId.value)
})
const loading = computed(() => smartBoxStore.isLoading || deviceStore.isLoading || deviceTypeStore.isLoading)

const editingAlias = ref(false)
const aliasDraft = ref('')
const savingAlias = ref(false)
const aliasError = ref(null)

const showRegisterForm = ref(false)
const registerLoading = ref(false)
const registerError = ref(null)
const selectedGroupId = ref('')
const selectedDeviceTypeId = ref('')
const deviceAlias = ref('')
const delaySignalDurationMs = ref(0)

function startAliasEdit() {
  aliasDraft.value = box.value?.alias ?? ''
  editingAlias.value = true
  aliasError.value = null
}
function cancelAliasEdit() { editingAlias.value = false; aliasError.value = null }
async function saveAlias() {
  if (!aliasDraft.value.trim()) return
  savingAlias.value = true; aliasError.value = null
  try {
    smartBoxStore.updateSmartBox(props.id, { alias: aliasDraft.value.trim() })
    await smartBoxStore.saveSmartBox(props.id)
    editingAlias.value = false
  } catch (e) {
    aliasError.value = e.message
  } finally {
    savingAlias.value = false
  }
}

function openRegisterForm() {
  showRegisterForm.value = true
  registerError.value = null
  selectedGroupId.value = deviceTypeGroups.value[0]?.id ?? ''
  selectedDeviceTypeId.value = ''
  deviceAlias.value = ''
  delaySignalDurationMs.value = 0
}
function cancelRegister() { showRegisterForm.value = false; registerError.value = null }

async function submitRegister() {
  if (!selectedGroupId.value || !selectedDeviceTypeId.value || !deviceAlias.value.trim()) return
  registerLoading.value = true; registerError.value = null
  try {
    await deviceStore.registerDevice(props.id, {
      groupId: selectedGroupId.value,
      deviceTypeId: selectedDeviceTypeId.value,
      alias: deviceAlias.value.trim(),
      delaySignalDurationMs: Number(delaySignalDurationMs.value) || null,
    })
    await smartBoxStore.loadApiData()
    cancelRegister()
  } catch (e) {
    registerError.value = e.message
  } finally {
    registerLoading.value = false
  }
}

function formatDate(dateStr) {
  if (!dateStr) return '–'
  return new Date(dateStr).toLocaleString('de-CH', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}
</script>

<template>
  <div class="view">
    <button class="btn-back" @click="router.push('/smartboxes')">← Zurück zur Liste</button>

    <div v-if="loading" class="loading-state">Lade SmartBox…</div>

    <template v-else-if="box">
      <div class="view-header">
        <div class="view-title-group">
          <h1 class="view-title">{{ box.alias ?? 'Unbenannte SmartBox' }}</h1>
          <span class="cell-mono subtitle">{{ box.macAddress }}</span>
        </div>
        <span class="status-badge" :class="box.status?.toLowerCase()">{{ box.status }}</span>
      </div>

      <section class="card">
        <h2 class="card-title">SmartBox-Daten</h2>
        <dl class="detail-grid">
          <div class="detail-row">
            <dt>ID</dt>
            <dd class="cell-mono">{{ box.id }}</dd>
          </div>
          <div class="detail-row">
            <dt>MAC-Adresse</dt>
            <dd class="cell-mono">{{ box.macAddress }}</dd>
          </div>
          <div class="detail-row">
            <dt>Name / Alias</dt>
            <dd>
              <template v-if="!editingAlias">
                {{ box.alias ?? '–' }}
                <Button variant="ghost" size="icon-only" aria-label="Bearbeiten" style="display: inline-flex; height: auto; width: auto; padding: 0.2rem 0.4rem; margin-left: 0.5rem;" @click="startAliasEdit">✎</Button>
              </template>
              <template v-else>
                <input v-model="aliasDraft" class="inline-input" maxlength="100" />
                <Button variant="primary" size="sm" style="margin-right: 0.25rem;" :disabled="savingAlias || !aliasDraft.trim()" @click="saveAlias">
                  {{ savingAlias ? '…' : 'Speichern' }}
                </Button>
                <Button variant="ghost" size="sm" @click="cancelAliasEdit">Abbrechen</Button>
                <span v-if="aliasError" class="inline-error">{{ aliasError }}</span>
              </template>
            </dd>
          </div>
          <div class="detail-row">
            <dt>Firmware</dt>
            <dd>{{ box.firmwareVersion ?? '–' }}</dd>
          </div>
          <div class="detail-row">
            <dt>Config-Sync</dt>
            <dd>
              <span class="sync-badge" :class="box.configSynced ? 'synced' : 'unsynced'">
                {{ box.configSynced ? '✓ Synchronisiert' : '⚠ Ausstehend' }}
              </span>
            </dd>
          </div>
          <div class="detail-row">
            <dt>Geräte</dt>
            <dd>{{ deviceCount }} verbunden</dd>
          </div>
        </dl>
      </section>

      <section class="card">
        <div class="card-header">
          <h2 class="card-title">Registrierte Geräte ({{ devices.length }})</h2>
          <Button variant="primary" :disabled="showRegisterForm" @click="openRegisterForm">
            + Gerät hinzufügen
          </Button>
        </div>

        <div v-if="showRegisterForm" class="register-form">
          <h3 class="form-subtitle">Neues Gerät registrieren</h3>
          <p class="form-hint">
            Wähle einen Gerätetyp und gib einen eindeutigen Namen an.
          </p>

          <div class="form-grid">
            <div class="form-field">
              <label class="form-label">Gerätegruppe *</label>
              <select v-model="selectedGroupId" class="form-select">
                <option disabled value="">– Gerätegruppe wählen –</option>
                <option v-for="g in deviceTypeGroups" :key="g.id" :value="g.id">
                  {{ g.name }}
                </option>
              </select>
            </div>

            <div class="form-field">
              <label class="form-label">Gerätetyp *</label>
              <select v-model="selectedDeviceTypeId" class="form-select" :disabled="!selectedGroupId || deviceTypesForGroup.length === 0">
                <option disabled value="">– Gerätetyp wählen –</option>
                <option v-for="dt in deviceTypesForGroup" :key="dt.id" :value="dt.id">
                  {{ dt.name }}
                </option>
              </select>
            </div>

            <div class="form-field">
              <label class="form-label">Alias *</label>
              <input v-model="deviceAlias" type="text" class="form-input"
                     placeholder="z.B. Werfer 1" maxlength="100" />
            </div>

            <div class="form-field">
              <label class="form-label">Verzögerung (ms, optional)</label>
              <input v-model.number="delaySignalDurationMs" type="number" class="form-input"
                     placeholder="0" min="0" />
              <span class="form-hint-small">Verzögerung vor dem Auslösen des Geräts</span>
            </div>
          </div>

          <div v-if="registerError" class="inline-error">⚠️ {{ registerError }}</div>

          <div class="form-actions">
            <Button variant="primary"
                    :disabled="registerLoading || !selectedGroupId || !selectedDeviceTypeId || !deviceAlias.trim()"
                    @click="submitRegister">
              {{ registerLoading ? 'Registrieren…' : 'Gerät registrieren' }}
            </Button>
            <Button variant="ghost" @click="cancelRegister">Abbrechen</Button>
          </div>
        </div>

        <div v-if="devices.length === 0 && !showRegisterForm" class="empty-state-small">
          Noch keine Geräte registriert. Füge Werfer, Knöpfe oder Sensoren über
          „+ Gerät hinzufügen" hinzu.
        </div>

        <table v-else-if="devices.length > 0" class="device-table">
          <thead>
            <tr>
              <th>Alias</th>
              <th>Gerätetyp</th>
              <th>Verzögerung</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="device in devices" :key="device.id" class="device-row">
              <td class="cell-name">{{ device.alias }}</td>
              <td>{{ device.deviceTypeGroup?.name ?? device.groupId ?? '–' }}</td>
              <td class="cell-mono">{{ device.delaySignalDurationMs ? device.delaySignalDurationMs + ' ms' : '–' }}</td>
              <td>
                <span v-if="device.blocked" class="status-badge offline">GESPERRT</span>
                <span v-else-if="device.healthy" class="status-badge online">OK</span>
                <span v-else class="status-badge" style="background:#fed7d7;color:#9b2c2c">FEHLER</span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>

<style scoped>
.view { max-width: 780px; margin: 0 auto; }
.btn-back { background: none; border: none; color: #4a5568; cursor: pointer;
            font-size: 0.9rem; padding: 0; margin-bottom: 1.5rem; }
.btn-back:hover { color: #1a1a2e; }
.view-header { display: flex; align-items: center; justify-content: space-between;
               margin-bottom: 1.5rem; }
.view-title-group { display: flex; flex-direction: column; gap: 0.2rem; }
.view-title { font-size: 1.4rem; font-weight: 700; color: #1a1a2e; margin: 0; }
.subtitle { font-size: 0.85rem; color: #718096; }
.status-badge { display: inline-block; padding: 0.25rem 0.75rem; border-radius: 999px;
              font-size: 0.8rem; font-weight: 600; text-transform: uppercase;
              letter-spacing: 0.05em; background: #e2e8f0; color: #4a5568; }
.status-badge.online  { background: #c6f6d5; color: #276749; }
.status-badge.offline { background: #fed7d7; color: #9b2c2c; }

.card { background: #fff; border-radius: 10px; box-shadow: 0 1px 4px rgba(0,0,0,0.08);
        padding: 1.5rem; margin-bottom: 1.5rem; }
.card-header { display: flex; align-items: center; justify-content: space-between;
              margin-bottom: 1rem; padding-bottom: 0.75rem; border-bottom: 1px solid #e2e8f0; }
.card-title { font-size: 1rem; font-weight: 600; color: #2d3748; margin: 0; }

.detail-grid { display: grid; gap: 0; margin: 0; }
.detail-row { display: grid; grid-template-columns: 160px 1fr;
              padding: 0.6rem 0; border-bottom: 1px solid #f0f4f8; }
.detail-row:last-child { border-bottom: none; }
.detail-row dt { font-size: 0.85rem; color: #718096; font-weight: 500; }
.detail-row dd { font-size: 0.875rem; color: #2d3748; margin: 0; }

.cell-mono { font-family: 'Courier New', monospace; font-size: 0.85rem; }
.cell-name { font-weight: 500; }

.sync-badge { font-size: 0.8rem; font-weight: 500; padding: 0.15rem 0.5rem; border-radius: 4px; display: inline-block; }
.sync-badge.synced   { background: #e6fffa; color: #276749; }
.sync-badge.unsynced { background: #fffbeb; color: #b7791f; }

.register-form { background: #f7fafc; border: 1px solid #e2e8f0; border-radius: 8px;
                 padding: 1.25rem; margin-bottom: 1.25rem; }
.form-subtitle { font-size: 0.95rem; font-weight: 600; color: #2d3748; margin: 0 0 0.5rem 0; }
.form-hint { font-size: 0.85rem; color: #718096; margin: 0 0 1rem 0; }
.form-hint-small { font-size: 0.78rem; color: #a0aec0; margin-top: 0.2rem; display: block; }

.form-grid { display: grid; grid-template-columns: 1fr; gap: 1rem; margin-bottom: 1rem; }
.form-field { display: flex; flex-direction: column; gap: 0.3rem; }
.form-label { font-size: 0.85rem; font-weight: 500; color: #4a5568; }

.form-input, .form-select { padding: 0.45rem 0.65rem; border: 1px solid #cbd5e0;
                          border-radius: 6px; font-size: 0.9rem; color: #2d3748;
                          outline: none; background: #fff; }
.form-input:focus, .form-select:focus { border-color: #4fc3f7;
                                     box-shadow: 0 0 0 3px rgba(79, 195, 247, 0.15); }

.form-actions { display: flex; gap: 0.5rem; }

.empty-state-small { padding: 1.5rem; text-align: center; color: #a0aec0; font-size: 0.875rem; }

.device-table { width: 100%; border-collapse: collapse; font-size: 0.875rem; margin-top: 0.5rem; }
.device-table th { text-align: left; padding: 0.6rem 0.75rem; font-size: 0.8rem; font-weight: 600;
                color: #4a5568; text-transform: uppercase; letter-spacing: 0.04em;
                border-bottom: 2px solid #e2e8f0; }
.device-row td { padding: 0.75rem 0.75rem; border-bottom: 1px solid #f0f4f8; color: #2d3748; }
.device-row:last-child td { border-bottom: none; }

.inline-input { padding: 0.3rem 0.5rem; border: 1px solid #cbd5e0; border-radius: 4px;
              font-size: 0.875rem; width: 200px; margin-right: 0.5rem; }

.inline-error { display: block; color: #c53030; font-size: 0.8rem; margin-top: 0.25rem; }
.loading-state { text-align: center; padding: 4rem 2rem; color: #718096; }
.error-banner { background: #fff5f5; border: 1px solid #fc8181; color: #c53030;
              padding: 0.75rem 1rem; border-radius: 8px; }
</style>
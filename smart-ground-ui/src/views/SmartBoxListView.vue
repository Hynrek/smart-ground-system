<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSmartBoxStore } from '@/stores/smartBoxStore.js'

const router = useRouter()
const smartBoxStore = useSmartBoxStore()

const boxes = computed(() => smartBoxStore.smartboxes)
const loading = computed(() => smartBoxStore.isLoading)

const editingId = ref(null)
const aliasDraft = ref('')
const savingAlias = ref(false)
const aliasError = ref(null)

const grouped = computed(() => {
  const online  = boxes.value.filter(b => b.status === 'ONLINE')
  const offline = boxes.value.filter(b => b.status === 'OFFLINE')
  const pending = boxes.value.filter(b => b.status === 'UNCONFIGURED')
  return { online, offline, pending }
})

async function refresh() {
  await smartBoxStore.loadApiData()
}

function startEdit(box) {
  editingId.value  = box.id
  aliasDraft.value = box.alias ?? ''
  aliasError.value = null
}

function cancelEdit() {
  editingId.value  = null
  aliasDraft.value = ''
  aliasError.value = null
}

async function saveAlias(box) {
  if (!aliasDraft.value.trim()) return
  savingAlias.value = true
  aliasError.value  = null
  try {
    smartBoxStore.updateSmartBox(box.id, { alias: aliasDraft.value.trim() })
    await smartBoxStore.saveSmartBox(box.id)
    cancelEdit()
  } catch (e) {
    aliasError.value = e.message
  } finally {
    savingAlias.value = false
  }
}
</script>

<template>
  <div class="view">
    <div class="view-header">
      <h1 class="view-title">SmartBoxen</h1>
      <div class="header-actions">
        <button class="btn-secondary" @click="router.push('/ranges')">Plätze verwalten</button>
        <button class="btn-refresh" :disabled="loading" @click="refresh">
          {{ loading ? 'Laden…' : '↻ Aktualisieren' }}
        </button>
      </div>
    </div>

    <div v-if="loading && boxes.length === 0" class="loading-state">Lade SmartBoxen…</div>

    <div v-else-if="boxes.length === 0 && !loading" class="empty-state">
      <p>Keine SmartBoxen gefunden.</p>
      <p class="empty-hint">
        Starte eine SmartBox (Pico 2W) – sie meldet sich automatisch am Backend an.
      </p>
    </div>

    <template v-else>
      <div v-if="grouped.pending.length" class="section">
        <h2 class="section-title">
          Neu entdeckt
          <span class="badge badge-warning">{{ grouped.pending.length }}</span>
        </h2>
        <p class="section-hint">
          Diese SmartBoxen wurden automatisch erkannt. Vergib ihnen einen Namen, um sie zu konfigurieren.
        </p>
        <div class="table-wrapper">
          <table class="box-table">
            <thead>
              <tr>
                <th>Name / Alias</th>
                <th>MAC-Adresse</th>
                <th>Firmware</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="box in grouped.pending" :key="box.id" class="box-row">
                <td>
                  <template v-if="editingId === box.id">
                    <input v-model="aliasDraft" class="inline-input" maxlength="100"
                           placeholder="z.B. SmartBox-Stand-A" />
                    <button class="btn-save-inline" :disabled="savingAlias || !aliasDraft.trim()" @click="saveAlias(box)">
                      Speichern
                    </button>
                    <button class="btn-cancel-inline" @click="cancelEdit">Abbrechen</button>
                    <span v-if="aliasError" class="inline-error">{{ aliasError }}</span>
                  </template>
                  <template v-else>
                    <span class="no-alias">Nicht benannt</span>
                    <button class="btn-edit-inline" title="Namen vergeben" @click="startEdit(box)">✎ Benennen</button>
                  </template>
                </td>
                <td class="cell-mono">{{ box.macAddress }}</td>
                <td class="cell-secondary">{{ box.firmwareVersion ?? '–' }}</td>
                <td>
                  <span class="status-badge unconfigured">NEU</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="grouped.online.length" class="section">
        <h2 class="section-title">
          Online
          <span class="badge badge-online">{{ grouped.online.length }}</span>
        </h2>
        <div class="table-wrapper">
          <table class="box-table">
            <thead>
              <tr>
                <th>Name / Alias</th>
                <th>MAC-Adresse</th>
                <th>Firmware</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="box in grouped.online" :key="box.id" class="box-row clickable" @click="router.push(`/smart-boxes/${box.id}`)">
                <td>
                  <template v-if="editingId === box.id">
                    <input v-model="aliasDraft" class="inline-input" maxlength="100"
                           placeholder="z.B. SmartBox-Stand-A" />
                    <button class="btn-save-inline" :disabled="savingAlias || !aliasDraft.trim()" @click="saveAlias(box)">
                      Speichern
                    </button>
                    <button class="btn-cancel-inline" @click="cancelEdit">Abbrechen</button>
                    <span v-if="aliasError" class="inline-error">{{ aliasError }}</span>
                  </template>
                  <template v-else>
                    <span class="alias-name">{{ box.alias }}</span>
                    <button class="btn-edit-inline" title="Bearbeiten" @click.stop="startEdit(box)">✎</button>
                  </template>
                </td>
                <td class="cell-mono">{{ box.macAddress }}</td>
                <td class="cell-secondary">{{ box.firmwareVersion ?? '–' }}</td>
                <td>
                  <span class="status-badge online">ONLINE</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="grouped.offline.length" class="section">
        <h2 class="section-title">
          Offline
          <span class="badge badge-offline">{{ grouped.offline.length }}</span>
        </h2>
        <div class="table-wrapper">
          <table class="box-table">
            <thead>
              <tr>
                <th>Name / Alias</th>
                <th>MAC-Adresse</th>
                <th>Firmware</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="box in grouped.offline" :key="box.id" class="box-row clickable" @click="router.push(`/smart-boxes/${box.id}`)">
                <td>
                  <template v-if="editingId === box.id">
                    <input v-model="aliasDraft" class="inline-input" maxlength="100"
                           placeholder="z.B. SmartBox-Stand-A" />
                    <button class="btn-save-inline" :disabled="savingAlias || !aliasDraft.trim()" @click="saveAlias(box)">
                      Speichern
                    </button>
                    <button class="btn-cancel-inline" @click="cancelEdit">Abbrechen</button>
                    <span v-if="aliasError" class="inline-error">{{ aliasError }}</span>
                  </template>
                  <template v-else>
                    <span class="alias-name">{{ box.alias }}</span>
                    <button class="btn-edit-inline" title="Bearbeiten" @click.stop="startEdit(box)">✎</button>
                  </template>
                </td>
                <td class="cell-mono">{{ box.macAddress }}</td>
                <td class="cell-secondary">{{ box.firmwareVersion ?? '–' }}</td>
                <td>
                  <span class="status-badge offline">OFFLINE</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <p class="table-footer">{{ boxes.length }} SmartBox{{ boxes.length !== 1 ? 'en' : '' }} insgesamt</p>
    </template>
  </div>
</template>

<style scoped>
.view { max-width: 900px; margin: 0 auto; }
.view-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.5rem; }
.view-title { font-size: 1.5rem; font-weight: 700; color: #1a1a2e; margin: 0; }
.header-actions { display: flex; gap: 0.75rem; }
.btn-secondary { padding: 0.4rem 1rem; background: #fff; color: #1a1a2e; border: 1px solid #cbd5e0;
               border-radius: 6px; cursor: pointer; font-size: 0.875rem; }
.btn-secondary:hover { background: #f7fafc; }
.btn-refresh { padding: 0.4rem 1rem; background: #1a1a2e; color: #fff; border: none;
              border-radius: 6px; cursor: pointer; font-size: 0.875rem; }
.btn-refresh:hover:not(:disabled) { background: #2d3a6e; }
.btn-refresh:disabled { opacity: 0.5; cursor: default; }

.error-banner { background: #fff5f5; border: 1px solid #fc8181; color: #c53030;
                 padding: 0.75rem 1rem; border-radius: 8px; margin-bottom: 1rem; }
.loading-state { text-align: center; padding: 4rem 2rem; color: #718096; }
.empty-state { text-align: center; padding: 3rem 2rem; color: #718096; }
.empty-hint { font-size: 0.875rem; margin-top: 0.5rem; }

.section { margin-bottom: 2.5rem; }
.section-title { font-size: 1.1rem; font-weight: 600; color: #2d3748; margin: 0 0 0.5rem 0;
               display: flex; align-items: center; gap: 0.5rem; }
.section-hint { font-size: 0.85rem; color: #718096; margin: 0 0 0.75rem 0; }

.badge { font-size: 0.75rem; font-weight: 600; padding: 0.15rem 0.5rem; border-radius: 999px; }
.badge-warning    { background: #fefcbf; color: #744210; }
.badge-online     { background: #c6f6d5; color: #276749; }
.badge-offline    { background: #fed7d7; color: #9b2c2c; }

.table-wrapper { background: #fff; border-radius: 10px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); overflow: hidden; }
.box-table { width: 100%; border-collapse: collapse; }
.box-table th { text-align: left; padding: 0.75rem 1rem; font-size: 0.8rem; font-weight: 600;
                color: #4a5568; text-transform: uppercase; letter-spacing: 0.04em; border-bottom: 2px solid #e2e8f0; }
.box-table td { padding: 0.75rem 1rem; font-size: 0.875rem; color: #2d3748; border-bottom: 1px solid #f0f4f8; }
.box-row:last-child td { border-bottom: none; }
.box-row.clickable { cursor: pointer; }
.box-row.clickable:hover { background: #f7fafc; }

.cell-mono { font-family: 'Courier New', monospace; font-size: 0.85rem; }
.cell-secondary { color: #718096; }

.status-badge { display: inline-block; padding: 0.2rem 0.6rem; border-radius: 999px; font-size: 0.75rem; font-weight: 600; }
.status-badge.unconfigured { background: #fefcbf; color: #744210; }
.status-badge.online  { background: #c6f6d5; color: #276749; }
.status-badge.offline { background: #fed7d7; color: #9b2c2c; }

.no-alias { color: #a0aec0; font-style: italic; font-size: 0.875rem; }
.alias-name { font-weight: 500; }

.btn-edit-inline { background: none; border: 1px solid #cbd5e0; border-radius: 4px; color: #4a5568;
                 cursor: pointer; font-size: 0.8rem; margin-left: 0.5rem; padding: 0.2rem 0.5rem; }
.btn-edit-inline:hover { background: #edf2f7; }

.inline-input { padding: 0.3rem 0.5rem; border: 1px solid #cbd5e0; border-radius: 4px;
               font-size: 0.875rem; width: 200px; margin-right: 0.4rem; }

.btn-save-inline, .btn-cancel-inline { padding: 0.25rem 0.6rem; border-radius: 4px; font-size: 0.8rem;
                                 cursor: pointer; margin-right: 0.25rem; }
.btn-save-inline  { background: #1a1a2e; color: #fff; border: none; }
.btn-cancel-inline { background: #fff; border: 1px solid #cbd5e0; color: #4a5568; }
.btn-save-inline:disabled { opacity: 0.5; cursor: default; }

.inline-error { display: block; color: #c53030; font-size: 0.8rem; margin-top: 0.25rem; }

.table-footer { text-align: right; font-size: 0.85rem; color: #718096; margin-top: 0.5rem; }
</style>
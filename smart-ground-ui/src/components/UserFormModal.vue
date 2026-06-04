<template>
  <div class="modal-overlay" @mousedown="onOverlayMouseDown" @click="onOverlayClick">
    <div class="modal-container">
      <div class="modal-header">
        <h2>{{ mode === 'create' ? 'Neuer Benutzer' : 'Benutzer bearbeiten' }}</h2>
        <button class="close-btn" aria-label="Schließen" @click="$emit('close')">✕</button>
      </div>

      <div class="modal-tabs" role="tablist">
        <button
          data-testid="tab-base"
          role="tab"
          :class="['tab', { active: activeTab === 'base' }]"
          :aria-selected="activeTab === 'base'"
          @click="activeTab = 'base'"
        >Basisdaten</button>
        <button
          data-testid="tab-extended"
          role="tab"
          :class="['tab', { active: activeTab === 'extended' }]"
          :aria-selected="activeTab === 'extended'"
          @click="activeTab = 'extended'"
        >Erweitert</button>
      </div>

      <!-- Tab 1: Basisdaten -->
      <div v-show="activeTab === 'base'" class="tab-content" role="tabpanel">
        <div class="form-row">
          <div class="form-group">
            <label for="input-vorname">Vorname *</label>
            <input
              id="input-vorname"
              v-model="form.vorname"
              data-testid="input-vorname"
              type="text"
              :class="{ 'input-error': errors.vorname }"
              :disabled="userStore.isLoading"
            />
            <span v-if="errors.vorname" data-testid="error-vorname" class="error-msg">{{ errors.vorname }}</span>
          </div>
          <div class="form-group">
            <label for="input-nachname">Nachname *</label>
            <input
              id="input-nachname"
              v-model="form.nachname"
              data-testid="input-nachname"
              type="text"
              :class="{ 'input-error': errors.nachname }"
              :disabled="userStore.isLoading"
            />
            <span v-if="errors.nachname" data-testid="error-nachname" class="error-msg">{{ errors.nachname }}</span>
          </div>
        </div>

        <div class="form-group">
          <label for="input-email">E-Mail *</label>
          <input
            id="input-email"
            v-model="form.email"
            data-testid="input-email"
            type="email"
            :class="{ 'input-error': errors.email }"
            :disabled="mode === 'edit' || userStore.isLoading"
          />
          <span v-if="errors.email" data-testid="error-email" class="error-msg">{{ errors.email }}</span>
        </div>

        <div v-if="mode === 'create'" class="form-group">
          <label for="input-password">Passwort *</label>
          <input
            id="input-password"
            v-model="form.password"
            data-testid="input-password"
            type="password"
            :class="{ 'input-error': errors.password }"
            :disabled="userStore.isLoading"
          />
          <span v-if="errors.password" data-testid="error-password" class="error-msg">{{ errors.password }}</span>
        </div>

        <div v-if="mode === 'create'" class="form-group">
          <label for="input-role">Rolle *</label>
          <select
            id="input-role"
            v-model="form.role"
            data-testid="input-role"
            :disabled="userStore.isLoading"
          >
            <option
              v-for="role in userStore.availableRoles"
              :key="role.name"
              :value="role.name"
            >{{ ROLE_LABELS[role.name] || role.name }}</option>
          </select>
          <span class="field-hint">Standard: Schütze</span>
        </div>
      </div>

      <!-- Tab 2: Erweitert -->
      <div v-show="activeTab === 'extended'" class="tab-content" role="tabpanel">
        <div class="form-row">
          <div class="form-group">
            <label for="input-geburtsdatum">Geburtsdatum</label>
            <input
              id="input-geburtsdatum"
              v-model="form.geburtsdatum"
              data-testid="input-geburtsdatum"
              type="date"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group">
            <label for="input-geschlecht">Geschlecht</label>
            <select
              id="input-geschlecht"
              v-model="form.geschlecht"
              data-testid="input-geschlecht"
              :disabled="userStore.isLoading"
            >
              <option value="">—</option>
              <option value="MAENNLICH">Männlich</option>
              <option value="WEIBLICH">Weiblich</option>
              <option value="DIVERS">Divers</option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label for="input-telefonnummer">Telefonnummer</label>
          <input
            id="input-telefonnummer"
            v-model="form.telefonnummer"
            data-testid="input-telefonnummer"
            type="tel"
            placeholder="+41 79 ..."
            :disabled="userStore.isLoading"
          />
        </div>

        <div class="form-row form-row--address">
          <div class="form-group form-group--street">
            <label for="input-strasse">Strasse</label>
            <input
              id="input-strasse"
              v-model="form.strasse"
              data-testid="input-strasse"
              type="text"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group form-group--housenr">
            <label for="input-hausnummer">Nr.</label>
            <input
              id="input-hausnummer"
              v-model="form.hausnummer"
              data-testid="input-hausnummer"
              type="text"
              :disabled="userStore.isLoading"
            />
          </div>
        </div>

        <div class="form-row form-row--city">
          <div class="form-group form-group--plz">
            <label for="input-plz">PLZ</label>
            <input
              id="input-plz"
              v-model="form.plz"
              data-testid="input-plz"
              type="text"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group form-group--city-name">
            <label for="input-stadt">Stadt</label>
            <input
              id="input-stadt"
              v-model="form.stadt"
              data-testid="input-stadt"
              type="text"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group form-group--land">
            <label for="input-land">Land</label>
            <input
              id="input-land"
              v-model="form.land"
              data-testid="input-land"
              type="text"
              placeholder="CH"
              :disabled="userStore.isLoading"
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="input-mitgliedsnummer">Mitgliedsnummer</label>
            <input
              id="input-mitgliedsnummer"
              v-model="form.mitgliedsnummer"
              data-testid="input-mitgliedsnummer"
              type="text"
              placeholder="SG-0001"
              :disabled="userStore.isLoading"
            />
          </div>
          <div class="form-group">
            <label for="input-sprache">Sprache</label>
            <select
              id="input-sprache"
              v-model="form.sprache"
              data-testid="input-sprache"
              :disabled="userStore.isLoading"
            >
              <option value="de">Deutsch</option>
              <option value="fr">Français</option>
              <option value="it">Italiano</option>
              <option value="en">English</option>
            </select>
          </div>
        </div>
      </div>

      <div class="modal-footer">
        <span class="field-hint">* Pflichtfelder</span>
        <div class="footer-actions">
          <button
            data-testid="cancel-btn"
            class="btn btn-secondary"
            :disabled="userStore.isLoading"
            @click="$emit('close')"
          >Abbrechen</button>
          <button
            data-testid="submit-btn"
            class="btn btn-primary"
            :disabled="userStore.isLoading"
            @click="submit"
          >{{ userStore.isLoading ? 'Wird gespeichert...' : (mode === 'create' ? 'Erstellen' : 'Speichern') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch, onMounted } from 'vue'
import { useUserStore } from '@/stores/userStore.js'

const props = defineProps({
  mode: { type: String, default: 'create' },
  initialUser: { type: Object, default: null },
})
const emit = defineEmits(['close', 'saved'])

const userStore = useUserStore()
const ROLE_LABELS = { ADMIN: 'Admin', SHOOTER: 'Schütze', OWNER: 'Bereichsleiter' }
const activeTab = ref('base')

let overlayMouseDownStarted = false
function onOverlayMouseDown(e) {
  overlayMouseDownStarted = e.target === e.currentTarget
}
function onOverlayClick(e) {
  if (overlayMouseDownStarted && e.target === e.currentTarget) emit('close')
  overlayMouseDownStarted = false
}

onMounted(() => {
  if (userStore.availableRoles.length === 0) {
    userStore.loadAvailableRoles()
  }
})

const form = reactive({
  vorname: '',
  nachname: '',
  email: '',
  password: '',
  role: 'SHOOTER',
  geburtsdatum: '',
  geschlecht: '',
  telefonnummer: '',
  strasse: '',
  hausnummer: '',
  plz: '',
  stadt: '',
  land: '',
  mitgliedsnummer: '',
  sprache: 'de',
})

const errors = reactive({})

watch(
  () => props.initialUser,
  (user) => {
    if (props.mode === 'edit' && user) {
      Object.assign(form, {
        vorname: user.vorname ?? '',
        nachname: user.nachname ?? '',
        email: user.email ?? '',
        geburtsdatum: user.geburtsdatum ?? '',
        geschlecht: user.geschlecht ?? '',
        telefonnummer: user.telefonnummer ?? '',
        strasse: user.strasse ?? '',
        hausnummer: user.hausnummer ?? '',
        plz: user.plz ?? '',
        stadt: user.stadt ?? '',
        land: user.land ?? '',
        mitgliedsnummer: user.mitgliedsnummer ?? '',
        sprache: user.sprache ?? 'de',
      })
  }
  },
  { immediate: true }
)

function validate() {
  Object.keys(errors).forEach((k) => delete errors[k])
  if (!form.vorname.trim()) errors.vorname = 'Pflichtfeld'
  if (!form.nachname.trim()) errors.nachname = 'Pflichtfeld'
  if (!form.email.trim()) errors.email = 'Pflichtfeld'
  if (props.mode === 'create' && !form.password) errors.password = 'Pflichtfeld'
  return Object.keys(errors).length === 0
}

function cleanPayload(obj) {
  return Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== '' && v !== null && v !== undefined)
  )
}

async function submit() {
  if (!validate()) {
    activeTab.value = 'base'
    return
  }
  try {
    if (props.mode === 'create') {
      const { role, ...raw } = { ...form }
      await userStore.createUser(cleanPayload(raw), role)
    } else {
      const { password, role, email, ...raw } = { ...form }
      await userStore.updateUser(props.initialUser.id, cleanPayload(raw))
    }
    emit('saved')
  } catch {
    // error surfaced via userStore.error
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-container {
  background: white;
  border-radius: 8px;
  width: 540px;
  max-width: 95vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.25rem 1.5rem 0;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.1rem;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.1rem;
  cursor: pointer;
  color: #6b7280;
  padding: 0.25rem;
}

.modal-tabs {
  display: flex;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 1.5rem;
  margin-top: 1rem;
}

.tab {
  padding: 0.6rem 1.25rem;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 0.9rem;
  color: #6b7280;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tab.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
  font-weight: 600;
}

.tab-content {
  padding: 1.25rem 1.5rem;
  overflow-y: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.85rem;
}

.form-row--address {
  grid-template-columns: 2fr 1fr;
}

.form-row--city {
  grid-template-columns: 1fr 2fr 1fr;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.form-group label {
  font-size: 0.78rem;
  color: #6b7280;
  font-weight: 500;
}

.form-group input,
.form-group select {
  padding: 0.55rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.9rem;
}

.form-group input:disabled,
.form-group select:disabled {
  background: #f9fafb;
  color: #9ca3af;
}

.input-error {
  border-color: #ef4444 !important;
}

.error-msg {
  font-size: 0.75rem;
  color: #ef4444;
}

.field-hint {
  font-size: 0.75rem;
  color: #9ca3af;
}

.modal-footer {
  padding: 0.85rem 1.5rem;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f9fafb;
  border-radius: 0 0 8px 8px;
}

.footer-actions {
  display: flex;
  gap: 0.5rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: #2563eb;
  color: white;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}
</style>

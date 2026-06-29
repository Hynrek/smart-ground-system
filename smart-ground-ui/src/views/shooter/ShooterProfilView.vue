<template>
  <div class="profil-view">
    <!-- Header -->
    <div class="profil-header">
      <button class="back-btn" aria-label="Zurück" @click="router.back()">
        <Icons icon="chevronLeft" :size="20" />
      </button>
      <div class="avatar">{{ initials }}</div>
      <div class="header-info">
        <div class="full-name">{{ fullName }}</div>
        <div class="username">@{{ authStore.profile?.username }}</div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tab-bar" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        role="tab"
        :aria-selected="activeTab === tab.id"
        :class="['tab-btn', { active: activeTab === tab.id }]"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab: Profil -->
    <div v-if="activeTab === 'profil'" class="tab-content" role="tabpanel">
      <div class="form-section">
        <div class="field-group">
          <label for="vorname">Vorname</label>
          <input id="vorname" v-model="profilForm.vorname" type="text" />
        </div>
        <div class="field-group">
          <label for="nachname">Nachname</label>
          <input id="nachname" v-model="profilForm.nachname" type="text" />
        </div>
        <div class="field-group">
          <label for="username">Benutzername</label>
          <input id="username" v-model="profilForm.username" type="text" />
          <span v-if="usernameError" class="field-error">{{ usernameError }}</span>
        </div>
        <div class="field-group">
          <label for="geburtsdatum">Geburtsdatum</label>
          <input id="geburtsdatum" v-model="profilForm.geburtsdatum" type="date" />
        </div>
        <div class="field-group">
          <label for="geschlecht">Geschlecht</label>
          <select id="geschlecht" v-model="profilForm.geschlecht">
            <option value="">— keine Angabe —</option>
            <option value="MAENNLICH">Männlich</option>
            <option value="WEIBLICH">Weiblich</option>
            <option value="DIVERS">Divers</option>
            <option value="UNBEKANNT">Unbekannt</option>
          </select>
        </div>
        <div class="field-group">
          <label for="sprache">Sprache</label>
          <select id="sprache" v-model="profilForm.sprache">
            <option value="">— keine Angabe —</option>
            <option value="DE">Deutsch</option>
            <option value="EN">English</option>
            <option value="FR">Français</option>
            <option value="IT">Italiano</option>
          </select>
        </div>
        <div class="field-group">
          <label for="biographie">Über mich</label>
          <textarea id="biographie" v-model="profilForm.biographie" maxlength="500" rows="3" />
          <span class="char-count">{{ profilForm.biographie?.length ?? 0 }} / 500</span>
        </div>
      </div>
      <div v-if="profilError" class="save-error">{{ profilError }}</div>
      <button class="save-btn" :disabled="authStore.isLoading" @click="saveProfilTab">
        {{ profilSaved ? 'Gespeichert ✓' : 'Speichern' }}
      </button>
    </div>

    <!-- Tab: Kontakt -->
    <div v-if="activeTab === 'kontakt'" class="tab-content" role="tabpanel">
      <div class="form-section">
        <div class="field-group">
          <label for="email">E-Mail</label>
          <input id="email" v-model="kontaktForm.email" type="email" />
        </div>
        <div class="field-group readonly">
          <label>E-Mail-Status</label>
          <span :class="['status-badge', authStore.profile?.emailBestaetigt ? 'badge-ok' : 'badge-pending']">
            {{ authStore.profile?.emailBestaetigt ? 'Bestätigt' : 'Ausstehend' }}
          </span>
        </div>
        <div class="field-group">
          <label for="telefonnummer">Telefon</label>
          <input id="telefonnummer" v-model="kontaktForm.telefonnummer" type="tel" />
        </div>
        <div class="field-group two-col">
          <div>
            <label for="strasse">Strasse</label>
            <input id="strasse" v-model="kontaktForm.strasse" type="text" />
          </div>
          <div>
            <label for="hausnummer">Nr.</label>
            <input id="hausnummer" v-model="kontaktForm.hausnummer" type="text" style="width: 70px" />
          </div>
        </div>
        <div class="field-group two-col">
          <div>
            <label for="plz">PLZ</label>
            <input id="plz" v-model="kontaktForm.plz" type="text" style="width: 80px" />
          </div>
          <div>
            <label for="stadt">Stadt</label>
            <input id="stadt" v-model="kontaktForm.stadt" type="text" />
          </div>
        </div>
        <div class="field-group">
          <label for="land">Land</label>
          <input id="land" v-model="kontaktForm.land" type="text" />
        </div>
      </div>
      <div v-if="kontaktError" class="save-error">{{ kontaktError }}</div>
      <button class="save-btn" :disabled="authStore.isLoading" @click="saveKontaktTab">
        {{ kontaktSaved ? 'Gespeichert ✓' : 'Speichern' }}
      </button>
    </div>

    <!-- Tab: Mitgliedschaft -->
    <div v-if="activeTab === 'mitgliedschaft'" class="tab-content" role="tabpanel">
      <div class="form-section">
        <div class="field-group readonly">
          <label>Mitgliedsnummer</label>
          <span class="field-value">{{ authStore.profile?.mitgliedsnummer ?? '—' }}</span>
        </div>
        <div class="field-group readonly">
          <label>Schiesslizenz</label>
          <span class="field-value">{{ authStore.profile?.schiessLizenz ?? '—' }}</span>
        </div>
        <div class="field-group readonly">
          <label>Lizenz gültig bis</label>
          <span class="field-value">{{ formatDate(authStore.profile?.schiessLizenzVerfallsdatum) }}</span>
        </div>
        <div class="field-group readonly">
          <label>Lizenz-Status</label>
          <span :class="['status-badge', authStore.profile?.schiessLizenzGueltig ? 'badge-ok' : 'badge-expired']">
            {{ authStore.profile?.schiessLizenzGueltig ? 'Gültig' : 'Abgelaufen / nicht vorhanden' }}
          </span>
        </div>
        <div class="field-group readonly">
          <label>Mitglied seit</label>
          <span class="field-value">{{ formatDate(authStore.profile?.erstelltAm) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watchEffect } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore.js'
import Icons from '@/components/Icons.vue'

const router = useRouter()
const authStore = useAuthStore()

const USERNAME_RE = /^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$/

const tabs = [
  { id: 'profil', label: 'Profil' },
  { id: 'kontakt', label: 'Kontakt' },
  { id: 'mitgliedschaft', label: 'Mitgliedschaft' },
]
const activeTab = ref('profil')

const profilSaved = ref(false)
const kontaktSaved = ref(false)
const profilError = ref('')
const kontaktError = ref('')
const usernameError = ref('')

const profilForm = reactive({
  vorname: '',
  nachname: '',
  username: '',
  geburtsdatum: '',
  geschlecht: '',
  sprache: '',
  biographie: '',
})

const kontaktForm = reactive({
  email: '',
  telefonnummer: '',
  strasse: '',
  hausnummer: '',
  plz: '',
  stadt: '',
  land: '',
})

watchEffect(() => {
  const p = authStore.profile
  if (!p) return
  profilForm.vorname = p.vorname ?? ''
  profilForm.nachname = p.nachname ?? ''
  profilForm.username = p.username ?? ''
  profilForm.geburtsdatum = p.geburtsdatum ?? ''
  profilForm.geschlecht = p.geschlecht ?? ''
  profilForm.sprache = p.sprache ?? ''
  profilForm.biographie = p.biographie ?? ''

  kontaktForm.email = p.email ?? ''
  kontaktForm.telefonnummer = p.telefonnummer ?? ''
  kontaktForm.strasse = p.strasse ?? ''
  kontaktForm.hausnummer = p.hausnummer ?? ''
  kontaktForm.plz = p.plz ?? ''
  kontaktForm.stadt = p.stadt ?? ''
  kontaktForm.land = p.land ?? ''
})

const fullName = computed(() => `${authStore.profile?.vorname ?? ''} ${authStore.profile?.nachname ?? ''}`.trim())
const initials = computed(() => {
  const v = authStore.profile?.vorname?.[0] ?? ''
  const n = authStore.profile?.nachname?.[0] ?? ''
  return (v + n).toUpperCase() || '?'
})

function buildPayload(form) {
  return Object.fromEntries(
    Object.entries(form).filter(([, v]) => v !== '' && v !== null && v !== undefined)
  )
}

async function saveProfilTab() {
  usernameError.value = ''
  profilError.value = ''
  if (!USERNAME_RE.test(profilForm.username.trim())) {
    usernameError.value = '3–30 Zeichen, Buchstaben/Ziffern/._-'
    return
  }
  try {
    await authStore.updateProfile(buildPayload(profilForm))
    profilSaved.value = true
    setTimeout(() => { profilSaved.value = false }, 1500)
  } catch {
    profilError.value = authStore.error ?? 'Speichern fehlgeschlagen'
  }
}

async function saveKontaktTab() {
  kontaktError.value = ''
  try {
    await authStore.updateProfile(buildPayload(kontaktForm))
    kontaktSaved.value = true
    setTimeout(() => { kontaktSaved.value = false }, 1500)
  } catch {
    kontaktError.value = authStore.error ?? 'Speichern fehlgeschlagen'
  }
}

function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleDateString('de-CH', { day: '2-digit', month: '2-digit', year: 'numeric' })
}
</script>

<style scoped>
.profil-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.profil-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.back-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.5);
  padding: 4px;
  display: flex;
  align-items: center;
  border-radius: 6px;
  transition: background 0.15s;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.07);
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--sg-accent) 20%, transparent);
  border: 1px solid color-mix(in srgb, var(--sg-accent) 35%, transparent);
  color: var(--sg-accent);
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.full-name {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.username {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.tab-bar {
  display: flex;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding: 0 20px;
}

.tab-btn {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  font-family: inherit;
  font-size: 13px;
  margin-bottom: -1px;
  padding: 12px 14px;
  transition: color 0.15s, border-color 0.15s;
}

.tab-btn.active {
  border-bottom-color: var(--sg-accent);
  color: var(--sg-accent);
}

.tab-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.field-group.two-col {
  flex-direction: row;
  gap: 10px;
}

.field-group.two-col > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  flex: 1;
}

.field-group label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.field-group input,
.field-group select,
.field-group textarea {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  padding: 9px 12px;
  transition: border-color 0.15s;
  width: 100%;
}

.field-group input:focus,
.field-group select:focus,
.field-group textarea:focus {
  border-color: var(--sg-accent);
  outline: none;
}

.field-group select option {
  background: #1a2340;
}

.field-group.readonly {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 8px;
  padding: 10px 12px;
}

.field-group.readonly label {
  text-transform: uppercase;
  margin: 0;
}

.field-value {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

.status-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 20px;
}

.badge-ok {
  background: rgba(74, 222, 128, 0.15);
  color: #4ade80;
}

.badge-pending {
  background: rgba(251, 191, 36, 0.15);
  color: #fbbf24;
}

.badge-expired {
  background: rgba(248, 113, 113, 0.15);
  color: #f87171;
}

.char-count {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  text-align: right;
}

.field-error {
  color: var(--sg-color-danger, #f87171);
  font-size: 12px;
}

.save-error {
  color: var(--sg-color-danger, #f87171);
  font-size: 13px;
  margin-top: 8px;
}

.save-btn {
  margin-top: 20px;
  width: 100%;
  padding: 12px;
  background: var(--sg-accent);
  border: none;
  border-radius: 10px;
  color: var(--sg-brand, #0f1a2e);
  cursor: pointer;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  transition: opacity 0.15s;
}

.save-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.save-btn:not(:disabled):hover {
  opacity: 0.9;
}
</style>

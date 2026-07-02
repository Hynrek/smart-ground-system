<template>
  <div class="testing-view">
    <header class="page-header">
      <h1>Testing</h1>
      <p class="subtitle">Testdaten schnell erzeugen (nur Admin).</p>
    </header>

    <div class="cards">
      <!-- Create User -->
      <section class="card">
        <h2>Benutzer erstellen</h2>
        <p class="hint">Wird als Benutzername UND Passwort verwendet. E-Mail: {credential}@test.local, Rolle SHOOTER.</p>
        <FormField label="Credential">
          <input v-model="credential" class="input" placeholder="z.B. bob" @keyup.enter="onCreateUser" />
        </FormField>
        <Button :disabled="userBusy || !credential.trim()" @click="onCreateUser">Erstellen</Button>
        <p v-if="userMsg" :class="['result', userError ? 'err' : 'ok']">{{ userMsg }}</p>
      </section>

      <!-- Schiessplatz Setup -->
      <section class="card">
        <h2>Schiessplatz Setup</h2>
        <p class="hint">Erstellt Vorderlader, Trapstand, Rollhase, Kippreh (falls noch nicht vorhanden).</p>
        <Button :disabled="rangeBusy" @click="onSeedRanges">4 Plätze erstellen</Button>
        <ul v-if="rangeResult.length" class="result-list">
          <li v-for="r in rangeResult" :key="r.id">
            {{ r.name }} — <span :class="r.created ? 'ok' : 'muted'">{{ r.created ? 'erstellt' : 'bereits vorhanden' }}</span>
          </li>
        </ul>
        <p v-if="rangeError" class="result err">{{ rangeError }}</p>
      </section>

      <!-- Mock SmartBox -->
      <section class="card">
        <h2>Mock SmartBox erstellen</h2>
        <p class="hint">Erstellt eine SmartBox mit N Werfer-Geräten (keiner Range zugeordnet).</p>
        <FormField label="Anzahl Geräte">
          <input v-model.number="deviceCount" type="number" min="1" max="50" class="input" />
        </FormField>
        <FormField label="Alias (optional)">
          <input v-model="boxAlias" class="input" placeholder="z.B. Mock-1" />
        </FormField>
        <Button :disabled="boxBusy || deviceCount < 1 || deviceCount > 50" @click="onCreateBox">Erstellen</Button>
        <p v-if="boxMsg" :class="['result', boxError ? 'err' : 'ok']">{{ boxMsg }}</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import Button from '@/components/Button.vue';
import FormField from '@/components/FormField.vue';
import { createTestUser, seedRanges, createMockSmartBox } from '@/services/testingApi.js';

const credential = ref('');
const userBusy = ref(false);
const userMsg = ref('');
const userError = ref(false);

const onCreateUser = async () => {
  if (!credential.value.trim()) return;
  userBusy.value = true;
  userMsg.value = '';
  userError.value = false;
  try {
    const u = await createTestUser(credential.value.trim());
    userMsg.value = `Benutzer "${u.username}" erstellt (${u.email}).`;
    credential.value = '';
  } catch (e) {
    userError.value = true;
    userMsg.value = e.message ?? 'Fehler beim Erstellen.';
  } finally {
    userBusy.value = false;
  }
};

const rangeBusy = ref(false);
const rangeResult = ref([]);
const rangeError = ref('');

const onSeedRanges = async () => {
  rangeBusy.value = true;
  rangeError.value = '';
  rangeResult.value = [];
  try {
    const res = await seedRanges();
    rangeResult.value = res.ranges ?? [];
  } catch (e) {
    rangeError.value = e.message ?? 'Fehler beim Erstellen.';
  } finally {
    rangeBusy.value = false;
  }
};

const deviceCount = ref(4);
const boxAlias = ref('');
const boxBusy = ref(false);
const boxMsg = ref('');
const boxError = ref(false);

const onCreateBox = async () => {
  boxBusy.value = true;
  boxMsg.value = '';
  boxError.value = false;
  try {
    const box = await createMockSmartBox({ deviceCount: deviceCount.value, alias: boxAlias.value.trim() || null });
    boxMsg.value = `SmartBox ${box.macAddress} mit ${box.deviceCount} Geräten erstellt.`;
    boxAlias.value = '';
  } catch (e) {
    boxError.value = true;
    boxMsg.value = e.message ?? 'Fehler beim Erstellen.';
  } finally {
    boxBusy.value = false;
  }
};
</script>

<style scoped>
.testing-view { padding: 24px; }
.page-header { margin-bottom: 20px; }
.page-header h1 { font-size: 20px; font-weight: 700; }
.subtitle { color: var(--sg-text-faint); font-size: 13px; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.card {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 8px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card h2 { font-size: 15px; font-weight: 600; }
.hint { font-size: 12px; color: var(--sg-text-faint); }
.input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--sg-border);
  border-radius: 6px;
  font: inherit;
}
.result { font-size: 13px; margin: 0; }
.result-list { font-size: 13px; list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; }
.ok { color: var(--sg-color-success, #2e7d32); }
.err { color: var(--sg-color-danger); }
.muted { color: var(--sg-text-faint); }
</style>

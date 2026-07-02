<template>
  <div class="firmware-updates-panel">
    <p class="subtitle">{{ otaStore.releases.length }} Release(s) hochgeladen</p>

    <form class="upload-card" @submit.prevent>
      <div class="form-row">
        <label class="form-group">
          <span>Typ</span>
          <select v-model="form.type" data-testid="ota-type">
            <option :value="OTA_TYPE.APP">{{ OTA_TYPE_LABELS.APP }}</option>
            <option :value="OTA_TYPE.FIRMWARE">{{ OTA_TYPE_LABELS.FIRMWARE }}</option>
          </select>
        </label>
        <label class="form-group">
          <span>Version</span>
          <input v-model="form.version" data-testid="ota-version" placeholder="z.B. 0.7" />
        </label>
        <label class="form-group file-group">
          <span>{{ form.type === OTA_TYPE.APP ? 'App-Code (ZIP)' : 'Firmware (.bin)' }}</span>
          <input
            ref="fileInput"
            type="file"
            data-testid="ota-file"
            :accept="form.type === OTA_TYPE.APP ? '.zip' : '.bin'"
            @change="onFile"
          />
        </label>
        <Button
          type="submit"
          variant="primary"
          data-testid="ota-upload-btn"
          :disabled="!canSubmit || otaStore.uploading"
          @click="submit"
        >
          {{ otaStore.uploading ? 'Lädt hoch…' : 'Hochladen' }}
        </Button>
      </div>
      <p v-if="otaStore.error" class="form-error">{{ otaStore.error }}</p>
    </form>

    <div v-if="otaStore.releases.length" class="release-table-wrap">
      <table class="release-table">
        <thead>
          <tr><th>Typ</th><th>Version</th><th>Grösse</th><th>SHA-256</th><th>Hochgeladen</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in otaStore.releases" :key="r.id">
            <td><Badge :color="r.type === OTA_TYPE.APP ? 'blue' : 'warn'">{{ OTA_TYPE_LABELS[r.type] }}</Badge></td>
            <td class="mono">{{ r.version }}</td>
            <td>{{ formatSize(r.sizeBytes) }}</td>
            <td class="mono sha">{{ r.sha256.slice(0, 12) }}…</td>
            <td>{{ formatDate(r.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="empty-state">Noch keine Releases hochgeladen.</div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useOtaStore } from '@/stores/otaStore.js';
import { OTA_TYPE, OTA_TYPE_LABELS } from '@/constants/ota.js';
import Button from '@/components/Button.vue';
import Badge from '@/components/Badge.vue';

const otaStore = useOtaStore();
const fileInput = ref(null);
const form = reactive({ type: OTA_TYPE.APP, version: '', file: null });

const canSubmit = computed(() => !!form.version.trim() && !!form.file);

const onFile = (e) => {
  form.file = e.target.files?.[0] ?? null;
};

const submit = async () => {
  if (!canSubmit.value) return;
  try {
    await otaStore.uploadRelease({ type: form.type, version: form.version.trim(), file: form.file });
    form.version = '';
    form.file = null;
    if (fileInput.value) fileInput.value.value = '';
  } catch {
    // error surfaced via otaStore.error
  }
};

const formatSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
};

const formatDate = (iso) =>
  new Date(iso).toLocaleString('de-CH', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

onMounted(() => otaStore.fetchReleases());
</script>

<style scoped>
.subtitle { font-size: 13px; color: var(--sg-text-muted); margin-bottom: 20px; }
.upload-card { background: var(--sg-bg-card); border: 1px solid var(--sg-border); border-radius: 12px; padding: 18px; margin-bottom: 24px; }
.form-row { display: flex; gap: 14px; align-items: flex-end; flex-wrap: wrap; }
.form-group { display: flex; flex-direction: column; gap: 4px; }
.form-group span { font-size: 11.5px; color: var(--sg-text-muted); font-weight: 500; }
.form-group select, .form-group input { padding: 7px 10px; border: 1.5px solid var(--sg-border); border-radius: 7px; font-size: 13px; font-family: inherit; }
.form-error { color: #e05252; font-size: 12px; margin-top: 10px; }
.release-table-wrap { border: 1px solid var(--sg-border); border-radius: 12px; overflow: hidden; }
.release-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.release-table th { text-align: left; padding: 9px 14px; background: var(--sg-bg-panel); color: var(--sg-text-muted); font-size: 11.5px; text-transform: uppercase; letter-spacing: 0.4px; }
.release-table td { padding: 9px 14px; border-top: 1px solid var(--sg-border); }
.mono { font-family: monospace; }
.sha { color: var(--sg-text-muted); }
.empty-state { text-align: center; padding: 40px; color: var(--sg-text-faint); font-size: 13px; }
@media (max-width: 768px) { .form-row { flex-direction: column; align-items: stretch; } }
</style>

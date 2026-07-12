<template>
  <div class="ota-panel">
    <div class="ota-row">
      <div class="ota-version">
        <span class="ota-label">App-Code</span>
        <span class="ota-current mono">v{{ box.appVersion ?? '—' }}</span>
      </div>

      <div v-if="status && !isTerminalPhase(status.phase)" class="ota-progress">
        <div class="ota-progress-head">
          <span>{{ phaseLabel(status.phase) }} → v{{ status.version }}</span>
          <span class="mono">{{ status.progress ?? 0 }}%</span>
        </div>
        <div class="ota-bar-track">
          <div
            class="ota-bar-fill"
            data-testid="ota-progress-bar"
            :style="{ width: (status.progress ?? 0) + '%' }"
          />
        </div>
      </div>

      <Badge v-else-if="status" :color="resultColor(status.phase)">
        {{ phaseLabel(status.phase) }}<template v-if="status.version"> · v{{ status.version }}</template>
      </Badge>

      <div class="ota-trigger">
        <select v-model="selectedVersion" data-testid="ota-version-select" :disabled="updating || !appReleases.length">
          <option value="" disabled>Version wählen</option>
          <option
            v-for="r in appReleases"
            :key="r.id"
            :value="r.version"
            data-testid="ota-version-option"
          >v{{ r.version }}</option>
        </select>
        <Button
          variant="primary"
          data-testid="ota-trigger-btn"
          :disabled="!selectedVersion || updating"
          @click="trigger"
        >
          Update
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { storeToRefs } from 'pinia';
import { useOtaStore } from '@/stores/otaStore.js';
import { OTA_PHASE_LABELS, isTerminalPhase, OTA_TYPE } from '@/constants/ota.js';
import Button from '@/components/Button.vue';
import Badge from '@/components/Badge.vue';

const props = defineProps({ box: { type: Object, required: true } });

const otaStore = useOtaStore();
const { releases, statusByBox } = storeToRefs(otaStore);

const selectedVersion = ref('');
const status = computed(() => statusByBox.value[props.box.id] ?? null);
const updating = computed(() => !!status.value && !isTerminalPhase(status.value.phase));
const appReleases = computed(() => releases.value.filter((r) => r.type === OTA_TYPE.APP));

const phaseLabel = (p) => OTA_PHASE_LABELS[p] ?? p;
const resultColor = (p) => (p === 'APPLIED' ? 'green' : p === 'ROLLED_BACK' ? 'warn' : 'red');

const trigger = async () => {
  if (!selectedVersion.value) return;
  try {
    await otaStore.triggerUpdate(props.box.id, OTA_TYPE.APP, selectedVersion.value);
  } catch {
    // surfaced via otaStore.error
  }
};
</script>

<style scoped>
.ota-panel { padding: 10px 18px; border-top: 1px dashed var(--sg-border, var(--sg-border)); }
.ota-row { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.ota-version { display: flex; flex-direction: column; }
.ota-label { font-size: 11px; color: var(--sg-text-muted, var(--sg-text-muted)); }
.ota-current { font-size: 13px; font-weight: 600; }
.ota-progress { flex: 1; min-width: 160px; }
.ota-progress-head { display: flex; justify-content: space-between; font-size: 11.5px; color: var(--sg-text-muted, var(--sg-text-muted)); margin-bottom: 3px; }
.ota-bar-track { height: 6px; border-radius: 99px; background: var(--sg-bg-panel, var(--sg-bg-panel)); overflow: hidden; }
.ota-bar-fill { height: 100%; background: var(--sg-brand, var(--sg-accent)); transition: width 0.3s; }
.ota-trigger { display: flex; gap: 8px; align-items: center; margin-left: auto; }
.ota-trigger select { padding: 6px 10px; border: 1.5px solid var(--sg-border, var(--sg-border)); border-radius: 7px; font-size: 13px; font-family: inherit; }
.mono { font-family: monospace; }
</style>

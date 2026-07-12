<template>
  <div class="pending-boxes-panel">
    <p v-if="onboardingStore.error" class="form-error">{{ onboardingStore.error }}</p>

    <div v-if="onboardingStore.pendingBoxes.length" class="pending-table-wrap">
      <table class="pending-table">
        <thead>
          <tr>
            <th>MAC-Adresse</th>
            <th>Signal</th>
            <th>Zuerst gesehen</th>
            <th>Zuletzt gesehen</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="box in onboardingStore.pendingBoxes" :key="box.mac">
            <td class="mono">{{ box.mac }}</td>
            <td>{{ box.rssi }} dBm</td>
            <td>{{ formatDate(box.firstSeen) }}</td>
            <td>{{ formatDate(box.lastSeen) }}</td>
            <td class="actions-cell">
              <Badge v-if="resultFor(box.mac)?.status === 'offered'" color="blue">Angeboten</Badge>
              <span v-else-if="resultFor(box.mac)?.error" class="row-error">{{ resultFor(box.mac).error }}</span>
              <Button
                v-else
                variant="primary"
                size="sm"
                :disabled="onboardingStore.couplingMac === box.mac"
                :data-testid="`couple-btn-${box.mac}`"
                @click="couple(box.mac)"
              >
                {{ onboardingStore.couplingMac === box.mac ? 'Koppelt…' : 'Koppeln' }}
              </Button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-else class="empty-state">
      Keine neuen Geräte gemeldet. Eine fabrikneue SmartBox meldet sich hier, sobald sie eingeschaltet wird.
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue';
import { useOnboardingStore } from '@/stores/onboardingStore.js';
import Button from '@/components/Button.vue';
import Badge from '@/components/Badge.vue';

const onboardingStore = useOnboardingStore();

const resultFor = (mac) => onboardingStore.coupleResults[mac];

const couple = async (mac) => {
  try {
    await onboardingStore.coupleBox(mac);
  } catch {
    // error surfaced via resultFor(mac).error
  }
};

const formatDate = (iso) =>
  new Date(iso).toLocaleString('de-CH', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

onMounted(() => onboardingStore.startPolling());
onUnmounted(() => onboardingStore.stopAllPolling());
</script>

<style scoped>
.pending-table-wrap {
  border: 1px solid var(--sg-border);
  border-radius: 12px;
  overflow: hidden;
}
.pending-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.pending-table th {
  text-align: left;
  padding: 9px 14px;
  background: var(--sg-bg-panel);
  color: var(--sg-text-muted);
  font-size: 11.5px;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}
.pending-table td {
  padding: 9px 14px;
  border-top: 1px solid var(--sg-border);
}
.mono {
  font-family: monospace;
}
.actions-cell {
  text-align: right;
}
.row-error {
  color: var(--sg-color-danger);
  font-size: 12px;
}
.form-error {
  color: var(--sg-color-danger);
  font-size: 12px;
  margin-bottom: 14px;
}
.empty-state {
  text-align: center;
  padding: 40px;
  color: var(--sg-text-faint);
  font-size: 13px;
}
</style>

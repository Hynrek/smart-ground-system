<script setup>
import { computed } from 'vue'
import { useDeviceTypeStore } from '../stores/deviceTypeStore.js'

const deviceTypeStore = useDeviceTypeStore()

const deviceTypes = computed(() => deviceTypeStore.deviceTypes)
const loading = computed(() => deviceTypeStore.isLoading)

function formatDuration(ms) {
  if (ms == null) return '—'
  return ms + ' ms'
}
</script>

<template>
  <div class="view">
    <div class="view-container">
      <div class="view-header">
        <h1 class="view-title">Gerätetypen</h1>
      </div>

      <div v-if="deviceTypeStore.error" class="error-banner">
        ⚠️ {{ deviceTypeStore.error }}
      </div>

      <div v-if="loading && deviceTypes.length === 0" class="loading-state">
        Lade Gerätetypen…
      </div>

      <div v-else-if="deviceTypes.length === 0 && !loading" class="empty-state">
        <p>Keine Gerätetypen vorhanden.</p>
      </div>

      <div v-else class="deviceType-grid">
        <div v-for="t in deviceTypes" :key="t.id" class="template-card">
          <div class="template-header">
            <span class="template-name">{{ t.name }}</span>
            <span class="template-type-badge">{{ t.device }}</span>
          </div>
          <dl class="template-details">
            <div class="tpl-row">
              <dt>Gruppe</dt>
              <dd>{{ t.groupName }}</dd>
            </div>
            <div class="tpl-row">
              <dt>Richtung</dt>
              <dd>{{ t.direction }}</dd>
            </div>
            <div class="tpl-row">
              <dt>Befehl</dt>
              <dd class="cell-mono">{{ t.command }}</dd>
            </div>
            <div class="tpl-row">
              <dt>Signal-Dauer</dt>
              <dd>{{ formatDuration(t.signalDurationMs) }}</dd>
            </div>
            <div class="tpl-row">
              <dt>Verzögerung</dt>
              <dd>{{ formatDuration(t.delaySignalDurationMs) }}</dd>
            </div>
          </dl>
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
  max-width: 900px;
}

.view-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #e2e8f0;
}

.view-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0;
}

.error-banner {
  background: #fff5f5;
  border: 1px solid #fc8181;
  color: #c53030;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.loading-state {
  text-align: center;
  padding: 4rem 2rem;
  color: #718096;
}

.empty-state {
  text-align: center;
  padding: 4rem 2rem;
  color: #718096;
}

.deviceType-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.template-card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  padding: 1.25rem;
  border: 1px solid #f0f4f8;
}

.template-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1rem;
}

.template-name {
  font-size: 1rem;
  font-weight: 600;
  color: #1a1a2e;
}

.template-type-badge {
  font-size: 0.75rem;
  font-weight: 600;
  background: #e9d8fd;
  color: #553c9a;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
}

.template-details {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.tpl-row {
  display: grid;
  grid-template-columns: 100px 1fr;
}

.tpl-row dt {
  font-size: 0.82rem;
  color: #718096;
  font-weight: 500;
}

.tpl-row dd {
  font-size: 0.85rem;
  color: #2d3748;
  margin: 0;
}

.cell-mono {
  font-family: 'Courier New', monospace;
  font-size: 0.8rem;
}
</style>
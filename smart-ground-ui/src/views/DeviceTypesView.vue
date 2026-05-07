<script setup>
import { computed } from 'vue'
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js'

const deviceTypeStore = useDeviceTypeStore()
const deviceTypes = computed(() => deviceTypeStore.deviceTypes || [])
const loading = computed(() => deviceTypeStore.isLoading)
</script>

<template>
  <div class="view">
    <div class="view-header">
      <h1 class="view-title">Gerätetypen</h1>
    </div>

    <p class="page-description">
      Gerätetypen kategorisieren physische Geräte (Werfer, Knöpfe, Sensoren).
      Sie werden beim Registrieren eines Geräts auf einer SmartBox verwendet.
    </p>

    <div v-if="loading" class="loading-state">Lade Gerätetypen…</div>

    <div v-else-if="deviceTypes.length === 0" class="empty-state">
      Keine Gerätetypen definiert.
    </div>

    <div v-else class="groups-grid">
      <div v-for="dt in deviceTypes" :key="dt.id" class="group-card">
        <div class="group-header">
          <span class="group-name">{{ dt.name }}</span>
        </div>
        <dl class="group-details">
          <div class="tpl-row">
            <dt>ID</dt>
            <dd class="cell-mono">{{ dt.id }}</dd>
          </div>
          <div class="tpl-row">
            <dt>Gruppe</dt>
            <dd>{{ dt.groupName }}</dd>
          </div>
          <div class="tpl-row">
            <dt>Kommando</dt>
            <dd>{{ dt.command }}</dd>
          </div>
          <div class="tpl-row">
            <dt>Dauer (ms)</dt>
            <dd>{{ dt.signalDurationMs }}</dd>
          </div>
        </dl>
      </div>
    </div>
  </div>
</template>

<style scoped>
.view { max-width: 900px; margin: 0 auto; }
.view-header { margin-bottom: 0.5rem; }
.view-title { font-size: 1.5rem; font-weight: 700; color: #1a1a2e; margin: 0; }
.page-description { font-size: 0.9rem; color: #718096; margin: 0 0 1.5rem 0; }

.loading-state { text-align: center; padding: 4rem 2rem; color: #718096; }
.empty-state { text-align: center; padding: 4rem 2rem; color: #718096; }

.groups-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
              gap: 1rem; }
.group-card { background: #fff; border-radius: 10px; box-shadow: 0 1px 4px rgba(0,0,0,0.08);
             padding: 1.25rem; }
.group-header { display: flex; align-items: center; justify-content: space-between;
               margin-bottom: 1rem; }
.group-name { font-size: 1rem; font-weight: 600; color: #1a1a2e; }

.group-details { margin: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.tpl-row { display: grid; grid-template-columns: 100px 1fr; }
.tpl-row dt { font-size: 0.82rem; color: #718096; font-weight: 500; }
.tpl-row dd { font-size: 0.85rem; color: #2d3748; margin: 0; }

.cell-mono { font-family: 'Courier New', monospace; font-size: 0.8rem; }
</style>
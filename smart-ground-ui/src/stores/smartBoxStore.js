import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { useDeviceStore } from './deviceStore.js';
import * as smartBoxApi from '../services/smartBoxApi.js';

export const useSmartBoxStore = defineStore('smartbox', () => {
  const smartboxes = ref([]);
  const isLoading = ref(false);
  const error = ref(null);

  const deviceStore = useDeviceStore();

  const getDeviceCount = computed(() => (boxId) => {
    return deviceStore.devices.filter(d => (d.smartBoxId ?? d.boxId) === boxId).length;
  });

  const initialize = async () => {
    await loadApiData();
  };

  const loadApiData = async () => {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await smartBoxApi.fetchSmartBoxes();
      smartboxes.value = response.content ?? [];
    } catch (e) {
      console.error('Failed to load smart boxes from API:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
    } finally {
      isLoading.value = false;
    }
  };

  const updateSmartBox = (boxId, updates) => {
    const box = smartboxes.value.find(b => b.id === boxId);
    if (box) {
      Object.assign(box, updates);
    }
  };

  const saveSmartBox = async (boxId) => {
    const box = smartboxes.value.find(b => b.id === boxId);
    if (!box) return;
    try {
      await smartBoxApi.setSmartBoxAlias(boxId, box.alias);
    } catch (e) {
      console.error('Failed to save SmartBox alias:', e);
      throw e;
    }
  };

  const deleteSmartBox = async (boxId) => {
    await smartBoxApi.deleteSmartBox(boxId);
    const index = smartboxes.value.findIndex((b) => b.id === boxId);
    if (index > -1) {
      smartboxes.value.splice(index, 1);
    }
  };

  const applySmartBoxEvent = (event) => {
    if (event.type === 'smartbox.status') {
      const box = smartboxes.value.find((b) => b.id === event.smartBoxId);
      if (box) box.status = event.status;
    } else if (event.type === 'smartbox.synced') {
      const box = smartboxes.value.find((b) => b.id === event.smartBoxId);
      if (box) box.configSynced = true;
    }
  };

  return {
    smartboxes,
    isLoading,
    error,
    getDeviceCount,
    updateSmartBox,
    saveSmartBox,
    deleteSmartBox,
    applySmartBoxEvent,
    initialize,
    loadApiData,
  };
});

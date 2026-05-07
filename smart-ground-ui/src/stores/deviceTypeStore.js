import { defineStore } from 'pinia';
import { ref } from 'vue';
import { fetchDeviceTypes, fetchDeviceTypeGroups, fetchFirmwareConfigs, updateDeviceType as updateDeviceTypeApi } from '../services/deviceTypeApi.js';

export const useDeviceTypeStore = defineStore('deviceType', () => {
  const deviceTypes = ref([]);
  const deviceTypeGroups = ref([]);
  const firmwareConfigs = ref([]);
  const isLoading = ref(false);
  const error = ref(null);

  const loadApiData = async () => {
    isLoading.value = true;
    error.value = null;
    try {
      deviceTypes.value = await fetchDeviceTypes();
      deviceTypeGroups.value = await fetchDeviceTypeGroups();
      firmwareConfigs.value = await fetchFirmwareConfigs();
    } catch (e) {
      console.error('Failed to load device types from API:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
    } finally {
      isLoading.value = false;
    }
  };

  const initialize = async () => {
    await loadApiData();
  };

  const updateDeviceType = async (id, payload) => {
    try {
      const updated = await updateDeviceTypeApi(id, payload);
      const idx = deviceTypes.value.findIndex((dt) => dt.id === id);
      if (idx !== -1) Object.assign(deviceTypes.value[idx], updated);
      return updated;
    } catch (e) {
      console.error('Failed to update device type:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
      throw e;
    }
  };

  return {
    deviceTypes,
    deviceTypeGroups,
    firmwareConfigs,
    isLoading,
    error,
    initialize,
    loadApiData,
    updateDeviceType,
  };
});
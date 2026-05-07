import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as deviceTypeGroupApi from '../services/deviceTypeGroupApi.js';

export const useDeviceTypeGroupStore = defineStore('deviceTypeGroup', () => {
  const groups = ref([]);
  const isLoading = ref(false);
  const error = ref(null);

  const loadApiData = async () => {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await deviceTypeGroupApi.fetchDeviceTypeGroups();
      groups.value = response;
    } catch (e) {
      console.error('Failed to load device type groups from API:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
    } finally {
      isLoading.value = false;
    }
  };

  const loadGroups = async () => {
    await loadApiData();
  };

  return {
    groups,
    isLoading,
    error,
    loadGroups,
    loadApiData,
  };
});

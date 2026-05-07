import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as rangeApi from '../services/rangeApi.js';
import * as deviceApi from '../services/deviceApi.js';

export const useRangeStore = defineStore('range', () => {
  const ranges = ref([]);
  const selectedRange = ref(null);
  const isLoading = ref(false);
  const error = ref(null);

  const loadApiData = async () => {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await rangeApi.fetchRanges();
      ranges.value = response.content ?? [];
    } catch (e) {
      console.error('Failed to load ranges from API:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
    } finally {
      isLoading.value = false;
    }
  };

  const initialize = async () => {
    await loadApiData();
  };

  const selectRange = (range) => {
    selectedRange.value = range;
  };

  const deselectRange = () => {
    selectedRange.value = null;
  };

  const assignDeviceToRange = async (deviceId, rangeId) => {
    try {
      await deviceApi.assignDeviceToRange(deviceId, rangeId);
    } catch (e) {
      console.error('Failed to assign device to range:', e);
      throw e;
    }
  };

  const unassignDeviceFromRange = async (deviceId) => {
    try {
      await deviceApi.removeDeviceFromRange(deviceId);
    } catch (e) {
      console.error('Failed to remove device from range:', e);
      throw e;
    }
  };

  const createRange = async (name, description = null) => {
    try {
      const created = await rangeApi.createRange(name, description);
      ranges.value.push(created);
      return created;
    } catch (e) {
      console.error('Failed to create range:', e);
      throw e;
    }
  };

  const updateRange = async (id, name, description = null) => {
    try {
      await rangeApi.updateRange(id, name, description);
      const range = ranges.value.find(r => r.id === id);
      if (range) {
        range.name = name;
        range.description = description;
      }
    } catch (e) {
      console.error('Failed to update range:', e);
      throw e;
    }
  };

  const deleteRange = async (id) => {
    try {
      await rangeApi.deleteRange(id);
      const index = ranges.value.findIndex(r => r.id === id);
      if (index > -1) {
        ranges.value.splice(index, 1);
      }
    } catch (e) {
      console.error('Failed to delete range:', e);
      throw e;
    }
  };

  const setLocked = async (id, locked) => {
    try {
      await rangeApi.setRangeLocked(id, locked);
      const range = ranges.value.find(r => r.id === id);
      if (range) range.locked = locked;
    } catch (e) {
      console.error('Failed to toggle range lock:', e);
      throw e;
    }
  };

  return {
    ranges,
    selectedRange,
    isLoading,
    error,
    selectRange,
    deselectRange,
    assignDeviceToRange,
    unassignDeviceFromRange,
    createRange,
    updateRange,
    deleteRange,
    setLocked,
    initialize,
    loadApiData,
  };
});

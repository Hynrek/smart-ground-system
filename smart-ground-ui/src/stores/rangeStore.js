import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as rangeApi from '../services/rangeApi.js';
import * as deviceApi from '../services/deviceApi.js';
import * as positionApi from '../services/rangePositionApi.js';

export const useRangeStore = defineStore('range', () => {
  const ranges = ref([]);
  const selectedRange = ref(null);
  const isLoading = ref(false);
  const error = ref(null);

  // positions keyed by rangeId → RangePositionResponse[]
  const positions = ref({});

  // ── Range actions ─────────────────────────────────────────────────────────

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

  const initialize = async () => { await loadApiData(); };
  const selectRange = (range) => { selectedRange.value = range; };
  const deselectRange = () => { selectedRange.value = null; };

  const assignDeviceToRange = async (deviceId, rangeId) => {
    try { await deviceApi.assignDeviceToRange(deviceId, rangeId); }
    catch (e) { console.error('Failed to assign device to range:', e); throw e; }
  };

  const unassignDeviceFromRange = async (deviceId) => {
    try { await deviceApi.removeDeviceFromRange(deviceId); }
    catch (e) { console.error('Failed to remove device from range:', e); throw e; }
  };

  const createRange = async (name, description = null) => {
    try {
      const created = await rangeApi.createRange(name, description);
      ranges.value.push(created);
      return created;
    } catch (e) { console.error('Failed to create range:', e); throw e; }
  };

  const updateRange = async (id, name, description = null) => {
    try {
      await rangeApi.updateRange(id, name, description);
      const range = ranges.value.find(r => r.id === id);
      if (range) { range.name = name; range.description = description; }
    } catch (e) { console.error('Failed to update range:', e); throw e; }
  };

  const deleteRange = async (id) => {
    try {
      await rangeApi.deleteRange(id);
      const index = ranges.value.findIndex(r => r.id === id);
      if (index > -1) ranges.value.splice(index, 1);
    } catch (e) { console.error('Failed to delete range:', e); throw e; }
  };

  const setLocked = async (id, locked) => {
    try {
      await rangeApi.setRangeLocked(id, locked);
      const range = ranges.value.find(r => r.id === id);
      if (range) range.locked = locked;
    } catch (e) { console.error('Failed to toggle range lock:', e); throw e; }
  };

  const assignUser = async (rangeId, userId) => {
    try {
      await rangeApi.assignRangeUser(rangeId, userId);
      const range = ranges.value.find((r) => r.id === rangeId);
      if (range) range.assignedUserId = userId;
    } catch (e) { console.error('Failed to assign user to range:', e); throw e; }
  };

  // ── Position actions ──────────────────────────────────────────────────────

  const loadPositions = async (rangeId) => {
    try {
      positions.value[rangeId] = await positionApi.fetchPositions(rangeId);
    } catch (e) {
      console.error('Failed to load positions:', e); throw e;
    }
  };

  const createPosition = async (rangeId, label = null) => {
    try {
      const created = await positionApi.createPosition(rangeId, label);
      if (!positions.value[rangeId]) positions.value[rangeId] = [];
      positions.value[rangeId].push(created);
      return created;
    } catch (e) { console.error('Failed to create position:', e); throw e; }
  };

  const renamePosition = async (rangeId, positionId, label) => {
    try {
      const updated = await positionApi.renamePosition(rangeId, positionId, label);
      _replacePosition(rangeId, updated);
      return updated;
    } catch (e) { console.error('Failed to rename position:', e); throw e; }
  };

  const deletePosition = async (rangeId, positionId) => {
    try {
      await positionApi.deletePosition(rangeId, positionId);
      if (positions.value[rangeId]) {
        positions.value[rangeId] = positions.value[rangeId].filter(p => p.id !== positionId);
      }
    } catch (e) { console.error('Failed to delete position:', e); throw e; }
  };

  const assignDeviceToPosition = async (rangeId, positionId, deviceId) => {
    try {
      const updated = await positionApi.assignDevice(rangeId, positionId, deviceId);
      _replacePosition(rangeId, updated);
      return updated;
    } catch (e) { console.error('Failed to assign device to position:', e); throw e; }
  };

  const removeDeviceFromPosition = async (rangeId, positionId) => {
    try {
      const updated = await positionApi.removeDevice(rangeId, positionId);
      _replacePosition(rangeId, updated);
      return updated;
    } catch (e) { console.error('Failed to remove device from position:', e); throw e; }
  };

  // ── Helpers ───────────────────────────────────────────────────────────────

  function _replacePosition(rangeId, updated) {
    if (!positions.value[rangeId]) return;
    const idx = positions.value[rangeId].findIndex(p => p.id === updated.id);
    if (idx > -1) positions.value[rangeId][idx] = updated;
  }

  return {
    ranges, selectedRange, isLoading, error, positions,
    selectRange, deselectRange,
    assignDeviceToRange, unassignDeviceFromRange,
    createRange, updateRange, deleteRange, setLocked, assignUser,
    initialize, loadApiData,
    loadPositions, createPosition, renamePosition, deletePosition,
    assignDeviceToPosition, removeDeviceFromPosition,
  };
});

import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as deviceApi from '../services/deviceApi.js';

export const useDeviceStore = defineStore('device', () => {
  const devices = ref([]);
  const isLoading = ref(false);
  const error = ref(null);

  const loadDevices = async (smartBoxId = null, rangeId = null) => {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await deviceApi.fetchDevices(smartBoxId, rangeId);
      devices.value = response?.content ?? response ?? [];
    } catch (e) {
      console.error('Failed to load devices:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
    } finally {
      isLoading.value = false;
    }
  };

  const loadDevicesForBox = async (smartBoxId) => {
    return loadDevices(smartBoxId);
  };

  const initialize = async () => {
    await loadDevices();
  };

  const removeDevice = async (deviceId) => {
    try {
      await deviceApi.deleteDevice(deviceId);
      const index = devices.value.findIndex(d => d.id === deviceId);
      if (index > -1) {
        devices.value.splice(index, 1);
      }
    } catch (e) {
      console.error('Failed to delete device:', e);
      throw e;
    }
  };

  const updateDeviceLocal = (deviceId, updates) => {
    const device = devices.value.find(d => d.id === deviceId);
    if (device) {
      Object.assign(device, updates);
    }
  };

  const updateDevice = async (deviceId, updates) => {
    try {
      const updated = await deviceApi.updateDevice(deviceId, updates);
      const index = devices.value.findIndex(d => d.id === deviceId);
      if (index > -1) {
        devices.value[index] = { ...devices.value[index], ...updated };
      }
      return updated;
    } catch (e) {
      console.error('Failed to update device:', e);
      throw e;
    }
  };

  const createDevice = async (smartBoxId, deviceTypeId, alias, rangeId = null) => {
    try {
      const created = await deviceApi.createDevice(smartBoxId, deviceTypeId, alias, rangeId);
      devices.value.push(created);
      return created;
    } catch (e) {
      console.error('Failed to create device:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
      throw e;
    }
  };

  const addDevice = async (device) => {
    return createDevice(
      device.smartBoxId || device.boxId,
      device.deviceTypeId,
      device.name || device.alias,
      device.rangeId ?? null,
    );
  };

  const applyDeviceEvent = (event) => {
    if (event.type !== 'device.health') return;
    const device = devices.value.find((d) => d.id === event.deviceId);
    if (device) {
      device.healthy = event.healthy;
      device.blocked = event.blocked;
    }
  };

  return {
    devices,
    isLoading,
    error,
    addDevice,
    removeDevice,
    updateDevice,
    updateDeviceLocal,
    createDevice,
    applyDeviceEvent,
    initialize,
    loadDevices,
    loadDevicesForBox,
  };
});

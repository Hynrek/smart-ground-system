/* global setInterval, clearInterval */
import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as otaApi from '@/services/otaApi.js';
import { isTerminalPhase } from '@/constants/ota.js';

export const useOtaStore = defineStore('ota', () => {
  // ── State ──
  const releases = ref([]);
  const statusByBox = ref({});      // boxId -> { version, phase, progress, detail, updatedAt }
  const isLoading = ref(false);
  const uploading = ref(false);
  const error = ref(null);

  // Non-reactive interval handles (boxId -> intervalId); not part of the public state.
  const pollers = new Map();

  // ── Actions: releases ──
  const fetchReleases = async () => {
    isLoading.value = true;
    error.value = null;
    try {
      releases.value = await otaApi.fetchReleases();
    } catch (e) {
      error.value = e.message ?? 'Unbekannter Fehler';
      releases.value = [];
    } finally {
      isLoading.value = false;
    }
  };

  const uploadRelease = async ({ type, version, file }) => {
    uploading.value = true;
    error.value = null;
    try {
      await otaApi.uploadRelease(type, version, file);
      await fetchReleases();
    } catch (e) {
      error.value = e.message ?? 'Upload fehlgeschlagen';
      throw e;
    } finally {
      uploading.value = false;
    }
  };

  // ── Actions: per-box status + polling ──
  const fetchStatus = async (boxId) => {
    try {
      const status = await otaApi.fetchOtaStatus(boxId);
      statusByBox.value = { ...statusByBox.value, [boxId]: status };
      return status;
    } catch (e) {
      console.error('OTA-Status konnte nicht geladen werden:', e);
      return null;
    }
  };

  const stopPolling = (boxId) => {
    const handle = pollers.get(boxId);
    if (handle !== undefined) {
      clearInterval(handle);
      pollers.delete(boxId);
    }
  };

  const startPolling = (boxId, intervalMs = 3000) => {
    stopPolling(boxId);
    const tick = async () => {
      const status = await fetchStatus(boxId);
      if (status && isTerminalPhase(status.phase)) {
        stopPolling(boxId);
      }
    };
    tick();
    pollers.set(boxId, setInterval(tick, intervalMs));
  };

  const stopAllPolling = () => {
    for (const handle of pollers.values()) clearInterval(handle);
    pollers.clear();
  };

  const triggerUpdate = async (boxId, type, version) => {
    error.value = null;
    try {
      await otaApi.triggerOta(boxId, type, version);
      startPolling(boxId);
    } catch (e) {
      error.value = e.message ?? 'Update konnte nicht gestartet werden';
      throw e;
    }
  };

  return {
    releases,
    statusByBox,
    isLoading,
    uploading,
    error,
    fetchReleases,
    uploadRelease,
    fetchStatus,
    startPolling,
    stopPolling,
    stopAllPolling,
    triggerUpdate,
    _pollers: pollers,
  };
});

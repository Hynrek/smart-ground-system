import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as otaApi from '@/services/otaApi.js';

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

  return {
    releases,
    statusByBox,
    isLoading,
    uploading,
    error,
    fetchReleases,
    uploadRelease,
    _pollers: pollers,
  };
});

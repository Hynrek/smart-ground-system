/* global setInterval, clearInterval */
import { defineStore } from 'pinia';
import { ref } from 'vue';
import * as onboardingApi from '@/services/onboardingApi.js';

export const useOnboardingStore = defineStore('onboarding', () => {
  // ── State ──
  const pendingBoxes = ref([]);
  const isLoading = ref(false);
  const error = ref(null);
  const couplingMac = ref(null);
  const coupleResults = ref({}); // mac -> {mac, status, tokenExpiresAt} | {error: string}

  // Non-reactive interval handle; not part of the public state.
  let pollHandle = null;

  // ── Actions: list + polling ──
  const fetchPending = async () => {
    isLoading.value = true;
    error.value = null;
    try {
      pendingBoxes.value = await onboardingApi.fetchPendingBoxes();
    } catch (e) {
      error.value = e.message ?? 'Unbekannter Fehler';
      pendingBoxes.value = [];
    } finally {
      isLoading.value = false;
    }
  };

  const stopPolling = () => {
    if (pollHandle !== null) {
      clearInterval(pollHandle);
      pollHandle = null;
    }
  };

  const startPolling = (intervalMs = 5000) => {
    stopPolling();
    fetchPending();
    pollHandle = setInterval(fetchPending, intervalMs);
  };

  const stopAllPolling = () => stopPolling();

  // ── Actions: coupling ──
  const coupleBox = async (mac) => {
    couplingMac.value = mac;
    try {
      const result = await onboardingApi.coupleBox(mac);
      coupleResults.value = { ...coupleResults.value, [mac]: result };
      return result;
    } catch (e) {
      coupleResults.value = { ...coupleResults.value, [mac]: { error: e.message ?? 'Kopplung fehlgeschlagen' } };
      throw e;
    } finally {
      couplingMac.value = null;
    }
  };

  return {
    pendingBoxes,
    isLoading,
    error,
    couplingMac,
    coupleResults,
    fetchPending,
    startPolling,
    stopPolling,
    stopAllPolling,
    coupleBox,
  };
});

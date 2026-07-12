import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import * as reservationApi from '../services/reservationApi.js';

export const useReservationStore = defineStore('reservation', () => {
  const currentReservation = ref(null);
  const isLoading = ref(false);
  const error = ref(null);

  const hasActiveReservation = computed(() => currentReservation.value !== null);

  const getReservationForRange = async (rangeId) => {
    try {
      const reservation = await reservationApi.getActiveReservation(rangeId);
      // apiFetch returns null for 204 No Content responses
      return reservation || null;
    } catch (e) {
      console.error('Failed to fetch reservation:', e);
      throw e;
    }
  };

  const reserve = async (rangeId) => {
    isLoading.value = true;
    error.value = null;
    try {
      const reservation = await reservationApi.reserveRange(rangeId);
      currentReservation.value = reservation;
      return reservation;
    } catch (e) {
      console.error('Failed to reserve range:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
      throw e;
    } finally {
      isLoading.value = false;
    }
  };

  const release = async (rangeId) => {
    isLoading.value = true;
    error.value = null;
    try {
      await reservationApi.releaseRange(rangeId);
      currentReservation.value = null;
    } catch (e) {
      console.error('Failed to release range:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
      throw e;
    } finally {
      isLoading.value = false;
    }
  };

  const forceRelease = async (rangeId) => {
    isLoading.value = true;
    error.value = null;
    try {
      await reservationApi.forceReleaseRange(rangeId);
      currentReservation.value = null;
    } catch (e) {
      console.error('Failed to force release range:', e);
      error.value = e.message ?? 'Unbekannter Fehler';
      throw e;
    } finally {
      isLoading.value = false;
    }
  };

  const clear = () => {
    currentReservation.value = null;
    error.value = null;
  };

  return {
    currentReservation,
    isLoading,
    error,
    hasActiveReservation,
    getReservationForRange,
    reserve,
    release,
    forceRelease,
    clear,
  };
});

import { apiFetch } from './apiClient.js';

export async function reserveRange(rangeId) {
  return apiFetch(`/reservations/ranges/${rangeId}/reserve`, {
    method: 'POST',
  });
}

export async function releaseRange(rangeId) {
  // Returns 204 No Content (null)
  return apiFetch(`/reservations/ranges/${rangeId}/release`, {
    method: 'POST',
  });
}

export async function getActiveReservation(rangeId) {
  // Returns 200 with reservation or 204 No Content (null)
  return apiFetch(`/reservations/ranges/${rangeId}`, { method: 'GET' });
}

export async function forceReleaseRange(rangeId) {
  // Returns 204 No Content (null)
  return apiFetch(`/reservations/ranges/${rangeId}`, {
    method: 'DELETE',
  });
}

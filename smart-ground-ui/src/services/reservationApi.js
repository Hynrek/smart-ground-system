import { apiFetch } from './apiClient.js';

export async function reserveRange(rangeId) {
  return apiFetch(`/ranges/${rangeId}/reservation`, {
    method: 'POST',
  });
}

export async function releaseRange(rangeId) {
  // Returns 204 No Content (null)
  return apiFetch(`/ranges/${rangeId}/reservation`, {
    method: 'DELETE',
  });
}

export async function getActiveReservation(rangeId) {
  // Returns 200 with reservation or 204 No Content (null)
  return apiFetch(`/ranges/${rangeId}/reservation`, { method: 'GET' });
}

export async function forceReleaseRange(rangeId) {
  // Returns 204 No Content (null); backend resolves force-vs-own release from the caller's role
  return apiFetch(`/ranges/${rangeId}/reservation`, {
    method: 'DELETE',
  });
}

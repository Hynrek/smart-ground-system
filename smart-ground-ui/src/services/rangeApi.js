/* global URLSearchParams */
import { apiFetch } from './apiClient.js';

export async function fetchRanges(page = 0, size = 50) {
  const params = new URLSearchParams({ page, size });
  return apiFetch(`/ranges?${params}`);
}

export async function fetchRange(id) {
  return apiFetch(`/ranges/${id}`);
}

export async function createRange(name, description = null) {
  return apiFetch('/ranges', {
    method: 'POST',
    body: JSON.stringify({ name, description }),
  });
}

export async function updateRange(id, name, description = null) {
  return apiFetch(`/ranges/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, description }),
  });
}

export async function deleteRange(id) {
  return apiFetch(`/ranges/${id}`, { method: 'DELETE' });
}


export async function setRangeLocked(id, locked) {
  return apiFetch(`/ranges/${id}/locked`, {
    method: 'PATCH',
    body: JSON.stringify({ locked }),
  });
}

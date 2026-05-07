/* global URLSearchParams */
import { apiFetch } from './apiClient.js';

export async function fetchSmartBoxes(page = 0, size = 50) {
  const params = new URLSearchParams({ page, size });
  return apiFetch(`/smart-boxes?${params}`);
}

export async function fetchSmartBox(id) {
  return apiFetch(`/smart-boxes/${id}`);
}

export async function setSmartBoxAlias(id, alias) {
  return apiFetch(`/smart-boxes/${id}/alias`, {
    method: 'PATCH',
    body: JSON.stringify({ alias }),
  });
}


export async function pushSmartBoxConfig(smartBoxId) {
  return apiFetch(`/smart-boxes/${smartBoxId}/push-config`, { method: 'POST' });
}

import { apiFetch } from './apiClient.js';

const base = (rangeId) => `/ranges/${rangeId}/positions`;

export async function fetchPositions(rangeId) {
  return apiFetch(base(rangeId));
}

export async function createPosition(rangeId, label = null) {
  return apiFetch(base(rangeId), {
    method: 'POST',
    body: JSON.stringify(label ? { label } : {}),
  });
}

export async function renamePosition(rangeId, positionId, label) {
  return apiFetch(`${base(rangeId)}/${positionId}`, {
    method: 'PUT',
    body: JSON.stringify({ label }),
  });
}

export async function deletePosition(rangeId, positionId) {
  return apiFetch(`${base(rangeId)}/${positionId}`, { method: 'DELETE' });
}

export async function assignDevice(rangeId, positionId, deviceId) {
  return apiFetch(`${base(rangeId)}/${positionId}/device`, {
    method: 'PUT',
    body: JSON.stringify({ deviceId }),
  });
}

export async function removeDevice(rangeId, positionId) {
  return apiFetch(`${base(rangeId)}/${positionId}/device`, { method: 'DELETE' });
}

export async function sendPositionCommand(rangeId, positionId) {
  return apiFetch(`/ranges/${rangeId}/positions/${positionId}/command`, { method: 'POST' });
}

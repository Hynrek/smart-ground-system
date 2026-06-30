/* global URLSearchParams */
import { apiFetch } from './apiClient.js';

export async function fetchDevices(smartBoxId = null, rangeId = null, page = 0, size = 50) {
  const params = new URLSearchParams({ page, size });
  if (smartBoxId) params.append('smartBoxId', smartBoxId);
  if (rangeId) params.append('rangeId', rangeId);
  return apiFetch(`/devices?${params}`);
}

export async function fetchDevice(id) {
  return apiFetch(`/devices/${id}`);
}

export async function createDevice(smartBoxId, deviceTypeId, alias, rangeId = null) {
  return apiFetch('/devices', {
    method: 'POST',
    body: JSON.stringify({ smartBoxId, deviceTypeId, alias, rangeId }),
  });
}

export async function updateDevice(id, updates) {
  return apiFetch(`/devices/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(updates),
  });
}

export async function deleteDevice(id) {
  return apiFetch(`/devices/${id}`, { method: 'DELETE' });
}

export async function sendDeviceCommand(id, command = null) {
  const options = { method: 'POST' };
  if (command !== null) {
    options.body = JSON.stringify({ command });
  }
  return apiFetch(`/devices/${id}/command`, options);
}

export async function assignDeviceToRange(deviceId, rangeId) {
  return apiFetch(`/devices/${deviceId}/range`, {
    method: 'POST',
    body: JSON.stringify({ rangeId }),
  });
}

export async function removeDeviceFromRange(deviceId) {
  return apiFetch(`/devices/${deviceId}/range`, { method: 'DELETE' });
}

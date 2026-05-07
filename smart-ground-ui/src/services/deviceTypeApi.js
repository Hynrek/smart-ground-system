import { apiFetch } from './apiClient.js';

// ── DeviceTypes ───────────────────────────────────────────────────────────────

export async function fetchDeviceTypes() {
  return apiFetch('/device-types');
}

export async function fetchDeviceType(id) {
  return apiFetch(`/device-types/${id}`);
}

export async function fetchDeviceTypesByFirmware(firmwareConfigId) {
  return apiFetch(`/device-types?firmwareConfigId=${firmwareConfigId}`);
}

// ── DeviceTypeGroups (now under /device-types/groups) ─────────────────────────

export async function fetchDeviceTypeGroups() {
  return apiFetch('/device-types/groups');
}

export async function fetchDeviceTypeGroup(id) {
  return apiFetch(`/device-types/groups/${id}`);
}

// ── FirmwareConfigs (now under /device-types/firmware-configs) ────────────────

export async function fetchFirmwareConfigs() {
  return apiFetch('/device-types/firmware-configs');
}

export async function fetchFirmwareConfig(id) {
  return apiFetch(`/device-types/firmware-configs/${id}`);
}

export async function registerFirmwareConfig(payload) {
  return apiFetch('/device-types/firmware-configs', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function updateDeviceType(id, payload) {
  return apiFetch(`/device-types/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

/* global FormData */
import { apiFetch, apiUpload } from './apiClient.js';

export async function fetchReleases() {
  return apiFetch('/ota/releases');
}

export async function uploadRelease(type, version, file) {
  const formData = new FormData();
  formData.append('type', type);
  formData.append('version', version);
  formData.append('file', file);
  return apiUpload('/ota/releases', formData);
}

export async function triggerOta(boxId, type, version) {
  return apiFetch(`/smart-boxes/${boxId}/ota`, {
    method: 'POST',
    body: JSON.stringify({ type, version }),
  });
}

export async function fetchOtaStatus(boxId) {
  return apiFetch(`/smart-boxes/${boxId}/ota`);
}

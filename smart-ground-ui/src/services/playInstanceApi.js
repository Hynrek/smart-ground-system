/* global URLSearchParams */
import { apiFetch } from './apiClient.js';

export async function startPasseInstance(passeId, players) {
  return apiFetch('/play-instances/passe', {
    method: 'POST',
    body: JSON.stringify({ passeId, players }),
  });
}

export async function startSerieInstance(serieId, players) {
  return apiFetch('/play-instances/serie', {
    method: 'POST',
    body: JSON.stringify({ serieId, players }),
  });
}

export async function listPlayInstances(params = {}) {
  const query = Object.keys(params).length
    ? '?' + new URLSearchParams(params).toString()
    : '';
  return apiFetch(`/play-instances${query}`);
}

export async function getPlayInstance(instanceId) {
  return apiFetch(`/play-instances/${instanceId}`);
}

export async function stopPlayInstance(instanceId) {
  return apiFetch(`/play-instances/${instanceId}`, { method: 'DELETE' });
}

export async function startBlock(instanceId, blockId) {
  return apiFetch(`/play-instances/${instanceId}/blocks/${blockId}/start`, { method: 'POST' });
}

export async function completeBlock(instanceId, blockId, playerResults) {
  return apiFetch(`/play-instances/${instanceId}/blocks/${blockId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ playerResults }),
  });
}

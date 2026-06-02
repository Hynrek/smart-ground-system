/* global URLSearchParams */
import { apiFetch } from './apiClient.js';

export async function startProgrammeInstance(programmeId, players) {
  return apiFetch('/play-instances/programme', {
    method: 'POST',
    body: JSON.stringify({ programmeId, players }),
  });
}

export async function startTrainingInstance(trainingId, players) {
  return apiFetch('/play-instances/training', {
    method: 'POST',
    body: JSON.stringify({ trainingId, players }),
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

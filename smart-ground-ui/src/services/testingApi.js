import { apiFetch } from './apiClient.js';

export async function createTestUser(credential) {
  return apiFetch('/testing/users', {
    method: 'POST',
    body: JSON.stringify({ credential }),
  });
}

export async function seedRanges() {
  return apiFetch('/testing/ranges/seed', { method: 'POST' });
}

export async function createMockSmartBox({ deviceCount, alias }) {
  return apiFetch('/testing/mock-smartbox', {
    method: 'POST',
    body: JSON.stringify({ deviceCount, alias }),
  });
}

import { apiFetch } from './apiClient.js';

export async function login(username, password) {
  return apiFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export async function createUser(username, password, role) {
  return apiFetch('/users', {
    method: 'POST',
    body: JSON.stringify({ username, password, role }),
  });
}

export async function getMe() {
  return apiFetch('/auth/me');
}

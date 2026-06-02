import { getAuthHeaders } from './authHeader.js';
import router from '@/router/index.js';

export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

export async function handleResponse(response) {
  if (response.ok) {
    if (response.status === 204) return null;
    return response.json();
  }
  if (response.status === 401) {
    router.push('/login');
    throw new Error('Session expired. Please log in again.');
  }
  if (response.status === 403) {
    router.push('/no-access');
    throw new Error('Access denied.');
  }
  let message = `HTTP ${response.status}`;
  try {
    const problem = await response.json();
    if (problem.detail) message = problem.detail;
    else if (problem.title) message = problem.title;
  } catch {
    // Ignore JSON parse errors
  }
  throw new Error(message);
}

export async function apiFetch(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      ...getAuthHeaders(),
      ...options.headers,
    },
  });
  return handleResponse(response);
}

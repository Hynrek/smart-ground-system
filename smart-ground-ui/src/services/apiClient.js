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
  let body = null;
  try {
    body = await response.json();
    if (body.detail) message = body.detail;
    else if (body.title) message = body.title;
    else if (body.message) message = body.message;
  } catch {
    // Ignore JSON parse errors
  }
  // Attach status + parsed body so callers can inspect structured errors
  // (e.g. a 409 UnresolvedTiesError carrying the unresolved tied blocks).
  const error = new Error(message);
  error.status = response.status;
  error.body = body;
  throw error;
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

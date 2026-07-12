import { getAuthHeaders } from './authHeader.js';
import router from '@/router/index.js';

// Relative by default so the browser hits whatever origin served the SPA.
// In production, nginx proxies /api to the backend container; in dev, the Vite
// proxy (see vite.config.js) forwards /api to localhost:8080. Override with an
// absolute URL via VITE_API_BASE_URL only if the backend lives on another origin.
export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

export async function handleResponse(response) {
  if (response.ok) {
    if (response.status === 204 || response.status === 202) return null;
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

// Multipart upload: send FormData with the auth token but WITHOUT a Content-Type
// header, so the browser sets multipart/form-data + boundary automatically.
export async function apiUpload(path, formData) {
  const token = localStorage.getItem('sg_token');
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers,
    body: formData,
  });
  return handleResponse(response);
}

export async function apiFetch(path, options = {}, baseUrl = BASE_URL) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers: {
      ...getAuthHeaders(),
      ...options.headers,
    },
  });
  return handleResponse(response);
}

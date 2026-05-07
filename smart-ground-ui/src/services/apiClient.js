import { getAuthHeaders } from './authHeader.js';

export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

export async function handleResponse(response) {
  if (response.ok) {
    if (response.status === 204) return null;
    return response.json();
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

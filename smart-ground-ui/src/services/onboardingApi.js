import { apiFetch } from './apiClient.js';

// node-api lives on smart-ground-node, a different origin than the Hub.
// Relative by default so the browser hits whatever origin served the SPA
// (Vite proxies /node-api in dev, nginx does the equivalent in prod) —
// override with an absolute URL via VITE_NODE_API_BASE_URL only if the
// Node genuinely runs on a different origin than the SPA.
const NODE_API_BASE_URL = import.meta.env.VITE_NODE_API_BASE_URL ?? '/node-api/v1';

export async function fetchPendingBoxes() {
  return apiFetch('/onboarding/pending', {}, NODE_API_BASE_URL);
}

export async function coupleBox(mac) {
  return apiFetch(`/onboarding/${encodeURIComponent(mac)}/couple`, { method: 'POST' }, NODE_API_BASE_URL);
}

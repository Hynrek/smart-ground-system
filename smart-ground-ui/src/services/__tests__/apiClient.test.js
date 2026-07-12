/* global FormData */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { apiFetch, apiUpload, handleResponse } from '@/services/apiClient.js';

describe('apiClient upload + 202', () => {
  beforeEach(() => {
    localStorage.setItem('sg_token', 'tok123');
    vi.stubGlobal('fetch', vi.fn());
  });
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('handleResponse returns null for 202 (empty accepted body)', async () => {
    const res = { ok: true, status: 202, json: () => Promise.reject(new Error('no body')) };
    await expect(handleResponse(res)).resolves.toBeNull();
  });

  it('apiUpload posts FormData with auth header and NO content-type', async () => {
    fetch.mockResolvedValue({ ok: true, status: 201, json: () => Promise.resolve({ id: 'r1' }) });
    const fd = new FormData();
    fd.append('version', '0.7');
    const out = await apiUpload('/ota/releases', fd);

    expect(out).toEqual({ id: 'r1' });
    const [url, opts] = fetch.mock.calls[0];
    expect(url).toContain('/ota/releases');
    expect(opts.method).toBe('POST');
    expect(opts.body).toBe(fd);
    expect(opts.headers.Authorization).toBe('Bearer tok123');
    expect(opts.headers['Content-Type']).toBeUndefined();
  });
});

describe('apiClient apiFetch base URL override', () => {
  beforeEach(() => {
    localStorage.setItem('sg_token', 'tok123');
    vi.stubGlobal('fetch', vi.fn());
  });
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  it('apiFetch defaults to the Hub base URL', async () => {
    fetch.mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve({ ok: true }) });
    await apiFetch('/ranges');
    const [url] = fetch.mock.calls[0];
    expect(url).toBe('/api/ranges');
  });

  it('apiFetch uses an override base URL when provided', async () => {
    fetch.mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve({ ok: true }) });
    await apiFetch('/onboarding/pending', {}, '/node-api/v1');
    const [url] = fetch.mock.calls[0];
    expect(url).toBe('/node-api/v1/onboarding/pending');
  });
});

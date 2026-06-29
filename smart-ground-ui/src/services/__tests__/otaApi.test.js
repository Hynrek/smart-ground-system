/* global File */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as apiClient from '@/services/apiClient.js';
import * as otaApi from '@/services/otaApi.js';

vi.mock('@/services/apiClient.js');

describe('otaApi', () => {
  beforeEach(() => vi.clearAllMocks());

  it('fetchReleases hits GET /ota/releases', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue([{ id: 'r1' }]);
    const out = await otaApi.fetchReleases();
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/ota/releases');
    expect(out).toEqual([{ id: 'r1' }]);
  });

  it('uploadRelease posts multipart with type, version, file', async () => {
    vi.mocked(apiClient.apiUpload).mockResolvedValue({ id: 'r2' });
    const file = new File(['x'], 'bundle.zip');
    await otaApi.uploadRelease('APP', '0.7', file);
    const [path, fd] = vi.mocked(apiClient.apiUpload).mock.calls[0];
    expect(path).toBe('/ota/releases');
    expect(fd.get('type')).toBe('APP');
    expect(fd.get('version')).toBe('0.7');
    expect(fd.get('file')).toBe(file);
  });

  it('triggerOta posts JSON {type, version} to the box', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue(null);
    await otaApi.triggerOta('box-1', 'APP', '0.7');
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/smart-boxes/box-1/ota', {
      method: 'POST',
      body: JSON.stringify({ type: 'APP', version: '0.7' }),
    });
  });

  it('fetchOtaStatus hits GET /smart-boxes/{id}/ota', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ phase: 'APPLIED' });
    const out = await otaApi.fetchOtaStatus('box-1');
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/smart-boxes/box-1/ota');
    expect(out).toEqual({ phase: 'APPLIED' });
  });
});

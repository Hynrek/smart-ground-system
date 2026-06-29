/* global File */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useOtaStore } from '@/stores/otaStore.js';
import * as otaApi from '@/services/otaApi.js';

vi.mock('@/services/otaApi.js');

describe('otaStore releases', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('fetchReleases loads releases into state', async () => {
    vi.mocked(otaApi.fetchReleases).mockResolvedValue([{ id: 'r1', type: 'APP', version: '0.7' }]);
    const store = useOtaStore();
    await store.fetchReleases();
    expect(store.releases).toHaveLength(1);
    expect(store.error).toBe(null);
    expect(store.isLoading).toBe(false);
  });

  it('fetchReleases records error message on failure', async () => {
    vi.mocked(otaApi.fetchReleases).mockRejectedValue(new Error('boom'));
    const store = useOtaStore();
    await store.fetchReleases();
    expect(store.error).toBe('boom');
    expect(store.releases).toEqual([]);
  });

  it('uploadRelease uploads then refetches releases', async () => {
    vi.mocked(otaApi.uploadRelease).mockResolvedValue({ id: 'r2' });
    vi.mocked(otaApi.fetchReleases).mockResolvedValue([{ id: 'r2', type: 'APP', version: '0.8' }]);
    const store = useOtaStore();
    const file = new File(['x'], 'b.zip');
    await store.uploadRelease({ type: 'APP', version: '0.8', file });
    expect(otaApi.uploadRelease).toHaveBeenCalledWith('APP', '0.8', file);
    expect(store.releases.some(r => r.version === '0.8')).toBe(true);
    expect(store.uploading).toBe(false);
  });
});

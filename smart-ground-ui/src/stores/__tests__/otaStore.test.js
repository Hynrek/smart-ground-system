/* global File */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
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

describe('otaStore status + polling', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('fetchStatus stores status under the box id', async () => {
    vi.mocked(otaApi.fetchOtaStatus).mockResolvedValue({ phase: 'DOWNLOADING', progress: 40, version: '0.7' });
    const store = useOtaStore();
    await store.fetchStatus('box-1');
    expect(store.statusByBox['box-1'].phase).toBe('DOWNLOADING');
  });

  it('startPolling fetches repeatedly until a terminal phase, then stops', async () => {
    vi.mocked(otaApi.fetchOtaStatus)
      .mockResolvedValueOnce({ phase: 'DOWNLOADING', progress: 20 })
      .mockResolvedValueOnce({ phase: 'APPLYING', progress: 80 })
      .mockResolvedValue({ phase: 'APPLIED', progress: 100 });
    const store = useOtaStore();

    store.startPolling('box-1', 1000);
    await vi.advanceTimersByTimeAsync(0);
    expect(store.statusByBox['box-1'].phase).toBe('DOWNLOADING');
    await vi.advanceTimersByTimeAsync(1000);
    expect(store.statusByBox['box-1'].phase).toBe('APPLYING');
    await vi.advanceTimersByTimeAsync(1000);
    expect(store.statusByBox['box-1'].phase).toBe('APPLIED');

    const callsAtTerminal = vi.mocked(otaApi.fetchOtaStatus).mock.calls.length;
    await vi.advanceTimersByTimeAsync(3000);
    expect(vi.mocked(otaApi.fetchOtaStatus).mock.calls.length).toBe(callsAtTerminal);
  });

  it('triggerUpdate posts the command then starts polling', async () => {
    vi.mocked(otaApi.triggerOta).mockResolvedValue(null);
    vi.mocked(otaApi.fetchOtaStatus).mockResolvedValue({ phase: 'DOWNLOADING', progress: 0 });
    const store = useOtaStore();
    await store.triggerUpdate('box-1', 'APP', '0.7');
    expect(otaApi.triggerOta).toHaveBeenCalledWith('box-1', 'APP', '0.7');
    await vi.advanceTimersByTimeAsync(0);
    expect(store.statusByBox['box-1'].phase).toBe('DOWNLOADING');
    store.stopAllPolling();
  });

  it('stopPolling clears the interval for a box', async () => {
    vi.mocked(otaApi.fetchOtaStatus).mockResolvedValue({ phase: 'DOWNLOADING', progress: 10 });
    const store = useOtaStore();
    store.startPolling('box-1', 1000);
    await vi.advanceTimersByTimeAsync(0);
    const calls = vi.mocked(otaApi.fetchOtaStatus).mock.calls.length;
    store.stopPolling('box-1');
    await vi.advanceTimersByTimeAsync(5000);
    expect(vi.mocked(otaApi.fetchOtaStatus).mock.calls.length).toBe(calls);
  });
});

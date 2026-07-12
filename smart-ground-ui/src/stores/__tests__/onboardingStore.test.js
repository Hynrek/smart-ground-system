import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useOnboardingStore } from '@/stores/onboardingStore.js';
import * as onboardingApi from '@/services/onboardingApi.js';

vi.mock('@/services/onboardingApi.js');

describe('onboardingStore fetch', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('fetchPending loads pending boxes into state', async () => {
    vi.mocked(onboardingApi.fetchPendingBoxes).mockResolvedValue([{ mac: 'AA:BB:CC:DD:EE:01', rssi: -40 }]);
    const store = useOnboardingStore();
    await store.fetchPending();
    expect(store.pendingBoxes).toHaveLength(1);
    expect(store.error).toBe(null);
    expect(store.isLoading).toBe(false);
  });

  it('fetchPending records error message on failure', async () => {
    vi.mocked(onboardingApi.fetchPendingBoxes).mockRejectedValue(new Error('boom'));
    const store = useOnboardingStore();
    await store.fetchPending();
    expect(store.error).toBe('boom');
    expect(store.pendingBoxes).toEqual([]);
  });
});

describe('onboardingStore polling', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('startPolling fetches immediately then repeatedly', async () => {
    vi.mocked(onboardingApi.fetchPendingBoxes)
      .mockResolvedValueOnce([{ mac: 'AA:BB:CC:DD:EE:01' }])
      .mockResolvedValue([{ mac: 'AA:BB:CC:DD:EE:01' }, { mac: 'AA:BB:CC:DD:EE:02' }]);
    const store = useOnboardingStore();

    store.startPolling(1000);
    await vi.advanceTimersByTimeAsync(0);
    expect(store.pendingBoxes).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1000);
    expect(store.pendingBoxes).toHaveLength(2);

    store.stopAllPolling();
  });

  it('stopAllPolling stops further fetches', async () => {
    vi.mocked(onboardingApi.fetchPendingBoxes).mockResolvedValue([{ mac: 'AA:BB:CC:DD:EE:01' }]);
    const store = useOnboardingStore();
    store.startPolling(1000);
    await vi.advanceTimersByTimeAsync(0);
    const calls = vi.mocked(onboardingApi.fetchPendingBoxes).mock.calls.length;
    store.stopAllPolling();
    await vi.advanceTimersByTimeAsync(5000);
    expect(vi.mocked(onboardingApi.fetchPendingBoxes).mock.calls.length).toBe(calls);
  });
});

describe('onboardingStore coupleBox', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('coupleBox records the result on success and clears couplingMac', async () => {
    vi.mocked(onboardingApi.coupleBox).mockResolvedValue({
      mac: 'AA:BB:CC:DD:EE:01',
      status: 'offered',
      tokenExpiresAt: '2026-07-12T12:05:00Z',
    });
    const store = useOnboardingStore();

    const promise = store.coupleBox('AA:BB:CC:DD:EE:01');
    expect(store.couplingMac).toBe('AA:BB:CC:DD:EE:01');
    await promise;

    expect(store.couplingMac).toBe(null);
    expect(store.coupleResults['AA:BB:CC:DD:EE:01'].status).toBe('offered');
  });

  it('coupleBox records an error result on failure and clears couplingMac', async () => {
    vi.mocked(onboardingApi.coupleBox).mockRejectedValue(new Error('Gerät nicht mehr erreichbar.'));
    const store = useOnboardingStore();

    await expect(store.coupleBox('AA:BB:CC:DD:EE:01')).rejects.toThrow('Gerät nicht mehr erreichbar.');

    expect(store.couplingMac).toBe(null);
    expect(store.coupleResults['AA:BB:CC:DD:EE:01'].error).toBe('Gerät nicht mehr erreichbar.');
  });
});

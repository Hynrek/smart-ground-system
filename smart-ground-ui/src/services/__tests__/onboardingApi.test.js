import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as apiClient from '@/services/apiClient.js';
import * as onboardingApi from '@/services/onboardingApi.js';

vi.mock('@/services/apiClient.js');

describe('onboardingApi', () => {
  beforeEach(() => vi.clearAllMocks());

  it('fetchPendingBoxes hits GET /onboarding/pending on the node-api base URL', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue([{ mac: 'AA:BB:CC:DD:EE:01' }]);
    const out = await onboardingApi.fetchPendingBoxes();
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/onboarding/pending', {}, '/node-api/v1');
    expect(out).toEqual([{ mac: 'AA:BB:CC:DD:EE:01' }]);
  });

  it('coupleBox posts to /onboarding/{mac}/couple on the node-api base URL', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ mac: 'AA:BB:CC:DD:EE:01', status: 'offered' });
    const out = await onboardingApi.coupleBox('AA:BB:CC:DD:EE:01');
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/onboarding/AA%3ABB%3ACC%3ADD%3AEE%3A01/couple',
      { method: 'POST' },
      '/node-api/v1',
    );
    expect(out).toEqual({ mac: 'AA:BB:CC:DD:EE:01', status: 'offered' });
  });

  it('coupleBox URL-encodes the MAC path segment', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ mac: 'weird mac', status: 'offered' });
    await onboardingApi.coupleBox('weird mac');
    expect(apiClient.apiFetch).toHaveBeenCalledWith(
      '/onboarding/weird%20mac/couple',
      { method: 'POST' },
      '/node-api/v1',
    );
  });
});

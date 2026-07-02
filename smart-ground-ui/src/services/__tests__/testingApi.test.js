import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as apiClient from '@/services/apiClient.js';
import * as testingApi from '@/services/testingApi.js';

vi.mock('@/services/apiClient.js');

describe('testingApi', () => {
  beforeEach(() => vi.clearAllMocks());

  it('createTestUser posts the credential', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ username: 'bob' });
    await testingApi.createTestUser('bob');
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/testing/users', {
      method: 'POST',
      body: JSON.stringify({ credential: 'bob' }),
    });
  });

  it('seedRanges posts to the seed endpoint', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ ranges: [] });
    await testingApi.seedRanges();
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/testing/ranges/seed', { method: 'POST' });
  });

  it('createMockSmartBox posts device count and alias', async () => {
    vi.mocked(apiClient.apiFetch).mockResolvedValue({ macAddress: 'x' });
    await testingApi.createMockSmartBox({ deviceCount: 4, alias: 'Mock-1' });
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/testing/mock-smartbox', {
      method: 'POST',
      body: JSON.stringify({ deviceCount: 4, alias: 'Mock-1' }),
    });
  });
});

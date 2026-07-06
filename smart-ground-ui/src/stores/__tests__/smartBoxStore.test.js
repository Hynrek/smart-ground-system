import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useSmartBoxStore } from '../smartBoxStore.js';
import * as smartBoxApi from '../../services/smartBoxApi.js';

vi.mock('../../services/smartBoxApi.js');

describe('smartBoxStore.deleteSmartBox', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('calls the API and removes the box from local state', async () => {
    const store = useSmartBoxStore();
    store.smartboxes = [
      { id: 'box-1', alias: 'A' },
      { id: 'box-2', alias: 'B' },
    ];
    smartBoxApi.deleteSmartBox.mockResolvedValue(null);

    await store.deleteSmartBox('box-1');

    expect(smartBoxApi.deleteSmartBox).toHaveBeenCalledWith('box-1');
    expect(store.smartboxes.map((b) => b.id)).toEqual(['box-2']);
  });

  it('keeps the box in state and rethrows if the API fails', async () => {
    const store = useSmartBoxStore();
    store.smartboxes = [{ id: 'box-1', alias: 'A' }];
    smartBoxApi.deleteSmartBox.mockRejectedValue(new Error('boom'));

    await expect(store.deleteSmartBox('box-1')).rejects.toThrow('boom');
    expect(store.smartboxes.map((b) => b.id)).toEqual(['box-1']);
  });
});

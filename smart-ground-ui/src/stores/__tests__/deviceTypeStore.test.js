import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useDeviceTypeStore } from '@/stores/deviceTypeStore.js';
import * as deviceTypeApi from '@/services/deviceTypeApi.js';

vi.mock('@/services/deviceTypeApi.js');

describe('deviceTypeStore createDeviceConfig', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('creates signal type then device type and appends to deviceTypes', async () => {
    vi.mocked(deviceTypeApi.createSignalType).mockResolvedValue({ id: 'st1' });
    vi.mocked(deviceTypeApi.createDeviceType).mockResolvedValue({
      id: 'dt1', name: 'Werfer 3', signalDurationMs: 500, groupId: 'g1', command: '8',
    });

    const store = useDeviceTypeStore();
    store.deviceTypes = [];

    await store.createDeviceConfig('fc1', {
      name: 'Werfer 3',
      groupId: 'g1',
      pin: 8,
      signalDurationMs: 500,
    });

    expect(deviceTypeApi.createSignalType).toHaveBeenCalledWith({
      firmwareConfigId: 'fc1',
      direction: 'OUTPUT',
      device: 'GPIO',
      command: '8',
    });
    expect(deviceTypeApi.createDeviceType).toHaveBeenCalledWith({
      name: 'Werfer 3',
      groupId: 'g1',
      signalTypeId: 'st1',
      signalDurationMs: 500,
    });
    expect(store.deviceTypes).toHaveLength(1);
    expect(store.deviceTypes[0].name).toBe('Werfer 3');
  });

  it('sets error and does not append if signal type creation fails', async () => {
    vi.mocked(deviceTypeApi.createSignalType).mockRejectedValue(new Error('forbidden'));

    const store = useDeviceTypeStore();
    store.deviceTypes = [];

    await expect(store.createDeviceConfig('fc1', {
      name: 'X', groupId: 'g1', pin: 5, signalDurationMs: 100,
    })).rejects.toThrow('forbidden');

    expect(store.deviceTypes).toHaveLength(0);
    expect(store.error).toBe('forbidden');
  });
});

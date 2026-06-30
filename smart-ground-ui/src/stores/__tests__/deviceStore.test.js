import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useDeviceStore } from '@/stores/deviceStore.js';
import * as deviceApi from '@/services/deviceApi.js';

vi.mock('@/services/deviceApi.js');

describe('deviceStore block/unblock', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('blockDevice sends BLOCK command and refreshes device state', async () => {
    vi.mocked(deviceApi.sendDeviceCommand).mockResolvedValue(undefined);
    vi.mocked(deviceApi.fetchDevice).mockResolvedValue({
      id: 'd1', alias: 'Werfer 1', blocked: true, adminBlocked: false,
    });

    const store = useDeviceStore();
    store.devices = [{ id: 'd1', alias: 'Werfer 1', blocked: false, adminBlocked: false }];

    await store.blockDevice('d1');

    expect(deviceApi.sendDeviceCommand).toHaveBeenCalledWith('d1', 'BLOCK');
    expect(deviceApi.fetchDevice).toHaveBeenCalledWith('d1');
    expect(store.devices[0].blocked).toBe(true);
  });

  it('unblockDevice sends UNBLOCK command and refreshes device state', async () => {
    vi.mocked(deviceApi.sendDeviceCommand).mockResolvedValue(undefined);
    vi.mocked(deviceApi.fetchDevice).mockResolvedValue({
      id: 'd1', alias: 'Werfer 1', blocked: false, adminBlocked: false,
    });

    const store = useDeviceStore();
    store.devices = [{ id: 'd1', alias: 'Werfer 1', blocked: true, adminBlocked: false }];

    await store.unblockDevice('d1');

    expect(deviceApi.sendDeviceCommand).toHaveBeenCalledWith('d1', 'UNBLOCK');
    expect(store.devices[0].blocked).toBe(false);
  });

  it('blockDevice sets error on failure', async () => {
    vi.mocked(deviceApi.sendDeviceCommand).mockRejectedValue(new Error('network error'));

    const store = useDeviceStore();
    store.devices = [{ id: 'd1', blocked: false }];

    await store.blockDevice('d1');

    expect(store.error).toBe('network error');
    expect(store.devices[0].blocked).toBe(false);
  });

  it('applyDeviceEvent updates adminBlocked', () => {
    const store = useDeviceStore();
    store.devices = [{ id: 'd1', healthy: true, blocked: false, adminBlocked: false }];

    store.applyDeviceEvent({ type: 'device.health', deviceId: 'd1', healthy: false, blocked: true, adminBlocked: true });

    expect(store.devices[0].healthy).toBe(false);
    expect(store.devices[0].blocked).toBe(true);
    expect(store.devices[0].adminBlocked).toBe(true);
  });
});

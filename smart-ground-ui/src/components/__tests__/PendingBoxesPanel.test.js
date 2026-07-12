import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import PendingBoxesPanel from '@/components/PendingBoxesPanel.vue';
import { useOnboardingStore } from '@/stores/onboardingStore.js';

describe('PendingBoxesPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('starts polling on mount and stops on unmount', () => {
    const store = useOnboardingStore();
    store.startPolling = vi.fn();
    store.stopAllPolling = vi.fn();
    const wrapper = mount(PendingBoxesPanel);
    expect(store.startPolling).toHaveBeenCalled();
    wrapper.unmount();
    expect(store.stopAllPolling).toHaveBeenCalled();
  });

  it('shows the empty state when no boxes are pending', () => {
    const store = useOnboardingStore();
    store.startPolling = vi.fn();
    store.pendingBoxes = [];
    const wrapper = mount(PendingBoxesPanel);
    expect(wrapper.text()).toContain('Keine neuen Geräte');
  });

  it('lists pending boxes with a Koppeln button', () => {
    const store = useOnboardingStore();
    store.startPolling = vi.fn();
    store.pendingBoxes = [
      { mac: 'AA:BB:CC:DD:EE:01', rssi: -42, firstSeen: '2026-07-12T10:00:00Z', lastSeen: '2026-07-12T10:00:05Z' },
    ];
    const wrapper = mount(PendingBoxesPanel);
    expect(wrapper.text()).toContain('AA:BB:CC:DD:EE:01');
    expect(wrapper.text()).toContain('-42');
    expect(wrapper.find('[data-testid="couple-btn-AA:BB:CC:DD:EE:01"]').exists()).toBe(true);
  });

  it('clicking Koppeln calls store.coupleBox with the mac', async () => {
    const store = useOnboardingStore();
    store.startPolling = vi.fn();
    store.coupleBox = vi.fn().mockResolvedValue({ mac: 'AA:BB:CC:DD:EE:01', status: 'offered' });
    store.pendingBoxes = [
      { mac: 'AA:BB:CC:DD:EE:01', rssi: -42, firstSeen: '2026-07-12T10:00:00Z', lastSeen: '2026-07-12T10:00:05Z' },
    ];
    const wrapper = mount(PendingBoxesPanel);
    await wrapper.find('[data-testid="couple-btn-AA:BB:CC:DD:EE:01"]').trigger('click');
    expect(store.coupleBox).toHaveBeenCalledWith('AA:BB:CC:DD:EE:01');
  });

  it('shows an "Angeboten" badge instead of the button once coupled', () => {
    const store = useOnboardingStore();
    store.startPolling = vi.fn();
    store.pendingBoxes = [
      { mac: 'AA:BB:CC:DD:EE:01', rssi: -42, firstSeen: '2026-07-12T10:00:00Z', lastSeen: '2026-07-12T10:00:05Z' },
    ];
    store.coupleResults = { 'AA:BB:CC:DD:EE:01': { mac: 'AA:BB:CC:DD:EE:01', status: 'offered' } };
    const wrapper = mount(PendingBoxesPanel);
    expect(wrapper.text()).toContain('Angeboten');
    expect(wrapper.find('[data-testid="couple-btn-AA:BB:CC:DD:EE:01"]').exists()).toBe(false);
  });

  it('shows the row error instead of the button after a failed couple', () => {
    const store = useOnboardingStore();
    store.startPolling = vi.fn();
    store.pendingBoxes = [
      { mac: 'AA:BB:CC:DD:EE:01', rssi: -42, firstSeen: '2026-07-12T10:00:00Z', lastSeen: '2026-07-12T10:00:05Z' },
    ];
    store.coupleResults = { 'AA:BB:CC:DD:EE:01': { error: 'Gerät nicht mehr erreichbar.' } };
    const wrapper = mount(PendingBoxesPanel);
    expect(wrapper.text()).toContain('Gerät nicht mehr erreichbar.');
    expect(wrapper.find('[data-testid="couple-btn-AA:BB:CC:DD:EE:01"]').exists()).toBe(false);
  });
});

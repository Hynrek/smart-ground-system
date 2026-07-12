import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import OtaUpdatePanel from '@/components/OtaUpdatePanel.vue';
import { useOtaStore } from '@/stores/otaStore.js';

const box = { id: 'box-1', alias: 'Box 1', appVersion: '0.6' };

describe('OtaUpdatePanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('shows the current app version and APP releases in the picker', () => {
    const store = useOtaStore();
    store.releases = [
      { id: 'r1', type: 'APP', version: '0.7' },
      { id: 'r2', type: 'FIRMWARE', version: 'mp-1.24' },
    ];
    const wrapper = mount(OtaUpdatePanel, { props: { box } });
    expect(wrapper.text()).toContain('0.6');
    const options = wrapper.findAll('[data-testid="ota-version-option"]');
    expect(options).toHaveLength(1);
    expect(options[0].text()).toContain('0.7');
  });

  it('calls triggerUpdate with the selected version', async () => {
    const store = useOtaStore();
    store.releases = [{ id: 'r1', type: 'APP', version: '0.7' }];
    store.triggerUpdate = vi.fn().mockResolvedValue();
    const wrapper = mount(OtaUpdatePanel, { props: { box } });
    await wrapper.find('[data-testid="ota-version-select"]').setValue('0.7');
    await wrapper.find('[data-testid="ota-trigger-btn"]').trigger('click');
    expect(store.triggerUpdate).toHaveBeenCalledWith('box-1', 'APP', '0.7');
  });

  it('renders a progress bar with the German phase label while updating', () => {
    const store = useOtaStore();
    store.releases = [];
    store.statusByBox = { 'box-1': { phase: 'DOWNLOADING', progress: 40, version: '0.7' } };
    const wrapper = mount(OtaUpdatePanel, { props: { box } });
    expect(wrapper.text()).toContain('Lädt herunter');
    expect(wrapper.find('[data-testid="ota-progress-bar"]').attributes('style')).toContain('40%');
  });
});

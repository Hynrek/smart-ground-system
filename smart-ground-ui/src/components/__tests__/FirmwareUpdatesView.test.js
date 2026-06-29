/* global File */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import FirmwareUpdatesView from '@/views/admin/FirmwareUpdatesView.vue';
import { useOtaStore } from '@/stores/otaStore.js';

describe('FirmwareUpdatesView', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('lists releases from the store', async () => {
    const store = useOtaStore();
    store.fetchReleases = vi.fn();
    store.releases = [
      { id: 'r1', type: 'APP', version: '0.7', sha256: 'ab', sizeBytes: 1024, createdAt: '2026-06-29T10:00:00Z' },
    ];
    const wrapper = mount(FirmwareUpdatesView);
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain('0.7');
    expect(wrapper.text()).toContain('App-Code');
  });

  it('calls uploadRelease with the form values on submit', async () => {
    const store = useOtaStore();
    store.fetchReleases = vi.fn();
    store.uploadRelease = vi.fn().mockResolvedValue();
    const wrapper = mount(FirmwareUpdatesView);

    await wrapper.find('[data-testid="ota-version"]').setValue('0.9');
    const file = new File(['x'], 'bundle.zip');
    const input = wrapper.find('[data-testid="ota-file"]');
    Object.defineProperty(input.element, 'files', { value: [file] });
    await input.trigger('change');

    await wrapper.find('[data-testid="ota-upload-btn"]').trigger('click');
    expect(store.uploadRelease).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'APP', version: '0.9', file }),
    );
  });
});

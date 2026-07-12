import { describe, it, expect, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import SmartBoxCard from '@/components/SmartBoxCard.vue';

const box = {
  id: 'box-1', alias: 'Box 1', macAddress: 'aabbccddeeff',
  status: 'ONLINE', firmwareVersion: 'micropython-1.23.0', appVersion: '0.6',
};

describe('SmartBoxCard OTA panel', () => {
  beforeEach(() => setActivePinia(createPinia()));

  it('renders the OTA panel for the box once the toggle is opened', async () => {
    const wrapper = mount(SmartBoxCard, {
      props: { box, devices: [], allDevicesCount: 0 },
    });
    expect(wrapper.find('.ota-panel').exists()).toBe(false);
    await wrapper.find('.ota-toggle-btn').trigger('click');
    expect(wrapper.find('.ota-panel').exists()).toBe(true);
  });
});

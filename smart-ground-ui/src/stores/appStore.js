import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useRangeStore } from './rangeStore.js';
import { useSmartBoxStore } from './smartBoxStore.js';
import { useDeviceStore } from './deviceStore.js';
import { useDeviceTypeStore } from './deviceTypeStore.js';
import { useDeviceTypeGroupStore } from './deviceTypeGroupStore.js';

export const useAppStore = defineStore('app', () => {
  const currentNav = ref('ranges');

  const setNav = (id) => {
    currentNav.value = id;
  };

  const initializeStore = async () => {
    const rangeStore = useRangeStore();
    const smartBoxStore = useSmartBoxStore();
    const deviceStore = useDeviceStore();
    const deviceTypeStore = useDeviceTypeStore();
    const deviceTypeGroupStore = useDeviceTypeGroupStore();

    await Promise.all([
      rangeStore.initialize(),
      smartBoxStore.initialize(),
      deviceStore.initialize(),
      deviceTypeStore.initialize(),
      deviceTypeGroupStore.loadGroups(),
    ]);
  };

  return {
    currentNav,
    setNav,
    initializeStore,
  };
});

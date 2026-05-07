import { computed } from 'vue';
import { useDeviceTypeStore } from '../stores/deviceTypeStore.js';

export function useDeviceTypeFilter() {
  const deviceTypeStore = useDeviceTypeStore();

  const deviceTypes = computed(() => {
    const types = deviceTypeStore.deviceTypes.map(dt => dt.deviceType);
    return [...new Set(types)].sort();
  });

  const filterOptions = computed(() => ['Alle', ...deviceTypes.value]);

  return {
    deviceTypes,
    filterOptions,
  };
}

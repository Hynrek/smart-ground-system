import { watch } from 'vue';
import { useSmartBoxStore } from '@/stores/smartBoxStore.js';
import { useDeviceStore } from '@/stores/deviceStore.js';

/**
 * Triggers device loading for every known SmartBox.
 * Safe to call multiple times — deviceStore deduplicates per box.
 */
export function useDeviceLoader() {
  const smartBoxStore = useSmartBoxStore();
  const deviceStore = useDeviceStore();

  watch(
    () => smartBoxStore.smartboxes,
    (boxes) => {
      boxes.forEach((box) => deviceStore.loadDevicesForBox(box.id));
    },
    { immediate: true },
  );
}

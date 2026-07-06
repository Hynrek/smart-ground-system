/* global setInterval, clearInterval */
import { onMounted, onUnmounted } from 'vue';

// Opt-in interval polling for a view. Pauses while the tab is hidden and
// refetches immediately on regaining visibility (stale-while-revalidate feel).
export function useRevalidate(loader, { interval = 10000, immediate = true } = {}) {
  let handle = null;

  const start = () => {
    if (handle == null) handle = setInterval(() => loader(), interval);
  };
  const stop = () => {
    if (handle != null) {
      clearInterval(handle);
      handle = null;
    }
  };
  const onVisibility = () => {
    if (document.hidden) {
      stop();
    } else {
      loader();
      start();
    }
  };

  onMounted(() => {
    if (immediate) loader();
    start();
    document.addEventListener('visibilitychange', onVisibility);
  });
  onUnmounted(() => {
    stop();
    document.removeEventListener('visibilitychange', onVisibility);
  });

  return { start, stop };
}

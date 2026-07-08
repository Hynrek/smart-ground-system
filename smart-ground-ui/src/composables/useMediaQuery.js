import { ref, onUnmounted } from 'vue'

/**
 * Reactive `matchMedia` result. Returns false when matchMedia is unavailable
 * (jsdom, SSR) so callers fall back to the desktop layout.
 */
export function useMediaQuery(query) {
  const supported = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
  const mql = supported ? window.matchMedia(query) : null
  const matches = ref(mql ? mql.matches : false)

  const update = (event) => { matches.value = event.matches }
  if (mql) mql.addEventListener('change', update)

  onUnmounted(() => { mql?.removeEventListener('change', update) })

  return matches
}

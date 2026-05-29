import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * Syncs a tab selection to the URL query parameter `?tab=xxx`.
 *
 * @param {string} defaultTab - Tab value used when the query param is absent or invalid.
 * @param {string[]} validTabs - Allowed tab values; guards against arbitrary URL input.
 * @returns {{ activeTab: import('vue').ComputedRef<string>, setTab: function }}
 */
export function useUrlTab(defaultTab, validTabs) {
  const route = useRoute()
  const router = useRouter()

  // Single source of truth: derived from the URL, never from local state.
  const activeTab = computed(() =>
    validTabs.includes(route.query.tab) ? route.query.tab : defaultTab
  )

  /**
   * Navigate to a new tab.
   * @param {string} tab - The tab to activate. Must be one of validTabs.
   * @param {{ replace?: boolean }} options
   *   replace: true  → router.replace (silent, no browser history entry)
   *   replace: false → router.push    (adds a browser history entry; Back button works)
   */
  function setTab(tab, { replace = false } = {}) {
    const nav = replace ? router.replace : router.push
    return nav({ query: { ...route.query, tab } })
  }

  return { activeTab, setTab }
}

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { useUrlTab } from '@/composables/useUrlTab.js'

const routes = [{ path: '/', component: { template: '<div />' } }]

async function setup(defaultTab, validTabs, initialPath = '/') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  if (initialPath !== '/') await router.push(initialPath)
  let result
  mount(defineComponent({
    setup() { result = useUrlTab(defaultTab, validTabs) },
    template: '<div />',
  }), { global: { plugins: [router] } })
  return { router, result }
}

describe('useUrlTab', () => {
  it('returns defaultTab when no query param is present', async () => {
    const { result } = await setup('score', ['score', 'wins'])
    expect(result.activeTab.value).toBe('score')
  })

  it('reads a valid tab from the URL query on mount', async () => {
    const { result } = await setup('score', ['score', 'wins'], '/?tab=wins')
    expect(result.activeTab.value).toBe('wins')
  })

  it('falls back to defaultTab when query tab is not in validTabs', async () => {
    const { result } = await setup('score', ['score', 'wins'], '/?tab=garbage')
    expect(result.activeTab.value).toBe('score')
  })

  it('setTab() calls router.push and updates activeTab', async () => {
    const { router, result } = await setup('score', ['score', 'wins'])
    const pushSpy = vi.spyOn(router, 'push')
    await result.setTab('wins')
    expect(pushSpy).toHaveBeenCalledWith({ query: { tab: 'wins' } })
    expect(result.activeTab.value).toBe('wins')
  })

  it('setTab({ replace: true }) calls router.replace instead of push', async () => {
    const { router, result } = await setup('score', ['score', 'wins'])
    const replaceSpy = vi.spyOn(router, 'replace')
    const pushSpy = vi.spyOn(router, 'push')
    await result.setTab('wins', { replace: true })
    expect(replaceSpy).toHaveBeenCalledWith({ query: { tab: 'wins' } })
    expect(pushSpy).not.toHaveBeenCalled()
  })

  it('preserves existing query params when setting tab', async () => {
    const { router, result } = await setup('score', ['score', 'wins'], '/?filter=active')
    const pushSpy = vi.spyOn(router, 'push')
    await result.setTab('wins')
    expect(pushSpy).toHaveBeenCalledWith({ query: { filter: 'active', tab: 'wins' } })
  })
})

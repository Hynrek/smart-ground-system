import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { useMediaQuery } from '@/composables/useMediaQuery.js'

const originalMatchMedia = window.matchMedia

function stubMatchMedia(matches) {
  const listeners = []
  const mql = {
    matches,
    media: '',
    addEventListener: (_evt, cb) => listeners.push(cb),
    removeEventListener: vi.fn(),
  }
  window.matchMedia = vi.fn(() => mql)
  return { mql, listeners }
}

function mountWith(query) {
  let result
  const wrapper = mount(defineComponent({
    setup() { result = useMediaQuery(query) },
    template: '<div />',
  }))
  return { wrapper, result: () => result }
}

afterEach(() => {
  window.matchMedia = originalMatchMedia
  vi.restoreAllMocks()
})

describe('useMediaQuery', () => {
  it('returns false when matchMedia is unavailable', () => {
    // jsdom has no matchMedia; this is the guard that keeps view tests alive
    window.matchMedia = undefined
    const { result } = mountWith('(max-width: 640px)')
    expect(result().value).toBe(false)
  })

  it('reflects the initial match state', () => {
    stubMatchMedia(true)
    const { result } = mountWith('(max-width: 640px)')
    expect(result().value).toBe(true)
  })

  it('updates when the media query changes', async () => {
    const { listeners } = stubMatchMedia(false)
    const { result } = mountWith('(max-width: 640px)')
    expect(result().value).toBe(false)
    listeners.forEach((cb) => cb({ matches: true }))
    expect(result().value).toBe(true)
  })

  it('removes its listener on unmount', () => {
    const { mql } = stubMatchMedia(false)
    const { wrapper } = mountWith('(max-width: 640px)')
    wrapper.unmount()
    expect(mql.removeEventListener).toHaveBeenCalled()
  })
})

import { describe, it, expect, vi } from 'vitest'

// Mock vue-router and pinia so the router module can be imported
vi.mock('vue-router', () => ({
  createRouter: vi.fn(() => ({ beforeEach: vi.fn() })),
  createWebHistory: vi.fn(),
}))
vi.mock('@/stores/authStore', () => ({ useAuthStore: vi.fn() }))

import { defaultHome } from '../index.js'

describe('defaultHome', () => {
  it('redirects assigned user to their range', () => {
    const auth = {
      profile: { assignedRangeId: 'range-abc' },
      hasPermission: () => false,
    }
    expect(defaultHome(auth)).toBe('/remote/range-abc')
  })

  it('redirects shooter without assignment to /home', () => {
    const auth = {
      profile: null,
      hasPermission: (p) => p === 'VIEW_REMOTE',
    }
    expect(defaultHome(auth)).toBe('/home')
  })

  it('redirects admin without assignment to /ranges', () => {
    const auth = {
      profile: null,
      hasPermission: (p) => p === 'MANAGE_RANGES',
    }
    expect(defaultHome(auth)).toBe('/ranges')
  })

  it('redirects user with no permissions to /no-access', () => {
    const auth = {
      profile: null,
      hasPermission: () => false,
    }
    expect(defaultHome(auth)).toBe('/no-access')
  })

  it('gives assignedRangeId priority over VIEW_REMOTE', () => {
    const auth = {
      profile: { assignedRangeId: 'range-xyz' },
      hasPermission: (p) => p === 'VIEW_REMOTE',
    }
    expect(defaultHome(auth)).toBe('/remote/range-xyz')
  })
})

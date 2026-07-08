import { describe, it, expect } from 'vitest'
import { scrollNearEdge, AUTOSCROLL_EDGE_PX, AUTOSCROLL_STEP_PX } from '@/composables/useDragAutoScroll.js'

const VIEWPORT = 800

describe('scrollNearEdge', () => {
  it('scrolls up near the top edge', () => {
    const container = { scrollTop: 100 }
    scrollNearEdge(container, AUTOSCROLL_EDGE_PX - 1, VIEWPORT)
    expect(container.scrollTop).toBe(100 - AUTOSCROLL_STEP_PX)
  })

  it('scrolls down near the bottom edge', () => {
    const container = { scrollTop: 100 }
    scrollNearEdge(container, VIEWPORT - AUTOSCROLL_EDGE_PX + 1, VIEWPORT)
    expect(container.scrollTop).toBe(100 + AUTOSCROLL_STEP_PX)
  })

  it('does nothing in the middle of the viewport', () => {
    const container = { scrollTop: 100 }
    scrollNearEdge(container, VIEWPORT / 2, VIEWPORT)
    expect(container.scrollTop).toBe(100)
  })

  it('is a no-op for a null container', () => {
    expect(() => scrollNearEdge(null, 0, VIEWPORT)).not.toThrow()
  })
})

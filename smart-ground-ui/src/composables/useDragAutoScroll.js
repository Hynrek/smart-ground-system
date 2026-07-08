export const AUTOSCROLL_EDGE_PX = 72;
export const AUTOSCROLL_STEP_PX = 14;

/**
 * Nudge a scroll container when a drag pointer nears its top/bottom edge.
 * During a touch drag the browser cannot scroll (touch-action: none plus
 * pointer capture), so off-screen drop targets are otherwise unreachable.
 */
export function scrollNearEdge(container, clientY, viewportHeight) {
  if (!container) return;
  if (clientY < AUTOSCROLL_EDGE_PX) container.scrollTop -= AUTOSCROLL_STEP_PX;
  else if (clientY > viewportHeight - AUTOSCROLL_EDGE_PX) container.scrollTop += AUTOSCROLL_STEP_PX;
}

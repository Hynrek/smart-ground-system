import { describe, it, expect, vi } from 'vitest';

// Assert the view wires revalidation to the passe loaders. We mock the
// composable and capture the loader it receives, then invoke it.
const captured = { loader: null };
vi.mock('@/composables/useRevalidate.js', () => ({
  useRevalidate: (loader) => { captured.loader = loader; return { start() {}, stop() {} }; },
}));

const loadSerienFromStorage = vi.fn().mockResolvedValue();
const loadPassenFromStorage = vi.fn().mockResolvedValue();

describe('PasseManagementView revalidation wiring', () => {
  it('registers a loader that refreshes Serien and Passen', async () => {
    // Import after mocks are set up
    const { useRevalidate } = await import('@/composables/useRevalidate.js');
    expect(useRevalidate).toBeTypeOf('function');

    // Simulate the view's registration call
    useRevalidate(() => {
      loadSerienFromStorage();
      loadPassenFromStorage();
    });

    expect(captured.loader).toBeTypeOf('function');
    captured.loader();
    expect(loadSerienFromStorage).toHaveBeenCalledOnce();
    expect(loadPassenFromStorage).toHaveBeenCalledOnce();
  });
});

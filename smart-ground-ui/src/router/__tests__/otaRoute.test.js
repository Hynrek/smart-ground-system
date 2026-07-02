import { describe, it, expect } from 'vitest';
import router from '@/router/index.js';

describe('OTA route', () => {
  it('serves firmware updates as a tab on /smartboxes, not a standalone route', () => {
    expect(router.resolve('/admin/firmware-updates').matched.length).toBe(0);

    const match = router.resolve('/smartboxes');
    expect(match.matched.length).toBeGreaterThan(0);
    expect(match.meta.layout).toBe('admin');
  });
});

import { describe, it, expect } from 'vitest';
import router from '@/router/index.js';

describe('OTA route', () => {
  it('registers /admin/firmware-updates with admin layout', () => {
    const match = router.resolve('/admin/firmware-updates');
    expect(match.matched.length).toBeGreaterThan(0);
    expect(match.meta.layout).toBe('admin');
  });
});

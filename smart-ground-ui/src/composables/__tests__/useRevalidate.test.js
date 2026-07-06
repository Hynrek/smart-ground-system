import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { useRevalidate } from '../useRevalidate.js';

function mountWith(loader, options) {
  return mount({
    template: '<div />',
    setup() {
      useRevalidate(loader, options);
      return {};
    },
  });
}

function setHidden(value) {
  Object.defineProperty(document, 'hidden', { value, configurable: true });
  document.dispatchEvent(new Event('visibilitychange'));
}

describe('useRevalidate', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setHidden(false);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('calls the loader immediately and on each interval', () => {
    const loader = vi.fn();
    mountWith(loader, { interval: 1000 });
    expect(loader).toHaveBeenCalledTimes(1); // immediate
    vi.advanceTimersByTime(3000);
    expect(loader).toHaveBeenCalledTimes(4);
  });

  it('pauses while hidden and refetches on regaining visibility', () => {
    const loader = vi.fn();
    mountWith(loader, { interval: 1000 });
    loader.mockClear();

    setHidden(true);
    vi.advanceTimersByTime(3000);
    expect(loader).not.toHaveBeenCalled(); // paused

    setHidden(false);
    expect(loader).toHaveBeenCalledTimes(1); // immediate refetch on return
    vi.advanceTimersByTime(1000);
    expect(loader).toHaveBeenCalledTimes(2); // interval resumed
  });

  it('stops polling after unmount', () => {
    const loader = vi.fn();
    const wrapper = mountWith(loader, { interval: 1000 });
    loader.mockClear();
    wrapper.unmount();
    vi.advanceTimersByTime(5000);
    expect(loader).not.toHaveBeenCalled();
  });
});

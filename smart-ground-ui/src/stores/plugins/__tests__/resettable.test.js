import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia, defineStore } from 'pinia';
import { ref } from 'vue';
import { resettablePlugin } from '../resettable.js';

const useThing = defineStore('thing', () => {
  const items = ref([]);
  const meta = ref({ nested: { count: 0 } });
  return { items, meta };
});

describe('resettablePlugin', () => {
  beforeEach(() => {
    const pinia = createPinia();
    pinia._a = true; // Make pinia.use() register plugins immediately
    pinia.use(resettablePlugin);
    setActivePinia(pinia);
  });

  it('restores primitive and array state', () => {
    const store = useThing();
    store.items.push('a', 'b');
    expect(store.items).toHaveLength(2);
    store.$reset();
    expect(store.items).toEqual([]);
  });

  it('restores nested objects without sharing references', () => {
    const store = useThing();
    store.meta.nested.count = 5;
    store.$reset();
    expect(store.meta.nested.count).toBe(0);
    // Mutating again after reset must not have leaked into the captured snapshot
    store.meta.nested.count = 9;
    store.$reset();
    expect(store.meta.nested.count).toBe(0);
  });
});

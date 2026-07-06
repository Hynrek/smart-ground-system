import { isRef, toRaw } from 'vue';

// Pinia plugin: snapshot each store's initial state at creation and expose a
// working $reset() for setup (factory) stores, which do not get one for free.
export function resettablePlugin({ store }) {
  // Helper to unwrap ref values from the state tree and make everything cloneable
  function unwrapRefs(state) {
    const unwrapped = {};
    for (const key in state) {
      const value = state[key];
      if (isRef(value)) {
        // Unwrap the ref and ensure the inner value is not reactive
        unwrapped[key] = toRaw(value.value);
      } else {
        // Ensure non-ref values are also not reactive
        unwrapped[key] = toRaw(value);
      }
    }
    return unwrapped;
  }

  // Snapshot the initial state, unwrapping any refs
  const initial = structuredClone(unwrapRefs(toRaw(store.$state)));

  store.$reset = () => {
    store.$patch(structuredClone(initial));
  };
}

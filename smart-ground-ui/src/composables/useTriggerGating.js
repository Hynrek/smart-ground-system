import { ref, computed } from 'vue';
import { useVoiceTrigger } from '@/composables/useVoiceTrigger.js';

/**
 * Shared trigger gate for the shooter flows. Given a set of ids (for UI feedback)
 * and a fire callback, it releases the callback either immediately (throwing),
 * after a countdown (Verzögerung), or on a mic-detected shout (Rufauslösung).
 * Mode + settings are read from the passed shooterRemoteStore.
 */
export function useTriggerGating(store) {
  const { startListening, stopListening, micLevel, wouldTrigger, micDenied } =
    useVoiceTrigger(store);

  const phase = ref('idle');   // 'idle' | 'counting' | 'totzeit' | 'listening'
  const armedIds = ref([]);
  const totalMs = ref(0);
  const remainingMs = ref(0);

  let interval = null;
  let timeout = null;

  const clearTimers = () => {
    if (interval) { clearInterval(interval); interval = null; }
    if (timeout) { clearTimeout(timeout); timeout = null; }
  };

  const resetCountdown = () => { totalMs.value = 0; remainingMs.value = 0; };

  const cancel = () => {
    clearTimers();
    stopListening();
    armedIds.value = [];
    phase.value = 'idle';
    resetCountdown();
  };

  // Run a visible countdown of `ms`, then invoke done().
  const runCountdown = (ms, done) => {
    totalMs.value = ms;
    remainingMs.value = ms;
    const startedAt = Date.now();
    interval = setInterval(() => {
      remainingMs.value = Math.max(0, ms - (Date.now() - startedAt));
    }, 50);
    timeout = setTimeout(() => {
      clearTimers();
      resetCountdown();
      done();
    }, ms);
  };

  const beginListening = (onFire) => {
    phase.value = 'listening';
    startListening(() => {
      if (phase.value !== 'listening') return;
      armedIds.value = [];
      phase.value = 'idle';
      onFire();
    });
  };

  const arm = (ids, onFire) => {
    // Re-arming a currently-armed id aborts the episode (tap-to-cancel).
    if (phase.value !== 'idle') {
      if (ids.some((id) => armedIds.value.includes(id))) cancel();
      return; // one episode at a time
    }

    const mode = store.sessionMode;

    if (mode === 'delayed') {
      armedIds.value = ids;
      phase.value = 'counting';
      runCountdown(store.delaySeconds * 1000, () => {
        armedIds.value = [];
        phase.value = 'idle';
        onFire();
      });
      return;
    }

    if (mode === 'rufausloesung') {
      armedIds.value = ids;
      const totzeitMs = store.rufTotzeit;
      if (totzeitMs > 0) {
        phase.value = 'totzeit';
        runCountdown(totzeitMs, () => beginListening(onFire));
      } else {
        beginListening(onFire);
      }
      return;
    }

    // throwing / recording / anything unexpected → fire immediately.
    onFire();
  };

  const isArmed = (id) => armedIds.value.includes(id);

  const ringStyle = computed(() => {
    const hue = phase.value === 'counting'
      ? 'var(--delay-color, #EF9F27)'
      : 'var(--ruf-color, #56C8D8)';
    const pct = totalMs.value ? (remainingMs.value / totalMs.value) * 360 : 0;
    return {
      background: `conic-gradient(${hue} ${pct}deg, rgba(255,255,255,0.12) ${pct}deg)`,
    };
  });

  const countdownLabel = computed(() => `${Math.ceil(remainingMs.value / 1000)}s`);

  return {
    // actions
    arm,
    cancel,
    isArmed,
    // state
    phase,
    armedIds,
    totalMs,
    remainingMs,
    ringStyle,
    countdownLabel,
    // voice pass-through (config modal preview + denial handling)
    startListening,
    stopListening,
    micLevel,
    wouldTrigger,
    micDenied,
  };
}

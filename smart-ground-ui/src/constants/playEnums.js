export const StepState = Object.freeze({
  PENDING: 'pending',
  DONE: 'done',
  FAILED_A: 'failed-a',
  FAILED_B: 'failed-b',
  FAILED_BOTH: 'failed-both',
});

export const StepType = Object.freeze({
  SOLO: 'solo',
  PAIR: 'pair',
  A_SCHUSS: 'a_schuss',
  RAFFALE: 'raffale',
});

export const PartialStep = Object.freeze({
  FIRST: 'first',
  SECOND: 'second',
});

export const FailType = Object.freeze({
  A: 'a',
  B: 'b',
  BOTH: 'both',
});

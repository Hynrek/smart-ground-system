/**
 * Step mode iconography — single source of truth.
 *
 * Step cards represent the shot modes of a Serie. The Shooter views (Werfer
 * Remote flyout, Play) AND the Admin views (Wettkampf scores, Serien) must
 * render steps identically. Never hard-code mode labels, notation, or colors in
 * a component — always import them from here.
 *
 * See smart-ground-ui/CLAUDE.md → "Step Iconography & Color Code".
 *
 * Notation grammar: single letter = one trap, two letters = two traps;
 *   `+`  = simultaneous (Pair)
 *   `→`  = on report / auf Schuss (a.Schuss): the shooter's first shot releases B
 *   `×2` = timed repeat (Raffale): one trap fires twice after a fixed timeout
 *
 * Colors encode escalating complexity. Red (DANGER_COLOR) is reserved for
 * Notfall/Stop and the × delete affordance — no mode may use red (or green, to
 * avoid the Solo/Pair clash that existed when both were green).
 */
import { StepType } from './playEnums.js';

export const STEP_MODES = Object.freeze({
  // `results` = how many clays/shots the step scores (drives split vs solid chips).
  [StepType.SOLO]: {
    type: StepType.SOLO,
    label: 'Solo',
    trapCount: 1,
    trigger: 'single',
    results: 1,
    base: '#1D9E75',
    badgeText: '#5DCAA5',
  },
  [StepType.PAIR]: {
    type: StepType.PAIR,
    label: 'Pair',
    trapCount: 2,
    trigger: 'simultaneous',
    results: 2,
    base: '#378ADD',
    badgeText: '#85B7EB',
  },
  [StepType.A_SCHUSS]: {
    type: StepType.A_SCHUSS,
    label: 'a.Schuss',
    trapCount: 2,
    trigger: 'on-report',
    results: 2,
    base: '#EF9F27',
    badgeText: '#FAC775',
  },
  [StepType.RAFFALE]: {
    type: StepType.RAFFALE,
    label: 'Raffale',
    trapCount: 1,
    trigger: 'timed-repeat',
    results: 2,
    base: '#7F77DD',
    badgeText: '#AFA9EC',
  },
});

// Display order for toggles and legends (escalating complexity).
export const STEP_MODE_ORDER = Object.freeze([
  StepType.SOLO,
  StepType.PAIR,
  StepType.A_SCHUSS,
  StepType.RAFFALE,
]);

export const STEP_MODE_LIST = Object.freeze(
  STEP_MODE_ORDER.map((type) => STEP_MODES[type]),
);

// Reserved for Notfall/Stop and the × delete affordance. Never assign to a mode.
export const DANGER_COLOR = '#E24B4A';

export function stepModeLabel(type) {
  return STEP_MODES[type]?.label ?? type;
}

export function stepMode(type) {
  return STEP_MODES[type] ?? null;
}

// True for steps that score two clays/shots (Pair, a.Schuss, Raffale) — i.e. the
// ones that render as a split chip and support per-clay fail correction.
export function isMultiResultStep(type) {
  return (STEP_MODES[type]?.results ?? 1) > 1;
}

/**
 * Resolve the position letter(s) of a step into { first, second }. Solo/Raffale
 * carry a single `letter` (Raffale repeats it across both shots); Pair/a.Schuss
 * carry `letter1`/`letter2`. Use this instead of re-deriving letters per component.
 */
export function stepLetters(step) {
  if (!step) return { first: '', second: '' };
  return {
    first: step.letter1 ?? step.letter ?? '',
    second: step.letter2 ?? step.letter ?? '',
  };
}

/**
 * Position notation for a step. Reads either `letter` (solo/raffale) or
 * `letter1`/`letter2` (pair/a_schuss). Pass `{ useAlias: true }` to read the
 * `alias` fields instead — the grammar stays identical so the two channels
 * always agree.
 */
export function stepNotation(step, { useAlias = false } = {}) {
  if (!step) return '';
  const key = useAlias ? 'alias' : 'letter';
  const one = step[key] ?? step[`${key}1`] ?? '?';
  const two = step[`${key}2`] ?? '?';
  switch (step.type) {
    case StepType.SOLO:
      return `${one}`;
    case StepType.RAFFALE:
      return `${one}×2`;
    case StepType.PAIR:
      return `${one} + ${two}`;
    case StepType.A_SCHUSS:
      return `${one} → ${two}`;
    default:
      return `${one}`;
  }
}

// Convert a #rrggbb hex to an rgba() string at the given alpha.
function hexToRgba(hex, alpha) {
  const n = parseInt(hex.slice(1), 16);
  const r = (n >> 16) & 255;
  const g = (n >> 8) & 255;
  const b = n & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

// Inline style for a mode badge/chip: translucent fill + readable badge text.
export function modeBadgeStyle(type) {
  const m = STEP_MODES[type];
  if (!m) return {};
  return {
    background: hexToRgba(m.base, 0.18),
    borderColor: hexToRgba(m.base, 0.35),
    color: m.badgeText,
  };
}

// Inline style for a small mode dot (solid base hue).
export function modeDotStyle(type) {
  const m = STEP_MODES[type];
  return m ? { background: m.base } : {};
}

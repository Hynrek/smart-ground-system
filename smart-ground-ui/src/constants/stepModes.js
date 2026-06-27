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

// Rendered placeholder for a step whose position was deleted (null letter/alias).
export const MISSING_POSITION = '—';

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
  const one = step[key] ?? step[`${key}1`] ?? MISSING_POSITION;
  const two = step[`${key}2`] ?? MISSING_POSITION;
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

// The '×2' repeat suffix shown for Raffale (one trap fired twice). Canonical
// glyph — also used by stepNotation/stepFailCells. Never re-spell it in a view.
export const RAFFALE_REPEAT = '×2';

/**
 * Infix connector glyph between a step's two positions: '+' (Pair, simultaneous)
 * or '→' (a.Schuss, on report). Solo has no second position and Raffale uses the
 * RAFFALE_REPEAT suffix instead of an infix, so both return ''. This is the same
 * grammar stepNotation() prints — shared so chips and notation strings agree.
 */
export function stepConnector(type) {
  switch (type) {
    case StepType.PAIR:
      return '+';
    case StepType.A_SCHUSS:
      return '→';
    default:
      return '';
  }
}

/**
 * German aria-label for a step: mode name + position letter(s), naming a deleted
 * position explicitly. Use on the element that renders a step so assistive tech
 * conveys what a bare "—" cannot.
 */
export function stepAriaLabel(step) {
  if (!step) return '';
  const mode = stepModeLabel(step.type);
  const name = (v) => (v == null ? 'gelöschte Position' : `Position ${v}`);
  const { first, second } = stepLetters(step);
  const firstV = first === '' ? null : first;
  const secondV = second === '' ? null : second;
  if (step.type === StepType.PAIR || step.type === StepType.A_SCHUSS) {
    return `${mode} ${name(firstV)} und ${name(secondV)}`;
  }
  return `${mode} ${name(firstV)}`;
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

/**
 * Inline custom-props that color a mode notation glyph (the + / → connector on a
 * step chip) in the mode's hue: `--mode-glyph` is the base hue for light surfaces,
 * `--mode-glyph-dark` the lifted badge-text hue for dark kiosk surfaces. Consume
 * with `color: var(--mode-glyph)` plus a `.results-view` override to the dark var,
 * so the same chip reads correctly on both the admin panel and the kiosk.
 */
export function modeGlyphVars(type) {
  const m = STEP_MODES[type];
  if (!m) return {};
  return { '--mode-glyph': m.base, '--mode-glyph-dark': m.badgeText };
}

/**
 * Fail-flyout cells for a double step: first-only / second-only / both, each with
 * a compact position-notation label and its point cost. Used by the in-play Fail
 * flyout. Solo steps never reach this (they fail in one tap), so only the double
 * types are handled. Raffale repeats its single trap letter across both shots.
 */
export function stepFailCells(step) {
  if (!step) return [];
  const { first, second } = stepLetters(step);
  if (step.type === StepType.RAFFALE) {
    const l = first || '?';
    return [
      { failType: 'a', label: `${l}1`, cost: 1 },
      { failType: 'b', label: `${l}2`, cost: 1 },
      { failType: 'both', label: `${l}×2`, cost: 2 },
    ];
  }
  const a = first || '?';
  const b = second || '?';
  return [
    { failType: 'a', label: `${a}`, cost: 1 },
    { failType: 'b', label: `${b}`, cost: 1 },
    { failType: 'both', label: `${a} + ${b}`, cost: 2 },
  ];
}

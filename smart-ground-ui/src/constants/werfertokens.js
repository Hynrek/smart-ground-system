// Werfer Remote Design Tokens
export const WERFER_COLORS = {
  // Primary palette
  pineGreen: '#1c3420',        // Background dark, button active
  parchmentLight: '#e8dfd0',   // Page background
  parchmentMedium: '#f0e8d8',  // Flyout panel background
  cream: '#fff8f2',            // Werfer button bg, ablauf item bg

  // Accents
  orangeAccent: '#c85a0e',     // Active borders, active state top stripe
  orangeLight: '#f2cdb0',      // Peach text on dark backgrounds

  // Status
  greenStatus: '#28a050',      // "Frei" status dot
  greenSuccess: '#1a6830',     // Notfall freigeben button
  redDanger: '#b82010',        // Notfall blockieren, delete actions

  // Additional (from design notes)
  orangeWarn: '#c89020',       // Offline warning dot
};

export const WERFER_TYPOGRAPHY = {
  // Font families
  serif: "'Playfair Display', serif",
  sans: "'DM Sans', sans-serif",
  mono: "'DM Mono', monospace",

  // Headings
  heading: {
    fontFamily: "'Playfair Display', serif",
    fontSize: '16px',
    fontWeight: 700,
  },

  // Werfer letter labels (large)
  werferLetter: {
    fontFamily: "'Playfair Display', serif",
    fontSize: '36px',
    fontWeight: 700,
  },

  // Play carousel label (largest)
  playLabel: {
    fontFamily: "'Playfair Display', serif",
    fontSize: '54px',
    fontWeight: 700,
  },

  // Body & buttons (DM Sans)
  body: {
    fontFamily: "'DM Sans', sans-serif",
    fontSize: '14px',
    fontWeight: 600,
  },

  bodySmall: {
    fontFamily: "'DM Sans', sans-serif",
    fontSize: '12px',
    fontWeight: 600,
  },

  // Mono labels
  monoBadge: {
    fontFamily: "'DM Mono', monospace",
    fontSize: '10px',
    fontWeight: 400,
  },

  monoLabel: {
    fontFamily: "'DM Mono', monospace",
    fontSize: '9px',
    fontWeight: 500,
  },
};

export const WERFER_SPACING = {
  xs: '4px',
  sm: '8px',
  md: '12px',
  lg: '16px',
  xl: '20px',
  xxl: '28px',
};

export const WERFER_RADIUS = {
  werferButton: '11px',
  actionButton: '9px',
  statusPill: '99px',
  rangeTab: '11px',
  flyoutPanel: '9px 0 0 9px',
  ablaufItem: '9px',
};

export const WERFER_BORDERS = {
  werferButton: '2.5px solid',
  actionButton: '2px solid',
  statusPill: '2px solid',
  rangeTab: '2px solid',
  flyoutPanel: '2.5px solid',
};

export const WERFER_ANIMATIONS = {
  werfuerFire: {
    duration: '700ms',
    easing: 'ease-out',
  },
  werferRecord: {
    duration: '500ms',
    easing: 'ease-out',
  },
  flyoutSlideIn: {
    duration: '220ms',
    easing: 'ease-out',
  },
  flyoutSlideOut: {
    duration: '180ms',
    easing: 'ease-in',
  },
  carouselStep: {
    duration: '220ms',
    easing: 'ease-out',
  },
  progressDot: {
    duration: '200ms',
    easing: 'linear',
  },
  statusPulseFrei: {
    duration: '2.5s',
    easing: 'ease-in-out',
  },
  statusPulseReserviert: {
    duration: '1.8s',
    easing: 'ease-in-out',
  },
  statusPulseBlockiert: {
    duration: '0.7s',
    easing: 'ease-in-out',
  },
};

export const WERFER_BREAKPOINTS = {
  mobile: '375px',
  tablet: '768px',
  desktop: '1024px',
};

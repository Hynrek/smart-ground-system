export const DEVICE_COLORS = {
  GPIO: { bg: '#dbeffe', text: '#1a5fa0' },
  LED: { bg: '#ede8ff', text: '#4a2da0' },
};

export const DIRECTION_COLORS = {
  INPUT: { bg: '#d4f5e2', text: '#1e6640' },
  OUTPUT: { bg: '#fff3d4', text: '#8a5a00' },
};

// Legacy color mapping for backward compatibility
export const TYPE_COLORS = {
  WERFER: { bg: '#dbeffe', text: '#1a5fa0' },
  SENSOR: { bg: '#d4f5e2', text: '#1e6640' },
  BUTTON: { bg: '#fff3d4', text: '#8a5a00' },
  SIGNAL: { bg: '#ede8ff', text: '#4a2da0' },
};

export const STATUS_COLORS = {
  online: '#38c97a',
  offline: '#e05252',
  warn: '#f5a623',
};

export const STATUS_LABELS = {
  online: 'Online',
  offline: 'Offline',
  warn: 'Warnung',
};

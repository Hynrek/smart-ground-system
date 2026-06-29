// OTA phase → German display label (shown next to the progress bar).
export const OTA_PHASE_LABELS = {
  DOWNLOADING: 'Lädt herunter',
  VERIFYING: 'Verifiziert',
  APPLYING: 'Wird angewendet',
  APPLIED: 'Aktualisiert',
  FAILED: 'Fehlgeschlagen',
  ROLLED_BACK: 'Zurückgerollt',
};

// Phases at which polling stops (the update reached a final outcome).
export const OTA_TERMINAL_PHASES = ['APPLIED', 'FAILED', 'ROLLED_BACK'];

export function isTerminalPhase(phase) {
  return OTA_TERMINAL_PHASES.includes(phase);
}

export const OTA_TYPE = { APP: 'APP', FIRMWARE: 'FIRMWARE' };

export const OTA_TYPE_LABELS = { APP: 'App-Code', FIRMWARE: 'Firmware (Kernel)' };

import { describe, it, expect } from 'vitest';
import { OTA_PHASE_LABELS, OTA_TERMINAL_PHASES, isTerminalPhase, OTA_TYPE } from '@/constants/ota.js';

describe('ota constants', () => {
  it('labels every phase in German', () => {
    ['DOWNLOADING', 'VERIFYING', 'APPLYING', 'APPLIED', 'FAILED', 'ROLLED_BACK']
      .forEach(p => expect(OTA_PHASE_LABELS[p]).toBeTruthy());
  });

  it('terminal phases are APPLIED, FAILED, ROLLED_BACK', () => {
    expect(OTA_TERMINAL_PHASES).toEqual(['APPLIED', 'FAILED', 'ROLLED_BACK']);
    expect(isTerminalPhase('APPLIED')).toBe(true);
    expect(isTerminalPhase('DOWNLOADING')).toBe(false);
    expect(isTerminalPhase(null)).toBe(false);
  });

  it('exposes APP/FIRMWARE type constants', () => {
    expect(OTA_TYPE.APP).toBe('APP');
    expect(OTA_TYPE.FIRMWARE).toBe('FIRMWARE');
  });
});

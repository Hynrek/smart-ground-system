/**
 * SmartBox Model
 * Represents a smart hardware box in the Smart Ground system
 */

/**
 * @typedef {Object} SmartBox
 * @property {string} id - Unique identifier (UUID)
 * @property {string} macAddress - MAC address of the device
 * @property {string|null} alias - User-friendly name for the box
 * @property {string} status - Status of the box (ONLINE, OFFLINE, MAINTENANCE, etc.)
 * @property {string} firmwareVersion - Current firmware version (e.g., "0.5v")
 * @property {boolean} configSynced - Whether the configuration is synchronized
 */

/**
 * Create a new SmartBox object
 * @param {Object} data - Raw data from API
 * @returns {SmartBox} SmartBox instance
 */
export function createSmartBox(data) {
  return {
    id: data.id,
    macAddress: data.macAddress,
    alias: data.alias || null,
    status: data.status || 'OFFLINE',
    firmwareVersion: data.firmwareVersion,
    configSynced: data.configSynced || false,
  };
}

/**
 * Check if a SmartBox is online
 * @param {SmartBox} box
 * @returns {boolean}
 */
export function isSmartBoxOnline(box) {
  return box.status === 'ONLINE';
}

/**
 * Get display name for a SmartBox
 * @param {SmartBox} box
 * @returns {string}
 */
export function getSmartBoxDisplayName(box) {
  return box.alias || box.macAddress;
}

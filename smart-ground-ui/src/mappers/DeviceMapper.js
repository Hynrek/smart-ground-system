/**
 * Device Mapper
 * Maps between API responses and application models
 */

export function toDevice(data) {
  return {
    id: data.id,
    boxId: data.boxId ?? null,
    name: data.name ?? null,
    type: data.type ?? null,
    pin: data.pin ?? null,
    status: data.status ?? 'offline',
    rangeId: data.rangeId ?? null,
    fireDelayMs: data.fireDelayMs ?? 0,
    templateId: data.templateId ?? null,
    pinConfig: data.pinConfig ?? null,
  };
}

export function toDeviceList(data) {
  return Array.isArray(data) ? data.map(toDevice) : [];
}

export function toRegisterDeviceRequest({ templateId, alias, pinConfig, fireDelayMs = 0 }) {
  return {
    templateId,
    alias,
    pinConfig,
    fireDelayMs,
  };
}

export function toDeviceCommandRequest(command) {
  return { command };
}
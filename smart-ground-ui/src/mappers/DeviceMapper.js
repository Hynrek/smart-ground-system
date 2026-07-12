export function toDevice(data) {
  return {
    id: data.id,
    alias: data.alias ?? data.name ?? null,
    name: data.name ?? null,
    boxId: data.boxId ?? data.smartBoxId ?? null,
    smartBoxId: data.smartBoxId ?? data.boxId ?? null,
    type: data.type ?? null,
    pin: data.pin ?? data.command ?? null,
    status: data.status ?? 'offline',
    rangeId: data.rangeId ?? null,
    commandsProcessed: data.commandsProcessed ?? null,
    lastCommandProcessedAt: data.lastCommandProcessedAt ?? null,
    groupId: data.groupId ?? null,
    groupName: data.groupName ?? null,
    deviceTypeId: data.deviceTypeId ?? null,
    signalDurationMs: data.signalDurationMs ?? null,
    blocked: data.blocked ?? false,
    adminBlocked: data.adminBlocked ?? false,
    healthy: data.healthy ?? null,
  };
}

export function toDeviceList(data) {
  return Array.isArray(data) ? data.map(toDevice) : [];
}

export function toRegisterDeviceRequest({ deviceTypeId, alias, rangeId }) {
  return { deviceTypeId, alias, rangeId };
}

export function toDeviceCommandRequest(command) {
  return { command };
}

/**
 * SmartBox Mapper
 * Maps between API responses and application models
 */

export function toSmartBox(data) {
  return {
    id: data.id,
    macAddress: data.macAddress,
    alias: data.alias ?? null,
    status: data.status ?? 'OFFLINE',
    firmwareVersion: data.firmwareVersion ?? null,
    configSynced: data.configSynced ?? false,
  };
}

export function toSmartBoxList(data) {
  return Array.isArray(data) ? data.map(toSmartBox) : [];
}

export function toSetAliasRequest(alias) {
  return { alias };
}
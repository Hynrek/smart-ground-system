/**
 * DeviceTemplate Mapper
 * Maps between API responses and application models
 */

export function toDeviceTemplate(data) {
  return {
    id: data.id,
    name: data.name ?? null,
    type: data.type ?? null,
    defaultPin: data.defaultPin ?? null,
    description: data.description ?? null,
  };
}

export function toDeviceTemplateList(data) {
  return Array.isArray(data) ? data.map(toDeviceTemplate) : [];
}
/**
 * Range Mapper
 * Maps between API responses and application models
 */

export function toRange(data) {
  return {
    id: data.id,
    name: data.name ?? null,
    description: data.description ?? null,
  };
}

export function toRangeList(data) {
  return Array.isArray(data) ? data.map(toRange) : [];
}

export function toCreateRangeRequest({ name, description = null }) {
  return { name, description };
}

export function toUpdateRangeRequest({ name, description = null }) {
  return { name, description };
}
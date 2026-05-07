import { apiFetch } from './apiClient.js';

export const fetchDeviceTypeGroups = () =>
  apiFetch('/device-types/groups');

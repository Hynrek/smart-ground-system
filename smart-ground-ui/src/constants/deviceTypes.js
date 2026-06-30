export const STATUS_LABELS = {
  online: 'Online',
  offline: 'Offline',
  warn: 'Warnung',
};

// Permission string for ADMIN-only actions (GPIO pin assignment, admin block).
// Confirm against SecurityConfig.java when backend plan is deployed.
export const ADMIN_PERMISSION = 'MANAGE_USERS';

// DeviceTypeGroup names treated as debug-only (excluded from production device-creation flows).
export const DEBUG_GROUP_NAMES = ['LED'];

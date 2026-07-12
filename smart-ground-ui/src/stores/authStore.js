import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { login as loginApi, getMe } from '../services/authApi.js';
import { updateUser as updateUserApi } from '../services/userApi.js';
import { resetAppData } from './dataLifecycle.js';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('sg_token') || null);
  const profile = ref(null);
  const permissions = ref([]);
  const isLoading = ref(false);
  const error = ref(null);
  const ready = ref(false);

  const displayName = computed(() => {
    if (!profile.value) return null;
    return `${profile.value.vorname} ${profile.value.nachname}`;
  });

  const isAuthenticated = () => !!token.value;

  const hasPermission = (permission) => permissions.value.includes(permission);

  const _loadProfile = async () => {
    const data = await getMe();
    profile.value = data;
    permissions.value = data.permissions ?? [];
  };

  const login = async (username, password) => {
    isLoading.value = true;
    error.value = null;
    try {
      const data = await loginApi(username, password);
      token.value = data.token;
      localStorage.setItem('sg_token', data.token);
      resetAppData(); // clear any prior session's data before loading this user's
      await _loadProfile();
    } catch (err) {
      // Authentifizierungsstatus bei Fehler vollständig zurücksetzen
      token.value = null;
      profile.value = null;
      permissions.value = [];
      localStorage.removeItem('sg_token');
      error.value = err.message;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  const updateProfile = async (data) => {
    isLoading.value = true;
    error.value = null;
    try {
      await updateUserApi(profile.value.id, data);
      await _loadProfile();
    } catch (err) {
      error.value = err.message;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  let _readyResolve;
  const readyPromise = new Promise((resolve) => { _readyResolve = resolve; });

  const init = async () => {
    if (!token.value) { ready.value = true; _readyResolve(); return; }
    try {
      await _loadProfile();
    } catch {
      token.value = null;
      profile.value = null;
      permissions.value = [];
      localStorage.removeItem('sg_token');
    } finally {
      ready.value = true;
      _readyResolve();
    }
  };

  const logout = () => {
    token.value = null;
    profile.value = null;
    permissions.value = [];
    localStorage.removeItem('sg_token');
    resetAppData();
  };

  return {
    token,
    profile,
    permissions,
    displayName,
    isLoading,
    error,
    ready,
    readyPromise,
    isAuthenticated,
    hasPermission,
    login,
    updateProfile,
    init,
    logout,
  };
});

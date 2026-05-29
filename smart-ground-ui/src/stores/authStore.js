import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { login as loginApi, createUser as createUserApi, getMe } from '../services/authApi.js';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('sg_token') || null);
  const profile = ref(null);
  const permissions = ref([]);
  const isLoading = ref(false);
  const error = ref(null);

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
      await _loadProfile();
    } catch (err) {
      error.value = err.message;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  const init = async () => {
    if (!token.value) return;
    try {
      await _loadProfile();
    } catch {
      token.value = null;
      profile.value = null;
      permissions.value = [];
      localStorage.removeItem('sg_token');
    }
  };

  const createUser = async (username, password, role) => {
    isLoading.value = true;
    error.value = null;
    try {
      await createUserApi(username, password, role);
    } catch (err) {
      error.value = err.message;
      throw err;
    } finally {
      isLoading.value = false;
    }
  };

  const logout = () => {
    token.value = null;
    profile.value = null;
    permissions.value = [];
    localStorage.removeItem('sg_token');
  };

  return {
    token,
    profile,
    permissions,
    displayName,
    isLoading,
    error,
    isAuthenticated,
    hasPermission,
    login,
    init,
    logout,
    createUser,
  };
});

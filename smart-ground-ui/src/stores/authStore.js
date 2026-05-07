/* global atob */
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { login as loginApi, createUser as createUserApi } from '../services/authApi.js';

const decodeJwtPayload = (token) => {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64));
  } catch {
    return null;
  }
};

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('sg_token') || null);
  const isLoading = ref(false);
  const error = ref(null);

  const jwtPayload = computed(() => (token.value ? decodeJwtPayload(token.value) : null));
  const role = computed(() => jwtPayload.value?.role ?? null);
  const userName = computed(() => jwtPayload.value?.sub ?? jwtPayload.value?.username ?? null);

  const login = async (username, password) => {
    isLoading.value = true;
    error.value = null;
    try {
      const data = await loginApi(username, password);
      token.value = data.token;
      localStorage.setItem('sg_token', data.token);
    } catch (err) {
      error.value = err.message;
      throw err;
    } finally {
      isLoading.value = false;
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
    localStorage.removeItem('sg_token');
  };

  const isAuthenticated = () => !!token.value;
  const isShooter = () => role.value === 'SHOOTER';
  const isAdminOrOwner = () => role.value === 'ADMIN' || role.value === 'GROUND_OWNER';

  return {
    token,
    role,
    userName,
    isLoading,
    error,
    login,
    logout,
    createUser,
    isAuthenticated,
    isShooter,
    isAdminOrOwner,
  };
});

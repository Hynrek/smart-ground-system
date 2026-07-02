<template>
  <aside class="sidebar">
    <div class="sidebar-header">
      <Logo variant="mark" :size="26" class="brand-logo" />
      <span class="brand-name">Smart Ground</span>
    </div>

    <nav class="sidebar-nav">
      <button
        v-for="item in navItems"
        :key="item.id"
        :class="{ active: activeNav === item.id }"
        :aria-current="activeNav === item.id ? 'page' : undefined"
        class="nav-item"
        @click="handleNavClick(item.id)"
      >
        <Icons :icon="item.icon" :size="15" class="nav-icon" />
        <span class="nav-label">{{ item.label }}</span>
      </button>
    </nav>

    <div class="sidebar-footer">
      <div class="user-profile">
        <div class="user-avatar">{{ userAvatarLetter }}</div>
        <div class="user-info">
          <div class="user-name">{{ username }}</div>
        </div>
      </div>
      <button class="logout-btn" title="Logout" @click="handleLogout">
        <Icons icon="logout" :size="15" />
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/authStore.js';
import Icons from './Icons.vue';
import Logo from './Logo.vue';

const props = defineProps({
  activeNav: String,
});

defineEmits(['nav']);

const router = useRouter();
const authStore = useAuthStore();

const allNavItems = [
  { id: 'home', label: 'Startseite', icon: 'home', requiredPermission: 'VIEW_REMOTE' },
  { id: 'ranges', label: 'Plätze', icon: 'target', requiredPermission: 'MANAGE_RANGES' },
  { id: 'smartboxes', label: 'SmartBoxen', icon: 'wifi', requiredPermission: 'MANAGE_RANGES' },
  { id: 'firmware-updates', path: '/admin/firmware-updates', label: 'Firmware-Updates', icon: 'download', requiredPermission: 'MANAGE_RANGES' },
  { id: 'competition', path: '/admin/wettkampf', label: 'Wettkampf', icon: 'award', requiredPermission: 'MANAGE_COMPETITIONS' },
  { id: 'passen', label: 'Passen', icon: 'program', requiredPermission: 'MANAGE_PASSE_TEMPLATES' },
  { id: 'users', label: 'Benutzer', icon: 'user', requiredPermission: 'MANAGE_USERS' },
];

const navItems = computed(() => {
  return allNavItems.filter(item => {
    if (item.requiredPermission && !authStore.hasPermission(item.requiredPermission)) {
      return false;
    }
    return true;
  });
});

const username = computed(() => {
  return authStore.displayName || 'Benutzer';
});

const userAvatarLetter = computed(() => {
  return username.value[0].toUpperCase();
});

const handleNavClick = (itemId) => {
  const item = allNavItems.find(i => i.id === itemId);
  router.push(item?.path ?? `/${itemId}`);
};

const handleLogout = () => {
  authStore.logout();
  router.push('/login');
};
</script>

<style scoped>
.sidebar {
  width: 210px;
  background: var(--sg-brand);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  height: 100vh;
  overflow-y: auto;
}

.sidebar-header {
  padding: 18px 18px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-logo {
  color: #fff;
  flex-shrink: 0;
}

.brand-name {
  font-size: 15.5px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.2px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 0;
  display: flex;
  flex-direction: column;
}

.nav-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 18px;
  background: transparent;
  border: none;
  border-left: 3px solid transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}

.nav-item:hover {
  background: var(--sg-accent-subtle);
}

.nav-item.active {
  background: var(--sg-accent-tint);
  border-left-color: var(--sg-accent);
}

.nav-icon {
  color: var(--sg-text-faint);
  transition: color 0.15s;
}

.nav-item.active .nav-icon {
  color: var(--sg-accent);
}

.nav-label {
  font-size: 13.5px;
  color: var(--sg-text-faint);
  font-weight: 400;
  transition: all 0.15s;
}

.nav-item.active .nav-label {
  color: #fff;
  font-weight: 600;
}

.sidebar-footer {
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  padding: 12px 18px;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: opacity 0.2s;
}

.user-profile:hover {
  opacity: 0.8;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--sg-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--sg-brand);
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 13px;
  color: #fff;
  font-weight: 600;
}

.user-email {
  font-size: 11px;
  color: var(--sg-text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logout-btn {
  margin-top: 10px;
  width: 100%;
  padding: 8px;
  background: var(--sg-danger-subtle);
  border: 1px solid var(--sg-color-danger-bg);
  border-radius: 4px;
  cursor: pointer;
  color: var(--sg-color-danger);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.logout-btn:hover {
  background: var(--sg-color-danger-bg);
  border-color: var(--sg-color-danger);
}

/* Mobile: Icon-only mode */
@media (max-width: 768px) {
  .sidebar {
    width: 70px;
  }

  .sidebar-header {
    justify-content: center;
    padding: 18px 12px 16px;
  }

  .brand-name {
    display: none;
  }

  .brand-logo {
    height: 30px;
  }

  .nav-item {
    justify-content: center;
    padding: 12px;
    gap: 0;
    border-left: none;
    border-top: 3px solid transparent;
  }

  .nav-item:hover {
    background: var(--sg-accent-subtle);
  }

  .nav-item.active {
    background: var(--sg-accent-tint);
    border-top-color: var(--sg-accent);
  }

  .nav-label {
    display: none;
  }

  .sidebar-footer {
    padding: 12px;
    justify-content: center;
  }

  .user-profile {
    justify-content: center;
    gap: 0;
  }

  .user-info {
    display: none;
  }

  .user-avatar {
    width: 36px;
    height: 36px;
  }

  .logout-btn {
    margin-top: 8px;
    width: 100%;
    padding: 8px;
  }
}
</style>

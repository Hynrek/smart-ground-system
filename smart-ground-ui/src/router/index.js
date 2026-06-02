import { createRouter, createWebHistory } from 'vue-router';
import RangesView from '@/views/RangesView.vue';
import RangeDetailView from '@/views/RangeDetailView.vue';
import SmartBoxesView from '@/views/SmartBoxesView.vue';
import FirmwareConfigsView from '@/views/FirmwareConfigsView.vue';
import UsersView from '@/views/UsersView.vue';
import ProfileView from '@/views/ProfileView.vue';
import LoginView from '@/views/LoginView.vue';
import ShooterHomeView from '@/views/shooter/ShooterHomeView.vue';
import ShooterRangeSelectView from '@/views/shooter/ShooterRangeSelectView.vue';
import ShooterRemoteView from '@/views/shooter/ShooterRemoteView.vue';
import ShooterPlayPage from '@/views/shooter/ShooterPlayPage.vue';
import CompetitionLiveView from '@/views/competition/CompetitionLiveView.vue';
import PasseManagementView from '@/views/shooter/PasseManagementView.vue';
import PassenAdminView from '@/views/PassenAdminView.vue';
import PlayerSetupView from '@/views/PlayerSetupView.vue';
import WettkampfListView from '@/views/admin/WettkampfListView.vue';
import WettkampfDetailView from '@/views/admin/WettkampfDetailView.vue';
import { useAuthStore } from '@/stores/authStore';

const routes = [
  { path: '/login', component: LoginView, meta: { requiresAuth: false } },
  { path: '/no-access', component: () => import('@/views/NoAccessView.vue'), meta: { requiresAuth: true } },
  { path: '/', component: { template: '<div />' }, meta: { requiresAuth: true } },

  // ── Admin routes ──────────────────────────────────────────────────────
  { path: '/ranges',               component: RangesView,                   meta: { layout: 'admin', permission: 'MANAGE_RANGES' } },
  { path: '/ranges/:id',           component: RangeDetailView, props: route => ({ id: route.params.id }), meta: { layout: 'admin', permission: 'MANAGE_RANGES' } },
  { path: '/smartboxes',           component: SmartBoxesView,               meta: { layout: 'admin', permission: 'MANAGE_RANGES' } },
  { path: '/admin/firmware-configs', component: FirmwareConfigsView,        meta: { layout: 'admin', permission: 'MANAGE_RANGES' } },
  { path: '/users',                component: UsersView,                    meta: { layout: 'admin', permission: 'MANAGE_USERS' } },
  { path: '/profile',              component: ProfileView,                  meta: { layout: 'admin' } },
  { path: '/player-setup',         component: PlayerSetupView,              meta: { layout: 'admin', permission: 'MANAGE_COMPETITIONS' } },
  { path: '/admin/wettkampf',       component: WettkampfListView,            meta: { layout: 'admin', permission: 'MANAGE_COMPETITIONS' } },
  { path: '/admin/wettkampf/:id',   component: WettkampfDetailView, props: true, meta: { layout: 'admin', permission: 'MANAGE_COMPETITIONS' } },
  { path: '/passen',               component: PassenAdminView,              meta: { layout: 'admin', permission: 'MANAGE_PASSE_TEMPLATES' } },

  // ── Shooter routes ────────────────────────────────────────────────────
  { path: '/home',                 component: ShooterHomeView,              meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
  { path: '/remote',               component: ShooterRangeSelectView,       meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
  { path: '/remote/:rangeId',      component: ShooterRemoteView, props: true, meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
  { path: '/remote/:rangeId/play', component: ShooterPlayPage,  props: true, meta: { layout: 'shooter', permission: 'PLAY_SERIES' } },
  { path: '/competition/live',     component: CompetitionLiveView,          meta: { layout: 'shooter', permission: 'PLAY_COMPETITION' } },
  { path: '/wettkampf/live/:instanceId', component: CompetitionLiveView, props: true, meta: { layout: 'shooter', permission: 'PLAY_COMPETITION' } },
  { path: '/meine-passen',         component: PasseManagementView,          meta: { layout: 'shooter', permission: 'PLAY_SERIES' } },
  { path: '/wettkampf',            component: () => import('@/views/shooter/CompetitionManagementView.vue'), meta: { layout: 'shooter', permission: 'PLAY_COMPETITION' } },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

// Gibt die Standardstartseite basierend auf den Berechtigungen des Benutzers zurück
export const defaultHome = (auth) => {
  if (auth.profile?.assignedRangeId) return `/remote/${auth.profile.assignedRangeId}`;
  if (auth.hasPermission('VIEW_REMOTE')) return '/home';
  if (auth.hasPermission('MANAGE_RANGES')) return '/ranges';
  return '/no-access';
};

router.beforeEach(async (to, from, next) => {
  const auth = useAuthStore();
  await auth.readyPromise;
  const authenticated = auth.isAuthenticated();
  const requiresAuth = to.meta.requiresAuth !== false;

  if (requiresAuth && !authenticated) {
    next('/login');
    return;
  }

  if (to.path === '/login' && authenticated) {
    next(defaultHome(auth));
    return;
  }

  // Handle root — redirect to permission-appropriate home
  if (to.path === '/' && authenticated) {
    next(defaultHome(auth));
    return;
  }

  // Hard-lock: assigned users may only visit their range path (allow login/no-access through)
  if (authenticated && auth.profile?.assignedRangeId) {
    const allowedPath = `/remote/${auth.profile.assignedRangeId}`;
    const isEscapeRoute = to.meta.requiresAuth === false || to.path === '/no-access';
    if (!isEscapeRoute && !to.path.startsWith(allowedPath)) {
      next(allowedPath);
      return;
    }
  }

  next();
});

export default router;

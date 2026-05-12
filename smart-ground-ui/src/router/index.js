import { createRouter, createWebHistory } from 'vue-router';
import RangesView from '@/views/RangesView.vue';
import RangeDetailView from '@/views/RangeDetailView.vue';
import SmartBoxesView from '@/views/SmartBoxesView.vue';
import DeviceTypeGroupsView from '@/views/DeviceTypeGroupsView.vue';
import FirmwareConfigsView from '@/views/FirmwareConfigsView.vue';
import UsersView from '@/views/UsersView.vue';
import ProfileView from '@/views/ProfileView.vue';
import LoginView from '@/views/LoginView.vue';
import ShooterHomeView from '@/views/shooter/ShooterHomeView.vue';
import ShooterRangeSelectView from '@/views/shooter/ShooterRangeSelectView.vue';
import ShooterRemoteView from '@/views/shooter/ShooterRemoteView.vue';
import ShooterPlayPage from '@/views/shooter/ShooterPlayPage.vue';
import CompetitionTemplateListView from '@/views/competition/CompetitionTemplateListView.vue';
import CompetitionSetupView from '@/views/competition/CompetitionSetupView.vue';
import CompetitionLiveView from '@/views/competition/CompetitionLiveView.vue';
import CompetitionLeaderboardView from '@/views/competition/CompetitionLeaderboardView.vue';
import CareerStatsView from '@/views/competition/CareerStatsView.vue';
import ProgramManagementView from '@/views/shooter/ProgramManagementView.vue';
import ProgrammeAdminView from '@/views/ProgrammeAdminView.vue';
import CompetitionBracketView from '@/views/CompetitionBracketView.vue';
import PlayerSetupView from '@/views/PlayerSetupView.vue';
import CompetitionManagementView from '@/views/CompetitionManagementView.vue';
import { useAuthStore } from '@/stores/authStore';

const routes = [
  { path: '/login', component: LoginView, meta: { requiresAuth: false } },

  // Smart redirect — handled in guard
  { path: '/', redirect: '/ranges' },

  // ── Admin / Ground Owner routes ───────────────────────────────────────
  { path: '/ranges', component: RangesView, meta: { layout: 'admin' } },
  { path: '/ranges/:id', component: RangeDetailView, props: route => ({ id: route.params.id }), meta: { layout: 'admin' } },
  { path: '/smartboxes', component: SmartBoxesView, meta: { layout: 'admin' } },
  { path: '/device-type-groups', component: DeviceTypeGroupsView, meta: { layout: 'admin' } },
  { path: '/deviceTypes', redirect: '/device-type-groups' },
  { path: '/admin/firmware-configs', component: FirmwareConfigsView, meta: { layout: 'admin', requiresAdmin: true } },
  { path: '/users', component: UsersView, meta: { layout: 'admin' } },
  { path: '/profile', component: ProfileView, meta: { layout: 'admin' } },
  { path: '/player-setup', component: PlayerSetupView, meta: { layout: 'admin' } },
  { path: '/competition', component: CompetitionManagementView, meta: { layout: 'admin' } },
  { path: '/competition/templates', component: CompetitionTemplateListView, meta: { layout: 'admin' } },
  { path: '/competition/setup', component: CompetitionSetupView, meta: { layout: 'admin' } },
  { path: '/competition/bracket', component: CompetitionBracketView, meta: { layout: 'admin' } },
  { path: '/programme', component: ProgrammeAdminView, meta: { layout: 'admin' } },

  // ── Shooter routes ────────────────────────────────────────────────────
  { path: '/home', component: ShooterHomeView, meta: { layout: 'shooter' } },
  { path: '/remote', component: ShooterRangeSelectView, meta: { layout: 'shooter' } },
  { path: '/remote/:rangeId', component: ShooterRemoteView, props: true, meta: { layout: 'shooter' } },
  { path: '/remote/:rangeId/play', component: ShooterPlayPage, props: true, meta: { layout: 'shooter' } },
  { path: '/competition/live', component: CompetitionLiveView, meta: { layout: 'shooter' } },
  { path: '/competition/leaderboard', component: CompetitionLeaderboardView, meta: { layout: 'shooter' } },
  { path: '/career-stats', component: CareerStatsView, meta: { layout: 'shooter' } },
  { path: '/programmes', component: ProgramManagementView, meta: { layout: 'shooter' } },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore();
  const authenticated = authStore.isAuthenticated();
  const requiresAuth = to.meta.requiresAuth !== false;

  // Not authenticated → login
  if (requiresAuth && !authenticated) {
    next('/login');
    return;
  }

  // Already authenticated on login page → role-based home
  if (to.path === '/login' && authenticated) {
    next(authStore.isShooter() ? '/home' : '/ranges');
    return;
  }

  // Root redirect → role-based home
  if (to.path === '/' && authenticated) {
    next(authStore.isShooter() ? '/home' : '/ranges');
    return;
  }

  // Shooter trying to access admin route (except /profile) → bounce to /home
  if (to.meta.layout === 'admin' && to.path !== '/profile' && authenticated && authStore.isShooter()) {
    next('/home');
    return;
  }

  // Admin/Owner trying to access shooter route → bounce to /ranges
  if (to.meta.layout === 'shooter' && authenticated && authStore.isAdminOrOwner()) {
    next('/ranges');
    return;
  }

  next();
});

export default router;

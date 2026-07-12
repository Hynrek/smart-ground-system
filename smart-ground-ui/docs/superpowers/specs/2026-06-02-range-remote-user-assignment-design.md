# Range Remote User Assignment

**Date:** 2026-06-02  
**Status:** Approved

## Problem

Shooting ranges need dedicated tablet devices (Range Remotes) permanently mounted on the range itself. An admin must be able to assign a specific user account to a range so that when that user logs in on the tablet, they are automatically locked to the remote view for that range. Other users must not be able to access a range that has a dedicated tablet user assigned to it.

---

## Backend Contract

Two additions to existing API responses (backend implementation not in scope here — frontend assumes these fields exist):

### Range model (`GET /ranges`, `GET /ranges/{id}`)
```json
{
  "id": "uuid",
  "name": "Stand 1",
  "locked": false,
  "assignedUserId": "uuid-or-null"
}
```

### Auth profile (`GET /auth/me`)
```json
{
  "vorname": "...",
  "nachname": "...",
  "permissions": [...],
  "assignedRangeId": "uuid-or-null"
}
```

### New endpoint
- `PATCH /ranges/{id}/assigned-user` — body `{ "userId": "uuid" }` to assign, `{ "userId": null }` to unassign

---

## Router & Auth Redirect

### Login redirect (`defaultHome` in `src/router/index.js`)

`assignedRangeId` is checked first, before any role-based redirect:

```js
const defaultHome = (auth) => {
  if (auth.profile?.assignedRangeId) return `/remote/${auth.profile.assignedRangeId}`;
  if (auth.hasPermission('VIEW_REMOTE')) return '/home';
  if (auth.hasPermission('MANAGE_RANGES')) return '/ranges';
  return '/no-access';
};
```

### Hard lock (navigation guard in `router.beforeEach`)

If the logged-in user has an `assignedRangeId`, any navigation outside of `/remote/{assignedRangeId}` is intercepted and redirected back:

```js
if (auth.profile?.assignedRangeId) {
  const allowedPath = `/remote/${auth.profile.assignedRangeId}`;
  if (!to.path.startsWith(allowedPath)) {
    next(allowedPath);
    return;
  }
}
```

The prefix check (`startsWith`) intentionally allows `/remote/:rangeId/play` so the user can still play series on their assigned range.

### Access rules summary

| User type | Navigates to `/remote/:rangeId` | Result |
|---|---|---|
| Assigned user — own range | `/remote/abc` | ✅ Allowed, hard-locked |
| Assigned user — other range | `/remote/xyz` | 🔒 Redirected to own range |
| Unassigned user — free range | `/remote/abc` | ✅ Allowed |
| Unassigned user — assigned range | `/remote/abc` | ↩ Redirected to `/remote` |

### Component-level guard (`ShooterRemoteView`)

The router guard cannot know if a specific range has an `assignedUserId` without an async fetch. This check therefore lives in `ShooterRemoteView` on mount:

```js
onMounted(async () => {
  const range = await rangeApi.fetchRange(props.rangeId);
  if (range.assignedUserId && range.assignedUserId !== auth.profile?.id) {
    router.replace('/remote');
  }
});
```

---

## Range List — Reserved Badge (`ShooterRangeSelectView`)

Ranges with `assignedUserId` set are visible in the range selection list but rendered as disabled with a "Reserviert" badge. No API changes needed — `rangeStore.ranges` already loads all ranges; the response just needs to include `assignedUserId`.

Rendering logic:
- `range.assignedUserId` is set and is not the current user's ID → button `disabled`, shows "Reserviert" badge
- Otherwise → normal selectable range

---

## Admin UI — Range Detail Panel (`RangeDetailView`)

A new **"User als Remote zuweisen"** section is added to the range detail panel.

### Unassigned state
```
User als Remote zuweisen
[ Kein Benutzer zugewiesen ]   [+ Zuweisen]
```

### Assigned state
```
User als Remote zuweisen
[ Avatar ] Max Mustermann       [Entfernen]
```

### User Search Modal

Opens when admin clicks "+ Zuweisen":

- Text search input with live client-side filtering across `userStore.users` (filters by `fullName`)
- Scrollable results list: each row shows name + role badges
- Clicking a user row calls `PATCH /ranges/{id}/assigned-user` with `{ "userId": user.id }`, closes modal, updates display
- "Entfernen" button calls the same endpoint with `{ "userId": null }`

`RangeDetailView` calls `userStore.fetchUsers()` on mount so the user list is available for the modal. The modal is a local component within the view — no new store needed.

---

## Back Button — Assigned Users

In `ShooterRemoteView`, the back button (navigates to `/remote`) is hidden when `auth.profile?.assignedRangeId` is set. Logout remains the only exit.

---

## Files Affected

| File | Change |
|---|---|
| `src/router/index.js` | Extend `defaultHome` + add hard-lock guard in `beforeEach` |
| `src/views/shooter/ShooterRemoteView.vue` | Add on-mount access check; hide back button for assigned users |
| `src/views/shooter/ShooterRangeSelectView.vue` | Disable + badge ranges with `assignedUserId` |
| `src/views/RangeDetailView.vue` | Add "User als Remote zuweisen" section + trigger modal |
| `src/components/UserSearchModal.vue` | New component — search modal for user assignment |
| `src/services/rangeApi.js` | Add `assignRangeUser(rangeId, userId)` function |
| `src/stores/userStore.js` | Ensure `fetchUsers()` is accessible (likely already exists) |

---

## Out of Scope (this plan)

- Multiple users assigned to one range
- Time-based assignments / reservations
- Notification when a tablet user is unassigned while logged in

---

## Next Step: Backend Implementation

After the UI is complete, a separate backend plan covers:

1. **Entity change** — add `assignedUser` (nullable `@ManyToOne` to `User`) on the `Range` entity. Hibernate applies the column (`assigned_user_id`) on next startup (pre-v1.0, Liquibase disabled).
2. **API response** — include `assignedUserId` in the `RangeResponse` DTO.
3. **Auth/me response** — include `assignedRangeId` in the `UserProfileResponse` DTO (derived from the range that references this user).
4. **New endpoint** — `PATCH /ranges/{id}/assigned-user` — accepts `{ "userId": "uuid" | null }`, validates user exists, sets or clears the assignment.
5. **Access enforcement** — optionally guard `GET /remote/:rangeId` at the API level so non-assigned users receive a 403 (defence in depth beyond the frontend redirect).

---
name: shooter-profile-page
description: Replace the UsernameEditModal with a dedicated /profil subpage in the shooter layout — 3-tab profile view with editable personal/contact data and read-only membership info
metadata:
  type: project
---

# Shooter profile page — design spec

## Context

The shooter home screen ("Mein Profil" tile, "Konto" button) currently opens `UsernameEditModal`, a minimal overlay that only allows changing the username. The backend already stores a rich user profile (name, address, phone, shooting licence, etc.) and exposes it fully via `GET /auth/me`. This spec replaces the modal with a proper subpage.

## Scope

- **UI only** — no backend changes required. `PATCH /api/users/{id}` and `GET /auth/me` already cover all needed fields.
- **Shooter layout only** — accessible from `ShooterHomeView`. The admin sidebar profile area is out of scope.

## Entry points

| Location | Element | Before | After |
|---|---|---|---|
| `ShooterHomeView` | "Mein Profil" tile | opens `UsernameEditModal` | `router.push('/profil')` |
| `ShooterHomeView` | "Konto" button (top bar) | opens `UsernameEditModal` | `router.push('/profil')` |

`UsernameEditModal.vue` is deleted; `showAccount` ref and its usages in `ShooterHomeView` are removed.

## New route

```
path:       /profil
component:  ShooterProfilView (src/views/shooter/ShooterProfilView.vue)
layout:     shooter
permission: VIEW_REMOTE
```

Added to `src/router/index.js` alongside the other shooter routes. The route is NOT blocked by the `assignedRangeId` hard-lock (assigned-kiosk users should not be able to reach it anyway since the lock redirects them to their range path — no special handling needed).

## Component: ShooterProfilView.vue

### Header (fixed, above tabs)

- Avatar circle showing initials (vorname[0] + nachname[0])
- Full name (`vorname + ' ' + nachname`)
- Username (`@username`)
- Back button → `router.back()` or `/home`

### Tab 1 — Profil

Editable fields (bound to local form state, saved on "Speichern"):

| Field | API key | Input type |
|---|---|---|
| Vorname | `vorname` | text |
| Nachname | `nachname` | text |
| Benutzername | `username` | text (existing regex `^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$`) |
| Geburtsdatum | `geburtsdatum` | date (ISO string → `LocalDate` on backend) |
| Geschlecht | `geschlecht` | select: MAENNLICH / WEIBLICH / DIVERS / UNBEKANNT |
| Sprache | `sprache` | select: DE / EN / FR / IT |
| Biographie | `biographie` | textarea (max 500 chars) |

### Tab 2 — Kontakt

Editable fields:

| Field | API key | Input type |
|---|---|---|
| E-Mail | `email` | email |
| Telefon | `telefonnummer` | tel |
| Strasse | `strasse` | text |
| Hausnummer | `hausnummer` | text |
| PLZ | `plz` | text |
| Stadt | `stadt` | text |
| Land | `land` | text |

Read-only display (no form field):

| Field | Source | Notes |
|---|---|---|
| E-Mail bestätigt | `profile.emailBestaetigt` | shown as badge (Bestätigt / Ausstehend) |

### Tab 3 — Mitgliedschaft

Read-only display only — no save button on this tab:

| Field | Source | Notes |
|---|---|---|
| Mitgliedsnummer | `profile.mitgliedsnummer` | dash if null |
| Schiesslizenz | `profile.schiessLizenz` | dash if null |
| Lizenz gültig bis | `profile.schiessLizenzVerfallsdatum` | formatted date |
| Lizenz-Status | `profile.schiessLizenzGueltig` | badge (Gültig / Abgelaufen) |
| Mitglied seit | `profile.erstelltAm` | formatted date |

## Save behaviour

- Each editable tab (Profil, Kontakt) has its own "Speichern" button at the bottom.
- On save: call `authStore.updateProfile(data)` → `PATCH /api/users/{id}` → `_loadProfile()`.
- Show inline error on failure (`authStore.error`).
- Show a brief success feedback (e.g. button text changes to "Gespeichert ✓" for 1.5 s) on success.
- Only changed fields are sent (partial PATCH — `UpdateUserRequest` uses `@JsonInclude(NON_NULL)` so null fields are omitted).

## authStore changes

Replace the narrow `updateOwnUsername(username)` action with a general `updateProfile(data)` action:

```js
const updateProfile = async (data) => {
  isLoading.value = true
  error.value = null
  try {
    await updateUserApi(profile.value.id, data)
    await _loadProfile()
  } catch (err) {
    error.value = err.message
    throw err
  } finally {
    isLoading.value = false
  }
}
```

`updateOwnUsername` is removed; the username field in Tab 1 (Profil) calls `updateProfile({ username })` instead.

## Files changed

| File | Change |
|---|---|
| `src/views/shooter/ShooterProfilView.vue` | **new** — 3-tab profile page |
| `src/views/shooter/ShooterHomeView.vue` | remove modal, navigate to `/profil` |
| `src/components/UsernameEditModal.vue` | **deleted** |
| `src/stores/authStore.js` | replace `updateOwnUsername` with `updateProfile` |
| `src/router/index.js` | add `/profil` route |

## What is NOT in scope

- Password change (separate concern; not in current `ShooterHomeView` flow)
- Profile picture upload
- Email/phone verification flows (backend stubs exist; not triggered here)
- Admin-layout profile access

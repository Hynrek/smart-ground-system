# UsersView Rework — Design Spec

**Date:** 2026-06-01  
**Status:** Approved

---

## Overview

Rework the admin `UsersView` to expose all available user fields and improve usability. The backend already supports a rich user model (address, birthday, gender, phone, shooting license, member number) but the current UI only shows and collects four fields (Vorname, Nachname, E-Mail, Passwort).

---

## Layout: Master-Detail

Replace the current card grid with a master-detail layout:

- **Left panel (260px)**: Scrollable user list with search input and a "+ Neu" button at the top. Each list entry shows: full name, status badge (Aktiv / Inaktiv), and role badge(s) (Schütze, Admin, Bereichsleiter). The selected user is highlighted with a blue left border.
- **Right panel (flex)**: Detail view for the selected user. Shows all fields grouped into sections. Has "Bearbeiten" and "Löschen" buttons in the header bar.
- When no user is selected (e.g. fresh load), the right panel shows an empty-state prompt ("Benutzer auswählen").

---

## Detail Panel Sections

Read-only display, grouped as:

| Section | Fields |
|---|---|
| **Kontakt** | E-Mail, Telefonnummer, Geburtsdatum |
| **Adresse** | Strasse + Hausnummer, PLZ + Stadt, Land |
| **Mitgliedschaft** | Mitgliedsnummer, Schiesslizenz, Lizenz-Ablaufdatum (warning highlight if within 90 days or expired) |
| **System** | Erstellt am, Zuletzt aktualisiert |

Role and status badges appear in the detail header alongside the user's name.

---

## Create / Edit Modal

A single reusable modal component (`UserFormModal.vue`) used for both creating and editing a user. It has two tabs:

### Tab 1 — Basisdaten (required fields + role)
- Vorname * 
- Nachname *
- E-Mail *
- Passwort * _(hidden on edit — separate "Passwort ändern" action)_
- Rolle * — dropdown: Schütze (default) | Admin

### Tab 2 — Erweitert (all optional)
- Geburtsdatum (date picker)
- Geschlecht (dropdown: Männlich / Weiblich / Divers / —)
- Telefonnummer
- Strasse + Hausnummer (2fr + 1fr grid)
- PLZ + Stadt + Land (1fr + 2fr + 1fr grid)
- Mitgliedsnummer
- Sprache (dropdown: Deutsch / Français / Italiano / English)

**Create flow:** "+ Neu" opens the modal in create mode. Both tabs are available from the start. "Erstellen" sends `POST /api/users` with all filled fields.  
**Edit flow:** "Bearbeiten" opens the modal pre-filled with the selected user's data. "Speichern" sends `PATCH /api/users/{id}`. Password field is absent — a separate "Passwort zurücksetzen" button can be added later.  
**Validation:** Pflichtfelder (Tab 1) are validated before submit. Clicking "Erstellen"/"Speichern" while on Tab 2 with missing required fields switches back to Tab 1 and highlights errors.

---

## Delete Confirmation

Clicking "Löschen" opens a simple confirmation modal (existing pattern). On confirm, calls `DELETE /api/users/{id}`. On success, deselects the user and reloads the list.

---

## Search / Filter

The search input in the list header filters by name or email (client-side, on the already-loaded list). No backend search call needed given the expected user count.

---

## Store Changes (`userStore.js`)

- Add `selectedUser` ref (the currently selected `UserDTO`)
- Add `selectUser(user)` action (sets `selectedUser`)
- Add `updateUser(id, data)` action → calls `PATCH /api/users/{id}`
- Keep existing: `loadUsers`, `createUser`, `deleteUser`

---

## Service Changes (`userApi.js`)

- Add `updateUser(userId, data)` → `PATCH /api/users/{userId}`  
  (already exists as an endpoint on the backend)

---

## Files Touched

| File | Change |
|---|---|
| `src/views/UsersView.vue` | Full rewrite — master-detail layout, search, user list |
| `src/components/UserFormModal.vue` | New — tabbed create/edit modal |
| `src/stores/userStore.js` | Add `selectedUser`, `selectUser`, `updateUser` |
| `src/services/userApi.js` | Add `updateUser` |

---

## Out of Scope

- Role assignment UI beyond the initial role set at creation (the backend supports scoped roles; this can be a follow-up)
- Password reset for existing users (separate action, not in this rework)
- Pagination (client-side filter is sufficient for now)
- Shooting license fields on create (not in `CreateUserRequest`; can be added via edit after creation)

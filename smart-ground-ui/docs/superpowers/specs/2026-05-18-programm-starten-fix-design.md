# Design: Programm "Starten" Flow Fix

**Date:** 2026-05-18  
**Status:** Approved  
**Scope:** smart-ground-ui frontend only

---

## Problem Summary

Three related issues in the "Starten" flow for saved Programmes:

1. **"Platz wählen" modal is unnecessary.** A Programme's Abläufe always carry their `rangeId`. Asking the user to pick a range is redundant.
2. **Active sessions never track the correct programId.** `startGroupPlay()` reads `programStore.pendingProgramId?.value`, which is never set. Every active session is created with `programId: null` and `programName: 'Programm'`.
3. **RemoteView (ShooterFlyoutPanel) has no "Aktive Programme" section.** Users who start a programme cannot see or resume it from the remote interface.

---

## Approach: Minimal Targeted Fix (Approach A)

Three isolated changes — one per file. No changes to ShooterPlayPage, no new components, no new routes.

---

## Section 1 — ProgramManagementView.vue

**Remove:**
- `startingProgram` ref
- `availableRanges` computed
- `launchProgram()` function
- Range-sheet overlay template (the "Platz wählen" bottom sheet)
- All range-sheet CSS classes (`range-sheet-overlay`, `range-sheet`, `range-sheet-header`, `range-sheet-title`, `range-sheet-sub`, `range-sheet-loading`, `range-sheet-empty`, `range-sheet-list`, `range-sheet-item`, `range-item-info`, `range-item-name`, `range-item-desc`, `range-sheet-cancel`)
- `rangeStore` import and `rangeStore.loadApiData?.()` call (no longer needed)

**Replace `startProgram(prog)` with:**
```js
const startProgram = (prog) => {
  const rangeId = prog.ablaeufe[0]?.rangeId ?? null;
  if (!rangeId) {
    alert('Dieses Programm hat keinen Platz zugeordnet.');
    return;
  }
  playSessionStore.pendingProgramInfo = { programId: prog.id, rangeId };
  router.push(`/remote/${rangeId}/play`);
};
```

**Invariant:** An Ablauf without a `rangeId` is considered broken. The alert surfaces this without crashing.

---

## Section 2 — playSessionStore.js

**Root cause:** `ShooterPlayPage` calls `clearPendingProgram()` immediately after `loadPendingProgram()` on mount. By the time the user confirms the group setup and `startGroupPlay()` runs, `pendingProgramInfo` is `null`. The fallback `programStore.pendingProgramId?.value` is never populated.

**Fix — add internal ref `_pendingProgramId`:**

```js
// Internal only — not exported
const _pendingProgramId = ref(null);
```

**Update `loadPendingProgram()`** — save programId before it is cleared:
```js
const loadPendingProgram = () => {
  if (!pendingProgramInfo.value) return null;
  const { programId } = pendingProgramInfo.value;
  _pendingProgramId.value = programId;          // ← persist for startGroupPlay
  const prog = programStore.savedPrograms.find((p) => p.id === programId);
  if (!prog) return null;
  pendingGroupAblaeufe.value = prog.ablaeufe;
  showGroupSetup.value = true;
  return prog;
};
```

**Update `startGroupPlay()`** — replace the broken lookup:
```js
// Before (broken):
const programId = programStore.pendingProgramId?.value || null;

// After (fixed):
const programId = _pendingProgramId.value;
const programName = programStore.savedPrograms.find((p) => p.id === programId)?.name || 'Programm';
// ... clear after session is created:
_pendingProgramId.value = null;
```

**Public API:** unchanged — `_pendingProgramId` is not exported.

---

## Section 3 — ShooterFlyoutPanel.vue

**Add "Aktive Programme" section** at the top of the non-recording, open-panel content block (before "Offene Wettkämpfe"), rendered only when `activeSessions.length > 0`.

**Each session card shows:**
- Programme name
- Player count (e.g. "2 Schützen")
- Completion percentage as a text badge
- "Fortfahren" button

**"Fortfahren" action:**
```js
const resumeSession = (session) => {
  if (playStore.resumeSession(session.sessionId)) {
    isOpen.value = false;
    router.push(`/remote/${session.rangeId}/play`);
  }
};
```

**Empty-state guard** — add `&& activeSessions.value.length === 0` to the existing empty-state `v-if` so it continues to show correctly when there are no ablaeufe and no active sessions.

**Script additions:**
```js
const activeSessions = computed(() => playStore.activeSessions);
// resumeSession() as above
```

---

## Files Changed

| File | Type of change |
|---|---|
| `src/views/shooter/ProgramManagementView.vue` | Remove range modal, simplify `startProgram` |
| `src/stores/playSessionStore.js` | Fix `_pendingProgramId` tracking in `startGroupPlay` |
| `src/components/shooter-remote/ShooterFlyoutPanel.vue` | Add "Aktive Programme" section |

## Files NOT Changed

- `ShooterPlayPage.vue` — group setup flow unchanged
- `programStore.js` — `pendingProgramId` left in place (deprecated but harmless)
- Router, other stores, other views

---

## Edge Cases

| Case | Behaviour |
|---|---|
| Programme has no ablaeufe with rangeId | Alert shown, no navigation |
| Programme has ablaeufe across multiple ranges | `ablaeufe[0].rangeId` is used (mixed-range "broken" indicator is a separate future task) |
| User resumes a session whose programme was deleted | `resumeSession()` returns `false`, no navigation |
| activeSessions is empty | "Aktive Programme" section is hidden entirely |

---

## Out of Scope

- Mixed-range programme validation / "broken" indicator
- Moving player selection into ProgramManagementView (Approach B)
- Any backend changes

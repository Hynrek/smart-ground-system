# Task G — Remove the vestigial `OPEN` session status

**Date:** 2026-06-19
**Scope:** `smart-ground-backend` + `smart-ground-ui` (one cross-cutting change)

## Goal

Delete the `OPEN` value from the competition `SessionStatus`, collapsing the
state machine from:

```
SETUP → OPEN → ACTIVE → PRE_COMPLETE → COMPLETED   (+ ABANDONED from any non-terminal)
```

to:

```
SETUP → ACTIVE → PRE_COMPLETE → COMPLETED          (+ ABANDONED from any non-terminal)
```

## Rationale

`OPEN` is vestigial: it grants no capability `SETUP` lacks. Group and member
editing (`createGroup`, `updateGroup`, `deleteGroup`, `addMember`,
`removeMember`, and their API variants) are all allowed in both `SETUP` and
`OPEN`, and nothing else in the codebase branches on `OPEN`. The only thing the
extra state bought was a two-step start that the UI already hides behind a
single `goLive` call. Removing it simplifies the state machine and the UI with
no loss of behaviour.

## Why both repos in one change

Removing `OPEN` backend-side is not safe in isolation. The UI's
`competitionEventStore.goLive(id)` advances a `SETUP` competition by first
patching status to `'open'`, then to `'active'`. Once the backend enum no
longer contains `OPEN`, `SessionStatus.valueOf("OPEN")` throws and the go-live
flow breaks. The UI collapse is therefore part of the same change, not a
follow-up.

No data migration is required: there is no production release, H2 test schema
is rebuilt each run, and Postgres `ddl-auto=update` does not need a column change
(the enum is stored as a string; no rows hold `OPEN` in any environment we keep).

## Backend changes (`smart-ground-backend`)

1. **`model/SessionStatus.java`** — remove `OPEN`:
   `SETUP, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED`.

2. **`service/SessionService.java`**
   - `validateStatusTransition`: replace the `SETUP → OPEN` and `OPEN → ACTIVE`
     cases with a single `case SETUP -> to == SessionStatus.ACTIVE;`. The
     `ABANDONED`, `ACTIVE`, and `PRE_COMPLETE` arms are unchanged.
   - The seven edit-guards currently testing
     `st != SessionStatus.SETUP && st != SessionStatus.OPEN` become
     `st != SessionStatus.SETUP` (methods: `createGroup`, `updateGroup`,
     `deleteGroup`, `addMember`, `removeMember`, `updateGroupApi`,
     `addMemberApi`).
   - Update the German javadoc lines that read "SETUP oder OPEN" → "SETUP".

3. **`model/LiveSession.java`** — fix the inline status comment (line ~43) to
   drop `OPEN`.

4. **`src/main/resources/static/openapi.yaml`**
   - Remove `OPEN` from both `SessionStatus` enum lists (the inline `status`
     query-param enum and the `SessionStatus` schema).
   - `patchSessionStatus` path description: transitions become
     `SETUP → ACTIVE, ACTIVE → ABANDONED, ACTIVE → PRE_COMPLETE,
     PRE_COMPLETE → COMPLETED`. Drop the `"open"` example.
   - `UpdateSessionStatusRequest.status` description: same transition update.
   - `deleteSession` summary: "Delete a session (SETUP or OPEN status only)" →
     "Delete a session (SETUP status only)". This also corrects existing drift —
     `deleteSession` already only permits `SETUP`.

5. **Regenerate**: `./mvnw generate-sources` so the generated
   `ch.jp.smartground.model.SessionStatus` enum drops `OPEN`. No handwritten
   code references the generated `OPEN` constant, so nothing else needs touching.

6. **Tests**
   - `service/SessionServiceStatusTest.java`: remove the
     `SessionStatus.valueOf("OPEN")` assertion; change the value-count assertion
     from `6` to `5`. Rename the `hasOpenAndPreComplete` test accordingly
     (e.g. `hasPreComplete`).
   - Add a transition test (in `SessionServiceStatusTest` or a focused test):
     assert `SETUP → ACTIVE` is now a valid transition, and that
     `SessionStatus.valueOf("OPEN")` throws `IllegalArgumentException`.

## Frontend changes (`smart-ground-ui`)

7. **`src/stores/competitionEventStore.js`**
   - Delete `openEvent` (only `goLive` called it — dead once `OPEN` is gone).
   - Delete `goLive` (it existed solely to hide the `SETUP → OPEN → ACTIVE`
     two-step; `startEvent` now advances `SETUP` straight to `ACTIVE`).
   - Remove `openEvent` and `goLive` from the store's returned interface.
   - Remove the obsolete `goLive`/`OPEN` explanatory comment.

8. **`src/views/admin/WettkampfDetailView.vue`**
   - `confirmStart()` calls `store.startEvent(eventId.value)` instead of
     `store.goLive(...)`.
   - Render branch (line ~26): `['SETUP', 'OPEN'].includes(...)` →
     `event.status?.toUpperCase() === 'SETUP'`.
   - `statusLabel` map (line ~216): remove the `OPEN: 'Offen'` entry.
   - Update the "SETUP / OPEN (Planung)" / "SETUP/OPEN → ACTIVE" comments to drop
     `OPEN`.

9. **Frontend tests**
   - `stores/__tests__/competitionEventStore.test.js`: remove the `openEvent`
     test and both `goLive` tests; verify the remaining `startEvent` test asserts
     `patchStatus(id, 'active')`.
   - `services/__tests__/wettkampfApi.test.js`: change the `patchStatus('s1',
     'open')` argument to a valid status (`'active'`) — the test only checks URL
     routing, but should not reference a removed status.
   - `views/admin/__tests__/WettkampfDetailView.test.js`: replace the three
     `store.goLive` spies/assertions with `store.startEvent`; remove the
     "renders the planning screen … for an OPEN event" test (`OPEN` is no longer a
     valid status; the `SETUP` case already covers the planning screen).

## Out of scope

- Any change to the finish guard, tiebreaker, or `PRE_COMPLETE` logic.
- Renaming `startEvent` or changing the `patchStatus` service signature.
- The `constants/playEnums.js` `SessionStatus` map (it never listed `OPEN`).

## Verification

- Backend: `./mvnw clean test` green; `./mvnw clean package` warning-free.
- Frontend: `npm run test` green; `npm run lint` clean.
- Manual smoke (optional): a `SETUP` competition can be started in one click and
  lands in `ACTIVE`.

## Documentation

- Backend `CLAUDE.md`: the `LiveSession` status line in the Competition/Session
  domain section already reads `SETUP → ACTIVE → PAUSED → COMPLETED/ABANDONED`
  (no `OPEN`), so no edit is required there — but confirm during implementation.

# Synchronized Passe Phasing (Task D) — Design

**Date:** 2026-06-18
**Status:** Draft (pending user review)
**Component area:** Competition runtime (backend session model + progress; frontend kiosk runtime + admin panel)
**Scope:** Cross-stack (smart-ground-backend + smart-ground-ui).

## Problem

A Rotte that finishes the current Passe can immediately start the next Passe's Serien. The
frontend runtime (`competitionEventStore`) advances each Rotte's `currentPhaseIndex` independently
in `_completeCompetitionBlock`, and `getBlocksForRange`/`getActiveCompetitionRotten` surface the
next Passe's blocks off that per-Rotte index. The requirement: **a new Passe must not start until
all Rotten have completed all Serien of the current Passe, and the move to the next Passe is
released by the admin** ("Nächste Passe freigeben") so all Rotten advance together.

## Why this needs the backend

The competition runtime is built **per-client, in memory** (admin device and each kiosk build
their own `competitionInstances` from the server's events + completion progress). There is no
shared runtime and no live cross-device push (STOMP is unwired). All clients only agree on what
the server persists. Therefore the admin's "release" decision must be a **persisted server field**
that every client reads via the existing progress poll — a frontend-only flag would gate only the
admin's own device.

## Goals

- The set of currently-playable Serien is gated to a single **released Passe**; Rotten cannot play
  beyond it.
- A Rotte that has finished all Serien of the released Passe shows an explicit
  **"Warte auf andere Rotten"** state (no playable Serie).
- When all Rotten have finished the released Passe, the admin sees an enabled
  **"Nächste Passe freigeben"** button; clicking it advances every Rotte to the next Passe.
- Reload-safe: the gate is server-persisted and converges across admin + kiosks.

## Non-Goals (YAGNI)

- Live push of the release to kiosks (kiosks pick it up on their next load/navigation — consistent
  with the current no-sync model; STAMP wiring remains deferred).
- Automatic advance (explicitly replaced by the admin-confirm gate).
- Changing how Serien map to ranges or how Rotten are assigned to ranges.

## Backend (contract-first)

### 1. `LiveSession` model
Add a persisted field:
```java
@Column(name = "released_passe_index", nullable = false)
private int releasedPasseIndex = 0;   // highest 0-based Passe index released for play
```
with getter/setter. Pre-v1.0 — Hibernate applies the column on restart.

### 2. `openapi.yaml`
- `SessionProgressResponse`: add
  ```yaml
  releasedPasseIndex:
    type: integer
    description: Highest 0-based Passe index released for play (admin-gated).
  ```
- New path:
  ```yaml
  /api/sessions/{sessionId}/passen/release:
    post:
      summary: Release the next Passe for play (admin gate)
      operationId: releaseNextPasse
      tags: [Session]
      parameters: [ sessionId (uuid, path) ]
      responses:
        '200': SessionProgressResponse
        '404': Session not found
        '409': Current Passe not fully complete, or already at the last Passe
  ```
Then `mvn generate-sources` regenerates `SessionApi` + `SessionProgressResponse`.

### 3. `CompetitionProgressService`
- `buildSessionProgressResponse`: `response.setReleasedPasseIndex(session.getReleasedPasseIndex())`.
- New `releaseNextPasse(UUID sessionId)`:
  - Load session; parse `passen` (count = `passen.length`).
  - Guard: `session.getStatus() == ACTIVE`, else `IllegalStateException`.
  - `idx = session.getReleasedPasseIndex()`; guard `idx + 1 < count`, else `IllegalStateException`.
  - Guard: every group has completed every Serie of `passen[idx]` (reuse the same CSR-key check
    as `isAllPassenDoneForAllGroups`, scoped to passe `idx`), else `IllegalStateException`.
  - `session.setReleasedPasseIndex(idx + 1)`; save; return `buildSessionProgressResponse(sessionId)`.

### 4. `SessionController`
Implement the generated `releaseNextPasse(sessionId)` → delegate to the service, `200` with progress.

### 5. Exception → 409
The guard failures throw `IllegalStateException`. Ensure the controller-advice maps
`IllegalStateException` for this route to **409 Conflict** (matching the existing finish-guard 409
convention). If the global handler maps `IllegalStateException` to a different status, add a small
dedicated exception (e.g. `PasseReleaseNotAllowedException`) mapped to 409 instead.

## Frontend

### 1. `wettkampfApi.js`
```js
export const releaseNextPasse = (sessionId) => apiClient.post(`/sessions/${sessionId}/passen/release`)
```

### 2. `competitionEventStore.js`
- **Instance shape:** add `releasedPasseIndex` (default `0` in `_buildInstance`).
- **`_hydrateProgress`:** set `inst.releasedPasseIndex = progress.releasedPasseIndex ?? 0`. Keep
  deriving each block's done state from `completedSerien`; set each `phase.status`:
  - `done` if all its blocks are done;
  - `active` if `phaseIndex === inst.releasedPasseIndex` and not all done;
  - `pending` otherwise.
  Set every Rotte's `currentPhaseIndex = inst.releasedPasseIndex` (kept for display compatibility).
- **Gating** — `getActiveCompetitionRotten(rangeId)` and `getBlocksForRange(rangeId)` use
  `inst.releasedPasseIndex` as the single playable phase (instead of `rotte.currentPhaseIndex`).
  A Rotte whose `phase[releasedPasseIndex]` blocks are all done surfaces no blocks.
- **`_completeCompetitionBlock`:** mark the block/phase done; **do not advance any Rotte past
  `inst.releasedPasseIndex`**. The "every Rotte finished every phase → push to
  `completedCompetitionInstances`" path is unchanged.
- **New action `releaseNextPasse(instanceId)`:** call `wettkampfApi.releaseNextPasse(sessionId)`;
  on success set `inst.releasedPasseIndex = res.releasedPasseIndex` (or `+1`) and mark the new
  phase `active` for all Rotten. try/catch per store convention.
- **New helpers (exported):**
  - `isReleasedPasseComplete(instanceId)` → all Rotten have all blocks of `releasedPasseIndex` done.
  - `isRotteWaitingForPasse(inst, rotte)` → that Rotte finished `releasedPasseIndex` but the
    instance isn't fully complete (used for the kiosk waiting state).

### 3. `useCompetitionProgress.js`
- `activePhaseIndex` = `instance.releasedPasseIndex ?? <existing derived fallback>`.
- `passenProgress`: a Passe is `fertig` when all Rotten finished it; `aktiv` when its index ===
  `releasedPasseIndex`; else `offen`. `serieCards` reads `phase[releasedPasseIndex]`.

### 4. Admin `ActiveCompetitionPanel.vue`
- The panel already polls `getProgress` (4 s) into `progressData`, which now carries
  `releasedPasseIndex`. Add a **"Nächste Passe freigeben"** button:
  - **Enabled** when the released Passe is fully complete by all groups — every group's
    `completions[releasedPasseIndex]?.completed === true` (the whole-Passe flag the backend already
    computes) — **and** `releasedPasseIndex + 1 < passen.length`.
  - On click → `store.releaseNextPasse(event.id)`; the next poll reflects the advance.
  - Disabled with a hint while the Passe isn't complete.

### 5. Kiosk `RangePanelCard.vue` + flyout
- When the assigned Rotte `isRotteWaitingForPasse`, show an explicit **"Warte auf andere Rotten"**
  block in the range card instead of the play CTA.
- `CompetitionFlyoutContent`: the per-Rotte serie row for a Rotte that finished the released Passe
  reads `done`; the stepper already reflects the released Passe via `useCompetitionProgress`. Add a
  small "Warte auf andere Rotten" hint when the viewing Rotte is waiting (optional, low-risk).

## Testing

**Backend:**
- `CompetitionProgressServiceTest`: `releaseNextPasse` advances when all groups completed the
  current Passe; throws (→409) when not complete; throws when already at the last Passe; throws when
  not ACTIVE. `getProgress` includes `releasedPasseIndex`.
- `SessionControllerTest` (if present): `POST …/passen/release` returns 200 + advanced index; 409 on
  guard failure.

**Frontend:**
- `competitionEventStore.test.js`: `getBlocksForRange`/`getActiveCompetitionRotten` surface only the
  released Passe's blocks; a Rotte that finished it surfaces none; `_completeCompetitionBlock` does
  not advance past `releasedPasseIndex`; `releaseNextPasse` action posts and bumps the index;
  `_hydrateProgress` reads `releasedPasseIndex`.
- `useCompetitionProgress.test.js`: `activePhaseIndex`/`passenProgress`/`serieCards` follow
  `releasedPasseIndex`.
- `ActiveCompetitionPanel.test.js`: the "Nächste Passe freigeben" button is disabled until the
  released Passe is complete, enabled after, hidden on the last Passe, and calls `releaseNextPasse`.
- `RangePanelCard.test.js` (new if absent): shows "Warte auf andere Rotten" for a waiting Rotte.

## Conventions

- Backend: edit `openapi.yaml` → `mvn generate-sources` → implement; German domain comments;
  JPA entity change (no migration pre-v1.0).
- Frontend: `<script setup>`; data flows store → composable → component; German display labels
  ("Nächste Passe freigeben", "Warte auf andere Rotten").
- Remove the per-Rotte independent advancement rather than leaving it beside the gated path.

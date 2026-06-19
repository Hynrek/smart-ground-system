# Stechen as Serie (Task F) — Design

**Date:** 2026-06-19
**Scope:** Full stack — `smart-ground-backend` (Spring Boot 4) + `smart-ground-ui` (Vue 3)
**Status:** Draft — awaiting review
**Builds on:** `2026-06-17-competition-stechen-tiebreaker-design.md` (the Stechen/Tiebreaker feature)

---

## 1. Problem & Goal

The Stechen (tiebreaker) feature is implemented, but only as a **Passe** run. The original
Stechen spec locked in *"Admin picks any existing Serie or Passe template"*, yet the backend
`TiebreakerService.startTiebreaker` explicitly rejects `templateType=serie` with a `409`
(`InvalidTiebreakerStateException`), and the UI (`StechenPanel.vue`) only offers Passe
templates.

A Stechen is, by nature, a single short shoot-off — a **Serie**, not a multi-Serie Passe.

**Goal:** A Stechen is **always run as a single Serie**. The admin picks a Serie template (not
a Passe) when starting a Stechen. The Passe option is **removed entirely**.

### Decision (locked during brainstorming)

| Question | Decision |
|---|---|
| Serie or Passe? | **Always a single Serie. Remove the Passe option entirely** (not "add Serie alongside Passe"). |
| Which Serien can the admin pick? | **Range-owned, published Serien only** (`ownership='range'` AND `published=true`) — mirrors the old "global" Passe scope. |
| How does a single Serie reach the live engine? | **Wrap it as a one-block `passe`-type `PlayInstance` from a snapshot** — no persisted Passe required. The live engine always sees a one-block passe instance. |
| `templateType` field in the contract? | **Removed** (pre-v1.0, "remove unused code eagerly"). `templateId` now always references a Serie. *(Open for review: keep pinned to `"serie"` instead.)* |

---

## 2. What does NOT change

Contained, low-risk change. Untouched:

- `TieResolver` (tie detection + Stechen ordering).
- Leaderboard tie-resolution in `CompetitionService`.
- `tieGroupId` / round-number / earliest-decisive-round resolution.
- Repeat rounds, `submitResults`, `listTies`, the per-tie history.
- The warn-don't-block PRE_COMPLETE finish guard.
- `CompetitionTiebreaker` results semantics (Stechen scores never touch `PlayerResult`).

---

## 3. Backend changes (`smart-ground-backend`)

### 3.1 `PlayInstanceService` — single-Serie live run

`startPasseInstance(StartPasseInstanceRequest)` currently requires a **persisted Passe**
(`passeRepository.findById`) and builds one `PlayBlockRecord` per embedded Serie.

- Extract the block-list + `PlayInstance` construction (owner, `playersJson`, `stateJson`,
  `status="active"`, `type="passe"`, `templateId`, `templateName`) into a **private helper**.
- Add a **public** `startSerieInstance(...)` that builds a **one-block** `passe`-type instance
  directly from a Serien snapshot (the chosen single Serie), reusing the helper. No persisted
  Passe is touched.
- `startPasseInstance` is refactored to delegate to the same helper (DRY).

The live engine downstream (`startBlock` / `completeBlock` / MQTT firing) is unchanged — it
still sees a one-block `passe` instance.

### 3.2 `TiebreakerService.startTiebreaker` — Serie path, Passe removed

- **Remove** the `serie`-rejection branch (lines ~181–186) and the Passe-loading branch.
- Load the chosen **`Serie`** via `SerieRepository.findById(req.getTemplateId())`
  (404 `TiebreakerNotFoundException` if missing — consistent with current Passe handling).
- Build a **one-element Serien snapshot** (same JSON shape `PlayMapper.parseEmbeddedSerien`
  consumes: `[{id, alias, rangeId, rangeName, steps}]`) and store it in
  `CompetitionTiebreaker.programSnapshot` (immutable record of what was shot).
- Set `templateName = serie.getName()`.
- Start the live run via `playInstanceService.startSerieInstance(...)`; store the returned
  `instanceId` on the tiebreaker; set status `ACTIVE`; save.
- Inject `SerieRepository`; **remove** the now-unused `PasseRepository` dependency.

### 3.3 Contract + entity cleanup

`openapi.yaml` (source of truth — edit first, `./mvnw generate-sources`, then implement):

- `StartTiebreakerRequest`: **remove** `templateType` and its `[serie, passe]` enum.
  `required` becomes `[playerIds, templateId]`. `templateId` documented as a **Serie** id.
- `TiebreakerResponse`: **remove** the `templateType` field.

Entity / persistence:

- `CompetitionTiebreaker`: **remove** the `templateType` field and the `template_type` column
  (pre-v1.0, `ddl-auto=update`; no Liquibase changeset). `templateName` stays (Serie name).
- Remove the `templateType` setter usage in `TiebreakerService` mapping (`toResponse`).

### 3.4 Errors

No new exceptions. `InvalidTiebreakerStateException` stays for the PRE_COMPLETE guard and the
already-completed-round guard. The serie-specific 409 message is deleted with its branch.

---

## 4. Frontend changes (`smart-ground-ui`)

### 4.1 `StechenPanel.vue`

- Replace `passenOptions` (and its "backend rejects serie" comment) with **`serieOptions`** =
  `passeStore.getGlobalSerien()` filtered to `published === true`.
- On mount, ensure Serien are loaded: `passeStore.loadSerienFromStorage()` (guarded like the
  current Passe load).
- Modal: relabel "Passe-Vorlage" → **"Serie-Vorlage"**; the `<select>` binds a `serieId`.
  Empty-state hint: "Keine Serie-Vorlagen verfügbar."
- `confirmStart` payload drops `templateType`; sends `{ playerIds, templateId: serieId,
  tiePosition }`.

### 4.2 `tiebreakerApi.js` + store

- `tiebreakerApi.startTiebreaker(sessionId, { playerIds, templateId, tiePosition })` — drop
  `templateType`.
- `competitionEventStore.startStechen` is a pass-through; no signature change beyond the
  payload no longer carrying `templateType`.

The round history, live-run note (`playInstanceId`), result entry, and finish-guard flow are
unchanged.

---

## 5. Testing

**Backend** (≥80% for new/changed code):
- `PlayInstanceServiceTest`: `startSerieInstance` produces a `passe`-type instance with exactly
  **one block** built from the given Serie's steps, with the supplied players and template name.
- `TiebreakerServiceTest`:
  - **Remove** the serie-409 expectation.
  - **Add**: `startTiebreaker` loads a Serie, sets `templateName` = Serie name, stores a
    one-Serie `programSnapshot`, calls `startSerieInstance`, and persists `playInstanceId` +
    status `ACTIVE`.
  - Keep the invariant test: `submitResults` never calls `playerResultRepo.save`.
- Full suite green: `./mvnw clean test`.

**Frontend:**
- `StechenPanel` test: the picker lists range-owned published Serien; starting posts a
  `templateType`-free payload with the chosen `serieId`.
- `competitionEventStore.stechen.test.js`: `startStechen` payload no longer includes
  `templateType`.

---

## 6. Conventions to honour

- `openapi.yaml` is the source of truth — edit + regenerate before implementing.
- Schema via JPA entity edits (pre-v1.0, `ddl-auto=update`); no Liquibase changeset.
- `@NullMarked`; `TEXT` for JSON columns; German inline comments for backend domain logic;
  English for frontend and tests.
- Remove unused code eagerly (the `PasseRepository` dependency, `templateType` field/column,
  and the serie-409 branch all go).
- Record the decision (Stechen is Serie-only; `startSerieInstance` live-run path; `templateType`
  removed) back into both `CLAUDE.md`s.
- Commits: `[backend] …` / `[ui] …`.
```

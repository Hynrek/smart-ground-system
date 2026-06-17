# Stechen (Tiebreaker) for Competitions — Design

**Date:** 2026-06-17
**Scope:** Full stack — `smart-ground-backend` (Spring Boot 4) + `smart-ground-ui` (Vue 3)
**Status:** Approved (brainstorming) — ready for implementation planning

---

## 1. Problem & Goal

When all Serien/Passen of a competition are completed for all groups, the `LiveSession`
auto-transitions `ACTIVE → PRE_COMPLETE` (see
`CompetitionProgressService.completeSerie`). `PRE_COMPLETE` is the "results in, not yet
final" stage where an admin can still correct scores before finishing
(`PRE_COMPLETE → COMPLETED`).

A real shooting competition cannot be finished while shooters in decisive positions
(typically 1st, but possibly any position) share an identical score. The admin must run a
**Stechen** ("take a trick") — an additional Serie or Passe shot by just the tied shooters
— to determine the real winner.

**Goal:** Let an admin, while a competition is in `PRE_COMPLETE`, run one or more Stechen
rounds for any tied group and have the standings reflect the resolved order, without
corrupting the main competition totals.

### Decisions locked during brainstorming

| Question | Decision |
|---|---|
| How is the Stechen run? | **Real live play** — reuses the existing live engine (SmartBox fires devices). |
| How does it affect ranking? | **Re-orders the tied block only.** Separate tiebreaker value; main totals untouched; the block keeps its position relative to everyone else. |
| Which ties qualify? | **Admin picks any tie.** System surfaces every tied block; admin decides which to break and for whom. |
| What course is shot? | **Admin picks any existing Serie or Passe template** at Stechen time. |
| Still tied afterward? | **Repeatable.** Admin can run further rounds; final order is decided by the first round that separates the players. |
| Finishing while tied? | **Warn, allow override.** Finishing surfaces a warning listing unresolved decisive ties; admin confirms to proceed. Never hard-blocks. |
| Scope | **Full stack in one** spec/plan. |

---

## 2. Concept & Lifecycle

- A **Stechen** is a tiebreaker round run while the competition `LiveSession` is in
  `PRE_COMPLETE`. **The session stays in `PRE_COMPLETE` throughout** — no new
  `SessionStatus` value is introduced.
- Each Stechen is real live play restricted to the tied shooters. Its scores live
  **outside** `PlayerResult` / `CompetitionSerieResult`, so the main competition totals
  never change. The Stechen only re-orders the tied block.
- Stechen rounds are **repeatable** until the tie breaks. Results stay **editable** (like
  main scores in `PRE_COMPLETE`) until the session is finished.

---

## 3. Data Model — new entity `CompetitionTiebreaker`

New JPA entity, table `competition_tiebreakers`. All JSON columns use `TEXT` (H2
compatibility). UUID PK with `GenerationType.UUID`. `@NullMarked`, `@Nullable` where
applicable. German inline comments for domain logic.

| Field | Type | Purpose |
|---|---|---|
| `id` | UUID PK | |
| `session` | `@ManyToOne` → `LiveSession` (LAZY) | owning competition |
| `tieGroupId` | UUID | shared across all rounds for the **same** original tie (round 1 + repeats) |
| `roundNumber` | int | 1-based; increments per repeat within a `tieGroupId` |
| `tiePosition` | int | the standings position the tie is for (1 = 1st place), for display/audit |
| `participantsJson` | TEXT | `SessionPlayer` IDs still tied at the start of this round |
| `templateType` | enum `passe` \| `serie` | what the admin picked |
| `templateId` | UUID | source template id |
| `templateName` | String | denormalized name for display |
| `programSnapshot` | TEXT | frozen snapshot of the chosen course (immutable, same shape as `LiveSession.programSnapshots`) |
| `playInstanceId` | UUID? | link to the live run that fires devices |
| `resultsJson` | TEXT | `[{playerId, totalPoints, maxPoints}]` |
| `status` | enum | `PENDING → ACTIVE → COMPLETED` (lifecycle of this round) |
| `createdAt` | Instant | |
| `completedAt` | Instant? | |

New `CompetitionTiebreakerRepository extends JpaRepository<CompetitionTiebreaker, UUID>`
with finders: `findBySessionId`, `findBySessionIdAndTieGroupIdOrderByRoundNumber`.

**`tieGroupId` semantics:** generated when the first round for a given tie is created and
reused for every repeat round of that same tied block. This is how repeat rounds are
grouped for resolution (section 4).

---

## 4. Live Execution

Starting a Stechen reuses the existing live engine — it does **not** introduce a new firing
mechanism:

1. Backend creates a **restricted `PlayInstance`** (`type = passe`) from the
   `programSnapshot`, with `playersJson` = the tied shooters only. A chosen single
   `Serie` is wrapped as a one-Serie passe run, so the live engine always sees a
   passe-type instance regardless of `templateType`.
2. That `PlayInstance` fires the SmartBox devices exactly like a normal competition Serie
   (`startBlock` / device commands over MQTT).
3. Results are submitted to the **tiebreaker results endpoint** (section 5), **not**
   `completeSerie`. They are written to `CompetitionTiebreaker.resultsJson` and bypass
   `PlayerResult`, `CompetitionSerieResult`, and the `ACTIVE → PRE_COMPLETE`
   auto-transition entirely.

The `playInstanceId` is stored on the tiebreaker so the live view can attach to the run.

---

## 5. Tie Detection & Resolution (Leaderboard)

`CompetitionService.computeLeaderboard` is extended:

- **Detect tied blocks** = players sharing an identical `totalScore`.
- For a tied block that has Stechen rounds under one `tieGroupId`, order its members by the
  **earliest round that separates them**: apply round 1's Stechen scores; players still
  tied after round 1 are separated by round 2; and so on. Players never separated stay
  tied.
- `PlayerScoreEntry` gains two fields:
  - `tied` (boolean) — still tied with a neighbour after all available Stechen rounds.
  - `tieResolvedByStechen` (boolean) — this player's order within the block was decided by a
    Stechen.
- Rank assignment changes from today's arbitrary `1..n` to **shared ranks for unresolved
  ties** and **ordered ranks where a Stechen resolved them**. (This also fixes the current
  arbitrary ordering of equal-score players.)

The leaderboard remains served by the existing handwritten `CompetitionService` /
`SessionLeaderboardResponse` (the enrichment reuses that path).

---

## 6. API

New endpoints are **contract-first**: declared in `openapi.yaml` first, regenerated
(`./mvnw generate-sources`), then implemented in a **new** `TiebreakerController implements
TiebreakerApi` — no Spring mapping annotations on the controller.

| Method & Path | Purpose |
|---|---|
| `GET /api/sessions/{id}/ties` | Current tied blocks: position, shared score, tied players, existing rounds + resolution status. Drives UI highlighting. |
| `POST /api/sessions/{id}/tiebreakers` | Start a Stechen `{ playerIds, templateType, templateId }`. Creates the entity + `programSnapshot` + restricted `PlayInstance`; returns the tiebreaker + `playInstanceId`. **Only allowed when session is `PRE_COMPLETE`.** |
| `POST /api/sessions/{id}/tiebreakers/{tbId}/results` | Submit/correct per-player Stechen scores `[{playerId, totalPoints, maxPoints}]`; marks the round `COMPLETED`; returns the updated ties view. Re-callable to correct results until finish. |
| `GET /api/sessions/{id}/tiebreakers` | List all Stechen rounds for the session (audit/history). |

**Finish guard:** the existing `PRE_COMPLETE → COMPLETED` finish endpoint returns a
**warning payload** listing unresolved decisive ties when any remain. The admin re-submits
with an acknowledge/`force` flag to proceed. It **never hard-blocks**.

New exceptions in `ch.jp.shooting.exception`, mapped in `GlobalExceptionHandler`:
- `TiebreakerNotFoundException` → 404 `/errors/tiebreaker-not-found`
- `InvalidTiebreakerStateException` → 409 `/errors/invalid-tiebreaker-state` (e.g. starting a
  Stechen when session is not `PRE_COMPLETE`, or submitting results to a completed round).

> **Out of scope:** the existing `CompetitionController` uses Spring mapping annotations
> directly, deviating from the contract-first hard rule. New endpoints go in the generated
> `TiebreakerController`; the old controller is **not** refactored here.

---

## 7. Frontend (Vue — `WettkampfDetailView`, PRE_COMPLETE tab)

- Standings table **highlights tied blocks** and shows a "Stechen starten" action per tied
  group.
- **Start modal:** pick a Serie/Passe template → start → **reuse the live competition run
  view**, restricted to the tied players → enter/confirm results.
- **Stechen history:** per tie, show each round, its participants, scores, and the resolved
  order.
- **Finish** button surfaces the warning dialog when unresolved decisive ties remain
  (confirm-to-override).
- New Pinia store methods on the competition/session store, plus **mock-mode fixtures**
  matching the existing api/mock dual-mode pattern.

---

## 8. Testing

**Backend** (≥80% coverage for new code):
- Tie detection: clear winner (no tie), 2-way tie, 3-way tie, tie below the podium.
- Stechen ordering, including **repeat rounds where round 1 only partially separates** the
  block (some players still tied → resolved by round 2).
- Invariant: Stechen results never alter `PlayerResult` / main totals.
- Finish guard returns the warning payload for unresolved decisive ties; override succeeds.
- Status guards: starting a Stechen is rejected unless the session is `PRE_COMPLETE`;
  results rejected for a non-`ACTIVE`/already-`COMPLETED` round.

**Frontend:**
- Store unit tests in mock mode: load ties, start Stechen, submit results, finish-warning
  flow.

---

## 9. Conventions to honour

- `openapi.yaml` is the source of truth; generate before implementing.
- Schema via JPA entity edits (pre-v1.0, `ddl-auto=update`); no Liquibase changesets.
- `@NullMarked` on new classes; UUID PKs; `TEXT` for JSON columns.
- German inline comments for backend domain logic; English for frontend and tests.
- Record any decisions made during implementation back into the relevant `CLAUDE.md`.
- Commit messages: `[backend] …` / `[ui] …`.

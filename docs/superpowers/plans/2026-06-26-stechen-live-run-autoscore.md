# Stechen Live-Run Auto-Score Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Score a Stechen by shooting its Serie as a live run on the range kiosk, with the run's score flowing into the tiebreaker automatically; remove manual entry.

**Architecture:** The Stechen already creates a one-block `PlayInstance`. The backend enriches the ties endpoint with the run's block info (for kiosk surfacing) and reconciles a completed run into the tiebreaker on read (auto-resolve). The frontend surfaces the run in the shooter flyout, plays it through the existing play page, and the completed run triggers a ties reload; the admin panel light-polls and drops its manual-entry form.

**Tech Stack:** Spring Boot 4 (Java 25, H2 tests, OpenAPI-generated DTOs) · Vue 3 (Composition API, Pinia, Vitest).

**Spec:** `docs/superpowers/specs/2026-06-26-stechen-live-run-autoscore-design.md`

---

## File Structure

**Backend (`smart-ground-backend`)**
- `src/main/resources/static/openapi.yaml` — remove results endpoint + `SubmitTiebreakerResultsRequest`; add `blockId` + `run` to `TiebreakerResponse`.
- `src/main/java/ch/jp/shooting/api/SessionController.java` — remove `submitTiebreakerResults` override.
- `src/main/java/ch/jp/shooting/service/TiebreakerService.java` — remove `submitResults`; add `reconcileActiveRuns` + active-round enrichment in `toResponse`.
- `src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java` — remove `submitResults` tests; add reconcile + enrich tests; stub `getPlayInstance` in the two start tests.

**Frontend (`smart-ground-ui`)**
- `src/services/tiebreakerApi.js` — remove `submitTiebreakerResults`.
- `src/stores/competitionEventStore.js` — remove `submitStechenResults`; add `startStechenBlock`, `completeStechenRun`, `getActiveStechenForRange`; load ties for PRE_COMPLETE sessions in `loadEvents`.
- `src/components/competition/StechenPanel.vue` — remove entry form; add live status + ties polling.
- `src/components/shooter-remote/ShooterFlyoutPanel.vue` — add a "Stechen" section.
- `src/views/shooter/ShooterPlayPage.vue` — thread `sessionId` into the block context.
- `src/stores/playSessionStore.js` — `startGroupPlay` accepts `sessionId`; `startGroupPlay`/`commitResults` route `instanceType === 'stechen'`.
- Tests: `src/stores/__tests__/competitionEventStore.stechen.test.js`, `src/components/__tests__/StechenPanel.test.js`.

**Docs:** `smart-ground-backend/CLAUDE.md`, `smart-ground-ui/CLAUDE.md`.

---

## Task 1: Backend contract — remove results endpoint, enrich TiebreakerResponse

**Files:**
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/SessionController.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/TiebreakerService.java`
- Modify: `smart-ground-backend/src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java`

This task makes the codebase compile after the contract change. The reconcile/enrich logic lands in Task 2.

- [ ] **Step 1: Remove the results endpoint from `openapi.yaml`**

Delete the entire `/api/sessions/{sessionId}/tiebreakers/{tiebreakerId}/results` path block (currently lines ~1434–1454, the `post: submitTiebreakerResults` operation through its `'409'` response).

- [ ] **Step 2: Remove the `SubmitTiebreakerResultsRequest` schema**

Delete this schema block (currently ~3796–3802):

```yaml
    SubmitTiebreakerResultsRequest:
      type: object
      required: [results]
      properties:
        results:
          type: array
          items: { $ref: '#/components/schemas/TiebreakerPlayerScore' }
```

Keep `TiebreakerPlayerScore` — it is still used by `TiebreakerResponse.results` and by reconciliation.

- [ ] **Step 3: Add `blockId` + `run` to `TiebreakerResponse`**

In the `TiebreakerResponse` schema (currently ~3812), add these two properties after `playInstanceId`:

```yaml
        blockId: { type: string, format: uuid, nullable: true, description: Live-run block id for an ACTIVE round (kiosk play target) }
        run:
          allOf: [ { $ref: '#/components/schemas/EmbeddedSerie' } ]
          description: The Serie being shot, for an ACTIVE round (surfaces the run on the range kiosk). Absent once resolved.
```

- [ ] **Step 4: Regenerate OpenAPI sources**

Run: `cd smart-ground-backend && ./mvnw generate-sources`
Expected: BUILD SUCCESS. The generated `TiebreakerResponse` now has `getBlockId()`/`run(...)`; `SubmitTiebreakerResultsRequest` no longer exists; `SessionApi` no longer declares `submitTiebreakerResults`.

Verify: `grep -rl "SubmitTiebreakerResultsRequest" target/generated-sources` returns nothing.

- [ ] **Step 5: Remove the controller override**

In `SessionController.java`, delete the `submitTiebreakerResults` method (currently ~216–227) and remove the now-unused import `import ch.jp.smartground.model.SubmitTiebreakerResultsRequest;` (line 21).

- [ ] **Step 6: Remove `submitResults` from `TiebreakerService`**

In `TiebreakerService.java`, delete the `submitResults(...)` method (currently ~261–280, the whole `// ── Stechen-Ergebnisse entgegennehmen ──` section body) and remove the import `import ch.jp.smartground.model.SubmitTiebreakerResultsRequest;` (line 22).

- [ ] **Step 7: Remove the obsolete tests**

In `TiebreakerServiceTest.java`, delete the two tests `submitResults_doesNotTouchPlayerResults` (~123–144) and `submitResults_onCompletedRound_throwsInvalidState` (~146–158).

- [ ] **Step 8: Compile + run the tiebreaker suite**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=TiebreakerServiceTest`
Expected: the two start tests (`startTiebreaker_loadsSerie_buildsSnapshot_startsSerieRun`, `startTiebreaker_snapshotIncludesRangeWhenSeriePresent`) FAIL with a `NullPointerException` from `toResponse` calling the (now-needed) `getPlayInstance` on an unstubbed mock. **This is expected** and fixed in Task 2, Step 6. If they instead pass, that's fine too. The suite must at minimum **compile**.

- [ ] **Step 9: Commit**

```bash
cd smart-ground-backend
git add src/main/resources/static/openapi.yaml src/main/java/ch/jp/shooting/api/SessionController.java src/main/java/ch/jp/shooting/service/TiebreakerService.java src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java
git commit -m "[backend] Remove Stechen manual-results endpoint; enrich TiebreakerResponse"
```

---

## Task 2: Backend — auto-resolve on read + active-run enrichment

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/TiebreakerService.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java`

- [ ] **Step 1: Write the failing reconcile test**

Add to `TiebreakerServiceTest.java`:

```java
@Test
void listTies_completedRun_autoResolvesTiebreaker() throws Exception {
    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    UUID instanceId = UUID.randomUUID();

    CompetitionTiebreaker tb = new CompetitionTiebreaker(session, UUID.randomUUID(), 1, 1);
    tb.setStatus(TiebreakerStatus.ACTIVE);
    tb.setPlayInstanceId(instanceId);
    tb.setParticipantsJson(objectMapper.writeValueAsString(List.of(p1.toString(), p2.toString())));
    when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of(tb));
    when(tbRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    when(playerResultRepo.findBySessionId(sessionId)).thenReturn(List.of());

    SessionPlayer sp1 = new SessionPlayer(); sp1.setId(p1); sp1.setDisplayName("Anna");
    SessionPlayer sp2 = new SessionPlayer(); sp2.setId(p2); sp2.setDisplayName("Ben");
    lenient().when(playerRepo.findAllById(anyList())).thenReturn(List.of(sp1, sp2));

    var inst = new ch.jp.smartground.model.PlayInstanceResponse();
    inst.setStatus(ch.jp.smartground.model.PlayInstanceStatus.COMPLETED);
    var block = new ch.jp.smartground.model.PlayBlock().blockId(UUID.randomUUID());
    block.result(new ch.jp.smartground.model.BlockResult().playerResults(List.of(
        new ch.jp.smartground.model.PlayerResult().playerId(p1.toString()).totalPoints(9).maxPoints(10),
        new ch.jp.smartground.model.PlayerResult().playerId(p2.toString()).totalPoints(7).maxPoints(10))));
    inst.blocks(List.of(block));
    when(playInstanceService.getPlayInstance(instanceId)).thenReturn(inst);

    service.listTies(sessionId);

    assertEquals(TiebreakerStatus.COMPLETED, tb.getStatus());
    assertNotNull(tb.getResultsJson());
    assertEquals(2, objectMapper.readTree(tb.getResultsJson()).size());
    verify(playerResultRepo, never()).save(any());
}
```

- [ ] **Step 2: Write the failing enrichment test**

Add to `TiebreakerServiceTest.java`:

```java
@Test
void listTiebreakers_activeRun_attachesRunBlock() throws Exception {
    UUID instanceId = UUID.randomUUID();
    UUID rangeId = UUID.randomUUID();
    UUID serieId = UUID.randomUUID();
    UUID blockId = UUID.randomUUID();

    CompetitionTiebreaker tb = new CompetitionTiebreaker(session, UUID.randomUUID(), 1, 1);
    tb.setStatus(TiebreakerStatus.ACTIVE);
    tb.setPlayInstanceId(instanceId);
    tb.setParticipantsJson("[]");
    when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of(tb));
    lenient().when(playerRepo.findAllById(anyList())).thenReturn(List.of());

    var inst = new ch.jp.smartground.model.PlayInstanceResponse();
    inst.setStatus(ch.jp.smartground.model.PlayInstanceStatus.ACTIVE);
    inst.blocks(List.of(new ch.jp.smartground.model.PlayBlock()
        .blockId(blockId).serieId(serieId).serieAlias("Stech-Serie")
        .rangeId(rangeId).rangeName("Stand 1").steps(List.of())));
    when(playInstanceService.getPlayInstance(instanceId)).thenReturn(inst);

    var rounds = service.listTiebreakers(sessionId);

    assertEquals(1, rounds.size());
    var r = rounds.get(0);
    assertEquals(blockId, r.getBlockId().orElse(null));
    assertEquals(rangeId, r.getRun().getRangeId().orElse(null));
    assertEquals("Stech-Serie", r.getRun().getAlias());
}
```

- [ ] **Step 3: Run the new tests to verify they fail**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=TiebreakerServiceTest`
Expected: `listTies_completedRun_autoResolvesTiebreaker` FAILS (tb stays ACTIVE) and `listTiebreakers_activeRun_attachesRunBlock` FAILS (getBlockId/getRun empty/null).

- [ ] **Step 4: Add imports to `TiebreakerService`**

Add these imports:

```java
import ch.jp.shooting.exception.PlayInstanceNotFoundException;
import ch.jp.smartground.model.EmbeddedSerie;
import ch.jp.smartground.model.PlayInstanceResponse;
import ch.jp.smartground.model.PlayInstanceStatus;
```

- [ ] **Step 5: Implement `reconcileActiveRuns` and call it**

Add this private method to `TiebreakerService`:

```java
/**
 * Übernimmt abgeschlossene Live-Läufe automatisch in ihre Stechen-Runde: liest die
 * Spielerergebnisse aus der verknüpften PlayInstance und schliesst die Runde ab.
 * Berührt bewusst NIE einen PlayerResult.
 */
private void reconcileActiveRuns(UUID sessionId) throws Exception {
    for (CompetitionTiebreaker tb : tbRepo.findBySessionId(sessionId)) {
        if (tb.getStatus() != TiebreakerStatus.ACTIVE || tb.getPlayInstanceId() == null) {
            continue;
        }
        PlayInstanceResponse inst;
        try {
            inst = playInstanceService.getPlayInstance(tb.getPlayInstanceId());
        } catch (PlayInstanceNotFoundException e) {
            continue; // Lauf entfernt — nichts zu übernehmen
        }
        if (inst.getStatus() != PlayInstanceStatus.COMPLETED) {
            continue;
        }
        var block = inst.getBlocks().orElse(List.of()).stream().findFirst().orElse(null);
        if (block == null || !block.getResult().isPresent()) {
            continue;
        }
        var scores = new ArrayList<TiebreakerPlayerScore>();
        for (var pr : block.getResult().get().getPlayerResults()) {
            scores.add(new TiebreakerPlayerScore()
                .playerId(UUID.fromString(pr.getPlayerId()))
                .totalPoints(pr.getTotalPoints() != null ? pr.getTotalPoints() : 0)
                .maxPoints(pr.getMaxPoints() != null ? pr.getMaxPoints() : 0));
        }
        tb.setResultsJson(objectMapper.writeValueAsString(scores));
        tb.setStatus(TiebreakerStatus.COMPLETED);
        tb.setCompletedAt(Instant.now());
        tbRepo.save(tb);
    }
}
```

Then add `reconcileActiveRuns(sessionId);` as the **first statement** inside both `listTies(UUID sessionId)` (before "1) Hauptstände…") and `listTiebreakers(UUID sessionId)` (before loading `tbRepo.findBySessionId`).

- [ ] **Step 6: Enrich ACTIVE rounds in `toResponse`**

In `toResponse`, after the `if (tb.getPlayInstanceId() != null) { response.playInstanceId(...); }` block, add:

```java
// Aktiven Lauf für die Range-Kiosk-Anzeige anreichern (Serie + Block).
if (tb.getStatus() == TiebreakerStatus.ACTIVE && tb.getPlayInstanceId() != null) {
    try {
        var inst = playInstanceService.getPlayInstance(tb.getPlayInstanceId());
        var block = inst.getBlocks().orElse(List.of()).stream().findFirst().orElse(null);
        if (block != null) {
            response.blockId(block.getBlockId());
            response.run(new EmbeddedSerie()
                .id(block.getSerieId())
                .alias(block.getSerieAlias())
                .rangeId(block.getRangeId().orElse(null))
                .rangeName(block.getRangeName().orElse(null))
                .steps(block.getSteps())
                .missing(false));
        }
    } catch (PlayInstanceNotFoundException ignored) {
        // Lauf nicht (mehr) vorhanden — Runde ohne run-Block ausliefern
    }
}
```

- [ ] **Step 7: Fix the two existing start tests (stub `getPlayInstance`)**

In `startTiebreaker_loadsSerie_buildsSnapshot_startsSerieRun` and `startTiebreaker_snapshotIncludesRangeWhenSeriePresent`, add this stubbing right after the existing `when(playInstanceService.startSerieInstance(...))` line (so `toResponse`'s enrichment fetch returns a valid ACTIVE instance):

```java
var activeInst = new ch.jp.smartground.model.PlayInstanceResponse();
activeInst.setStatus(ch.jp.smartground.model.PlayInstanceStatus.ACTIVE);
activeInst.blocks(List.of(new ch.jp.smartground.model.PlayBlock()
    .blockId(UUID.randomUUID()).steps(List.of())));
when(playInstanceService.getPlayInstance(any())).thenReturn(activeInst);
```

- [ ] **Step 8: Run the full tiebreaker suite**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=TiebreakerServiceTest`
Expected: PASS (all start, reconcile, and enrich tests green).

- [ ] **Step 9: Run the full backend suite**

Run: `cd smart-ground-backend && ./mvnw clean test`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
cd smart-ground-backend
git add src/main/java/ch/jp/shooting/service/TiebreakerService.java src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java
git commit -m "[backend] Auto-resolve Stechen from completed live run; surface active run"
```

---

## Task 3: Frontend API + store — remove manual submit, add run wiring

**Files:**
- Modify: `smart-ground-ui/src/services/tiebreakerApi.js`
- Modify: `smart-ground-ui/src/stores/competitionEventStore.js`
- Test: `smart-ground-ui/src/stores/__tests__/competitionEventStore.stechen.test.js`

- [ ] **Step 1: Update the store test (remove submit, add new actions)**

In `competitionEventStore.stechen.test.js`:

Replace the `tiebreakerApi.js` mock (lines 5–10) with (drop `submitTiebreakerResults`):

```javascript
vi.mock('@/services/tiebreakerApi.js', () => ({
  getTies: vi.fn(),
  listTiebreakers: vi.fn(),
  startTiebreaker: vi.fn(),
}))
vi.mock('@/services/playInstanceApi.js', () => ({
  startBlock: vi.fn(),
  completeBlock: vi.fn(),
}))
```

Add the import after the existing ones (line 19 area):

```javascript
import * as playApi from '@/services/playInstanceApi.js'
```

Delete the test `submitStechenResults posts results then stores refreshed ties` (lines ~41–47) and add:

```javascript
it('completeStechenRun completes the block then reloads ties', async () => {
  playApi.completeBlock.mockResolvedValue({ status: 'completed' })
  tb.getTies.mockResolvedValue({ sessionId: 's1', tiedBlocks: [] })
  const store = useCompetitionEventStore()
  const results = [{ playerId: 'p1', totalPoints: 9, maxPoints: 10 }]
  await store.completeStechenRun('inst1', 'blk1', results, 's1')
  expect(playApi.completeBlock).toHaveBeenCalledWith('inst1', 'blk1', results)
  expect(tb.getTies).toHaveBeenCalledWith('s1')
})

it('getActiveStechenForRange returns active rounds matching the range', async () => {
  tb.getTies.mockResolvedValue({
    sessionId: 's1',
    tiedBlocks: [{
      tiePosition: 1, sharedScore: 24, resolved: false,
      players: [{ playerId: 'p1', displayName: 'Anna' }],
      rounds: [
        { sessionId: 's1', status: 'COMPLETED', playInstanceId: 'iX', run: { rangeId: 'r1' } },
        { sessionId: 's1', status: 'ACTIVE', playInstanceId: 'iA', blockId: 'bA', templateName: 'Stech',
          participants: [{ playerId: 'p1', displayName: 'Anna' }],
          run: { id: 'se1', alias: 'Stech', rangeId: 'r1', rangeName: 'Stand 1', steps: [] } },
      ],
    }],
  })
  const store = useCompetitionEventStore()
  await store.loadTies('s1')
  const onR1 = store.getActiveStechenForRange('r1')
  expect(onR1).toHaveLength(1)
  expect(onR1[0]).toMatchObject({ instanceId: 'iA', blockId: 'bA', serieId: 'se1', sessionId: 's1' })
  expect(store.getActiveStechenForRange('r2')).toHaveLength(0)
})
```

- [ ] **Step 2: Run the store test to verify it fails**

Run: `cd smart-ground-ui && npm run test -- src/stores/__tests__/competitionEventStore.stechen.test.js`
Expected: FAIL — `completeStechenRun`/`getActiveStechenForRange` are not functions; the deleted submit test is gone.

- [ ] **Step 3: Remove `submitTiebreakerResults` from `tiebreakerApi.js`**

Delete the `submitTiebreakerResults` export (lines 17–21) in `tiebreakerApi.js`. The file ends after `startTiebreaker`.

- [ ] **Step 4: Update `competitionEventStore.js` — import play API**

At the top, add after the `tiebreakerApi` import (line 4):

```javascript
import * as playInstanceApi from '@/services/playInstanceApi.js'
```

- [ ] **Step 5: Load ties for PRE_COMPLETE sessions in `loadEvents`**

In `loadEvents`, the existing `Promise.all(activeEvents.map(...))` hydrates progress. Immediately after that `Promise.all` (before the `catch`/`finally`), add a second best-effort load:

```javascript
      // Stechen: load ties for PRE_COMPLETE sessions so active runs surface on the kiosk.
      await Promise.all(
        activeEvents
          .filter(ev => ev.status?.toUpperCase() === 'PRE_COMPLETE')
          .map(ev => loadTies(ev.id).catch(e =>
            console.error('[competitionEventStore] tie load failed:', e))),
      )
```

- [ ] **Step 6: Replace `submitStechenResults` with the new actions**

In the `// ── Stechen (tiebreaker) ──` section, delete `submitStechenResults` (lines ~517–521) and add:

```javascript
  // Surface active Stechen runs for a range (mirrors getActiveCompetitionRotten).
  // Reads enriched ties: ACTIVE rounds carry run (EmbeddedSerie) + blockId.
  const getActiveStechenForRange = (rangeId) => {
    const result = []
    for (const ties of Object.values(tiesBySession.value)) {
      for (const block of (ties?.tiedBlocks ?? [])) {
        for (const round of (block.rounds ?? [])) {
          if (round.status !== 'ACTIVE' || !round.run || round.run.rangeId !== rangeId) continue
          result.push({
            instanceId: round.playInstanceId,
            blockId: round.blockId,
            serieId: round.run.id,
            serieAlias: round.run.alias,
            steps: round.run.steps ?? [],
            rangeId: round.run.rangeId,
            rangeName: round.run.rangeName ?? null,
            players: (round.participants ?? []).map(p => ({ id: p.playerId, displayName: p.displayName })),
            sessionId: round.sessionId,
            templateName: round.templateName,
            tiePosition: round.tiePosition,
          })
        }
      }
    }
    return result
  }

  // Mark the Stechen run's block in progress (required before completeBlock).
  const startStechenBlock = async (instanceId, blockId) => {
    await playInstanceApi.startBlock(instanceId, blockId)
  }

  // Complete the Stechen run; the backend auto-resolves the tie on the next ties read.
  const completeStechenRun = async (instanceId, blockId, results, sessionId) => {
    await playInstanceApi.completeBlock(instanceId, blockId, results)
    await loadTies(sessionId)
  }
```

- [ ] **Step 7: Update the store's returned interface**

In the `return { ... }` object, in the Stechen section, remove `submitStechenResults` and add the three new actions:

```javascript
    // Stechen (tiebreaker)
    tiesBySession,
    loadTies, startStechen, finishEvent,
    getActiveStechenForRange, startStechenBlock, completeStechenRun,
```

- [ ] **Step 8: Run the store test**

Run: `cd smart-ground-ui && npm run test -- src/stores/__tests__/competitionEventStore.stechen.test.js`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
cd smart-ground-ui
git add src/services/tiebreakerApi.js src/stores/competitionEventStore.js src/stores/__tests__/competitionEventStore.stechen.test.js
git commit -m "[ui] Wire Stechen live-run surfacing/completion; drop manual submit"
```

---

## Task 4: Frontend — StechenPanel live status + polling (no manual entry)

**Files:**
- Modify: `smart-ground-ui/src/components/competition/StechenPanel.vue`
- Test: `smart-ground-ui/src/components/__tests__/StechenPanel.test.js`

- [ ] **Step 1: Add the failing "no entry form / live status" test**

In `StechenPanel.test.js`, change the `tiedBlock` factory (lines 12–21) to include an ACTIVE round so the live-status path renders:

```javascript
const tiedBlock = () => ({
  tiePosition: 1,
  sharedScore: 24,
  resolved: false,
  players: [
    { playerId: 'p1', displayName: 'Anna' },
    { playerId: 'p2', displayName: 'Ben' },
  ],
  rounds: [
    {
      id: 'tb1', roundNumber: 1, status: 'ACTIVE', templateName: 'Stech-Serie',
      playInstanceId: 'iA', blockId: 'bA',
      run: { id: 'se1', alias: 'Stech-Serie', rangeId: 'r1', rangeName: 'Stand 1', steps: [] },
      participants: [
        { playerId: 'p1', displayName: 'Anna' },
        { playerId: 'p2', displayName: 'Ben' },
      ],
      results: [],
    },
  ],
})
```

Add a new test in the `describe` block:

```javascript
it('shows a live run status for an ACTIVE round and no manual entry inputs', async () => {
  const { wrapper } = await setup()
  expect(wrapper.find('.result-form').exists()).toBe(false)
  expect(wrapper.findAll('input.result-input')).toHaveLength(0)
  expect(wrapper.find('.round-live').exists()).toBe(true)
  expect(wrapper.text()).toContain('Stand 1')
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd smart-ground-ui && npm run test -- src/components/__tests__/StechenPanel.test.js`
Expected: FAIL — `.round-live` not found (the old `.result-form` still renders).

- [ ] **Step 3: Remove the entry form + drafts from the template**

In `StechenPanel.vue`, replace the editable `<form class="result-form" ...> ... </form>` block (lines ~73–118) and the `round-instance-note` paragraph (lines ~67–71) with a single live-status block:

```html
          <!-- Live run status for an ACTIVE round (auto-scored from the range) -->
          <div v-if="round.status === 'ACTIVE'" class="round-live">
            <span class="round-live-dot" aria-hidden="true"></span>
            <span class="round-live-text">
              Läuft am Bereich{{ round.run?.rangeName ? ` ${round.run.rangeName}` : '' }} —
              Ergebnis wird automatisch übernommen.
            </span>
          </div>
```

Keep the read-only COMPLETED results block (`<div v-else-if="(round.results ?? []).length > 0" class="result-readonly">…`) unchanged.

- [ ] **Step 4: Remove drafts/save logic from the script**

In `<script setup>`, delete: the `drafts` reactive, `syncDrafts`, the `watch(tiedBlocks, syncDrafts, …)`, `saving`, and `saveResults` (lines ~222–265). Also remove the now-unused `reactive`, `watch` from the `vue` import if no longer used (keep `ref`, `computed`, `onMounted`; add `onUnmounted`).

- [ ] **Step 5: Add ties polling on mount**

Replace the existing `onMounted(...)` (lines ~305–309) with:

```javascript
let pollTimer = null
const anyRoundActive = () =>
  tiedBlocks.value.some(b => (b.rounds ?? []).some(r => r.status === 'ACTIVE'))

onMounted(async () => {
  if (passeStore.savedSerien.length === 0) {
    await passeStore.loadSerienFromStorage().catch(() => {})
  }
  await store.loadTies(props.sessionId).catch(() => {})
  // Light-poll while a run is active so auto-resolution surfaces without a manual refresh.
  pollTimer = setInterval(() => {
    if (anyRoundActive()) store.loadTies(props.sessionId).catch(() => {})
  }, 4000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
```

- [ ] **Step 6: Add the live-status styles**

In `<style scoped>`, replace the `.round-instance-note` / `.instance-id` rules (lines ~442–455) with:

```css
.round-live {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--sg-text-muted);
}

.round-live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--sg-color-warning);
  animation: round-live-pulse 1.2s ease-in-out infinite;
}

@keyframes round-live-pulse {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 1; }
}
```

- [ ] **Step 7: Run the StechenPanel test**

Run: `cd smart-ground-ui && npm run test -- src/components/__tests__/StechenPanel.test.js`
Expected: PASS (Serie-picker tests + new live-status test).

- [ ] **Step 8: Lint**

Run: `cd smart-ground-ui && npm run lint:check`
Expected: no errors (no unused `reactive`/`watch`/`drafts`).

- [ ] **Step 9: Commit**

```bash
cd smart-ground-ui
git add src/components/competition/StechenPanel.vue src/components/__tests__/StechenPanel.test.js
git commit -m "[ui] StechenPanel: live run status + ties polling, drop manual entry"
```

---

## Task 5: Frontend — surface + play the Stechen run on the kiosk

**Files:**
- Modify: `smart-ground-ui/src/components/shooter-remote/ShooterFlyoutPanel.vue`
- Modify: `smart-ground-ui/src/views/shooter/ShooterPlayPage.vue`
- Modify: `smart-ground-ui/src/stores/playSessionStore.js`

No unit test for the flyout wiring (it mirrors the existing competition-rotte path, which is integration-shaped); verified manually in Task 6. The store/play-page changes are exercised by the existing play flow.

- [ ] **Step 1: Thread `sessionId` through the play block context (`ShooterPlayPage.vue`)**

In the `if (store.pendingPasseInfo)` block, change the `_blockContext.value` assignment (line ~455) to carry `sessionId`:

```javascript
    _blockContext.value = { instanceId: info.instanceId, blockId: info.blockId, rotteId: info.rotteId ?? null, instanceType: info.instanceType ?? null, sessionId: info.sessionId ?? null };
```

In `beginGroupPlay`, add the `sessionId` argument to the `store.startGroupPlay(...)` call (after the `instanceType` argument, line ~489):

```javascript
    _blockContext.value?.instanceType ?? null,
    _blockContext.value?.sessionId ?? null,
```

- [ ] **Step 2: Accept + route `sessionId` and `stechen` in `playSessionStore.startGroupPlay`**

Change the signature (line 597) to add a trailing param:

```javascript
  const startGroupPlay = async (players, rangeId = null, rangeName = null, instanceId = null, blockId = null, rotteId = null, instanceType = null, sessionId = null) => {
```

In the in-progress block (lines ~660–669), set `sessionId` on the context and add a `stechen` branch:

```javascript
    if (instanceId && blockId) {
      activeBlockContext.value = { instanceId, blockId, rotteId: rotteId ?? null, instanceType: instanceType ?? null, sessionId: sessionId ?? null }
      if (instanceType === 'competition') {
        const { useCompetitionEventStore } = await import('@/stores/competitionEventStore.js')
        useCompetitionEventStore().markBlockInProgress(instanceId, blockId, rotteId ?? null)
      } else if (instanceType === 'stechen') {
        const { useCompetitionEventStore } = await import('@/stores/competitionEventStore.js')
        await useCompetitionEventStore().startStechenBlock(instanceId, blockId)
      } else {
        const { useActivePasseStore } = await import('@/stores/activePasseStore.js')
        useActivePasseStore().markBlockInProgress(instanceId, blockId)
      }
```

(Leave the rest of that `if` block — the closing brace — intact.)

- [ ] **Step 3: Route `stechen` completion in `playSessionStore.commitResults`**

In `commitResults` (lines ~550–556), add a `stechen` branch:

```javascript
      if (ctx.instanceType === 'competition') {
        const { useCompetitionEventStore } = await import('@/stores/competitionEventStore.js')
        useCompetitionEventStore().markBlockDone(ctx.instanceId, ctx.blockId, results, ctx.rotteId ?? null)
      } else if (ctx.instanceType === 'stechen') {
        const { useCompetitionEventStore } = await import('@/stores/competitionEventStore.js')
        await useCompetitionEventStore().completeStechenRun(ctx.instanceId, ctx.blockId, results, ctx.sessionId)
      } else {
        const { useActivePasseStore } = await import('@/stores/activePasseStore.js')
        useActivePasseStore().markBlockDone(ctx.instanceId, ctx.blockId, results)
      }
```

- [ ] **Step 4: Add the "Stechen" section to the flyout template (`ShooterFlyoutPanel.vue`)**

In the normal-mode `<template v-else>` (after the "Offene Wettkämpfe" `competitionSerien` section, before "Offene Trainings", around line 151), add:

```html
          <!-- Stechen -->
          <template v-if="stechenBlocks.length > 0">
            <div class="section">
              <span class="section-label">Stechen</span>
              <div class="serien-list">
                <div
                  v-for="item in stechenBlocks"
                  :key="`${item.instanceId}-${item.blockId}`"
                  class="serie-card"
                  data-testid="stechen-card"
                >
                  <button class="serie-header-btn" @click="playStechen(item)">
                    <div class="block-info">
                      <span class="rotte-instance-name">Platz {{ item.tiePosition }} · Stechen</span>
                      <span class="serie-name">{{ item.serieAlias }}</span>
                    </div>
                    <Icons icon="play" :size="12" color="rgba(79,195,247,0.6)" />
                  </button>
                  <div class="serie-actions">
                    <div class="session-meta">
                      {{ (item.players ?? []).map(p => p.displayName).join(', ') }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
```

Also update the empty-state `v-if` condition (line ~265) to include `stechenBlocks.length === 0`:

```html
          <div
            v-if="passenBlocks.length === 0 && competitionSerien.length === 0 && stechenBlocks.length === 0 && trainingBlocks.length === 0 && userSerien.length === 0 && globalSerien.length === 0"
            class="empty-state"
          >
```

- [ ] **Step 5: Add the flyout script bindings**

In `<script setup>`, add the computed after `competitionSerien` (line ~377):

```javascript
const stechenBlocks = computed(() =>
  competitionEventStore.getActiveStechenForRange(currentRangeId.value),
);
```

Add the play handler after `playCompetitionRotte` (line ~434):

```javascript
const playStechen = (item) => {
  playStore.pendingPasseInfo = {
    serie: {
      id: item.serieId,
      name: item.serieAlias,
      alias: item.serieAlias,
      steps: item.steps,
      rangeId: item.rangeId,
      rangeName: item.rangeName,
    },
    rangeId: currentRangeId.value,
    instanceId: item.instanceId,
    blockId: item.blockId,
    players: item.players,
    instanceType: 'stechen',
    sessionId: item.sessionId,
    serieName: item.serieAlias,
  };
  isOpen.value = false;
  router.push(`/remote/${currentRangeId.value}/play`);
};
```

- [ ] **Step 6: Run the frontend suite + lint**

Run: `cd smart-ground-ui && npm run test`
Expected: PASS (no regressions).
Run: `cd smart-ground-ui && npm run lint:check`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
cd smart-ground-ui
git add src/components/shooter-remote/ShooterFlyoutPanel.vue src/views/shooter/ShooterPlayPage.vue src/stores/playSessionStore.js
git commit -m "[ui] Surface and play Stechen run on the range kiosk"
```

---

## Task 6: Manual end-to-end verification

**Files:** none (runtime check).

- [ ] **Step 1: Start the stack**

```bash
cd smart-ground-backend && docker compose up -d && ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```
In another terminal: `cd smart-ground-ui && npm run dev`

- [ ] **Step 2: Drive the flow**

1. Create a COMPETITION session, add a Rotte with ≥2 players, add a Passe with a range-owned **published** Serie, start it, and complete its Serien so two players share a top score (tie at position 1).
2. Move the session to `PRE_COMPLETE`. In `WettkampfDetailView` → Stechen panel, click **Stechen starten**, pick the published Serie, **Starten**. The round shows the live "Läuft am Bereich …" status (no input fields).
3. Open the shooter kiosk for that Serie's range (`/remote/{rangeId}`); open the flyout — a **Stechen** card appears. Play it through and finish (Beenden).
4. Back on the Stechen panel (within ~4s poll), the round flips to **Aufgelöst** with read-only scores and the tied block re-orders.

Expected: the score arrived automatically; no manual entry anywhere. Confirm in DB/logs that `player_results` was **not** written by the Stechen (only `competition_tiebreakers.results_json`).

- [ ] **Step 3: Note the result** in the session (pass/fail + anything observed). No commit.

---

## Task 7: Documentation

**Files:**
- Modify: `smart-ground-backend/CLAUDE.md`
- Modify: `smart-ground-ui/CLAUDE.md`

- [ ] **Step 1: Update backend `CLAUDE.md`**

In the **Stechen (Tiebreaker)** section, replace the 4th bullet's "4 endpoints" list: remove the `POST …/results` endpoint line, and update the start bullet to note auto-scoring. Add after the endpoint list:

```markdown
- **Auto-scored from the live run (no manual entry).** The Stechen Serie is shot as its live
  `PlayInstance` on the range. `TiebreakerService.listTies` / `listTiebreakers` reconcile on read:
  a round whose linked instance is `completed` has its per-player scores derived from the run's
  block result, written to `resultsJson`, and marked `COMPLETED` (auto-resolve). For an ACTIVE
  round, the response carries `blockId` + `run` (an `EmbeddedSerie`) so the range kiosk can
  surface and play it. The `submitTiebreakerResults` endpoint was removed. `PlayerResult` is
  never touched.
```

- [ ] **Step 2: Update frontend `CLAUDE.md`**

In the competition/Stechen-relevant notes, add a short bullet (e.g. under "Partially Implemented" or a Stechen note):

```markdown
- ✅ **Stechen auto-scoring**: The Stechen Serie is shot on the range kiosk (a "Stechen" section
  in `ShooterFlyoutPanel`, surfaced via `competitionEventStore.getActiveStechenForRange`). On
  completion the live run is scored automatically (backend reconcile-on-read); `StechenPanel`
  light-polls ties and shows a live status — manual score entry was removed.
```

- [ ] **Step 3: Commit**

```bash
cd "C:/Users/hynre/IdeaProjects/Smart Ground"
git add smart-ground-backend/CLAUDE.md smart-ground-ui/CLAUDE.md
git commit -m "[backend][ui] Document Stechen live-run auto-scoring"
```

---

## Final verification

- [ ] Backend: `cd smart-ground-backend && ./mvnw clean test` → BUILD SUCCESS.
- [ ] Frontend: `cd smart-ground-ui && npm run test && npm run lint:check && npm run build` → all green.
- [ ] Manual flow (Task 6) confirmed: live run auto-resolves the tie; `PlayerResult` untouched.

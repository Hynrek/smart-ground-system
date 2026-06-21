# Position Label Resolution — Group 3: Snapshot resolved labels into completed results

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a competition Serie is finalized, freeze its **resolved** definition (serie name, range name, per-step letters) onto the `CompetitionSerieResult` row, so a later position rename never rewrites a finished scorecard. The completed-results read path returns this frozen snapshot instead of joining live Serie definitions.

**Architecture:** A new nullable `serie_snapshot_json` column on `CompetitionSerieResult` holds a `SerieSnapshotRecord` = `{ serieName, rangeName, steps:[StepRecord] }`. `CompetitionProgressService` writes the snapshot at the moment a Serie is recorded done (`completeSerie`) and re-writes it on admin correction (`correctSerieResult`, still in `PRE_COMPLETE`), resolving step labels via `PositionLabelResolver`. The handwritten read DTO `CompetitionSerieResultDetailResponse` gains a `serieSnapshot` field returned by `getSerieResults`. Resolution is centralised: `PositionLabelResolver` gains a reusable `resolveStep` / `resolveSteps` (StepRecord-level), which `PasseService` (Group 2) also delegates to.

**Tech Stack:** Java 25, Spring Boot 4, JPA, JUnit 5 + Mockito + AssertJ. Sibling repo: `../smart-ground-backend`.

**Spec:** `docs/superpowers/specs/2026-06-19-position-label-resolution-design.md` (Group 3).

**Settled open question:** The snapshot lives in a **dedicated `serie_snapshot_json` column** (not embedded in the `results` scoring blob) — confirmed with the user.

---

## Build / environment notes (Windows)

- Run from **PowerShell**, in `../smart-ground-backend`. `JAVA_HOME=C:\Users\hynre\.jdks\jbr-25.0.2`.
- Use system `mvn` (3.9.x); the `./mvnw` wrapper is a broken stub — do NOT use it.
- Quote `-Dtest` lists in PowerShell: `mvn -q "-Dtest=FooTest,BarTest" test` (the comma is otherwise parsed as an argument separator). Single class needs no quotes.
- Do **not** pipe `2>&1` on `mvn` in PowerShell 5.1. Trust the process exit code.
- `StepRecord` (`ch.jp.shooting.dto.play.StepRecord`) is the 11-arg record `(id, type, posId, alias, posId1, posId2, alias1, alias2, letter, letter1, letter2)`. `@JsonInclude(NON_NULL)`.

## Scope notes (deliberate boundaries)

- **Snapshot is taken per row at completion time** (`completeSerie`), and re-taken on `correctSerieResult` (PRE_COMPLETE). By the time the session reaches `COMPLETED`, every row already carries a snapshot frozen as of when that Serie finished. This satisfies the spec's isolation requirement (a rename *after* finish never touches a frozen row). We deliberately do **not** add a separate "re-resolve all rows at the COMPLETED transition" pass — it would re-read live positions at a later instant for no benefit and add a second write path.
- **Frontend consumption is Group 4.** This group only produces the snapshot and exposes it on the read DTO; `useCompletedResults` still joins `serieDefs` today and is switched to the snapshot in Group 4.
- **The `getSerieResults` endpoint is intentionally outside the OpenAPI contract.** `CompetitionController` already uses raw `@GetMapping` with the handwritten `CompetitionSerieResultDetailResponse` DTO (Jackson-serialised). This group follows that existing pattern — no `openapi.yaml` change, no `generate-sources`. (The contract-first rule is not retrofitted here; that would be an unrelated refactor.)

---

## File Structure

**Create:**
- `src/main/java/ch/jp/shooting/dto/play/SerieSnapshotRecord.java` — the frozen serie definition `{serieName, rangeName, steps}`.

**Modify:**
- `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java` — add reusable `resolveStep(StepRecord, Map)` + `resolveSteps(List<StepRecord>)`.
- `src/main/java/ch/jp/shooting/service/PasseService.java` — delegate its private `applyLabels` to `PositionLabelResolver.resolveStep`.
- `src/main/java/ch/jp/shooting/model/CompetitionSerieResult.java` — add `serie_snapshot_json` column + accessors.
- `src/main/java/ch/jp/shooting/service/CompetitionProgressService.java` — inject `SerieRepository` + `PositionLabelResolver`; write the snapshot in `completeSerie` and `correctSerieResult`.
- `src/main/java/ch/jp/shooting/dto/CompetitionSerieResultDetailResponse.java` — add `serieSnapshot` field.
- `src/main/java/ch/jp/shooting/service/CompetitionService.java` — populate `serieSnapshot` in `getSerieResults`.
- `CLAUDE.md` — document the completion snapshot.

**Test (modify):**
- `src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java`
- `src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java`
- `src/test/java/ch/jp/shooting/service/CompetitionServiceSerieResultsTest.java`

---

### Task 1: Reusable StepRecord-level resolution on `PositionLabelResolver`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java`
- Modify: `src/main/java/ch/jp/shooting/service/PasseService.java`
- Test: `src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java`

Rationale: Group 2 left the StepRecord resolution (`applyLabels`) private in `PasseService`. Group 3's snapshot needs the same per-step resolution for a single Serie. Promote it to `PositionLabelResolver` (the resolution hub) so both reuse one implementation.

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java`. (`StepRecord` import: `ch.jp.shooting.dto.play.StepRecord`.)

```java
    @Test
    void resolveSteps_overridesLetterAndAliasFromCurrentPositions() {
        var posId = UUID.randomUUID();
        when(positionRepository.findAllById(anyIterable()))
            .thenReturn(List.of(position(posId, "C3")));

        var stale = new ch.jp.shooting.dto.play.StepRecord(
            "1", "solo", posId.toString(), "STALE_ALIAS",
            null, null, null, null, "OLD", null, null);

        var resolved = resolver.resolveSteps(List.of(stale));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).letter()).isEqualTo("C3");
        assertThat(resolved.get(0).alias()).isEqualTo("C3"); // no device -> alias falls back to label
        assertThat(resolved.get(0).posId()).isEqualTo(posId.toString()); // posId preserved
    }

    @Test
    void resolveSteps_deletedPosition_yieldsNullLetterAndAlias() {
        var posId = UUID.randomUUID();
        // repository returns nothing -> position deleted
        var stale = new ch.jp.shooting.dto.play.StepRecord(
            "1", "solo", posId.toString(), "STALE",
            null, null, null, null, "OLD", null, null);

        var resolved = resolver.resolveSteps(List.of(stale));

        assertThat(resolved.get(0).letter()).isNull();
        assertThat(resolved.get(0).alias()).isNull();
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -Dtest=PositionLabelResolverTest test`
Expected: FAIL — `resolver.resolveSteps` does not exist (compilation error).

- [ ] **Step 3: Add the resolution methods**

In `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java`, add imports:

```java
import ch.jp.shooting.dto.play.StepRecord;
import java.util.List;
import java.util.stream.Stream;
```

Add these methods to the class body:

```java
    /**
     * Löst die Positions-Labels einer Step-Liste in EINEM Lookup auf und gibt neue
     * StepRecords mit aktualisierten letter/alias-Werten zurück (posId/type bleiben erhalten).
     */
    public List<StepRecord> resolveSteps(List<StepRecord> steps) {
        var posIds = steps.stream()
            .flatMap(s -> Stream.of(s.posId(), s.posId1(), s.posId2()))
            .filter(Objects::nonNull)
            .toList();
        var positions = byPosIds(posIds);
        return steps.stream().map(s -> resolveStep(s, positions)).toList();
    }

    /** Erzeugt einen Step mit live aufgelösten letter/alias-Werten aus der gegebenen Positions-Map. */
    public static StepRecord resolveStep(StepRecord s, Map<String, RangePosition> positions) {
        var p  = s.posId()  != null ? positions.get(s.posId())  : null;
        var p1 = s.posId1() != null ? positions.get(s.posId1()) : null;
        var p2 = s.posId2() != null ? positions.get(s.posId2()) : null;
        return new StepRecord(
            s.id(),
            s.type(),
            s.posId(),
            p  != null ? aliasOf(p)  : null,
            s.posId1(),
            s.posId2(),
            p1 != null ? aliasOf(p1) : null,
            p2 != null ? aliasOf(p2) : null,
            p  != null ? p.getLabel()  : null,
            p1 != null ? p1.getLabel() : null,
            p2 != null ? p2.getLabel() : null
        );
    }
```

- [ ] **Step 4: Delegate from `PasseService`**

In `src/main/java/ch/jp/shooting/service/PasseService.java`, delete the private `applyLabels(StepRecord, Map)` method and replace its single call site (inside `resolveLiveSerien`):

```java
            var resolved = stepsBySerie.get(id).stream()
                .map(step -> PositionLabelResolver.resolveStep(step, positions))
                .toList();
```

(Remove the now-unused imports if `RangePosition` / `Stream` become unused — verify after editing; `RangePosition` is still referenced via the `Map<String, RangePosition> positions` local, so it stays.)

- [ ] **Step 5: Run resolver + Passe tests**

Run: `mvn -q "-Dtest=PositionLabelResolverTest,PasseServiceTest" test`
Expected: PASS — resolver gains 2 tests; PasseService behaviour unchanged (delegation only).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/PositionLabelResolver.java \
        src/main/java/ch/jp/shooting/service/PasseService.java \
        src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java
git commit -m "[backend] add reusable StepRecord resolution to PositionLabelResolver

resolveSteps/resolveStep centralise per-step label resolution; PasseService
delegates its private applyLabels to it. Reused by the completion snapshot.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Snapshot the resolved Serie onto `CompetitionSerieResult` at completion

**Files:**
- Create: `src/main/java/ch/jp/shooting/dto/play/SerieSnapshotRecord.java`
- Modify: `src/main/java/ch/jp/shooting/model/CompetitionSerieResult.java`
- Modify: `src/main/java/ch/jp/shooting/service/CompetitionProgressService.java`
- Test: `src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java`

- [ ] **Step 1: Create the snapshot record**

Create `src/main/java/ch/jp/shooting/dto/play/SerieSnapshotRecord.java`:

```java
package ch.jp.shooting.dto.play;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Eingefrorene Serie-Definition zum Abschlusszeitpunkt eines Wettkampf-Serie-Ergebnisses.
 * Hält die zur Anzeige aufgelösten Werte (Serie-Name, Platz-Name, Step-Buchstaben),
 * damit ein späteres Umbenennen einer Position eine abgeschlossene Wertung nicht verändert.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SerieSnapshotRecord(
    String serieName,
    @Nullable String rangeName,
    List<StepRecord> steps
) {}
```

- [ ] **Step 2: Add the column to `CompetitionSerieResult`**

In `src/main/java/ch/jp/shooting/model/CompetitionSerieResult.java`, add a field after `results` and accessors after `setResults`:

```java
    /**
     * Eingefrorene, aufgelöste Serie-Definition zum Abschlusszeitpunkt (SerieSnapshotRecord als JSON).
     * Quelle der Wahrheit für die Anzeige abgeschlossener Wertungen – nie live nachgeladen.
     */
    @Column(name = "serie_snapshot_json", columnDefinition = "TEXT")
    @Nullable
    private String serieSnapshotJson;
```

```java
    @Nullable public String getSerieSnapshotJson() { return serieSnapshotJson; }
    public void setSerieSnapshotJson(@Nullable String serieSnapshotJson) { this.serieSnapshotJson = serieSnapshotJson; }
```

- [ ] **Step 3: Write the failing test**

Add to `src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java`. Add the two new `@Mock` fields next to the existing ones:

```java
    @Mock SerieRepository serieRepository;
    @Mock PositionLabelResolver positionLabelResolver;
```

(`@InjectMocks` will pass them to the extended constructor.) Add imports `ch.jp.shooting.dto.play.SerieSnapshotRecord`, `ch.jp.shooting.dto.play.StepRecord`, `ch.jp.shooting.model.Serie`, `ch.jp.shooting.model.RangePosition`. Then add this test:

```java
    @Test
    void completeSerie_freezesResolvedSerieSnapshotOnRow() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
            any(), any(), anyInt(), any())).thenReturn(false);
        when(csrRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of());

        // Live serie with one solo step carrying a stale letter
        var serie = new Serie();
        serie.setId(serieId);
        serie.setName("Serie 1");
        serie.setOwnership("user");
        serie.setStepsJson(
            "[{\"id\":\"1\",\"type\":\"solo\",\"posId\":\"p-1\",\"letter\":\"OLD\"}]");
        when(serieRepository.findById(serieId)).thenReturn(Optional.of(serie));
        when(positionLabelResolver.resolveSteps(anyList())).thenReturn(List.of(
            new StepRecord("1", "solo", "p-1", "A1", null, null, null, null, "A1", null, null)));

        var captor = org.mockito.ArgumentCaptor.forClass(CompetitionSerieResult.class);

        CompleteSerieRequest req = new CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));
        req.setResults(List.of());
        progressService.completeSerie(sessionId, groupId, serieId, req);

        verify(csrRepository).save(captor.capture());
        String json = captor.getValue().getSerieSnapshotJson();
        assertNotNull(json);
        SerieSnapshotRecord snapshot = objectMapper.readValue(json, SerieSnapshotRecord.class);
        assertEquals("Serie 1", snapshot.serieName());
        assertEquals(1, snapshot.steps().size());
        assertEquals("A1", snapshot.steps().get(0).letter()); // resolved, not "OLD"
    }
```

> Note: the existing `completeSerie_insertsRow` test does not stub `serieRepository.findById` → it returns `Optional.empty()` → snapshot stays null → that test is unaffected.

- [ ] **Step 4: Run to verify failure**

Run: `mvn -Dtest=CompetitionProgressServiceTest test`
Expected: FAIL — constructor arity mismatch (new mocks) and `getSerieSnapshotJson()` returns null because the snapshot is not written yet.

- [ ] **Step 5: Wire the snapshot into `CompetitionProgressService`**

In `src/main/java/ch/jp/shooting/service/CompetitionProgressService.java`:

(a) Add imports:

```java
import ch.jp.shooting.dto.play.SerieSnapshotRecord;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.repository.SerieRepository;
```

(b) Add two fields and extend the constructor:

```java
    private final SerieRepository serieRepository;
    private final PositionLabelResolver positionLabelResolver;
```

```java
    public CompetitionProgressService(
            CompetitionSerieResultRepository csrRepository,
            LiveSessionRepository sessionRepository,
            ShooterGroupRepository groupRepository,
            PlayerResultRepository playerResultRepository,
            ObjectMapper objectMapper,
            SerieRepository serieRepository,
            PositionLabelResolver positionLabelResolver) {
        this.csrRepository = csrRepository;
        this.sessionRepository = sessionRepository;
        this.groupRepository = groupRepository;
        this.playerResultRepository = playerResultRepository;
        this.objectMapper = objectMapper;
        this.serieRepository = serieRepository;
        this.positionLabelResolver = positionLabelResolver;
    }
```

(c) In `completeSerie`, set the snapshot right after `csr.setPlayInstanceId(playInstanceId);` and before the `results` block (so it is set whether or not results are present):

```java
        csr.setSerieSnapshotJson(buildSerieSnapshotJson(serieId));
```

(d) In `correctSerieResult`, re-freeze the snapshot when results are corrected — add right after the existing `csr.setResults(...)` line:

```java
        csr.setSerieSnapshotJson(buildSerieSnapshotJson(serieId));
```

(e) Add the private helper at the end of the class:

```java
    /**
     * Baut die eingefrorene, aufgelöste Serie-Definition (Name, Platz, Step-Buchstaben)
     * zum Abschlusszeitpunkt. Gibt null zurück, wenn die Serie nicht (mehr) existiert.
     */
    @org.jspecify.annotations.Nullable
    private String buildSerieSnapshotJson(UUID serieId) throws Exception {
        Serie serie = serieRepository.findById(serieId).orElse(null);
        if (serie == null) return null;
        var resolvedSteps = positionLabelResolver.resolveSteps(
            ch.jp.shooting.mapper.PlayMapper.parseSteps(serie.getStepsJson()));
        var snapshot = new SerieSnapshotRecord(
            serie.getName(),
            serie.getRange() != null ? serie.getRange().getName() : null,
            resolvedSteps);
        return objectMapper.writeValueAsString(snapshot);
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -Dtest=CompetitionProgressServiceTest test`
Expected: PASS — including the pre-existing tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ch/jp/shooting/dto/play/SerieSnapshotRecord.java \
        src/main/java/ch/jp/shooting/model/CompetitionSerieResult.java \
        src/main/java/ch/jp/shooting/service/CompetitionProgressService.java \
        src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java
git commit -m "[backend] freeze resolved Serie snapshot on completion

CompetitionSerieResult gains serie_snapshot_json; CompetitionProgressService
writes a {serieName, rangeName, resolved steps} snapshot when a Serie is
completed (and re-writes it on PRE_COMPLETE correction), via PositionLabelResolver.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Return the frozen snapshot on the completed-results read path

**Files:**
- Modify: `src/main/java/ch/jp/shooting/dto/CompetitionSerieResultDetailResponse.java`
- Modify: `src/main/java/ch/jp/shooting/service/CompetitionService.java`
- Test: `src/test/java/ch/jp/shooting/service/CompetitionServiceSerieResultsTest.java`

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/ch/jp/shooting/service/CompetitionServiceSerieResultsTest.java`:

```java
    @Test
    void getSerieResults_returnsFrozenSerieSnapshot() throws Exception {
        when(sessionRepository.existsById(sessionId)).thenReturn(true);

        LiveSession session = new LiveSession(SessionType.COMPETITION, SessionStatus.COMPLETED);
        ShooterGroup group = new ShooterGroup();
        group.setId(UUID.randomUUID());
        UUID serieId = UUID.randomUUID();

        CompetitionSerieResult csr = new CompetitionSerieResult(session, group, 0, serieId);
        csr.setSerieSnapshotJson(
            "{\"serieName\":\"Serie 1\",\"rangeName\":\"Stand 1\","
            + "\"steps\":[{\"id\":\"1\",\"type\":\"solo\",\"letter\":\"A1\"}]}");
        when(serieResultRepository.findBySessionId(sessionId)).thenReturn(List.of(csr));

        List<CompetitionSerieResultDetailResponse> out = service.getSerieResults(sessionId);

        assertEquals(1, out.size());
        CompetitionSerieResultDetailResponse dto = out.get(0);
        assertNotNull(dto.serieSnapshot);
        assertEquals("Serie 1", dto.serieSnapshot.get("serieName").asText());
        assertEquals("A1", dto.serieSnapshot.get("steps").get(0).get("letter").asText());
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -Dtest=CompetitionServiceSerieResultsTest test`
Expected: FAIL — `CompetitionSerieResultDetailResponse` has no `serieSnapshot` field (compilation error).

- [ ] **Step 3: Add the field to the DTO**

In `src/main/java/ch/jp/shooting/dto/CompetitionSerieResultDetailResponse.java`, add after the `results` field:

```java
    /** Eingefrorene, aufgelöste Serie-Definition zum Abschlusszeitpunkt (Name, Platz, Step-Buchstaben). */
    @JsonProperty("serieSnapshot") @Nullable public JsonNode serieSnapshot;
```

- [ ] **Step 4: Populate it in `getSerieResults`**

In `src/main/java/ch/jp/shooting/service/CompetitionService.java`, inside the loop in `getSerieResults`, after the `results` parsing block:

```java
            String snapshotRaw = csr.getSerieSnapshotJson();
            if (snapshotRaw != null && !snapshotRaw.isBlank()) {
                dto.serieSnapshot = objectMapper.readTree(snapshotRaw);
            }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -Dtest=CompetitionServiceSerieResultsTest test`
Expected: PASS (3 tests).

- [ ] **Step 6: Run the full backend suite**

Run: `mvn test`
Expected: PASS — confirms the new `CompetitionProgressService` constructor arg is satisfied everywhere it is wired (Spring `@SpringBootTest` contexts and any manual construction) and no other consumer broke.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ch/jp/shooting/dto/CompetitionSerieResultDetailResponse.java \
        src/main/java/ch/jp/shooting/service/CompetitionService.java \
        src/test/java/ch/jp/shooting/service/CompetitionServiceSerieResultsTest.java
git commit -m "[backend] return frozen serie snapshot on completed-results read path

getSerieResults now exposes serieSnapshot (frozen serieName/rangeName/step
letters) on CompetitionSerieResultDetailResponse; the frontend will read this
instead of joining live serie defs (Group 4).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Record the design decision in backend CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Document the completion snapshot**

In `CLAUDE.md`, under the **Serie / Passe / Training / Play tables** schema block, change the (currently absent) `competition_serie_results` note — locate the Competition/Session tables list and add/adjust a line so it reads:

`competition_serie_results  id, session_id, group_id, passe_index, serie_id, play_instance_id?, results TEXT?, serie_snapshot_json TEXT?, completed_at  UNIQUE(session_id, group_id, passe_index, serie_id)`

(If that table is not yet listed there, add it under the Competition/Session tables section.)

Then add a short decision note near the Competition/Session domain section:

> **Completed-result label snapshot (Group 3):** When a competition Serie is completed (`CompetitionProgressService.completeSerie`, re-done on `correctSerieResult` in PRE_COMPLETE), its resolved definition — serie name, range name, per-step letters resolved via `PositionLabelResolver` — is frozen into `CompetitionSerieResult.serie_snapshot_json` (`SerieSnapshotRecord`). The completed-results read path (`getSerieResults`) returns this snapshot and never joins the live Serie, so renaming a position after a competition finishes does not rewrite a finished scorecard.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "[backend] document completion label snapshot (Group 3)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Manual verification (end of Group 3)

1. Start backend (`mvn spring-boot:run -Dspring-boot.run.profiles=postgres`) — Hibernate adds `serie_snapshot_json`.
2. Run a competition Serie to completion (or via the kiosk flow) for a group on a range with position `A`.
3. Inspect the `competition_serie_results` row: `serie_snapshot_json` holds `{serieName, rangeName, steps:[…letter:"A"…]}`.
4. `GET /api/sessions/{id}/serie-results` → each item carries `serieSnapshot` with the frozen letters.
5. Rename position `A` → `A1`. Re-fetch serie-results → `serieSnapshot` still shows `A` (frozen at completion), proving isolation.

---

## Self-review checklist (to complete during execution)

- **Spec coverage (Group 3):**
  - Snapshot `{rangeName, serieName, per-step letters}` persisted at finalization ✅ (Task 2).
  - Read path returns the frozen snapshot, never joins live ✅ (Task 3).
  - `PositionLabelResolver` reused for posId→label ✅ (Task 1 + Task 2).
- **Placeholders:** none — every step has concrete code/commands.
- **Type consistency:** `SerieSnapshotRecord(serieName, rangeName, steps:List<StepRecord>)` written by `buildSerieSnapshotJson`, read by the test via `objectMapper.readValue`; `PositionLabelResolver.resolveSteps(List<StepRecord>)` returns `List<StepRecord>`, consumed identically by `PasseService` (via `resolveStep`) and `CompetitionProgressService`; `serieSnapshot` is a `JsonNode` on the handwritten DTO, mirroring the existing `results` field.
- **Out of scope (later groups):** frontend reading the snapshot + dropping `serieDefs` join, in-place `updateSerie` PUT wiring (Group 4); migration/reset + cross-stack propagation/isolation tests (Group 5).
- **Pre-execution reads to confirm:** `CompetitionProgressService` field/ctor ordering (to place the new params cleanly) and that `objectMapper` is already a field there (it is, set via reflection in the test).
</content>

# Serie Scoring — Kind + Level-Scoped Drill-Down Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist standalone Serie runs, tag every score with a structural `kind` (SERIE | PASSE | COMPETITION), and rework the shooter's score view into three level-scoped drill-down tabs ending at the per-clay breakdown.

**Architecture:** Add `PlayInstance.type = "serie"` (and `"stechen"` for tiebreakers) so a standalone Serie is a first-class instance; carry a derived `kind` onto the existing `UserSerieScore` projection; expose a `POST /play-instances/serie` endpoint plus two grouped read endpoints (`/users/me/passen`, `/users/me/wettkaempfe`) that embed their child Serien; on the frontend, route the currently-ephemeral solo/group Serie play through a real instance (gated by a "Punkte aufnehmen?" modal with QR impersonation) and rebuild the Ergebnisse tab as accordions reusing `StepScorecard`.

**Tech Stack:** Spring Boot 4 / Java 25 / JPA (Hibernate auto-DDL, pre-v1.0), OpenAPI generator, JUnit 5 + Mockito; Vue 3 `<script setup>`, Pinia 3, Vitest 4.

**Spec:** `docs/superpowers/specs/2026-07-07-serie-scoring-drilldown-design.md`.

## Global Constraints

- Backend build/tests: use system `mvn` from `smart-ground-backend/` (the `mvnw` wrapper is broken — never call `./mvnw`).
- API changes: edit `src/main/resources/static/openapi.yaml` FIRST, then `mvn generate-sources`, then implement. Never edit generated files under `ch.jp.smartground.*`.
- Every controller implements a generated interface; no `@RequestMapping`/`@GetMapping`/etc. on controller classes or methods.
- Backend domain comments in German; test code and frontend comments in English.
- `@NullMarked` on all new Java classes; `@Nullable` where appropriate. JSON columns use `TEXT` (never JSONB). UUID PK via `@GeneratedValue(strategy = GenerationType.UUID)`.
- UI: no direct API calls in components (`store → service` chain); `@/` import alias; design tokens `--sg-*` only; German display labels, English identifiers/comments; touch targets ≥48px in shooter views; step rendering imports from `constants/stepModes.js`.
- Remove replaced code eagerly (backend + UI).
- Commit prefix: `[backend]` / `[ui]` per sub-project.
- All paths below are relative to the monorepo root `C:\Users\hynre\IdeaProjects\Smart Ground\`.

---

### Task 1: Backend — `kind` column + repository queries

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/model/UserSerieScore.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/repository/UserSerieScoreRepository.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/repository/UserSerieScoreRepositoryTest.java` (extend)

**Interfaces:**
- Produces: `UserSerieScore.getKind()/setKind(String)` (values `SERIE`|`PASSE`|`COMPETITION`); repository methods `findByUserIdAndKindOrderByCompletedAtDesc(UUID, String)`, and a reused `findByUserIdOrderByCompletedAtDesc(UUID)` (already exists) for the grouped endpoints.

- [ ] **Step 1: Write the failing repository test**

Append to `UserSerieScoreRepositoryTest`. Reuse the existing `score(...)` helper in that class but set a kind via the new setter; add this test:

```java
    @Test
    void findByUserIdAndKind_returnsOnlyThatKind() {
        var userId = java.util.UUID.randomUUID();
        var now = java.time.Instant.now();
        var serie = score(userId, java.util.UUID.randomUUID(), "TRAINING", 5, now);
        serie.setKind("SERIE");
        var passe = score(userId, java.util.UUID.randomUUID(), "TRAINING", 7, now.minusSeconds(60));
        passe.setKind("PASSE");
        repository.save(serie);
        repository.save(passe);

        var serien = repository.findByUserIdAndKindOrderByCompletedAtDesc(userId, "SERIE");
        assertEquals(1, serien.size());
        assertEquals("SERIE", serien.get(0).getKind());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run from `smart-ground-backend/`: `mvn test -Dtest=UserSerieScoreRepositoryTest`
Expected: COMPILE ERROR — `setKind` / `findByUserIdAndKindOrderByCompletedAtDesc` do not exist.

- [ ] **Step 3: Add the `kind` field to the entity**

In `UserSerieScore.java`, add the column next to `context` and add an index. Add after the `context` field:

```java
    /** Strukturelle Kategorie: 'SERIE' | 'PASSE' | 'COMPETITION'. Feiner als context. */
    @Column(nullable = false, length = 12)
    private String kind = "PASSE";
```

Add getter/setter with the others:

```java
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
```

Add the index to the existing `@Table(indexes = {...})` list:

```java
        @Index(name = "idx_uss_user_kind_completed", columnList = "user_id, kind, completed_at DESC")
```

- [ ] **Step 4: Add the repository query**

In `UserSerieScoreRepository.java` add:

```java
    List<UserSerieScore> findByUserIdAndKindOrderByCompletedAtDesc(UUID userId, String kind);
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=UserSerieScoreRepositoryTest`
Expected: PASS (all existing + the new test).

- [ ] **Step 6: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/model/UserSerieScore.java smart-ground-backend/src/main/java/ch/jp/shooting/repository/UserSerieScoreRepository.java smart-ground-backend/src/test/java/ch/jp/shooting/repository/UserSerieScoreRepositoryTest.java
git commit -m "[backend] add structural kind to UserSerieScore projection"
```

---

### Task 2: Backend — `PlayInstance.type` = serie/stechen + kind derivation

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/PlayInstanceService.java` (`buildAndSaveInstance`, `startSerieInstance`; new `startSerieInstance` overload with type)
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/TiebreakerService.java` (pass `"stechen"`)
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/UserScoreService.java` (`recordTrainingInstance` — derive kind, skip non serie/passe)
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/UserScoreServiceTest.java` (extend)

**Interfaces:**
- Consumes: `PlayInstance.getType()` returning `"passe"` | `"serie"` | `"stechen"`.
- Produces: `PlayInstanceService.startSerieInstance(UUID, String, String, List<PlayerRef>, String type)` (new arg); `recordTrainingInstance` writes `kind = SERIE` for a serie instance, `PASSE` for a passe instance, and writes **no row** for a `stechen` instance.

- [ ] **Step 1: Add failing tests to `UserScoreServiceTest`**

The existing `completedInstance(blockId, userId)` helper sets `inst.setType("passe")`. Add a parameterized variant and three tests. Add near the training helpers:

```java
    private PlayInstance completedInstanceOfType(java.util.UUID blockId, java.util.UUID userId, String type) {
        var inst = completedInstance(blockId, userId);
        inst.setType(type);
        return inst;
    }

    @Test
    void recordTrainingInstance_serieType_writesSerieKind() {
        var blockId = java.util.UUID.randomUUID();
        var userId = java.util.UUID.randomUUID();
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(java.util.Optional.empty());

        service().recordTrainingInstance(completedInstanceOfType(blockId, userId, "serie"));

        var captor = org.mockito.ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository).save(captor.capture());
        assertEquals("SERIE", captor.getValue().getKind());
    }

    @Test
    void recordTrainingInstance_passeType_writesPasseKind() {
        var blockId = java.util.UUID.randomUUID();
        var userId = java.util.UUID.randomUUID();
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(java.util.Optional.empty());

        service().recordTrainingInstance(completedInstanceOfType(blockId, userId, "passe"));

        var captor = org.mockito.ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository).save(captor.capture());
        assertEquals("PASSE", captor.getValue().getKind());
    }

    @Test
    void recordTrainingInstance_stechenType_writesNothing() {
        var blockId = java.util.UUID.randomUUID();
        var userId = java.util.UUID.randomUUID();

        service().recordTrainingInstance(completedInstanceOfType(blockId, userId, "stechen"));

        verify(scoreRepository, never()).save(any());
    }
```

Also update the existing competition test to assert kind: in `recordCompetitionSerie_resolvesUserViaGroupMember`, add `assertEquals("COMPETITION", row.getKind());` after the existing `row` assertions.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=UserScoreServiceTest`
Expected: FAIL — `getKind()` returns the default `"PASSE"` for the serie case and the stechen case still saves a row.

- [ ] **Step 3: Guard + derive kind in `recordTrainingInstance`**

In `UserScoreService.recordTrainingInstance`, at the very top of the method add the guard, and set the kind per row. Replace the method's opening and the `row.setContext("TRAINING");` line region:

```java
    public void recordTrainingInstance(PlayInstance instance) {
        // Stechen-Instanzen sind Wettkampf-Tiebreaker, keine Trainings-Serien — nie ins Score-History schreiben.
        var type = instance.getType();
        if (!"serie".equals(type) && !"passe".equals(type)) return;
        for (var block : PlayMapper.parseBlocks(instance.getStateJson())) {
            if (block.result() == null) continue;
            for (var pr : block.result().playerResults()) {
                if (pr.userId() == null) continue;
                var row = scoreRepository.findBySourceIdAndUserId(block.blockId(), pr.userId())
                    .orElseGet(UserSerieScore::new);
                row.setUserId(pr.userId());
                row.setContext("TRAINING");
                row.setKind("serie".equals(type) ? "SERIE" : "PASSE");
                // ... rest of the existing field-setting stays unchanged ...
```

Keep every other line of the loop body exactly as-is (only the guard and `row.setKind(...)` are added).

- [ ] **Step 4: Set kind in `recordCompetitionSerie`**

In `recordCompetitionSerie`, right after `row.setContext("COMPETITION");` add:

```java
            row.setKind("COMPETITION");
```

- [ ] **Step 5: Parameterize the instance type**

In `PlayInstanceService.java`, change `buildAndSaveInstance` to accept a type, and add a type param to `startSerieInstance`.

Replace the `startSerieInstance` signature body call and the `buildAndSaveInstance` signature + its `instance.setType("passe")`:

```java
    public PlayInstanceResponse startSerieInstance(UUID serieId, String serieName,
                                                   String serienSnapshotJson, List<PlayerRef> players,
                                                   String type) {
        var serien = PlayMapper.parseEmbeddedSerien(serienSnapshotJson);
        if (serien.size() != 1) {
            throw new IllegalArgumentException(
                "Serien-Snapshot für startSerieInstance muss genau eine Serie enthalten, enthält aber: " + serien.size());
        }
        return buildAndSaveInstance(serieId, serieName, serien, players, type);
    }
```

`startPasseInstance` already calls `buildAndSaveInstance(...)` — update that call to pass `"passe"` as the final arg. Update `buildAndSaveInstance`:

```java
    private PlayInstanceResponse buildAndSaveInstance(UUID templateId, String templateName,
                                                      List<EmbeddedSerieRecord> serien,
                                                      List<PlayerRef> players, String type) {
        // ... unchanged body ...
        instance.setType(type);   // was: instance.setType("passe");
        // ... unchanged body ...
    }
```

- [ ] **Step 6: Update the Stechen caller**

In `TiebreakerService.java`, the `startSerieInstance(serie.getId(), serie.getName(), snapshot, playerRefs)` call now needs the type. Change it to:

```java
        var instance = playInstanceService.startSerieInstance(
                serie.getId(), serie.getName(), snapshot, playerRefs, "stechen");
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn test -Dtest=UserScoreServiceTest,TiebreakerServiceTest,PlayInstanceServiceTest`
Expected: PASS. If `PlayInstanceServiceTest` or `TiebreakerServiceTest` call `startSerieInstance`/`buildAndSaveInstance` with the old arity, fix those call sites to pass the type (`"serie"` for a plain serie test, `"stechen"` for tiebreaker).

- [ ] **Step 8: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/service/PlayInstanceService.java smart-ground-backend/src/main/java/ch/jp/shooting/service/TiebreakerService.java smart-ground-backend/src/main/java/ch/jp/shooting/service/UserScoreService.java smart-ground-backend/src/test/java/ch/jp/shooting/service/UserScoreServiceTest.java
git commit -m "[backend] tag scores with kind; serie/stechen instance types; exclude stechen from history"
```

---

### Task 3: Backend — OpenAPI contract (serie endpoint, kind, grouped read endpoints)

**Files:**
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml`

**Interfaces:**
- Produces (after `mvn generate-sources`): `StartSerieInstanceRequest { serieId: UUID, players: PlayerRef[] }` on `PlayInstanceApi.startSerieInstance`; `ScoreKind` enum; `kind` on `UserSerieScoreEntry`; `PasseScoreGroup`, `WettkampfScoreGroup`, `WettkampfPasseGroup` schemas; `ScoreApi.listMyPassen()` → `List<PasseScoreGroup>`, `ScoreApi.listMyWettkaempfe()` → `List<WettkampfScoreGroup>`; `kind` query param on `listMyScores` and `getScoreLeaderboard`.
- Removes: `passen`/`wettkaempfe` arrays from `UserScoreSummary` (superseded); the generated `GroupedScoreSummary` schema if now unused.

- [ ] **Step 1: Add the `serie` play-instance endpoint**

In `openapi.yaml`, find the `/api/play-instances/passe` path and add a sibling immediately after it:

```yaml
  /api/play-instances/serie:
    post:
      summary: Start a standalone Serie as a live play instance
      operationId: startSerieInstance
      tags: [PlayInstance]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/StartSerieInstanceRequest'
      responses:
        '201':
          description: Created serie instance
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PlayInstanceResponse'
```

Add the request schema next to `StartPasseInstanceRequest` (copy its shape, swap `passeId`→`serieId`):

```yaml
    StartSerieInstanceRequest:
      type: object
      required: [serieId, players]
      properties:
        serieId: { type: string, format: uuid }
        players:
          type: array
          items:
            $ref: '#/components/schemas/PlayerRef'
```

- [ ] **Step 2: Add `ScoreKind` and put `kind` on the entry + params**

Add the enum near `ScoreContext`:

```yaml
    ScoreKind:
      type: string
      enum: [SERIE, PASSE, COMPETITION]
```

Add to `UserSerieScoreEntry.properties`:

```yaml
        kind:
          $ref: '#/components/schemas/ScoreKind'
```

Add a `kind` query parameter to both `listMyScores` and `getScoreLeaderboard` (alongside their existing `context` param):

```yaml
        - name: kind
          in: query
          required: false
          schema:
            $ref: '#/components/schemas/ScoreKind'
```

- [ ] **Step 3: Add the two grouped endpoints**

After the `/api/users/me/scores/summary` path, add:

```yaml
  /api/users/me/passen:
    get:
      summary: The caller's training Passen with their child Serien
      operationId: listMyPassen
      tags: [Score]
      responses:
        '200':
          description: Passe groups, newest first
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/PasseScoreGroup'

  /api/users/me/wettkaempfe:
    get:
      summary: The caller's Wettkämpfe, nested Passe → Serie
      operationId: listMyWettkaempfe
      tags: [Score]
      responses:
        '200':
          description: Wettkampf groups, newest first
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/WettkampfScoreGroup'
```

- [ ] **Step 4: Add the group schemas; trim `UserScoreSummary`**

Add:

```yaml
    PasseScoreGroup:
      type: object
      properties:
        key: { type: string, format: uuid, description: playInstanceId }
        label: { type: string, nullable: true }
        serieCount: { type: integer }
        totalPoints: { type: integer }
        maxPoints: { type: integer }
        lastCompletedAt: { type: string, format: date-time }
        serien:
          type: array
          items:
            $ref: '#/components/schemas/UserSerieScoreEntry'

    WettkampfPasseGroup:
      type: object
      properties:
        passeIndex: { type: integer }
        totalPoints: { type: integer }
        maxPoints: { type: integer }
        serien:
          type: array
          items:
            $ref: '#/components/schemas/UserSerieScoreEntry'

    WettkampfScoreGroup:
      type: object
      properties:
        key: { type: string, format: uuid, description: sessionId }
        label: { type: string, nullable: true }
        serieCount: { type: integer }
        totalPoints: { type: integer }
        maxPoints: { type: integer }
        lastCompletedAt: { type: string, format: date-time }
        passen:
          type: array
          items:
            $ref: '#/components/schemas/WettkampfPasseGroup'
```

In `UserScoreSummary`, **remove** the `passen` and `wettkaempfe` array properties (keep only `contexts`). Then delete the now-unused `GroupedScoreSummary` schema.

- [ ] **Step 5: Generate and confirm**

Run: `mvn generate-sources`
Expected: SUCCESS; `StartSerieInstanceRequest`, `ScoreKind`, `PasseScoreGroup`, `WettkampfScoreGroup`, `WettkampfPasseGroup` appear under `target/generated-sources`; `PlayInstanceApi` gains `startSerieInstance`; `ScoreApi` gains `listMyPassen`/`listMyWettkaempfe`.

Run: `mvn test-compile`
Expected: FAIL in `ScoreController` (missing `listMyPassen`/`listMyWettkaempfe`), `PlayInstanceController` (missing `startSerieInstance`), and `UserScoreService.getMyScoreSummary` (references removed `.passen(...)`/`.wettkaempfe(...)` builders). Those are Task 4's surface.

- [ ] **Step 6: Commit (contract only)**

```bash
git add smart-ground-backend/src/main/resources/static/openapi.yaml
git commit -m "[backend] openapi: serie instance endpoint, score kind, grouped passe/wettkampf reads"
```

---

### Task 4: Backend — grouped aggregation + controller wiring

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/UserScoreService.java` (new `listMyPassen`, `listMyWettkaempfe`, `toEntry` reuse, trim `getMyScoreSummary`, add `kind` to `listMyScores`/leaderboard)
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/PlayInstanceService.java` (`startSerieInstance(StartSerieInstanceRequest)`)
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/PlayInstanceController.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/ScoreController.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/UserScoreServiceTest.java` (extend)

**Interfaces:**
- Consumes: `UserSerieScoreRepository.findByUserIdOrderByCompletedAtDesc`, `findByUserIdAndKindOrderByCompletedAtDesc`; generated group models from Task 3; `SerieRepository`/`PasseService` snapshot resolution used by `startPasseInstance` (mirror it).
- Produces: `UserScoreService.listMyPassen()`, `listMyWettkaempfe()`; `PlayInstanceService.startSerieInstance(StartSerieInstanceRequest)`; controllers implementing the new interface methods.

- [ ] **Step 1: Add failing service tests**

Add to `UserScoreServiceTest`, reusing the `row(...)` helper (extend it to set kind if needed):

```java
    @Test
    void listMyPassen_groupsTrainingPasseRowsByInstance() {
        var userId = java.util.UUID.randomUUID();
        var me = mock(ch.jp.shooting.model.auth.User.class);
        when(me.getId()).thenReturn(userId);
        when(securityHelper.currentUser()).thenReturn(me);
        var passeId = java.util.UUID.randomUUID();
        var now = java.time.Instant.now();
        var r1 = row(userId, "TRAINING", 8, 10, passeId, null, now); r1.setKind("PASSE");
        var r2 = row(userId, "TRAINING", 6, 10, passeId, null, now.minusSeconds(60)); r2.setKind("PASSE");
        var serieRow = row(userId, "TRAINING", 9, 10, null, null, now); serieRow.setKind("SERIE");
        when(scoreRepository.findByUserIdOrderByCompletedAtDesc(userId))
            .thenReturn(java.util.List.of(r1, r2, serieRow));

        var groups = service().listMyPassen();
        assertEquals(1, groups.size());
        assertEquals(passeId, groups.get(0).getKey());
        assertEquals(2, groups.get(0).getSerien().size());
        assertEquals(14, groups.get(0).getTotalPoints());
    }

    @Test
    void listMyWettkaempfe_nestsSessionPasseSerie() {
        var userId = java.util.UUID.randomUUID();
        var me = mock(ch.jp.shooting.model.auth.User.class);
        when(me.getId()).thenReturn(userId);
        when(securityHelper.currentUser()).thenReturn(me);
        var sessionId = java.util.UUID.randomUUID();
        var now = java.time.Instant.now();
        var c1 = row(userId, "COMPETITION", 7, 10, null, sessionId, now); c1.setKind("COMPETITION"); c1.setPasseIndex(1);
        var c2 = row(userId, "COMPETITION", 8, 10, null, sessionId, now); c2.setKind("COMPETITION"); c2.setPasseIndex(2);
        when(scoreRepository.findByUserIdOrderByCompletedAtDesc(userId))
            .thenReturn(java.util.List.of(c1, c2));

        var groups = service().listMyWettkaempfe();
        assertEquals(1, groups.size());
        assertEquals(2, groups.get(0).getPassen().size());
        assertEquals(15, groups.get(0).getTotalPoints());
    }
```

If the `row(...)` helper does not set `passeIndex`, set it on the returned object in the test as shown (`c1.setPasseIndex(1)`).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=UserScoreServiceTest`
Expected: COMPILE ERROR — `listMyPassen` / `listMyWettkaempfe` do not exist.

- [ ] **Step 3: Implement the grouped read methods**

In `UserScoreService`, reuse the existing private row→entry mapper (the one `listMyScores` already uses to build `UserSerieScoreEntry`; if it is inline, extract it to `private UserSerieScoreEntry toEntry(UserSerieScore s)` first and have `listMyScores` call it). Then add:

```java
    /** Trainings-Passen des aktuellen Users, gruppiert nach playInstanceId, mit Kind-Serien. */
    public java.util.List<ch.jp.smartground.model.PasseScoreGroup> listMyPassen() {
        var userId = securityHelper.currentUser().getId();
        var groups = new java.util.LinkedHashMap<UUID, ch.jp.smartground.model.PasseScoreGroup>();
        for (var s : scoreRepository.findByUserIdOrderByCompletedAtDesc(userId)) {
            if (!"PASSE".equals(s.getKind()) || s.getPlayInstanceId() == null) continue;
            var g = groups.computeIfAbsent(s.getPlayInstanceId(), k ->
                new ch.jp.smartground.model.PasseScoreGroup()
                    .key(k).label(s.getParentName())
                    .serieCount(0).totalPoints(0).maxPoints(0)
                    .lastCompletedAt(s.getCompletedAt().atOffset(java.time.ZoneOffset.UTC))
                    .serien(new java.util.ArrayList<>()));
            g.setSerieCount(g.getSerieCount() + 1);
            g.setTotalPoints(g.getTotalPoints() + s.getTotalPoints());
            g.setMaxPoints(g.getMaxPoints() + s.getMaxPoints());
            g.getSerien().add(toEntry(s));
        }
        return new java.util.ArrayList<>(groups.values());
    }

    /** Wettkämpfe des aktuellen Users: Session → Passe (passeIndex) → Serie. */
    public java.util.List<ch.jp.smartground.model.WettkampfScoreGroup> listMyWettkaempfe() {
        var userId = securityHelper.currentUser().getId();
        var sessions = new java.util.LinkedHashMap<UUID, ch.jp.smartground.model.WettkampfScoreGroup>();
        var passenBySession = new java.util.HashMap<UUID, java.util.LinkedHashMap<Integer, ch.jp.smartground.model.WettkampfPasseGroup>>();
        for (var s : scoreRepository.findByUserIdOrderByCompletedAtDesc(userId)) {
            if (!"COMPETITION".equals(s.getKind()) || s.getSessionId() == null) continue;
            var g = sessions.computeIfAbsent(s.getSessionId(), k ->
                new ch.jp.smartground.model.WettkampfScoreGroup()
                    .key(k).label(s.getParentName())
                    .serieCount(0).totalPoints(0).maxPoints(0)
                    .lastCompletedAt(s.getCompletedAt().atOffset(java.time.ZoneOffset.UTC))
                    .passen(new java.util.ArrayList<>()));
            g.setSerieCount(g.getSerieCount() + 1);
            g.setTotalPoints(g.getTotalPoints() + s.getTotalPoints());
            g.setMaxPoints(g.getMaxPoints() + s.getMaxPoints());
            int idx = s.getPasseIndex() != null ? s.getPasseIndex() : 0;
            var passeMap = passenBySession.computeIfAbsent(s.getSessionId(), k -> new java.util.LinkedHashMap<>());
            var pg = passeMap.computeIfAbsent(idx, k -> {
                var np = new ch.jp.smartground.model.WettkampfPasseGroup()
                    .passeIndex(k).totalPoints(0).maxPoints(0)
                    .serien(new java.util.ArrayList<>());
                g.getPassen().add(np);
                return np;
            });
            pg.setTotalPoints(pg.getTotalPoints() + s.getTotalPoints());
            pg.setMaxPoints(pg.getMaxPoints() + s.getMaxPoints());
            pg.getSerien().add(toEntry(s));
        }
        return new java.util.ArrayList<>(sessions.values());
    }
```

Set `kind` on the entry inside `toEntry`: add `.kind(ch.jp.smartground.model.ScoreKind.fromValue(s.getKind()))` to the entry builder.

Trim `getMyScoreSummary`: delete the code that builds the `.passen(...)` / `.wettkaempfe(...)` arrays and the now-unused local variables; keep only the per-context stats. Add the `kind` filter to `listMyScores` and `getLeaderboard`: thread a `@Nullable String kind` param through to the repository (extend `findFiltered`/`findForLeaderboard` with `and (:kind is null or s.kind = :kind)` following the exact pattern the existing `:context is null` checks use).

- [ ] **Step 4: Implement `startSerieInstance` on the service + controller**

In `PlayInstanceService`, add the request-level entry (mirror `startPasseInstance` — resolve the Serie to a one-element embedded snapshot the same way `startPasseInstance` resolves a Passe; if `startPasseInstance` uses `PasseService`/`SerieService` to build the snapshot JSON, use the Serie equivalent):

```java
    public PlayInstanceResponse startSerieInstance(StartSerieInstanceRequest request) {
        var serie = serieRepository.findById(request.getSerieId())
            .orElseThrow(() -> new SerieNotFoundException(request.getSerieId()));
        var snapshot = PlayMapper.writeEmbeddedSerienFromSerie(serie); // build [{id,alias,rangeId,rangeName,steps}]
        return startSerieInstance(serie.getId(), serie.getName(), snapshot, request.getPlayers(), "serie");
    }
```

If a single-Serie snapshot helper does not already exist, reuse the exact snapshot-building logic `TiebreakerService.buildSnapshotFromSerie` uses (referenced in its javadoc) — extract it to `PlayMapper` or call the existing method. Verify the real helper name during implementation and use it; do not invent a new format.

In `PlayInstanceController` add:

```java
    @Override
    public ResponseEntity<PlayInstanceResponse> startSerieInstance(
            StartSerieInstanceRequest startSerieInstanceRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(playInstanceService.startSerieInstance(startSerieInstanceRequest));
    }
```

Add the `StartSerieInstanceRequest` import.

In `ScoreController` add:

```java
    @Override
    public ResponseEntity<java.util.List<ch.jp.smartground.model.PasseScoreGroup>> listMyPassen() {
        return ResponseEntity.ok(userScoreService.listMyPassen());
    }

    @Override
    public ResponseEntity<java.util.List<ch.jp.smartground.model.WettkampfScoreGroup>> listMyWettkaempfe() {
        return ResponseEntity.ok(userScoreService.listMyWettkaempfe());
    }
```

Update the `listMyScores` / `getScoreLeaderboard` overrides to pass the new `ScoreKind kind` param through (`kind != null ? kind.getValue() : null`).

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=UserScoreServiceTest`
Expected: PASS.

Run: `mvn test`
Expected: PASS (full backend suite; fix any call sites still using the old `startSerieInstance` arity or the removed summary fields).

- [ ] **Step 6: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/service/UserScoreService.java smart-ground-backend/src/main/java/ch/jp/shooting/service/PlayInstanceService.java smart-ground-backend/src/main/java/ch/jp/shooting/api/PlayInstanceController.java smart-ground-backend/src/main/java/ch/jp/shooting/api/ScoreController.java smart-ground-backend/src/test/java/ch/jp/shooting/service/UserScoreServiceTest.java
git commit -m "[backend] grouped passe/wettkampf reads, serie instance endpoint, kind filters"
```

---

### Task 5: Frontend — API + store wiring for serie instance and grouped reads

**Files:**
- Modify: `smart-ground-ui/src/services/playInstanceApi.js` (add `startSerieInstance`; remove dead `startTrainingInstance`)
- Modify: `smart-ground-ui/src/services/scoreApi.js` (add `fetchMyPassen`, `fetchMyWettkaempfe`; add `kind` to score list)
- Modify: `smart-ground-ui/src/stores/scoreStore.js` (add `passen`, `wettkaempfe` state + actions; `loadScores({ kind })`)
- Modify: `smart-ground-ui/src/stores/activePasseStore.js` (add `startSerie`; remove dead `startTraining`)
- Test: `smart-ground-ui/src/stores/__tests__/scoreStore.test.js` (extend)

**Interfaces:**
- Consumes: backend `POST /play-instances/serie`, `GET /users/me/passen`, `GET /users/me/wettkaempfe`, `GET /users/me/scores?kind=`.
- Produces: `scoreStore.loadPassen()`, `scoreStore.loadWettkaempfe()`, `scoreStore.passen`, `scoreStore.wettkaempfe`, `scoreStore.loadScores({ kind })`; `activePasseStore.startSerie(serie, players)`.

- [ ] **Step 1: Write failing store tests**

In `scoreStore.test.js`, mirror the existing mock setup of `scoreApi`. Add:

```javascript
  it('loadPassen stores grouped passen', async () => {
    scoreApi.fetchMyPassen.mockResolvedValue([{ key: 'p1', serien: [], totalPoints: 14 }])
    const store = useScoreStore()
    await store.loadPassen()
    expect(store.passen).toHaveLength(1)
    expect(store.passen[0].totalPoints).toBe(14)
  })

  it('loadWettkaempfe stores grouped wettkaempfe', async () => {
    scoreApi.fetchMyWettkaempfe.mockResolvedValue([{ key: 'w1', passen: [], totalPoints: 15 }])
    const store = useScoreStore()
    await store.loadWettkaempfe()
    expect(store.wettkaempfe).toHaveLength(1)
  })
```

Add `fetchMyPassen` / `fetchMyWettkaempfe` to the `vi.mock('@/services/scoreApi.js', ...)` factory at the top of the file.

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm run test src/stores/__tests__/scoreStore.test.js`
Expected: FAIL — `fetchMyPassen` undefined / `loadPassen` not a function.

- [ ] **Step 3: Add the service functions**

In `scoreApi.js` add (follow the existing `apiFetch`/query pattern in the file):

```javascript
export async function fetchMyPassen() {
  return apiFetch('/users/me/passen');
}

export async function fetchMyWettkaempfe() {
  return apiFetch('/users/me/wettkaempfe');
}
```

Extend the existing score-list function to forward a `kind` query param (add `kind` to its params object when present).

In `playInstanceApi.js` add and remove:

```javascript
export async function startSerieInstance(serieId, players) {
  return apiFetch('/play-instances/serie', {
    method: 'POST',
    body: JSON.stringify({ serieId, players }),
  });
}
```

Delete `startTrainingInstance` (the `/play-instances/training` endpoint no longer exists).

- [ ] **Step 4: Add store state + actions**

In `scoreStore.js` add `const passen = ref([])`, `const wettkaempfe = ref([])`, actions `loadPassen()` / `loadWettkaempfe()` calling the new service functions (mirror the existing `loadScores`/`loadSummary` try/catch + loading/error pattern), extend `loadScores` to accept `{ kind } = {}` and pass it through, and return the new refs/actions in the store's public interface.

In `activePasseStore.js` add `startSerie` (mirror `startPasse`, calling `playInstanceApi.startSerieInstance(serie.id, players)`) and delete `startTraining` (dead — `startTrainingInstance` is gone).

- [ ] **Step 5: Run tests to verify they pass**

Run: `npm run test src/stores/__tests__/scoreStore.test.js src/stores/__tests__/activePasseStore.test.js`
Expected: PASS. Fix `activePasseStore.test.js` if it referenced `startTraining`.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-ui/src/services/playInstanceApi.js smart-ground-ui/src/services/scoreApi.js smart-ground-ui/src/stores/scoreStore.js smart-ground-ui/src/stores/activePasseStore.js smart-ground-ui/src/stores/__tests__/scoreStore.test.js
git commit -m "[ui] wire serie instance start + grouped passe/wettkampf score reads"
```

---

### Task 6: Frontend — solo run modal ("Punkte aufnehmen?" + Schütze ändern)

**Files:**
- Create: `smart-ground-ui/src/components/shooter-remote/SoloSerieStartModal.vue`
- Test: `smart-ground-ui/src/components/shooter-remote/__tests__/SoloSerieStartModal.test.js`

**Interfaces:**
- Consumes: `authStore` (current user `id`, `displayName`/`vorname`), `QrScanModal.vue`, `userApi.resolveByQr(token)` (verify the exact export name in `services/userApi.js` — the by-qr resolve used by `QrScanModal`).
- Produces: emits `confirm` with `{ record: boolean, shooter: { userId: string|null, displayName: string } }` and `cancel`.

- [ ] **Step 1: Write the failing component test**

```javascript
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import SoloSerieStartModal from '@/components/shooter-remote/SoloSerieStartModal.vue'

vi.mock('@/stores/authStore.js', () => ({
  useAuthStore: () => ({ profile: { id: 'u1', vorname: 'Max', nachname: 'M' } }),
}))

describe('SoloSerieStartModal', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows the current user and record unchecked by default', () => {
    const w = mount(SoloSerieStartModal)
    expect(w.text()).toContain('Max')
    expect(w.find('[data-testid="record-toggle"]').element.checked).toBe(false)
  })

  it('emits confirm with current user and record flag', async () => {
    const w = mount(SoloSerieStartModal)
    await w.find('[data-testid="record-toggle"]').setValue(true)
    await w.find('[data-testid="confirm"]').trigger('click')
    expect(w.emitted('confirm')[0][0]).toMatchObject({ record: true, shooter: { userId: 'u1' } })
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/components/shooter-remote/__tests__/SoloSerieStartModal.test.js`
Expected: FAIL — component does not exist.

- [ ] **Step 3: Implement the modal**

Create `SoloSerieStartModal.vue` (`<script setup>`, design tokens, ≥48px targets). Behavior:
- Local state `record = ref(false)`, `shooter = ref({ userId: authStore.profile.id, displayName: <vorname nachname> })`, `showQr = ref(false)`.
- Render the shooter name, a checkbox `data-testid="record-toggle"` bound to `record`, a "Schütze ändern" button that sets `showQr = true`, a `data-testid="confirm"` button and a cancel button.
- Embed `QrScanModal` when `showQr`; on its resolve event, call `userApi.resolveByQr(token)`, set `shooter.value = { userId: user.id, displayName: ... }`, and set `record.value = true` (changing shooter implies recording). Show an inline error if resolve fails; do not change the shooter.
- `confirm` emits `{ record: record.value, shooter: shooter.value }`; cancel emits `cancel`.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/components/shooter-remote/__tests__/SoloSerieStartModal.test.js`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-ui/src/components/shooter-remote/SoloSerieStartModal.vue smart-ground-ui/src/components/shooter-remote/__tests__/SoloSerieStartModal.test.js
git commit -m "[ui] add solo serie start modal with record toggle and QR shooter switch"
```

---

### Task 7: Frontend — persist solo/group Serie runs through a real instance

**Files:**
- Modify: `smart-ground-ui/src/components/shooter-remote/ShooterFlyoutPanel.vue` (`playSerieSolo`, `playSerieGroup`)
- Test: `smart-ground-ui/src/components/shooter-remote/__tests__/ShooterFlyoutPanel.serie.test.js` (create; if a flyout test exists, extend it)

**Interfaces:**
- Consumes: `SoloSerieStartModal` (Task 6), `activePasseStore.startSerie` (Task 5), the existing block-play launch path (`playStore.startGroupPlay` with a real `instanceId`/`blockId` — the same path range Passe blocks use).
- Produces: solo/group Serie play that creates a persisted `type="serie"` instance whose completion writes `UserSerieScore` rows.

- [ ] **Step 1: Write the failing test**

Mock `activePasseStore` and assert that confirming the solo modal with `record: true` calls `startSerie` with a player carrying the resolved `userId`:

```javascript
// mount ShooterFlyoutPanel with mocked stores; open "Meine Serien" → Als Solo Starten
// simulate SoloSerieStartModal confirm { record: true, shooter: { userId: 'u1', displayName: 'Max' } }
// expect activePasseStore.startSerie called with (serie, [{ type:'user', userId:'u1', displayName:'Max' }])
```

Write it concretely against the component's structure: stub `SoloSerieStartModal` to emit `confirm` on mount, spy on the mocked `activePasseStore.startSerie`, trigger `playSerieSolo`, and assert the call args. Follow the mocking style already used in the shooter-remote `__tests__` folder.

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/components/shooter-remote/__tests__/ShooterFlyoutPanel.serie.test.js`
Expected: FAIL — solo path still uses the client-side `playPasseWithScore`.

- [ ] **Step 3: Rewire `playSerieSolo`**

Replace the body of `playSerieSolo(serie)`: open `SoloSerieStartModal`. On confirm:
- If `record === false`: keep the current ephemeral behavior (`playStore.playPasseWithScore(tempPasse...)`) — practice, unsaved.
- If `record === true`: `await activePasseStore.startSerie(serie, [{ id, type: shooter.userId ? 'user' : 'guest', userId: shooter.userId, displayName: shooter.displayName }])`, then launch the returned instance's single block through the same persisted block-play path the range Passe uses (`startGroupPlay(players, rangeId, rangeName, instance.instanceId, block.blockId, null, 'training')`) and route to the play page. Remove the temp-Passe hack for the recording path.

Rewire `playSerieGroup(serie)`: build the group's players from the existing group/QR setup, call `activePasseStore.startSerie(serie, players)`, and launch the block via the persisted path (recording is implicit for groups — no modal). Remove the `setPendingGroupSerien` client-only launch for standalone serien.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/components/shooter-remote/__tests__/ShooterFlyoutPanel.serie.test.js`
Expected: PASS.

- [ ] **Step 5: Full UI test + lint**

Run: `npm run test && npm run lint:check`
Expected: PASS. Fix any test that relied on the old ephemeral solo/group serie behavior.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-ui/src/components/shooter-remote/ShooterFlyoutPanel.vue smart-ground-ui/src/components/shooter-remote/__tests__/ShooterFlyoutPanel.serie.test.js
git commit -m "[ui] persist solo/group serie runs via real serie instance"
```

---

### Task 8: Frontend — level-scoped drill-down views in ShooterKarriereView

**Files:**
- Modify: `smart-ground-ui/src/views/shooter/ShooterKarriereView.vue`
- Create: `smart-ground-ui/src/components/shooter/SerieScoreCard.vue` (a Serie row that expands into `StepScorecard`)
- Test: `smart-ground-ui/src/views/shooter/__tests__/ShooterKarriereView.scores.test.js` (extend)

**Interfaces:**
- Consumes: `scoreStore.scores` (loaded with `{ kind: 'SERIE' }`), `scoreStore.passen`, `scoreStore.wettkaempfe`; `components/competition/StepScorecard.vue`; `constants/stepModes.js`.
- Produces: three accordion tab panels; a reusable `SerieScoreCard` used at every leaf.

- [ ] **Step 1: Write the failing view test**

Extend `ShooterKarriereView.scores.test.js`. Mock `scoreStore` with `scores` (kind SERIE), `passen`, `wettkaempfe`. Assert:

```javascript
  it('Serien tab lists only standalone SERIE rows and expands to clay breakdown', async () => {
    // scoreStore.scores = [{ id:'s1', serieAlias:'A', totalPoints:8, maxPoints:10, stepStates:[...] }]
    // click score-group-serien; expect one SerieScoreCard; click it; expect StepScorecard rendered
  })

  it('Passen tab shows groups and drills into child serien', async () => {
    // scoreStore.passen = [{ key:'p1', label:'Passe X', serien:[{...}], totalPoints:14 }]
    // click score-group-passen; expand group; expect child SerieScoreCard
  })
```

Write both concretely against the DOM (`data-testid` hooks you add in Step 3), following the file's existing mounting/mock pattern.

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/views/shooter/__tests__/ShooterKarriereView.scores.test.js`
Expected: FAIL.

- [ ] **Step 3: Implement `SerieScoreCard` + rework the tab**

Create `SerieScoreCard.vue`: props `{ serie }`; shows `serieAlias`, `totalPoints/maxPoints`, meta (`rangeName`, date); a toggle button (`aria-expanded`) that reveals `StepScorecard` rendered from `serie.stepStates` (pass the props `StepScorecard` expects — check its `defineProps`; feed step states + resolved step letters via `constants/stepModes.js`). Design tokens, ≥48px targets.

In `ShooterKarriereView.vue`:
- Change the watch that loads the Ergebnisse tab to `Promise.all([scoreStore.loadSummary(), scoreStore.loadScores({ kind: 'SERIE' }), scoreStore.loadPassen(), scoreStore.loadWettkaempfe()])`.
- **Serien** panel: `v-for` over `scoreStore.scores` → `<SerieScoreCard>`.
- **Passen** panel: `v-for` over `scoreStore.passen` → group header (label, serieCount, total, date) that expands to a `v-for` of its `.serien` → `<SerieScoreCard>`.
- **Wettkämpfe** panel: `v-for` over `scoreStore.wettkaempfe` → session header → expand to `v-for` over `.passen` (label `Passe {{ passeIndex + 1 }}`, its total) → expand to `.serien` → `<SerieScoreCard>`.
- Remove the old flat "grouped rows" template block and the `currentScoreRows` computed's `passen`/`wettkaempfe` branches (now real drill-downs). Keep per-tab empty states.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/views/shooter/__tests__/ShooterKarriereView.scores.test.js`
Expected: PASS.

- [ ] **Step 5: Full UI suite, lint, build**

Run: `npm run test && npm run lint:check && npm run build`
Expected: PASS, no warnings.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-ui/src/views/shooter/ShooterKarriereView.vue smart-ground-ui/src/components/shooter/SerieScoreCard.vue smart-ground-ui/src/views/shooter/__tests__/ShooterKarriereView.scores.test.js
git commit -m "[ui] level-scoped drill-down score views with per-clay breakdown"
```

---

### Task 9: Docs — record decisions

**Files:**
- Modify: `smart-ground-backend/CLAUDE.md`

- [ ] **Step 1: Update the backend guide**

Under "Domain: Serie / Passe / Play", update the `PlayInstance.type` row to list `passe | serie | stechen` and note: standalone Serie runs are `type="serie"` (via `POST /api/play-instances/serie`); Stechen instances are `type="stechen"` and are **excluded** from the `UserSerieScore` projection (`recordTrainingInstance` skips non serie/passe). Under the score-tracking notes, record: `UserSerieScore.kind` (SERIE|PASSE|COMPETITION) is the structural discriminator, derived at write time; solo/group only affects which users get rows; grouped reads are `/users/me/passen` and `/users/me/wettkaempfe` (embed child Serien), replacing the old summary `passen`/`wettkaempfe` arrays.

- [ ] **Step 2: Commit**

```bash
git add smart-ground-backend/CLAUDE.md
git commit -m "[backend] document serie/stechen instance types and score kind model"
```

---

## Self-Review

**Spec coverage:**
- §1 kind column → Task 1. §2 type=serie/stechen + kind derivation + Stechen exclusion → Task 2. §2 serie endpoint → Tasks 3–4. §3 grouped read endpoints + kind filter → Tasks 3–4. §4 solo modal (Punkte aufnehmen / Schütze ändern / auto-check) → Task 6; persist solo+group → Task 7. §5 drill-down views + StepScorecard reuse → Task 8. §6 error handling (unresolvable QR, empty states) → Tasks 6 & 8. §7 testing → each task's TDD steps. Decisions doc → Task 9. No gaps.

**Placeholder scan:** No TBD/TODO. The two spots that defer to codebase verification (the single-Serie snapshot helper name in Task 4 Step 4; `StepScorecard` prop names in Task 8 Step 3) explicitly instruct "verify the real name, do not invent" rather than leaving a blank — acceptable because the exact existing artifact must be read at implementation time.

**Type consistency:** `kind` values `SERIE|PASSE|COMPETITION` consistent across entity, service, OpenAPI enum, and store filter. `startSerieInstance` arity (`..., String type`) consistent between Tasks 2 and 4 and the Tiebreaker caller. Group model names (`PasseScoreGroup`, `WettkampfScoreGroup`, `WettkampfPasseGroup`) consistent between Task 3 (schema) and Task 4 (service/controller). `activePasseStore.startSerie` consistent between Task 5 (defined) and Task 7 (consumed). `scoreStore.loadPassen/loadWettkaempfe/loadScores({kind})` consistent between Task 5 and Task 8.

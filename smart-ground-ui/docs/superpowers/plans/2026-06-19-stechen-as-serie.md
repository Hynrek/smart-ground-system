# Stechen as Serie (Task F) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a Stechen (tiebreaker) always run as a single **Serie** instead of a Passe, and remove the Passe option entirely.

**Architecture:** Add a one-block `startSerieInstance` path to `PlayInstanceService` (no persisted Passe needed). `TiebreakerService.startTiebreaker` loads a `Serie`, snapshots it, and starts the live run through that path. The `templateType` field is removed from the API, the entity, and the UI; `templateId` now always references a Serie. The UI Stechen picker switches from Passe templates to range-owned published Serien. Tie-resolution, repeat rounds, leaderboard, and the finish guard are untouched.

**Tech Stack:** Backend — Java 25, Spring Boot 4, JPA/Hibernate (H2 tests), OpenAPI contract-first, JUnit 5 + Mockito. Frontend — Vue 3 (`<script setup>`), Pinia, Vitest.

**Spec:** `docs/superpowers/specs/2026-06-19-stechen-as-serie-design.md`

**Two repos:** backend = `smart-ground-backend`, UI = `smart-ground-ui` (each its own git repo). Run all `git`/`mvnw`/`npm` commands from the respective repo root. Phase 1 (backend) must land first — the frontend payload shape comes from the backend contract.

---

## File Structure

### Backend (`smart-ground-backend`)
- Modify: `service/PlayInstanceService.java` — add `startSerieInstance(...)`; extract a shared `buildAndSaveInstance(...)`.
- Modify: `service/TiebreakerService.java` — Serie path; remove the Passe branch + `serie`-409; new `buildSerieSnapshot` helper; swap `PasseRepository` → `SerieRepository`.
- Modify: `model/CompetitionTiebreaker.java` — remove `templateType` field/column + accessors.
- Modify: `src/main/resources/static/openapi.yaml` — remove `templateType` from `StartTiebreakerRequest` + `TiebreakerResponse`.
- Test (create): `service/PlayInstanceServiceTest.java` — `startSerieInstance` builds one block.
- Test (modify): `service/TiebreakerServiceTest.java` — serie-start test; swap mock.
- Test (modify): `repository/CompetitionTiebreakerRepositoryTest.java` — drop `setTemplateType`.

### Frontend (`smart-ground-ui`)
- Modify: `services/tiebreakerApi.js` — drop `templateType` from `startTiebreaker`.
- Modify: `components/competition/StechenPanel.vue` — Serie picker (published range Serien).
- Test (create): `services/__tests__/tiebreakerApi.test.js` — `templateType`-free request body.
- Test (modify): `stores/__tests__/competitionEventStore.stechen.test.js` — `templateType`-free payload.
- Test (create): `components/__tests__/StechenPanel.test.js` — picker + start payload.

---

## Naming contract (keep consistent across tasks)

- `PlayInstanceService.startSerieInstance(UUID serieId, String serieName, String serienSnapshotJson, List<PlayerRef> players) → PlayInstanceResponse`
- `PlayInstanceService.buildAndSaveInstance(UUID templateId, String templateName, List<EmbeddedSerieRecord> serien, List<PlayerRef> players) → PlayInstanceResponse` (private)
- `TiebreakerService.buildSerieSnapshot(Serie serie) → String` (private; produces the same JSON shape as `Passe.serienJson`: `[{id, alias, rangeId, rangeName, steps}]`)
- Frontend Stechen start payload: `{ playerIds, templateId, tiePosition }` (NO `templateType`).
- `templateId` everywhere now references a **Serie** id.

---

# Phase 1 — Backend (`smart-ground-backend`)

## Task 1: `PlayInstanceService.startSerieInstance` (single-Serie live run)

Additive and self-contained — leaves the build green.

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/PlayInstanceService.java`
- Create: `src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.PlayBlockRecord;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.PlayInstanceRepository;
import ch.jp.smartground.model.PlayerRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayInstanceServiceTest {

    @Mock PlayInstanceRepository playInstanceRepository;
    @Mock PasseRepository passeRepository;
    @Mock SecurityHelper securityHelper;

    @InjectMocks PlayInstanceService service;

    @Test
    void startSerieInstance_buildsSinglePasseBlockFromSerie() {
        when(securityHelper.currentUser()).thenReturn(mock(User.class));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID serieId = UUID.randomUUID();
        String snapshot = "[{\"id\":\"" + serieId + "\",\"alias\":\"Stech-Serie\",\"steps\":[]}]";
        var players = List.of(new PlayerRef()
                .id(UUID.randomUUID().toString())
                .type(PlayerRef.TypeEnum.USER)
                .displayName("Anna"));

        service.startSerieInstance(serieId, "Stech-Serie", snapshot, players);

        ArgumentCaptor<PlayInstance> captor = ArgumentCaptor.forClass(PlayInstance.class);
        verify(playInstanceRepository).save(captor.capture());
        PlayInstance saved = captor.getValue();

        assertEquals("passe", saved.getType());
        assertEquals(serieId, saved.getTemplateId());
        assertEquals("Stech-Serie", saved.getTemplateName());

        List<PlayBlockRecord> blocks = PlayMapper.parseBlocks(saved.getStateJson());
        assertEquals(1, blocks.size());
        assertEquals("Stech-Serie", blocks.get(0).serieAlias());
        assertEquals("pending", blocks.get(0).status());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=PlayInstanceServiceTest`
Expected: FAIL — `startSerieInstance` does not exist (compilation error).

- [ ] **Step 3: Add the imports**

In `src/main/java/ch/jp/shooting/service/PlayInstanceService.java`, add to the import block:

```java
import ch.jp.shooting.dto.play.EmbeddedSerieRecord;
import ch.jp.smartground.model.PlayerRef;
```

- [ ] **Step 4: Refactor `startPasseInstance` + add `startSerieInstance` and the shared helper**

Replace the existing `startPasseInstance` method (the `public PlayInstanceResponse startPasseInstance(StartPasseInstanceRequest request) { ... }` block, ~lines 56–85) with:

```java
    /** Startet einen neuen Passe-Lauf für einen oder mehrere Spieler. */
    public PlayInstanceResponse startPasseInstance(StartPasseInstanceRequest request) {
        var passe = passeRepository.findById(request.getPasseId())
            .orElseThrow(() -> new PasseNotFoundException(request.getPasseId()));
        var serien = PlayMapper.parseEmbeddedSerien(passe.getSerienJson());
        return buildAndSaveInstance(passe.getId(), passe.getName(), serien, request.getPlayers());
    }

    /**
     * Startet einen Einzel-Serie-Lauf als einblockige Passe-Instanz.
     * Erwartet einen Serien-Listen-Snapshot (gleiche Form wie {@code Passe.serienJson}),
     * der genau eine Serie enthält. Es wird KEINE persistierte Passe benötigt.
     */
    public PlayInstanceResponse startSerieInstance(UUID serieId, String serieName,
                                                   String serienSnapshotJson, List<PlayerRef> players) {
        var serien = PlayMapper.parseEmbeddedSerien(serienSnapshotJson);
        return buildAndSaveInstance(serieId, serieName, serien, players);
    }

    /** Baut aus einer Serien-Liste eine aktive Passe-Instanz und speichert sie. */
    private PlayInstanceResponse buildAndSaveInstance(UUID templateId, String templateName,
                                                      List<EmbeddedSerieRecord> serien,
                                                      List<PlayerRef> players) {
        var owner = securityHelper.currentUser();

        var blocks = serien.stream().map(s ->
            new PlayBlockRecord(
                UUID.randomUUID(), s.id(), s.alias(),
                s.rangeId(), s.rangeName(),
                s.steps(), "pending", null, null
            )
        ).toList();

        var playerRecords = players.stream()
            .map(p -> new PlayerRefRecord(p.getId(), p.getType().getValue(), p.getDisplayName()))
            .toList();

        var instance = new PlayInstance();
        instance.setType("passe");
        instance.setTemplateId(templateId);
        instance.setTemplateName(templateName);
        instance.setOwner(owner);
        instance.setPlayersJson(PlayMapper.writePlayerRefs(playerRecords));
        instance.setStateJson(PlayMapper.writeBlocks(blocks));
        instance.setStatus("active");

        return PlayMapper.toPlayInstanceResponse(playInstanceRepository.save(instance));
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=PlayInstanceServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/PlayInstanceService.java src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java
git commit -m "[backend] play: add startSerieInstance (single-Serie one-block passe run)"
```

---

## Task 2: OpenAPI — remove `templateType` from the tiebreaker contract

**Files:**
- Modify: `src/main/resources/static/openapi.yaml`

- [ ] **Step 1: Edit `StartTiebreakerRequest`**

Find (~line 3773):

```yaml
    StartTiebreakerRequest:
      type: object
      required: [playerIds, templateType, templateId]
      properties:
        playerIds:
          type: array
          items: { type: string, format: uuid }
        templateType: { type: string, enum: [serie, passe] }
        templateId: { type: string, format: uuid }
        tiePosition: { type: integer, description: Standings position the tie is for (1 = first) }
```

Replace with:

```yaml
    StartTiebreakerRequest:
      type: object
      required: [playerIds, templateId]
      properties:
        playerIds:
          type: array
          items: { type: string, format: uuid }
        templateId: { type: string, format: uuid, description: Serie template id (a Stechen is always a single Serie) }
        tiePosition: { type: integer, description: Standings position the tie is for (1 = first) }
```

- [ ] **Step 2: Edit `TiebreakerResponse`**

Find (~line 3809) and **delete** this single line from `TiebreakerResponse.properties`:

```yaml
        templateType: { type: string }
```

(Leave `templateId` and `templateName` in place.)

- [ ] **Step 3: Regenerate sources**

Run: `./mvnw generate-sources`
Expected: BUILD SUCCESS. The generated `StartTiebreakerRequest` no longer has `getTemplateType()`; `TiebreakerResponse` no longer has `templateType`. (The handwritten code still references them and will not fully compile until Task 3 — that is expected; do not run the full test suite yet.)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] openapi: drop templateType from tiebreaker request/response (Serie-only Stechen)"
```

---

## Task 3: Serie-only `TiebreakerService` + entity cleanup

This task touches the entity, the service, and two tests together so the build returns to green.

**Files:**
- Modify: `src/main/java/ch/jp/shooting/model/CompetitionTiebreaker.java`
- Modify: `src/main/java/ch/jp/shooting/service/TiebreakerService.java`
- Modify: `src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java`
- Modify: `src/test/java/ch/jp/shooting/repository/CompetitionTiebreakerRepositoryTest.java`

- [ ] **Step 1: Rewrite `TiebreakerServiceTest` for the Serie path**

Replace the whole contents of `src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java` with:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TiebreakerServiceTest {

    @Mock LiveSessionRepository sessionRepo;
    @Mock CompetitionTiebreakerRepository tbRepo;
    @Mock PlayerResultRepository playerResultRepo;
    @Mock SessionPlayerRepository playerRepo;
    @Mock PlayInstanceService playInstanceService;
    @Mock SerieRepository serieRepo;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @Spy TieResolver tieResolver = new TieResolver();

    @InjectMocks TiebreakerService service;

    private LiveSession session;
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        session = new LiveSession(SessionType.COMPETITION, SessionStatus.PRE_COMPLETE);
        session.setId(sessionId);
        lenient().when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
    }

    @Test
    void startTiebreaker_loadsSerie_buildsSnapshot_startsSerieRun() throws Exception {
        UUID serieId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        Serie serie = new Serie();
        serie.setId(serieId);
        serie.setName("Stech-Serie");
        serie.setStepsJson("[]");
        when(serieRepo.findById(serieId)).thenReturn(Optional.of(serie));

        when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of());
        when(tbRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        SessionPlayer sp1 = new SessionPlayer();
        sp1.setId(p1); sp1.setDisplayName("Anna"); sp1.setType(PlayerType.USER);
        SessionPlayer sp2 = new SessionPlayer();
        sp2.setId(p2); sp2.setDisplayName("Ben"); sp2.setType(PlayerType.USER);
        when(playerRepo.findAllById(List.of(p1, p2))).thenReturn(List.of(sp1, sp2));

        var instanceResp = new ch.jp.smartground.model.PlayInstanceResponse();
        UUID instanceId = UUID.randomUUID();
        instanceResp.setInstanceId(instanceId);
        when(playInstanceService.startSerieInstance(eq(serieId), eq("Stech-Serie"), anyString(), anyList()))
                .thenReturn(instanceResp);

        var req = new ch.jp.smartground.model.StartTiebreakerRequest();
        req.setPlayerIds(List.of(p1, p2));
        req.setTemplateId(serieId);
        req.setTiePosition(1);

        var resp = service.startTiebreaker(sessionId, req);

        assertEquals("Stech-Serie", resp.getTemplateName());
        assertEquals(instanceId, resp.getPlayInstanceId());

        ArgumentCaptor<CompetitionTiebreaker> captor = ArgumentCaptor.forClass(CompetitionTiebreaker.class);
        verify(tbRepo).save(captor.capture());
        CompetitionTiebreaker saved = captor.getValue();
        assertEquals(TiebreakerStatus.ACTIVE, saved.getStatus());
        assertEquals(serieId, saved.getTemplateId());
        assertNotNull(saved.getProgramSnapshot());

        verify(playInstanceService).startSerieInstance(eq(serieId), eq("Stech-Serie"), anyString(), anyList());
        verify(playerResultRepo, never()).save(any());
    }

    @Test
    void startTiebreaker_rejectedWhenSessionNotPreComplete() {
        session.setStatus(SessionStatus.ACTIVE);
        var req = new ch.jp.smartground.model.StartTiebreakerRequest();
        req.setPlayerIds(List.of(UUID.randomUUID()));
        req.setTemplateId(UUID.randomUUID());

        assertThrows(ch.jp.shooting.exception.InvalidTiebreakerStateException.class,
                () -> service.startTiebreaker(sessionId, req));
    }

    @Test
    void submitResults_doesNotTouchPlayerResults() throws Exception {
        UUID tbId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, UUID.randomUUID(), 1, 1);
        tb.setStatus(TiebreakerStatus.ACTIVE);
        tb.setParticipantsJson(objectMapper.writeValueAsString(List.of(p1.toString())));
        when(tbRepo.findById(tbId)).thenReturn(Optional.of(tb));
        when(tbRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of(tb));
        when(playerResultRepo.findBySessionId(sessionId)).thenReturn(List.of());

        var req = new ch.jp.smartground.model.SubmitTiebreakerResultsRequest();
        var score = new ch.jp.smartground.model.TiebreakerPlayerScore();
        score.setPlayerId(p1); score.setTotalPoints(8); score.setMaxPoints(10);
        req.setResults(List.of(score));

        service.submitResults(sessionId, tbId, req);

        assertEquals(TiebreakerStatus.COMPLETED, tb.getStatus());
        verify(playerResultRepo, never()).save(any());
    }

    @Test
    void submitResults_onCompletedRound_throwsInvalidState() throws Exception {
        UUID tbId = UUID.randomUUID();
        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, UUID.randomUUID(), 1, 1);
        tb.setStatus(TiebreakerStatus.COMPLETED);
        when(tbRepo.findById(tbId)).thenReturn(Optional.of(tb));

        var req = new ch.jp.smartground.model.SubmitTiebreakerResultsRequest();
        req.setResults(List.of());

        assertThrows(ch.jp.shooting.exception.InvalidTiebreakerStateException.class,
                () -> service.submitResults(sessionId, tbId, req));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=TiebreakerServiceTest`
Expected: FAIL — does not compile (`SerieRepository` not yet a constructor arg; `getTemplateType` references remain in `TiebreakerService`/entity).

- [ ] **Step 3: Remove `templateType` from `CompetitionTiebreaker`**

In `src/main/java/ch/jp/shooting/model/CompetitionTiebreaker.java`:

Delete the field (the `@Column(name = "template_type", nullable = false)` line and the `private String templateType = "passe";` line, ~lines 43–44):

```java
    @Column(name = "template_type", nullable = false)
    private String templateType = "passe"; // passe | serie
```

Delete the accessors (~lines 99–100):

```java
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
```

- [ ] **Step 4: Rewrite `TiebreakerService.startTiebreaker` for the Serie path**

In `src/main/java/ch/jp/shooting/service/TiebreakerService.java`:

**4a. Imports** — remove these three:

```java
import ch.jp.shooting.model.Passe;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.smartground.model.StartPasseInstanceRequest;
```

and add these four:

```java
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.repository.SerieRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
```

**4b. Constructor field** — change the `PasseRepository passeRepo` field and constructor parameter to `SerieRepository serieRepo`. The field declaration becomes:

```java
    private final SerieRepository serieRepo;
```

In the constructor signature replace `PasseRepository passeRepo,` with `SerieRepository serieRepo,` and the assignment `this.passeRepo = passeRepo;` with `this.serieRepo = serieRepo;`.

**4c. Method body** — replace the entire `startTiebreaker` method (from `public TiebreakerResponse startTiebreaker(` through its closing `}`, ~lines 174–245) with:

```java
    /** Startet eine neue Stechen-Runde als Live-Lauf einer Einzel-Serie für die gleichstehenden Spieler. */
    public TiebreakerResponse startTiebreaker(UUID sessionId, StartTiebreakerRequest req) throws Exception {
        LiveSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new TiebreakerNotFoundException(sessionId));
        if (session.getStatus() != SessionStatus.PRE_COMPLETE) {
            throw new InvalidTiebreakerStateException("Stechen nur im Status PRE_COMPLETE möglich");
        }

        int tiePosition = req.getTiePosition() != null ? req.getTiePosition() : 1;
        List<UUID> playerIds = req.getPlayerIds() != null ? req.getPlayerIds() : List.of();
        Set<UUID> requested = new HashSet<>(playerIds);

        // tieGroupId / roundNumber: bestehende Runde derselben Position wiederverwenden,
        // wenn ihre Teilnehmer eine Obermenge der angefragten Spieler sind.
        UUID tieGroupId = null;
        int roundNumber = 1;
        var existing = tbRepo.findBySessionId(sessionId);
        for (CompetitionTiebreaker tb : existing) {
            if (tb.getTiePosition() != tiePosition) {
                continue;
            }
            Set<UUID> participants = new HashSet<>(parseUuidList(tb.getParticipantsJson()));
            if (participants.containsAll(requested)) {
                if (tieGroupId == null) {
                    tieGroupId = tb.getTieGroupId();
                }
                if (tb.getTieGroupId().equals(tieGroupId)) {
                    roundNumber = Math.max(roundNumber, tb.getRoundNumber() + 1);
                }
            }
        }
        if (tieGroupId == null) {
            tieGroupId = UUID.randomUUID();
            roundNumber = 1;
        }

        // Serie-Vorlage laden — ein Stechen ist immer eine Einzel-Serie.
        Serie serie = serieRepo.findById(req.getTemplateId())
                .orElseThrow(() -> new TiebreakerNotFoundException(req.getTemplateId()));
        String snapshot = buildSerieSnapshot(serie);

        // Tiebreaker anlegen.
        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, tieGroupId, roundNumber, tiePosition);
        tb.setTemplateId(serie.getId());
        tb.setTemplateName(serie.getName());
        tb.setProgramSnapshot(snapshot); // unveränderlicher Ablauf-Snapshot (genau eine Serie)
        tb.setParticipantsJson(objectMapper.writeValueAsString(
                playerIds.stream().map(UUID::toString).toList()));

        // Live-Lauf: einblockige Passe-Instanz aus der Serie für die gleichstehenden Spieler.
        var tiedPlayers = playerRepo.findAllById(playerIds);
        var playerRefs = tiedPlayers.stream()
                .map(p -> new PlayerRef()
                        .id(p.getId().toString())
                        .type(mapPlayerType(p.getType()))
                        .displayName(p.getDisplayName()))
                .toList();
        var instance = playInstanceService.startSerieInstance(
                serie.getId(), serie.getName(), snapshot, playerRefs);
        tb.setPlayInstanceId(instance.getInstanceId());

        tb.setStatus(TiebreakerStatus.ACTIVE);
        return toResponse(tbRepo.save(tb));
    }

    /**
     * Baut aus einer einzelnen Serie einen Serien-Listen-Snapshot in derselben Form wie
     * {@code Passe.serienJson}: {@code [{id, alias, rangeId, rangeName, steps}]}.
     */
    private String buildSerieSnapshot(Serie serie) throws Exception {
        ObjectNode serieNode = objectMapper.createObjectNode();
        serieNode.put("id", serie.getId().toString());
        serieNode.put("alias", serie.getName());
        if (serie.getRange() != null) {
            serieNode.put("rangeId", serie.getRange().getId().toString());
            serieNode.put("rangeName", serie.getRange().getName());
        }
        String steps = serie.getStepsJson();
        serieNode.set("steps", objectMapper.readTree(steps == null || steps.isBlank() ? "[]" : steps));
        ArrayNode arrayNode = objectMapper.createArrayNode();
        arrayNode.add(serieNode);
        return objectMapper.writeValueAsString(arrayNode);
    }
```

**4d. `toResponse`** — delete the `templateType` line from the response builder (in the `toResponse(CompetitionTiebreaker tb)` method, ~line 294):

```java
                .templateType(tb.getTemplateType())
```

- [ ] **Step 5: Drop `setTemplateType` from the repository test**

In `src/test/java/ch/jp/shooting/repository/CompetitionTiebreakerRepositoryTest.java`, delete this line (~line 35):

```java
        tb.setTemplateType("passe");
```

- [ ] **Step 6: Run the changed tests**

Run: `./mvnw test -Dtest=TiebreakerServiceTest,CompetitionTiebreakerRepositoryTest,PlayInstanceServiceTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/TiebreakerService.java src/main/java/ch/jp/shooting/model/CompetitionTiebreaker.java src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java src/test/java/ch/jp/shooting/repository/CompetitionTiebreakerRepositoryTest.java
git commit -m "[backend] competition: Stechen runs a single Serie; remove templateType + Passe path"
```

---

## Task 4: Backend full suite + document the decision

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Full backend suite**

Run: `./mvnw clean test`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Update the Stechen section in `CLAUDE.md`**

In `smart-ground-backend/CLAUDE.md`, in the **Stechen (Tiebreaker)** subsection, replace the bullet:

```
  - `POST /api/sessions/{id}/tiebreakers` → `startTiebreaker` (201) — start a new round (only in `PRE_COMPLETE`, Passe templates only)
```

with:

```
  - `POST /api/sessions/{id}/tiebreakers` → `startTiebreaker` (201) — start a new round (only in `PRE_COMPLETE`). A Stechen is **always a single Serie**: `templateId` is a Serie id; the round runs as a one-block `passe`-type `PlayInstance` via `PlayInstanceService.startSerieInstance` (no persisted Passe). There is no `templateType` field — the Passe option was removed (Task F).
```

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "[backend] document Serie-only Stechen (Task F) in CLAUDE.md"
```

---

# Phase 2 — Frontend (`smart-ground-ui`)

> Prerequisite: Phase 1 done so the request shape (`templateType` removed) is final.

## Task 5: `tiebreakerApi` payload — drop `templateType`

> The store Stechen test mocks the whole `tiebreakerApi` module, so it does **not** exercise the
> payload-serialization change — the real test lives at the service layer (mirroring
> `services/__tests__/serieApi.test.js`). The store test is updated for consistency only.

**Files:**
- Create: `src/services/__tests__/tiebreakerApi.test.js`
- Modify: `src/services/tiebreakerApi.js`
- Modify: `src/stores/__tests__/competitionEventStore.stechen.test.js`

- [ ] **Step 1: Write the failing service test**

Create `src/services/__tests__/tiebreakerApi.test.js`:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as apiClient from '../apiClient.js'

vi.mock('../apiClient.js', () => ({ apiFetch: vi.fn() }))

describe('tiebreakerApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('startTiebreaker posts a Serie payload without templateType', async () => {
    apiClient.apiFetch.mockResolvedValue({ id: 'tb1' })
    const { startTiebreaker } = await import('../tiebreakerApi.js')
    // Pass a stray templateType to prove it is stripped from the request body.
    await startTiebreaker('s1', { playerIds: ['p1', 'p2'], templateId: 'se1', tiePosition: 1, templateType: 'serie' })
    expect(apiClient.apiFetch).toHaveBeenCalledWith('/sessions/s1/tiebreakers', {
      method: 'POST',
      body: JSON.stringify({ playerIds: ['p1', 'p2'], templateId: 'se1', tiePosition: 1 }),
    })
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test -- src/services/__tests__/tiebreakerApi.test.js`
Expected: FAIL — the current `startTiebreaker` serializes a body that includes `templateType`, so the `body` string does not match.

- [ ] **Step 3: Drop `templateType` from `tiebreakerApi.startTiebreaker`**

In `src/services/tiebreakerApi.js`, replace the `startTiebreaker` export with:

```javascript
export const startTiebreaker = (sessionId, { playerIds, templateId, tiePosition }) =>
  apiFetch(`/sessions/${sessionId}/tiebreakers`, {
    method: 'POST',
    body: JSON.stringify({ playerIds, templateId, tiePosition }),
  })
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test -- src/services/__tests__/tiebreakerApi.test.js`
Expected: PASS.

- [ ] **Step 5: Update the store Stechen test for consistency**

In `src/stores/__tests__/competitionEventStore.stechen.test.js`, replace the `startStechen posts and refreshes ties` test (lines 31–39) with:

```javascript
  it('startStechen posts and refreshes ties', async () => {
    tb.startTiebreaker.mockResolvedValue({ id: 'tb1', roundNumber: 1 })
    tb.getTies.mockResolvedValue({ sessionId: 's1', tiedBlocks: [] })
    const store = useCompetitionEventStore()
    const res = await store.startStechen('s1', { playerIds: ['p1', 'p2'], templateId: 't1', tiePosition: 1 })
    expect(tb.startTiebreaker).toHaveBeenCalledWith('s1', { playerIds: ['p1', 'p2'], templateId: 't1', tiePosition: 1 })
    expect(res.id).toBe('tb1')
    expect(tb.getTies).toHaveBeenCalledWith('s1')
  })
```

- [ ] **Step 6: Run both tests to verify they pass**

Run: `npm run test -- src/services/__tests__/tiebreakerApi.test.js src/stores/__tests__/competitionEventStore.stechen.test.js`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/services/tiebreakerApi.js src/services/__tests__/tiebreakerApi.test.js src/stores/__tests__/competitionEventStore.stechen.test.js
git commit -m "[ui] stechen: drop templateType from startTiebreaker payload"
```

---

## Task 6: `StechenPanel` — Serie picker (published range Serien)

**Files:**
- Modify: `src/components/competition/StechenPanel.vue`
- Create: `src/components/__tests__/StechenPanel.test.js`

- [ ] **Step 1: Write the failing component test**

Create `src/components/__tests__/StechenPanel.test.js`:

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import StechenPanel from '@/components/competition/StechenPanel.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
import { usePasseStore } from '@/stores/passeStore.js'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

const STUBS = { Icons: true, Badge: { template: '<span><slot /></span>' }, Button: { template: '<button><slot /></button>' } }

const tiedBlock = () => ({
  tiePosition: 1,
  sharedScore: 24,
  resolved: false,
  players: [
    { playerId: 'p1', displayName: 'Anna' },
    { playerId: 'p2', displayName: 'Ben' },
  ],
  rounds: [],
})

const setup = async () => {
  setActivePinia(createPinia())
  const store = useCompetitionEventStore()
  store.tiesBySession = { s1: { sessionId: 's1', tiedBlocks: [tiedBlock()] } }
  vi.spyOn(store, 'startStechen').mockResolvedValue({ id: 'tb1' })

  const passeStore = usePasseStore()
  passeStore.savedSerien = [
    { id: 'se-pub', name: 'Stech-Serie', ownership: 'range', published: true },
    { id: 'se-unpub', name: 'Entwurf', ownership: 'range', published: false },
    { id: 'se-user', name: 'Privat', ownership: 'user', published: true },
  ]
  vi.spyOn(passeStore, 'loadSerienFromStorage').mockResolvedValue()

  const wrapper = mount(StechenPanel, {
    props: { sessionId: 's1' },
    global: { plugins: [], stubs: STUBS },
  })
  await flushPromises()
  return { wrapper, store }
}

describe('StechenPanel — Serie picker', () => {
  beforeEach(() => vi.clearAllMocks())

  it('offers only published range Serien in the start modal', async () => {
    const { wrapper } = await setup()
    await wrapper.find('.tie-block-header button').trigger('click') // "Stechen starten"
    await flushPromises()
    const options = wrapper.findAll('.modal-select option').map(o => o.text())
    expect(options).toContain('Stech-Serie')
    expect(options).not.toContain('Entwurf')
    expect(options).not.toContain('Privat')
  })

  it('starts a Stechen with a templateType-free Serie payload', async () => {
    const { wrapper, store } = await setup()
    await wrapper.find('.tie-block-header button').trigger('click')
    await flushPromises()
    await wrapper.find('.modal-select').setValue('se-pub')
    const startBtn = wrapper.findAll('.modal-actions button').at(-1)
    await startBtn.trigger('click')
    await flushPromises()
    expect(store.startStechen).toHaveBeenCalledWith('s1', {
      playerIds: ['p1', 'p2'],
      templateId: 'se-pub',
      tiePosition: 1,
    })
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test -- src/components/__tests__/StechenPanel.test.js`
Expected: FAIL — the panel still uses a Passe picker (`passenOptions`) and a `templateType: 'passe'` payload.

- [ ] **Step 3: Switch the picker source to published range Serien**

In `src/components/competition/StechenPanel.vue` `<script setup>`, replace these two lines (~lines 205–206):

```javascript
// Passe templates only — the backend rejects a `serie` Stechen with a 409.
const passenOptions = computed(() => passeStore.savedGlobalPassen)
```

with:

```javascript
// A Stechen is always a single Serie — offer published, range-owned Serien only.
const serieOptions = computed(() =>
  passeStore.getGlobalSerien().filter((s) => s.published === true),
)
```

- [ ] **Step 4: Update the start modal markup and state**

In the same file, change the start-modal `reactive` (~line 266) from `passeId` to `serieId`:

```javascript
const startModal = reactive({ open: false, block: null, serieId: null })
```

In `openStartModal` (~line 270) replace `startModal.passeId = null` with `startModal.serieId = null`.

In the template, replace the whole Passe `modal-section` (the block from `<label for="stechen-passe" ...>` through its closing `</div>`, ~lines 154–169) with:

```html
        <div class="modal-section">
          <label for="stechen-serie" class="modal-section-label">Serie-Vorlage</label>
          <select
            id="stechen-serie"
            v-model="startModal.serieId"
            class="modal-select"
          >
            <option :value="null" disabled>Serie wählen…</option>
            <option v-for="serie in serieOptions" :key="serie.id" :value="serie.id">
              {{ serie.name }}
            </option>
          </select>
          <span v-if="serieOptions.length === 0" class="modal-hint">
            Keine Serie-Vorlagen verfügbar.
          </span>
        </div>
```

In the modal's confirm `<Button>` (~line 178), change the disabled guard from `!startModal.passeId` to `!startModal.serieId`:

```html
          <Button
            variant="primary"
            size="sm"
            :disabled="!startModal.serieId || starting"
            @click="confirmStart"
          >
```

- [ ] **Step 5: Update `confirmStart` to send a Serie payload**

Replace the `confirmStart` function (~lines 282–302) with:

```javascript
const confirmStart = async () => {
  if (!startModal.serieId || starting.value) return
  starting.value = true
  startError.value = null
  const block = startModal.block
  try {
    await store.startStechen(props.sessionId, {
      playerIds: block.players.map(p => p.playerId),
      templateId: startModal.serieId,
      tiePosition: block.tiePosition,
    })
    closeStartModal()
    // The created round (with its playInstanceId) now appears in the block history.
  } catch (e) {
    console.error('[StechenPanel] start Stechen failed:', e)
    startError.value = e?.message ?? 'Stechen konnte nicht gestartet werden.'
  } finally {
    starting.value = false
  }
}
```

- [ ] **Step 6: Update the `onMounted` Serie load**

Replace the `onMounted` block (~lines 304–308) with:

```javascript
onMounted(async () => {
  if (passeStore.savedSerien.length === 0) {
    await passeStore.loadSerienFromStorage().catch(() => {})
  }
})
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `npm run test -- src/components/__tests__/StechenPanel.test.js`
Expected: PASS.

- [ ] **Step 8: Lint**

Run: `npm run lint:check`
Expected: no errors in the touched files (no unused `passenOptions`/`savedGlobalPassen`).

- [ ] **Step 9: Commit**

```bash
git add src/components/competition/StechenPanel.vue src/components/__tests__/StechenPanel.test.js
git commit -m "[ui] stechen: pick a published range Serie instead of a Passe"
```

---

## Task 7: Full-suite verification

- [ ] **Step 1: Backend**

Run (in `smart-ground-backend`): `./mvnw clean test`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Frontend**

Run (in `smart-ground-ui`): `npm run test && npm run lint:check && npm run build`
Expected: all tests pass, no lint errors, build succeeds.

- [ ] **Step 3: Manual smoke (optional, requires running stack)**

Bring a competition to `PRE_COMPLETE` with two players tied for 1st → open Stechen → confirm the picker lists only published range Serien → start one → run it live → enter results → confirm the leaderboard re-orders and the finish guard clears.

---

## Self-Review Notes (for the implementer)

- **Spec coverage:** §3.1 → Task 1; §3.3 (contract) → Task 2; §3.2 + §3.3 (entity) → Task 3; docs → Task 4; §4.2 → Task 5; §4.1 → Task 6; §5 tests are folded into Tasks 1/3/5/6; full verification → Task 7.
- **Naming consistency:** `startSerieInstance(serieId, serieName, snapshotJson, players)` is defined in Task 1 and called verbatim in Task 3 and asserted in both Java tests. The frontend payload `{ playerIds, templateId, tiePosition }` is identical across Task 5 (api/store test) and Task 6 (panel + panel test).
- **Build-green ordering:** Task 2 (contract) intentionally leaves the handwritten code non-compiling until Task 3 lands the matching Java changes — do not run the full suite between them, only `generate-sources`.
- **Snapshot shape:** `buildSerieSnapshot` must emit the same JSON shape `PlayMapper.parseEmbeddedSerien` consumes (`[{id, alias, rangeId, rangeName, steps}]`), so `startSerieInstance` builds exactly one block.
```

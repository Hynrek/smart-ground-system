# Competition Stechen (Tiebreaker) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin run one or more Stechen (tiebreaker) rounds for tied shooters while a competition is in `PRE_COMPLETE`, resolving the tied block's order without altering main competition totals.

**Architecture:** A new `CompetitionTiebreaker` entity stores each Stechen round (participants, chosen course snapshot, per-player scores) outside `PlayerResult`. Starting a Stechen reuses the existing live-play engine via a restricted `PlayInstance`. The leaderboard detects tied blocks and orders them by the earliest Stechen round that separates the players. The finish transition warns (does not block) when decisive ties remain. The Vue admin UI surfaces ties, drives the Stechen run, and gates finishing.

**Tech Stack:** Backend — Java 25, Spring Boot 4, JPA/Hibernate (H2 tests), OpenAPI contract-first, JUnit 5 + Mockito. Frontend — Vue 3 (`<script setup>`), Pinia, Vitest.

**Spec:** `docs/superpowers/specs/2026-06-17-competition-stechen-tiebreaker-design.md`

**Two repos:** backend = `smart-ground-backend` (own git repo), UI = `smart-ground-ui` (own git repo). Run all `git`/`mvnw`/`npm` commands from the respective repo root. Phase 1 (backend) is fully testable on its own and must be completed first — the frontend's exact request/response shapes come from the generated OpenAPI model.

---

## File Structure

### Backend (`smart-ground-backend`)
- Create: `model/CompetitionTiebreaker.java` — JPA entity (one row per Stechen round).
- Create: `model/TiebreakerStatus.java` — enum `PENDING, ACTIVE, COMPLETED`.
- Create: `repository/CompetitionTiebreakerRepository.java` — finders by session / tieGroup.
- Create: `service/TieResolver.java` — pure tie-detection + Stechen-ordering logic (no Spring, fully unit-testable).
- Create: `service/TiebreakerService.java` — orchestration: list ties, start round, submit results.
- Create: `exception/TiebreakerNotFoundException.java`, `exception/InvalidTiebreakerStateException.java`.
- Create: `api/TiebreakerController.java` — implements generated `TiebreakerApi`.
- Modify: `service/CompetitionService.java` — leaderboard tie-resolution + new fields.
- Modify: `dto/SessionLeaderboardResponse.java` — add `tied`, `tieResolvedByStechen` to `PlayerScoreEntry`.
- Modify: `service/SessionService.java` — finish guard (warning payload + `force`).
- Modify: `config/GlobalExceptionHandler.java` — map new exceptions.
- Modify: `src/main/resources/static/openapi.yaml` — schemas + 4 tiebreaker endpoints + finish-guard fields.
- Tests: `service/TieResolverTest.java`, `service/TiebreakerServiceTest.java`, `service/CompetitionServiceLeaderboardTest.java`, `service/SessionServiceFinishGuardTest.java`.

### Frontend (`smart-ground-ui`)
- Create: `services/tiebreakerApi.js` — REST wrappers (ties, start, results, list).
- Modify: `stores/competitionEventStore.js` — Stechen actions + ties state.
- Modify: `views/admin/WettkampfDetailView.vue` — tie highlighting, Stechen modal, history, finish guard.
- Tests: `stores/__tests__/competitionEventStore.stechen.test.js`.

---

## Naming contract (used across tasks — keep consistent)

- Entity: `CompetitionTiebreaker` with fields `id, session, tieGroupId, roundNumber, tiePosition, participantsJson, templateType, templateId, templateName, programSnapshot, playInstanceId, resultsJson, status, createdAt, completedAt`.
- `TieResolver.resolve(List<PlayerStanding> standings, List<TiebreakerRound> rounds)` → `List<ResolvedStanding>`.
- `PlayerStanding(UUID playerId, String displayName, int totalScore, int maxScore)`.
- `TiebreakerRound(UUID tieGroupId, int roundNumber, List<UUID> participantIds, Map<UUID,Integer> scoresByPlayer)`.
- `ResolvedStanding(UUID playerId, String displayName, int totalScore, int maxScore, int rank, boolean tied, boolean tieResolvedByStechen)`.
- OpenAPI operationIds: `getSessionTies`, `startTiebreaker`, `submitTiebreakerResults`, `listTiebreakers`.
- Frontend store actions: `loadTies(sessionId)`, `startStechen(sessionId, { playerIds, templateType, templateId })`, `submitStechenResults(sessionId, tiebreakerId, results)`, `finishEvent(sessionId, force)`.

---

# Phase 1 — Backend

## Task 1: `TieResolver` — pure tie-detection + Stechen ordering

This is the load-bearing algorithm. Build it first, in isolation, with no Spring/JPA dependencies.

**Files:**
- Create: `src/main/java/ch/jp/shooting/service/TieResolver.java`
- Test: `src/test/java/ch/jp/shooting/service/TieResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TieResolverTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    private TieResolver.PlayerStanding ps(UUID id, String name, int total) {
        return new TieResolver.PlayerStanding(id, name, total, 25);
    }

    @Test
    void noTies_assignsSequentialRanks() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 20), ps(C, "Cara", 18));
        var resolved = new TieResolver().resolve(standings, List.of());

        assertEquals(List.of(1, 2, 3), resolved.stream().map(r -> r.rank()).toList());
        assertFalse(resolved.get(0).tied());
        assertFalse(resolved.get(0).tieResolvedByStechen());
    }

    @Test
    void twoWayTie_noStechen_sharesRank() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 24), ps(C, "Cara", 18));
        var resolved = new TieResolver().resolve(standings, List.of());

        // Both tied players share rank 1; next player is rank 3 (standard competition ranking).
        assertEquals(1, rankOf(resolved, A));
        assertEquals(1, rankOf(resolved, B));
        assertEquals(3, rankOf(resolved, C));
        assertTrue(byId(resolved, A).tied());
        assertTrue(byId(resolved, B).tied());
    }

    @Test
    void twoWayTie_stechenBreaksIt_ordersWithinBlock() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 24), ps(C, "Cara", 18));
        var tieGroup = UUID.randomUUID();
        var round = new TieResolver.TiebreakerRound(
                tieGroup, 1, List.of(A, B), Map.of(A, 5, B, 8)); // Ben wins the Stechen
        var resolved = new TieResolver().resolve(standings, List.of(round));

        assertEquals(1, rankOf(resolved, B));
        assertEquals(2, rankOf(resolved, A));
        assertEquals(3, rankOf(resolved, C));
        assertFalse(byId(resolved, B).tied());
        assertTrue(byId(resolved, B).tieResolvedByStechen());
        assertTrue(byId(resolved, A).tieResolvedByStechen());
    }

    @Test
    void threeWayTie_round1PartiallySeparates_round2BreaksRest() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 24), ps(C, "Cara", 24));
        var tieGroup = UUID.randomUUID();
        // Round 1: Cara clearly behind (3); Anna & Ben still tied (9, 9).
        var r1 = new TieResolver.TiebreakerRound(tieGroup, 1, List.of(A, B, C), Map.of(A, 9, B, 9, C, 3));
        // Round 2: only Anna & Ben re-shoot; Anna wins (7 vs 6).
        var r2 = new TieResolver.TiebreakerRound(tieGroup, 2, List.of(A, B), Map.of(A, 7, B, 6));
        var resolved = new TieResolver().resolve(standings, List.of(r1, r2));

        assertEquals(1, rankOf(resolved, A));
        assertEquals(2, rankOf(resolved, B));
        assertEquals(3, rankOf(resolved, C));
        assertFalse(byId(resolved, A).tied());
        assertFalse(byId(resolved, B).tied());
    }

    @Test
    void stechenStillTied_playersRemainTied() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 24));
        var tieGroup = UUID.randomUUID();
        var r1 = new TieResolver.TiebreakerRound(tieGroup, 1, List.of(A, B), Map.of(A, 5, B, 5));
        var resolved = new TieResolver().resolve(standings, List.of(r1));

        assertEquals(1, rankOf(resolved, A));
        assertEquals(1, rankOf(resolved, B));
        assertTrue(byId(resolved, A).tied());
        assertTrue(byId(resolved, B).tied());
    }

    private int rankOf(List<TieResolver.ResolvedStanding> rs, UUID id) { return byId(rs, id).rank(); }
    private TieResolver.ResolvedStanding byId(List<TieResolver.ResolvedStanding> rs, UUID id) {
        return rs.stream().filter(r -> r.playerId().equals(id)).findFirst().orElseThrow();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TieResolverTest`
Expected: FAIL — `TieResolver` does not exist (compilation error).

- [ ] **Step 3: Write the implementation**

```java
package ch.jp.shooting.service;

import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Reine Tie-Break-Logik (kein Spring/JPA-State): Erkennt Punktgleichheit und ordnet
 * gleichstehende Blöcke anhand der Stechen-Runden. Die Reihenfolge innerhalb eines
 * Blocks wird durch die erste Runde bestimmt, die die Spieler trennt.
 */
@Component
@NullMarked
public class TieResolver {

    public record PlayerStanding(UUID playerId, String displayName, int totalScore, int maxScore) {}

    public record TiebreakerRound(UUID tieGroupId, int roundNumber,
                                  List<UUID> participantIds, Map<UUID, Integer> scoresByPlayer) {}

    public record ResolvedStanding(UUID playerId, String displayName, int totalScore, int maxScore,
                                   int rank, boolean tied, boolean tieResolvedByStechen) {}

    public List<ResolvedStanding> resolve(List<PlayerStanding> standings, List<TiebreakerRound> rounds) {
        // 1) Nach Hauptpunkten absteigend sortieren.
        List<PlayerStanding> sorted = new ArrayList<>(standings);
        sorted.sort(Comparator.comparingInt(PlayerStanding::totalScore).reversed());

        // 2) Stechen-Runden chronologisch pro Spieler sammeln (playerId -> [score je Runde]).
        List<TiebreakerRound> orderedRounds = new ArrayList<>(rounds);
        orderedRounds.sort(Comparator.comparingInt(TiebreakerRound::roundNumber));

        List<ResolvedStanding> result = new ArrayList<>();
        int i = 0;
        int rank = 1;
        while (i < sorted.size()) {
            // Block gleicher Hauptpunkte finden.
            int j = i;
            while (j + 1 < sorted.size() && sorted.get(j + 1).totalScore() == sorted.get(i).totalScore()) {
                j++;
            }
            List<PlayerStanding> block = sorted.subList(i, j + 1);

            if (block.size() == 1) {
                PlayerStanding p = block.get(0);
                result.add(new ResolvedStanding(p.playerId(), p.displayName(), p.totalScore(),
                        p.maxScore(), rank, false, false));
            } else {
                resolveBlock(block, orderedRounds, rank, result);
            }
            rank += block.size(); // Standard-Wettkampf-Ranking: nächster Rang springt um Blockgröße.
            i = j + 1;
        }
        return result;
    }

    /** Ordnet einen punktgleichen Block anhand der Stechen-Runden. */
    private void resolveBlock(List<PlayerStanding> block, List<TiebreakerRound> rounds,
                              int baseRank, List<ResolvedStanding> out) {
        Set<UUID> blockIds = new HashSet<>();
        for (PlayerStanding p : block) blockIds.add(p.playerId());

        // Nur Runden, die (auch) Spieler dieses Blocks betreffen.
        List<TiebreakerRound> relevant = rounds.stream()
                .filter(r -> r.participantIds().stream().anyMatch(blockIds::contains))
                .toList();
        boolean hasStechen = !relevant.isEmpty();

        // Vergleichsschlüssel je Spieler: Liste der Stechen-Scores in Rundenreihenfolge.
        // Höherer Score je Runde = besser. Fehlt ein Score in einer Runde (Spieler war schon
        // getrennt/nicht dabei), zählt diese Runde für ihn nicht weiter — der frühere
        // trennende Score hat dann bereits entschieden.
        Comparator<PlayerStanding> byStechen = (x, y) -> {
            for (TiebreakerRound r : relevant) {
                Integer sx = r.scoresByPlayer().get(x.playerId());
                Integer sy = r.scoresByPlayer().get(y.playerId());
                if (sx == null || sy == null) continue;
                int cmp = Integer.compare(sy, sx); // absteigend
                if (cmp != 0) return cmp;
            }
            return 0; // weiterhin gleich
        };

        List<PlayerStanding> ordered = new ArrayList<>(block);
        ordered.sort(byStechen);

        // Ränge innerhalb des Blocks vergeben; Spieler mit identischem Stechen-Schlüssel
        // teilen sich den Rang und bleiben "tied".
        int idx = 0;
        while (idx < ordered.size()) {
            int k = idx;
            while (k + 1 < ordered.size() && byStechen.compare(ordered.get(idx), ordered.get(k + 1)) == 0) {
                k++;
            }
            boolean sharedRank = (k > idx); // mehr als ein Spieler auf diesem Rang
            int rankHere = baseRank + idx;
            for (int m = idx; m <= k; m++) {
                PlayerStanding p = ordered.get(m);
                out.add(new ResolvedStanding(p.playerId(), p.displayName(), p.totalScore(),
                        p.maxScore(), rankHere, sharedRank, hasStechen));
            }
            idx = k + 1;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=TieResolverTest`
Expected: PASS (all 5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/TieResolver.java src/test/java/ch/jp/shooting/service/TieResolverTest.java
git commit -m "[backend] add TieResolver tie-detection and Stechen ordering logic"
```

---

## Task 2: `CompetitionTiebreaker` entity, status enum, repository

**Files:**
- Create: `src/main/java/ch/jp/shooting/model/TiebreakerStatus.java`
- Create: `src/main/java/ch/jp/shooting/model/CompetitionTiebreaker.java`
- Create: `src/main/java/ch/jp/shooting/repository/CompetitionTiebreakerRepository.java`
- Test: `src/test/java/ch/jp/shooting/repository/CompetitionTiebreakerRepositoryTest.java`

- [ ] **Step 1: Write the failing test** (`@DataJpaTest`, H2)

```java
package ch.jp.shooting.repository;

import ch.jp.shooting.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class CompetitionTiebreakerRepositoryTest {

    @Autowired CompetitionTiebreakerRepository repo;
    @Autowired LiveSessionRepository sessionRepo;

    @Test
    void persistsAndFindsBySessionAndTieGroup() {
        LiveSession session = sessionRepo.save(new LiveSession(SessionType.COMPETITION, SessionStatus.PRE_COMPLETE));
        UUID tieGroup = UUID.randomUUID();

        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, tieGroup, 1, 1);
        tb.setTemplateType("passe");
        tb.setTemplateId(UUID.randomUUID());
        tb.setTemplateName("Stech-Passe");
        tb.setParticipantsJson("[]");
        tb.setStatus(TiebreakerStatus.PENDING);
        repo.save(tb);

        List<CompetitionTiebreaker> bySession = repo.findBySessionId(session.getId());
        assertEquals(1, bySession.size());

        List<CompetitionTiebreaker> byGroup =
                repo.findBySessionIdAndTieGroupIdOrderByRoundNumberAsc(session.getId(), tieGroup);
        assertEquals(1, byGroup.size());
        assertEquals(TiebreakerStatus.PENDING, byGroup.get(0).getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=CompetitionTiebreakerRepositoryTest`
Expected: FAIL — types do not exist.

- [ ] **Step 3a: Create `TiebreakerStatus.java`**

```java
package ch.jp.shooting.model;

public enum TiebreakerStatus {
    PENDING, ACTIVE, COMPLETED
}
```

- [ ] **Step 3b: Create `CompetitionTiebreaker.java`**

```java
package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Eine Stechen-Runde für einen punktgleichen Block in einem Wettkampf.
 * Ergebnisse werden bewusst NICHT in PlayerResult geschrieben — sie ordnen nur den
 * gleichstehenden Block. Mehrere Runden teilen sich dieselbe tieGroupId.
 */
@Entity
@Table(name = "competition_tiebreakers")
@NullMarked
public class CompetitionTiebreaker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    /** Verbindet alle Runden desselben Stechens (Runde 1 + Wiederholungen). */
    @Column(name = "tie_group_id", nullable = false)
    private UUID tieGroupId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    /** Position im Klassement, für die das Stechen läuft (1 = erster Platz). */
    @Column(name = "tie_position", nullable = false)
    private int tiePosition;

    /** SessionPlayer-IDs, die zu Beginn dieser Runde noch gleichstanden. JSON: ["uuid", ...]. */
    @Column(name = "participants_json", columnDefinition = "TEXT", nullable = false)
    private String participantsJson = "[]";

    @Column(name = "template_type", nullable = false)
    private String templateType = "passe"; // passe | serie

    @Column(name = "template_id")
    @Nullable
    private UUID templateId;

    @Column(name = "template_name")
    @Nullable
    private String templateName;

    /** Eingefrorener Snapshot des gewählten Ablaufs (unveränderlich, wie programSnapshots). */
    @Column(name = "program_snapshot", columnDefinition = "TEXT")
    @Nullable
    private String programSnapshot;

    @Column(name = "play_instance_id")
    @Nullable
    private UUID playInstanceId;

    /** Stechen-Ergebnisse. JSON: [{"playerId":"uuid","totalPoints":8,"maxPoints":10}]. */
    @Column(name = "results_json", columnDefinition = "TEXT")
    @Nullable
    private String resultsJson;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TiebreakerStatus status = TiebreakerStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    @Nullable
    private Instant completedAt;

    public CompetitionTiebreaker() {}

    public CompetitionTiebreaker(LiveSession session, UUID tieGroupId, int roundNumber, int tiePosition) {
        this.session = session;
        this.tieGroupId = tieGroupId;
        this.roundNumber = roundNumber;
        this.tiePosition = tiePosition;
    }

    public UUID getId() { return id; }
    public LiveSession getSession() { return session; }
    public void setSession(LiveSession session) { this.session = session; }
    public UUID getTieGroupId() { return tieGroupId; }
    public void setTieGroupId(UUID tieGroupId) { this.tieGroupId = tieGroupId; }
    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
    public int getTiePosition() { return tiePosition; }
    public void setTiePosition(int tiePosition) { this.tiePosition = tiePosition; }
    public String getParticipantsJson() { return participantsJson; }
    public void setParticipantsJson(String participantsJson) { this.participantsJson = participantsJson; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    @Nullable public UUID getTemplateId() { return templateId; }
    public void setTemplateId(@Nullable UUID templateId) { this.templateId = templateId; }
    @Nullable public String getTemplateName() { return templateName; }
    public void setTemplateName(@Nullable String templateName) { this.templateName = templateName; }
    @Nullable public String getProgramSnapshot() { return programSnapshot; }
    public void setProgramSnapshot(@Nullable String programSnapshot) { this.programSnapshot = programSnapshot; }
    @Nullable public UUID getPlayInstanceId() { return playInstanceId; }
    public void setPlayInstanceId(@Nullable UUID playInstanceId) { this.playInstanceId = playInstanceId; }
    @Nullable public String getResultsJson() { return resultsJson; }
    public void setResultsJson(@Nullable String resultsJson) { this.resultsJson = resultsJson; }
    public TiebreakerStatus getStatus() { return status; }
    public void setStatus(TiebreakerStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    @Nullable public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(@Nullable Instant completedAt) { this.completedAt = completedAt; }
}
```

- [ ] **Step 3c: Create `CompetitionTiebreakerRepository.java`**

```java
package ch.jp.shooting.repository;

import ch.jp.shooting.model.CompetitionTiebreaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompetitionTiebreakerRepository extends JpaRepository<CompetitionTiebreaker, UUID> {

    List<CompetitionTiebreaker> findBySessionId(UUID sessionId);

    List<CompetitionTiebreaker> findBySessionIdAndTieGroupIdOrderByRoundNumberAsc(
            UUID sessionId, UUID tieGroupId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=CompetitionTiebreakerRepositoryTest`
Expected: PASS. (Hibernate creates `competition_tiebreakers` automatically under H2 `create-drop`.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/model/TiebreakerStatus.java src/main/java/ch/jp/shooting/model/CompetitionTiebreaker.java src/main/java/ch/jp/shooting/repository/CompetitionTiebreakerRepository.java src/test/java/ch/jp/shooting/repository/CompetitionTiebreakerRepositoryTest.java
git commit -m "[backend] add CompetitionTiebreaker entity, status enum and repository"
```

---

## Task 3: Domain exceptions + handler mapping

**Files:**
- Create: `src/main/java/ch/jp/shooting/exception/TiebreakerNotFoundException.java`
- Create: `src/main/java/ch/jp/shooting/exception/InvalidTiebreakerStateException.java`
- Modify: `src/main/java/ch/jp/shooting/config/GlobalExceptionHandler.java`

- [ ] **Step 1: Create the exceptions**

```java
package ch.jp.shooting.exception;

import java.util.UUID;

public class TiebreakerNotFoundException extends RuntimeException {
    public TiebreakerNotFoundException(UUID id) {
        super("Tiebreaker not found: " + id);
    }
}
```

```java
package ch.jp.shooting.exception;

public class InvalidTiebreakerStateException extends RuntimeException {
    public InvalidTiebreakerStateException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Map them in `GlobalExceptionHandler`**

Open `config/GlobalExceptionHandler.java`. Mirror the existing `@ExceptionHandler` style (each returns a `ProblemDetail` with a `/errors/{slug}` type URI). Add:

```java
@ExceptionHandler(TiebreakerNotFoundException.class)
ProblemDetail handleTiebreakerNotFound(TiebreakerNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setType(URI.create("/errors/tiebreaker-not-found"));
    return pd;
}

@ExceptionHandler(InvalidTiebreakerStateException.class)
ProblemDetail handleInvalidTiebreakerState(InvalidTiebreakerStateException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    pd.setType(URI.create("/errors/invalid-tiebreaker-state"));
    return pd;
}
```

(If `java.net.URI` / `org.springframework.http.HttpStatus` / `ProblemDetail` are not already imported in the file, add them — match the existing handlers exactly.)

- [ ] **Step 3: Compile**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ch/jp/shooting/exception/TiebreakerNotFoundException.java src/main/java/ch/jp/shooting/exception/InvalidTiebreakerStateException.java src/main/java/ch/jp/shooting/config/GlobalExceptionHandler.java
git commit -m "[backend] add tiebreaker exceptions and handler mappings"
```

---

## Task 4: OpenAPI contract — tiebreaker endpoints + finish-guard fields

**Files:**
- Modify: `src/main/resources/static/openapi.yaml`

> Existing competition complete endpoints live around `/api/sessions/{sessionId}/groups/...`. Add the new paths in the `Session` tag area, and the schemas alongside `CompleteSerieRequest` / `SessionProgressResponse`.

- [ ] **Step 1: Add the four paths** (under `paths:`)

```yaml
  /api/sessions/{sessionId}/ties:
    get:
      summary: List tied blocks in the current standings
      description: >
        Returns every group of players sharing an identical total score, with any
        Stechen rounds already run and whether the tie is resolved. Drives the admin
        Stechen UI. Available while the session is PRE_COMPLETE.
      operationId: getSessionTies
      tags: [Session]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200':
          description: Tied blocks
          content:
            application/json:
              schema: { $ref: '#/components/schemas/SessionTiesResponse' }
        '404': { description: Session not found }

  /api/sessions/{sessionId}/tiebreakers:
    get:
      summary: List all Stechen rounds for a session
      operationId: listTiebreakers
      tags: [Session]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200':
          description: Tiebreaker rounds
          content:
            application/json:
              schema:
                type: array
                items: { $ref: '#/components/schemas/TiebreakerResponse' }
        '404': { description: Session not found }
    post:
      summary: Start a Stechen round for a tied block
      description: Allowed only while the session is PRE_COMPLETE. Creates a restricted PlayInstance.
      operationId: startTiebreaker
      tags: [Session]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/StartTiebreakerRequest' }
      responses:
        '201':
          description: Stechen round created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/TiebreakerResponse' }
        '404': { description: Session not found }
        '409': { description: Session not in PRE_COMPLETE, or invalid participants }

  /api/sessions/{sessionId}/tiebreakers/{tiebreakerId}/results:
    post:
      summary: Submit or correct Stechen results for a round
      operationId: submitTiebreakerResults
      tags: [Session]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: tiebreakerId, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/SubmitTiebreakerResultsRequest' }
      responses:
        '200':
          description: Results recorded; updated ties view returned
          content:
            application/json:
              schema: { $ref: '#/components/schemas/SessionTiesResponse' }
        '404': { description: Session or tiebreaker not found }
        '409': { description: Round already completed }
```

- [ ] **Step 2: Add the schemas** (under `components.schemas:`)

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

    SubmitTiebreakerResultsRequest:
      type: object
      required: [results]
      properties:
        results:
          type: array
          items: { $ref: '#/components/schemas/TiebreakerPlayerScore' }

    TiebreakerPlayerScore:
      type: object
      required: [playerId, totalPoints, maxPoints]
      properties:
        playerId: { type: string, format: uuid }
        totalPoints: { type: integer }
        maxPoints: { type: integer }

    TiebreakerResponse:
      type: object
      properties:
        id: { type: string, format: uuid }
        sessionId: { type: string, format: uuid }
        tieGroupId: { type: string, format: uuid }
        roundNumber: { type: integer }
        tiePosition: { type: integer }
        status: { type: string, enum: [PENDING, ACTIVE, COMPLETED] }
        templateType: { type: string }
        templateId: { type: string, format: uuid }
        templateName: { type: string }
        playInstanceId: { type: string, format: uuid, nullable: true }
        participants:
          type: array
          items: { $ref: '#/components/schemas/TiebreakerParticipant' }
        results:
          type: array
          items: { $ref: '#/components/schemas/TiebreakerPlayerScore' }

    TiebreakerParticipant:
      type: object
      properties:
        playerId: { type: string, format: uuid }
        displayName: { type: string }

    SessionTiesResponse:
      type: object
      properties:
        sessionId: { type: string, format: uuid }
        tiedBlocks:
          type: array
          items: { $ref: '#/components/schemas/TiedBlock' }

    TiedBlock:
      type: object
      properties:
        tiePosition: { type: integer }
        sharedScore: { type: integer }
        resolved: { type: boolean, description: true when Stechen has fully ordered the block }
        players:
          type: array
          items: { $ref: '#/components/schemas/TiebreakerParticipant' }
        rounds:
          type: array
          items: { $ref: '#/components/schemas/TiebreakerResponse' }
```

- [ ] **Step 3: Extend the finish flow for the warning guard**

Find the existing status-update request schema used by the PATCH session-status endpoint (`UpdateSessionStatusRequest`). Add an optional `force` flag, and add a `ties` warning to `SessionResponse` is NOT desired — instead the finish endpoint returns `409` with a dedicated body. Add:

```yaml
    UnresolvedTiesError:
      type: object
      properties:
        message: { type: string }
        unresolvedTies:
          type: array
          items: { $ref: '#/components/schemas/TiedBlock' }
```

And in `UpdateSessionStatusRequest.properties` add:

```yaml
        force:
          type: boolean
          description: When true, finishing proceeds despite unresolved decisive ties.
```

On the PATCH session-status operation responses, add:

```yaml
        '409':
          description: Unresolved decisive ties (finish requires force=true)
          content:
            application/json:
              schema: { $ref: '#/components/schemas/UnresolvedTiesError' }
```

- [ ] **Step 4: Regenerate sources**

Run: `./mvnw generate-sources`
Expected: BUILD SUCCESS; new interface `target/generated-sources/openapi/ch/jp/smartground/api/` includes the tiebreaker operations (the operations are tagged `Session`, so they may land in `SessionApi` — confirm the generated interface name before Task 7 and use whatever interface actually carries `startTiebreaker`).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] openapi: tiebreaker endpoints, ties view, finish-guard force flag"
```

---

## Task 5: `TiebreakerService` — list ties, start round (restricted PlayInstance), submit results

**Files:**
- Create: `src/main/java/ch/jp/shooting/service/TiebreakerService.java`
- Test: `src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java`

**Dependencies it uses (already exist):** `LiveSessionRepository`, `PlayerResultRepository`, `SessionPlayerRepository`, `ShooterGroupRepository`, `CompetitionTiebreakerRepository`, `TieResolver`, `SerieRepository`, `PasseRepository`, `PlayInstanceService` (`startPasseInstance(StartPasseInstanceRequest)` / `startBlock(UUID,UUID)`), `ObjectMapper`. Inspect `PlayInstanceService.startPasseInstance` and `StartPasseInstanceRequest` before writing the start-round wiring; build the request from the chosen template's snapshot with `playersJson` = the tied players only.

- [ ] **Step 1: Write the failing test** (Mockito; focus on the testable orchestration — tie listing + results submission + the no-PlayerResult-mutation invariant; mock `PlayInstanceService`)

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TiebreakerServiceTest {

    @Mock LiveSessionRepository sessionRepo;
    @Mock CompetitionTiebreakerRepository tbRepo;
    @Mock PlayerResultRepository playerResultRepo;
    @Mock SessionPlayerRepository playerRepo;
    @Mock PlayInstanceService playInstanceService;
    @Mock SerieRepository serieRepo;
    @Mock PasseRepository passeRepo;
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
        verify(playerResultRepo, never()).save(any()); // invariant: main totals never change
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

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TiebreakerServiceTest`
Expected: FAIL — `TiebreakerService` does not exist.

- [ ] **Step 3: Implement `TiebreakerService`**

Implement these public methods (German domain comments). Use the generated model types from `ch.jp.smartground.model` for request/response DTOs.

```
SessionTiesResponse listTies(UUID sessionId)
TiebreakerResponse  startTiebreaker(UUID sessionId, StartTiebreakerRequest req)
SessionTiesResponse submitResults(UUID sessionId, UUID tiebreakerId, SubmitTiebreakerResultsRequest req)
List<TiebreakerResponse> listTiebreakers(UUID sessionId)
```

Key logic:
- **`listTies`:** load `PlayerResult`s for the session, fold each player's `programResults` into a total (reuse the same summation `CompetitionService.computeLeaderboard` uses), build `TieResolver.PlayerStanding`s, load all tiebreaker rounds, convert them to `TieResolver.TiebreakerRound`s, call `tieResolver.resolve`, then group consecutive `ResolvedStanding`s that share a rank AND are `tied==true` OR belong to a `tieGroupId` into `TiedBlock`s. A block is `resolved` when no member has `tied==true`. Attach the rounds whose `tieGroupId` matches.
- **`startTiebreaker`:**
  1. Guard: `session.getStatus() == PRE_COMPLETE` else throw `InvalidTiebreakerStateException`.
  2. Resolve a `tieGroupId`: if an existing tiebreaker for this session already covers (a superset of) these players at this position, reuse its `tieGroupId` and set `roundNumber = max+1`; else new `tieGroupId`, `roundNumber = 1`.
  3. Load the chosen template (`serieRepo`/`passeRepo` by `templateId`), build `programSnapshot` (a one-Serie passe wrapper when `templateType == serie`).
  4. Create + save the `CompetitionTiebreaker` (status `PENDING`, participants from `req.playerIds`).
  5. Build a `StartPasseInstanceRequest` from the snapshot with players = the tied `SessionPlayer`s; call `playInstanceService.startPasseInstance(...)`; store the returned `instanceId` on the tiebreaker; set status `ACTIVE`; save.
  6. Map to `TiebreakerResponse`.
- **`submitResults`:**
  1. Load tiebreaker; guard `status != COMPLETED` else `InvalidTiebreakerStateException`.
  2. Write `req.results` to `resultsJson`; set status `COMPLETED`, `completedAt = now`. Save. **Never** call `playerResultRepo.save`.
  3. Return `listTies(sessionId)`.

> Helper for the total summation: extract a small private static method `int sumTotal(String programResultsJson, ObjectMapper om)` so it matches `CompetitionService`. (If you prefer, move that summation into `TieResolver` or a shared helper and reuse from both — DRY.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=TiebreakerServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/TiebreakerService.java src/test/java/ch/jp/shooting/service/TiebreakerServiceTest.java
git commit -m "[backend] add TiebreakerService: list ties, start round, submit results"
```

---

## Task 6: Leaderboard tie-resolution in `CompetitionService`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/dto/SessionLeaderboardResponse.java`
- Modify: `src/main/java/ch/jp/shooting/service/CompetitionService.java`
- Test: `src/test/java/ch/jp/shooting/service/CompetitionServiceLeaderboardTest.java`

- [ ] **Step 1: Add fields to `PlayerScoreEntry`**

In `SessionLeaderboardResponse.PlayerScoreEntry` add two boolean fields with getters/setters:

```java
private boolean tied;
private boolean tieResolvedByStechen;

public boolean isTied() { return tied; }
public void setTied(boolean tied) { this.tied = tied; }
public boolean isTieResolvedByStechen() { return tieResolvedByStechen; }
public void setTieResolvedByStechen(boolean v) { this.tieResolvedByStechen = v; }
```

- [ ] **Step 2: Write the failing test** (two equal-score players + a resolving Stechen → ordered ranks, flags set)

```java
package ch.jp.shooting.service;

// Arrange a session with two players tied on total score and one COMPLETED tiebreaker
// round that separates them; call competitionService.computeLeaderboard(sessionId);
// assert the winner has rank 1, loser rank 2, both isTieResolvedByStechen()==true,
// winner.isTied()==false. (Use the same repository wiring style as existing
// CompetitionService tests; inject CompetitionTiebreakerRepository + TieResolver.)
```

(Write concrete arrange/act/assert mirroring `TiebreakerServiceTest`'s mock setup; assert on `computeLeaderboard(...).getPlayerScores()`.)

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw test -Dtest=CompetitionServiceLeaderboardTest`
Expected: FAIL — leaderboard still assigns arbitrary 1..n and ignores Stechen.

- [ ] **Step 4: Implement**

In `CompetitionService`, inject `CompetitionTiebreakerRepository` and `TieResolver` (constructor). Replace the sort/rank block (lines ~92-100) so that instead of sorting by score and assigning `i+1`:
1. Build `TieResolver.PlayerStanding`s from `playerScores`.
2. Load tiebreaker rounds via `tiebreakerRepository.findBySessionId(sessionId)`, map COMPLETED ones to `TieResolver.TiebreakerRound`s (parse `participantsJson` + `resultsJson`).
3. Call `tieResolver.resolve(...)`.
4. Rebuild `sortedScores` in resolved order, set `rank`, `tied`, `tieResolvedByStechen` on each entry.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=CompetitionServiceLeaderboardTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/dto/SessionLeaderboardResponse.java src/main/java/ch/jp/shooting/service/CompetitionService.java src/test/java/ch/jp/shooting/service/CompetitionServiceLeaderboardTest.java
git commit -m "[backend] leaderboard: resolve tied blocks via Stechen rounds"
```

---

## Task 7: `TiebreakerController` + finish guard wiring

**Files:**
- Create: `src/main/java/ch/jp/shooting/api/TiebreakerController.java`
- Modify: `src/main/java/ch/jp/shooting/service/SessionService.java` (finish guard)
- Test: `src/test/java/ch/jp/shooting/service/SessionServiceFinishGuardTest.java`

- [ ] **Step 1: Implement `TiebreakerController`**

Implement the generated interface that carries the tiebreaker operations (confirmed in Task 4 — likely `SessionApi` if the ops were tagged `Session`, otherwise a dedicated `TiebreakerApi`). No `@RequestMapping`/`@GetMapping` on the class or methods. Delegate each method to `TiebreakerService`. Return `ResponseEntity.status(HttpStatus.CREATED)` for `startTiebreaker`, `ResponseEntity.ok(...)` for the rest.

> If the tiebreaker ops landed on the same generated interface as existing session endpoints already implemented by another controller, instead add the delegating methods to that controller (a generated interface must be implemented by exactly one `@RestController`). Decide based on the actual generated interface from Task 4, Step 4.

- [ ] **Step 2: Write the failing finish-guard test**

```java
package ch.jp.shooting.service;

// Given a PRE_COMPLETE session with an unresolved decisive tie (two players tied for 1st,
// no resolving Stechen), calling the finish path with force=false throws/returns the
// UnresolvedTiesError signal; with force=true it transitions to COMPLETED and sets completedAt.
// Mirror the repository wiring of existing SessionService tests; inject the
// CompetitionTiebreakerRepository + TieResolver (or TiebreakerService.listTies) used by the guard.
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw test -Dtest=SessionServiceFinishGuardTest`
Expected: FAIL — finish has no guard yet.

- [ ] **Step 4: Implement the guard**

In `SessionService.patchSessionStatus` (the generated-model variant, ~line 504): when transitioning `PRE_COMPLETE → COMPLETED`, first compute unresolved decisive ties (reuse `TiebreakerService.listTies` / `TieResolver`); if any block with `tiePosition` at a decisive position is unresolved and `req.getForce()` is not true, throw a new `UnresolvedTiesException` carrying the unresolved blocks. Map it in `GlobalExceptionHandler` to `409` with the `UnresolvedTiesError` body. Otherwise proceed with the existing transition.

> "Decisive position" = any tie whose block includes rank 1 for the warning's purpose; lower ties still surface in `/ties` but do not trigger the finish warning. Keep this rule in one private method `boolean isDecisive(TiedBlock b)` so it is easy to adjust.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=SessionServiceFinishGuardTest`
Expected: PASS.

- [ ] **Step 6: Full backend suite + commit**

Run: `./mvnw clean test`
Expected: BUILD SUCCESS, all tests pass.

```bash
git add -A
git commit -m "[backend] add TiebreakerController and PRE_COMPLETE finish guard"
```

- [ ] **Step 7: Record decisions in backend CLAUDE.md**

Add a short subsection under "Domain: Competition / Session" documenting: the `CompetitionTiebreaker` entity, that Stechen results never touch `PlayerResult`, the `tieGroupId`/earliest-decisive-round resolution, and the warn-don't-block finish guard. Commit:

```bash
git add CLAUDE.md
git commit -m "[backend] document Stechen tiebreaker design in CLAUDE.md"
```

---

# Phase 2 — Frontend (`smart-ground-ui`)

> Prerequisite: Phase 1 merged/available so the REST shapes are final. The competition admin store is `stores/competitionEventStore.js` (uses `services/wettkampfApi.js`). Add a sibling `services/tiebreakerApi.js` rather than overloading `wettkampfApi.js`.

## Task 8: `tiebreakerApi.js` service wrappers

**Files:**
- Create: `src/services/tiebreakerApi.js`

- [ ] **Step 1: Implement** (mirror `apiClient` usage from existing services)

```javascript
import { apiClient } from './apiClient.js'

export const getTies = (sessionId) =>
  apiClient.get(`/sessions/${sessionId}/ties`)

export const listTiebreakers = (sessionId) =>
  apiClient.get(`/sessions/${sessionId}/tiebreakers`)

export const startTiebreaker = (sessionId, { playerIds, templateType, templateId, tiePosition }) =>
  apiClient.post(`/sessions/${sessionId}/tiebreakers`, { playerIds, templateType, templateId, tiePosition })

export const submitTiebreakerResults = (sessionId, tiebreakerId, results) =>
  apiClient.post(`/sessions/${sessionId}/tiebreakers/${tiebreakerId}/results`, { results })
```

- [ ] **Step 2: Commit**

```bash
git add src/services/tiebreakerApi.js
git commit -m "[ui] add tiebreakerApi service wrappers"
```

---

## Task 9: Stechen actions + ties state in `competitionEventStore`

**Files:**
- Modify: `src/stores/competitionEventStore.js`
- Test: `src/stores/__tests__/competitionEventStore.stechen.test.js`

- [ ] **Step 1: Write the failing test**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionEventStore } from '../competitionEventStore.js'

vi.mock('@/services/tiebreakerApi.js', () => ({
  getTies: vi.fn(),
  listTiebreakers: vi.fn(),
  startTiebreaker: vi.fn(),
  submitTiebreakerResults: vi.fn(),
}))
vi.mock('@/services/wettkampfApi.js', () => ({
  listSessions: vi.fn(), getSession: vi.fn(), patchStatus: vi.fn(), getProgress: vi.fn(),
  createSession: vi.fn(), deleteSession: vi.fn(), createGroup: vi.fn(), updateGroup: vi.fn(),
  deleteGroup: vi.fn(), addMember: vi.fn(), removeMember: vi.fn(), patchMember: vi.fn(),
  addPasse: vi.fn(), removePasse: vi.fn(),
}))

import * as tb from '@/services/tiebreakerApi.js'

describe('competitionEventStore — Stechen', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('loadTies stores the tied blocks for a session', async () => {
    tb.getTies.mockResolvedValue({ sessionId: 's1', tiedBlocks: [{ tiePosition: 1, sharedScore: 24, resolved: false, players: [], rounds: [] }] })
    const store = useCompetitionEventStore()
    await store.loadTies('s1')
    expect(store.tiesBySession['s1'].tiedBlocks).toHaveLength(1)
  })

  it('startStechen posts and refreshes ties', async () => {
    tb.startTiebreaker.mockResolvedValue({ id: 'tb1', roundNumber: 1 })
    tb.getTies.mockResolvedValue({ sessionId: 's1', tiedBlocks: [] })
    const store = useCompetitionEventStore()
    const res = await store.startStechen('s1', { playerIds: ['p1', 'p2'], templateType: 'passe', templateId: 't1' })
    expect(tb.startTiebreaker).toHaveBeenCalled()
    expect(res.id).toBe('tb1')
    expect(tb.getTies).toHaveBeenCalledWith('s1')
  })

  it('submitStechenResults posts results then refreshes ties', async () => {
    tb.submitTiebreakerResults.mockResolvedValue({ sessionId: 's1', tiedBlocks: [] })
    const store = useCompetitionEventStore()
    await store.submitStechenResults('s1', 'tb1', [{ playerId: 'p1', totalPoints: 8, maxPoints: 10 }])
    expect(tb.submitTiebreakerResults).toHaveBeenCalledWith('s1', 'tb1', [{ playerId: 'p1', totalPoints: 8, maxPoints: 10 }])
    expect(store.tiesBySession['s1'].tiedBlocks).toEqual([])
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/stores/__tests__/competitionEventStore.stechen.test.js`
Expected: FAIL — actions/state not defined.

- [ ] **Step 3: Implement in `competitionEventStore.js`**

Add near the top imports: `import * as tiebreakerApi from '@/services/tiebreakerApi.js'`. Add state `const tiesBySession = ref({})`. Add actions:

```javascript
const loadTies = async (sessionId) => {
  const res = await tiebreakerApi.getTies(sessionId)
  tiesBySession.value = { ...tiesBySession.value, [sessionId]: res }
  return res
}

const startStechen = async (sessionId, payload) => {
  const created = await tiebreakerApi.startTiebreaker(sessionId, payload)
  await loadTies(sessionId)
  return created
}

const submitStechenResults = async (sessionId, tiebreakerId, results) => {
  const updated = await tiebreakerApi.submitTiebreakerResults(sessionId, tiebreakerId, results)
  tiesBySession.value = { ...tiesBySession.value, [sessionId]: updated }
  return updated
}
```

Add a `finishEvent(sessionId, force = false)` action that calls `wettkampfApi.patchStatus(sessionId, 'completed', { force })` (extend `patchStatus` to forward the optional body), catching the `409` and surfacing the `unresolvedTies` to the caller. Export `tiesBySession, loadTies, startStechen, submitStechenResults, finishEvent` in the store's return object.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/stores/__tests__/competitionEventStore.stechen.test.js`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/stores/competitionEventStore.js src/stores/__tests__/competitionEventStore.stechen.test.js
git commit -m "[ui] add Stechen store actions and ties state"
```

---

## Task 10: `WettkampfDetailView` — tie highlighting, Stechen modal, history, finish guard

**Files:**
- Modify: `src/views/admin/WettkampfDetailView.vue`

> No new exact algorithm here — UI wiring to the store. Keep `<script setup>`, German display labels, `storeToRefs` for reactive store state, touch targets ≥48px, focus-visible styles.

- [ ] **Step 1: Load ties when a competition is in PRE_COMPLETE**

In the component's setup, when the viewed session status is `PRE_COMPLETE`, call `store.loadTies(sessionId)` on mount and after any score correction. Read `tiesBySession[sessionId]` via the store.

- [ ] **Step 2: Highlight tied blocks in the standings**

In the standings/leaderboard table for this view, for each `tiedBlock`, visually mark the rows (e.g. a "Gleichstand" badge) and render a "Stechen starten" button per unresolved block (`resolved === false`). Disable the button when a round for that block is already `ACTIVE` (in progress).

- [ ] **Step 3: Stechen start modal**

Add a modal: lists the tied players (read-only), a Serie/Passe template picker (reuse the existing template/passe selection used elsewhere in competition setup), and a "Starten" button calling `store.startStechen(sessionId, { playerIds, templateType, templateId, tiePosition })`. On success, route into / reveal the existing live competition run view restricted to the returned `playInstanceId`.

- [ ] **Step 4: Result entry + history**

After the live run, show the round with an editable score row per player; "Speichern" calls `store.submitStechenResults(...)`. Render a per-tie history list of rounds (round number, participants, scores, resolved order).

- [ ] **Step 5: Finish guard dialog**

Wire the "Wettkampf abschliessen" button to `store.finishEvent(sessionId, false)`. On the `409`/unresolved-ties result, open a confirmation dialog listing the unresolved ties; "Trotzdem abschliessen" calls `store.finishEvent(sessionId, true)`.

- [ ] **Step 6: Lint + build**

Run: `npm run lint && npm run build`
Expected: no ESLint warnings; build succeeds.

- [ ] **Step 7: Commit**

```bash
git add src/views/admin/WettkampfDetailView.vue
git commit -m "[ui] WettkampfDetailView: tie highlighting, Stechen flow, finish guard"
```

---

## Task 11: Full-suite verification

- [ ] **Step 1: Backend**

Run (in `smart-ground-backend`): `./mvnw clean test`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Frontend**

Run (in `smart-ground-ui`): `npm run test && npm run lint:check && npm run build`
Expected: all tests pass, no lint errors, build succeeds.

- [ ] **Step 3: Manual smoke (optional, requires running stack)**

Bring a competition to `PRE_COMPLETE` with two players tied for 1st → start a Stechen → run it live → enter results → confirm the leaderboard re-orders and the finish guard clears.

---

## Self-Review Notes (for the implementer)

- The whole `TieResolver` contract (records, method) is fixed in Task 1 and reused verbatim in Tasks 5, 6, 7 — do not rename.
- `tied` vs `tieResolvedByStechen`: `tied` = still sharing a rank after all rounds; `tieResolvedByStechen` = this player's block had at least one Stechen round. Both can be true (a 3-way tie partially resolved).
- Confirm the actual generated interface name from Task 4 Step 4 before writing the controller in Task 7.
- The summation of `programResults` into a total must be identical in `CompetitionService` and `TiebreakerService` — extract one shared helper (DRY).

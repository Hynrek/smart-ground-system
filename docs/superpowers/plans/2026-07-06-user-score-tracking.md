# User Score Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist per-user Serie scores (training + Wettkampf) in a queryable projection table and expose personal history/summary + leaderboard endpoints with UI.

**Architecture:** New `user_serie_scores` table — one row per user × completed Serie, written in-transaction by the two existing completion paths (`PlayInstanceService.completeBlock` on full instance completion; `CompetitionProgressService.completeSerie`/`correctSerie`). Passe/Wettkampf scores are aggregations computed in the service, never stored. Contract-first API (`openapi.yaml` → generated interfaces), then UI via service → store → view chain.

**Tech Stack:** Spring Boot 4 / Java 25 / JPA (Hibernate auto-DDL, pre-v1.0), OpenAPI generator, JUnit 5 + Mockito; Vue 3 `<script setup>`, Pinia 3, Vitest 4.

**Spec:** `docs/superpowers/specs/2026-07-06-user-score-tracking-design.md`. Two additive deviations, both display-driven: (1) extra column `parent_name` (training: PlayInstance templateName; competition: session name) so grouped Passe/Wettkampf lists have labels without joins; (2) leaderboard `period` param realized as optional `from` date-time filter.

## Global Constraints

- Backend build/tests: use system `mvn` from `smart-ground-backend/` (the `mvnw` wrapper is broken — never call `./mvnw`).
- API changes: edit `src/main/resources/static/openapi.yaml` FIRST, then `mvn generate-sources`, then implement.
- Backend domain comments in German; test code and frontend comments in English.
- UI: no direct API calls in components; `@/` import alias; design tokens `--sg-*` only; German display labels; touch targets ≥48px in shooter views.
- Step rendering in UI must import from `constants/stepModes.js`.
- Remove replaced code eagerly (the `/users/me/play-results` LIKE-scan path is deleted, backend + UI).
- Commit prefix: `[backend]` / `[ui]` per sub-project.
- All paths below are relative to the monorepo root `C:\Users\hynre\IdeaProjects\Smart Ground\`.

---

### Task 1: Backend — `UserSerieScore` entity + repository

**Files:**
- Create: `smart-ground-backend/src/main/java/ch/jp/shooting/model/UserSerieScore.java`
- Create: `smart-ground-backend/src/main/java/ch/jp/shooting/repository/UserSerieScoreRepository.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/repository/UserSerieScoreRepositoryTest.java`

**Interfaces:**
- Produces: entity `UserSerieScore` (fields below, plain getters/setters, `userId` is a plain UUID column — no `@ManyToOne`), repository methods `findBySourceIdAndUserId(UUID, UUID)`, `findBySourceId(UUID)`, `findByUserIdOrderByCompletedAtDesc(UUID)`, `findFiltered(UUID userId, String context, UUID serieId, Instant from, Instant to, Pageable)`, `findForLeaderboard(String context, UUID serieId, UUID rangeId, Instant from)`.

- [ ] **Step 1: Write the failing repository test**

Mirror the test setup annotations of `smart-ground-backend/src/test/java/ch/jp/shooting/repository/CompetitionTiebreakerRepositoryTest.java` (open it and copy its class-level annotations exactly — e.g. `@DataJpaTest`). Content:

```java
package ch.jp.shooting.repository;

import ch.jp.shooting.model.UserSerieScore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserSerieScoreRepositoryTest {

    @Autowired UserSerieScoreRepository repository;

    private UserSerieScore score(UUID userId, UUID sourceId, String context, int points, Instant completedAt) {
        var s = new UserSerieScore();
        s.setUserId(userId);
        s.setSourceId(sourceId);
        s.setContext(context);
        s.setTotalPoints(points);
        s.setMaxPoints(10);
        s.setSerieId(UUID.randomUUID());
        s.setSerieAlias("Serie A");
        s.setCompletedAt(completedAt);
        return s;
    }

    @Test
    void uniqueConstraint_rejectsDuplicateSourceAndUser() {
        var userId = UUID.randomUUID();
        var sourceId = UUID.randomUUID();
        repository.saveAndFlush(score(userId, sourceId, "TRAINING", 5, Instant.now()));
        assertThrows(DataIntegrityViolationException.class,
            () -> repository.saveAndFlush(score(userId, sourceId, "TRAINING", 7, Instant.now())));
    }

    @Test
    void findFiltered_appliesNullableFilters() {
        var userId = UUID.randomUUID();
        var now = Instant.now();
        repository.save(score(userId, UUID.randomUUID(), "TRAINING", 5, now.minusSeconds(3600)));
        repository.save(score(userId, UUID.randomUUID(), "COMPETITION", 8, now));
        repository.save(score(UUID.randomUUID(), UUID.randomUUID(), "TRAINING", 9, now));

        // no filters: both rows of this user, newest first
        var all = repository.findFiltered(userId, null, null,
            Instant.EPOCH, now.plusSeconds(60), PageRequest.of(0, 20));
        assertEquals(2, all.getTotalElements());
        assertEquals("COMPETITION", all.getContent().get(0).getContext());

        // context filter
        var training = repository.findFiltered(userId, "TRAINING", null,
            Instant.EPOCH, now.plusSeconds(60), PageRequest.of(0, 20));
        assertEquals(1, training.getTotalElements());

        // time window excludes the older row
        var recent = repository.findFiltered(userId, null, null,
            now.minusSeconds(60), now.plusSeconds(60), PageRequest.of(0, 20));
        assertEquals(1, recent.getTotalElements());
    }

    @Test
    void findForLeaderboard_filtersByContextAndFrom() {
        var now = Instant.now();
        var s1 = score(UUID.randomUUID(), UUID.randomUUID(), "TRAINING", 5, now);
        s1.setRangeId(UUID.randomUUID());
        repository.save(s1);
        repository.save(score(UUID.randomUUID(), UUID.randomUUID(), "COMPETITION", 8, now));

        assertEquals(1, repository.findForLeaderboard("TRAINING", null, null, Instant.EPOCH).size());
        assertEquals(2, repository.findForLeaderboard(null, null, null, Instant.EPOCH).size());
        assertEquals(0, repository.findForLeaderboard(null, null, null, now.plusSeconds(60)).size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `smart-ground-backend/`: `mvn test -Dtest=UserSerieScoreRepositoryTest`
Expected: COMPILE ERROR — `UserSerieScore` / `UserSerieScoreRepository` do not exist.

- [ ] **Step 3: Create the entity**

```java
package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Projektion: ein abgeschlossenes Serie-Ergebnis eines registrierten Users.
 * Atom der Score-Verfolgung — Passe-/Wettkampf-Totale werden per Aggregation
 * berechnet, nie gespeichert. Quelle bleibt PlayInstance.stateJson bzw.
 * CompetitionSerieResult; diese Tabelle ist daraus wiederherstellbar.
 */
@Entity
@Table(
    name = "user_serie_scores",
    uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "user_id"}),
    indexes = {
        @Index(name = "idx_uss_user_completed", columnList = "user_id, completed_at DESC"),
        @Index(name = "idx_uss_serie_points", columnList = "serie_id, total_points DESC")
    })
@NullMarked
public class UserSerieScore {

    public UserSerieScore() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 'TRAINING' | 'COMPETITION' */
    @Column(nullable = false, length = 12)
    private String context;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Column(name = "max_points", nullable = false)
    private int maxPoints;

    /** Eigenständige Kopie der StepStates des Spielers (StepStateRecord[] als JSON). */
    @Column(name = "step_states_json", columnDefinition = "TEXT")
    @Nullable
    private String stepStatesJson;

    @Column(name = "serie_id", nullable = false)
    private UUID serieId;

    @Column(name = "serie_alias", nullable = false)
    private String serieAlias;

    /** Training: blockId; Wettkampf: CompetitionSerieResult-Id. Idempotenz-Schlüssel. */
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    /** Gruppiert Trainings-Zeilen zu einer Passe. */
    @Column(name = "play_instance_id")
    @Nullable
    private UUID playInstanceId;

    @Column(name = "session_id")
    @Nullable
    private UUID sessionId;

    @Column(name = "group_id")
    @Nullable
    private UUID groupId;

    @Column(name = "passe_index")
    @Nullable
    private Integer passeIndex;

    /** Anzeige-Label des Parents: Training = templateName, Wettkampf = Session-Name. */
    @Column(name = "parent_name")
    @Nullable
    private String parentName;

    @Column(name = "range_id")
    @Nullable
    private UUID rangeId;

    @Column(name = "range_name")
    @Nullable
    private String rangeName;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public int getMaxPoints() { return maxPoints; }
    public void setMaxPoints(int maxPoints) { this.maxPoints = maxPoints; }
    public @Nullable String getStepStatesJson() { return stepStatesJson; }
    public void setStepStatesJson(@Nullable String stepStatesJson) { this.stepStatesJson = stepStatesJson; }
    public UUID getSerieId() { return serieId; }
    public void setSerieId(UUID serieId) { this.serieId = serieId; }
    public String getSerieAlias() { return serieAlias; }
    public void setSerieAlias(String serieAlias) { this.serieAlias = serieAlias; }
    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID sourceId) { this.sourceId = sourceId; }
    public @Nullable UUID getPlayInstanceId() { return playInstanceId; }
    public void setPlayInstanceId(@Nullable UUID playInstanceId) { this.playInstanceId = playInstanceId; }
    public @Nullable UUID getSessionId() { return sessionId; }
    public void setSessionId(@Nullable UUID sessionId) { this.sessionId = sessionId; }
    public @Nullable UUID getGroupId() { return groupId; }
    public void setGroupId(@Nullable UUID groupId) { this.groupId = groupId; }
    public @Nullable Integer getPasseIndex() { return passeIndex; }
    public void setPasseIndex(@Nullable Integer passeIndex) { this.passeIndex = passeIndex; }
    public @Nullable String getParentName() { return parentName; }
    public void setParentName(@Nullable String parentName) { this.parentName = parentName; }
    public @Nullable UUID getRangeId() { return rangeId; }
    public void setRangeId(@Nullable UUID rangeId) { this.rangeId = rangeId; }
    public @Nullable String getRangeName() { return rangeName; }
    public void setRangeName(@Nullable String rangeName) { this.rangeName = rangeName; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
```

- [ ] **Step 4: Create the repository**

```java
package ch.jp.shooting.repository;

import ch.jp.shooting.model.UserSerieScore;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NullMarked
public interface UserSerieScoreRepository extends JpaRepository<UserSerieScore, UUID> {

    Optional<UserSerieScore> findBySourceIdAndUserId(UUID sourceId, UUID userId);

    List<UserSerieScore> findBySourceId(UUID sourceId);

    List<UserSerieScore> findByUserIdOrderByCompletedAtDesc(UUID userId);

    // from/to sind im Service immer gesetzt (EPOCH/now-Defaults) — vermeidet
    // typisierte Null-Parameter auf Timestamps; context/serieId bleiben optional.
    @Query("select s from UserSerieScore s where s.userId = :userId"
        + " and (:context is null or s.context = :context)"
        + " and (:serieId is null or s.serieId = :serieId)"
        + " and s.completedAt >= :from and s.completedAt <= :to"
        + " order by s.completedAt desc")
    Page<UserSerieScore> findFiltered(@Param("userId") UUID userId,
                                      @Param("context") @Nullable String context,
                                      @Param("serieId") @Nullable UUID serieId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to,
                                      Pageable pageable);

    @Query("select s from UserSerieScore s where"
        + " (:context is null or s.context = :context)"
        + " and (:serieId is null or s.serieId = :serieId)"
        + " and (:rangeId is null or s.rangeId = :rangeId)"
        + " and s.completedAt >= :from")
    List<UserSerieScore> findForLeaderboard(@Param("context") @Nullable String context,
                                            @Param("serieId") @Nullable UUID serieId,
                                            @Param("rangeId") @Nullable UUID rangeId,
                                            @Param("from") Instant from);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=UserSerieScoreRepositoryTest`
Expected: PASS (3 tests). If the `:serieId is null` UUID null-check trips Hibernate parameter typing on H2, replace those UUID params with `String` params compared via `cast(s.serieId as string)` — but try the straightforward form first.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/model/UserSerieScore.java smart-ground-backend/src/main/java/ch/jp/shooting/repository/UserSerieScoreRepository.java smart-ground-backend/src/test/java/ch/jp/shooting/repository/UserSerieScoreRepositoryTest.java
git commit -m "[backend] add UserSerieScore projection entity and repository"
```

---

### Task 2: Backend — `UserScoreService` write paths

**Files:**
- Create: `smart-ground-backend/src/main/java/ch/jp/shooting/service/UserScoreService.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/UserScoreServiceTest.java`

**Interfaces:**
- Consumes: `UserSerieScoreRepository` (Task 1), `PlayMapper.parseBlocks(String)` → `List<PlayBlockRecord>`, `PlayBlockRecord(blockId, serieId, serieAlias, rangeId, rangeName, steps, status, completedAt, result)`, `BlockResultRecord.playerResults()` → `List<PlayerResultRecord>`, `PlayerResultRecord(playerId, displayName, totalPoints, maxPoints, stepStates, userId)`.
- Produces: `public void recordTrainingInstance(PlayInstance instance)` and `public void recordCompetitionSerie(CompetitionSerieResult csr, ShooterGroup group, String serieAlias, List<ch.jp.smartground.model.PlayerResult> results, boolean replaceExisting)` — both upsert on (`sourceId`, `userId`), skip players without userId.

- [ ] **Step 1: Write the failing tests**

```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.*;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.UserSerieScoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserScoreServiceTest {

    @Mock UserSerieScoreRepository scoreRepository;
    @Mock ch.jp.shooting.repository.auth.UserRepository userRepository;
    @Mock ch.jp.shooting.config.SecurityHelper securityHelper;

    UserScoreService service() {
        return new UserScoreService(scoreRepository, userRepository, securityHelper, new ObjectMapper());
    }

    // ── Training ──

    private PlayInstance completedInstance(UUID blockId, UUID userId) {
        var inst = new PlayInstance();
        inst.setInstanceId(UUID.randomUUID());
        inst.setType("passe");
        inst.setTemplateId(UUID.randomUUID());
        inst.setTemplateName("Trainings-Passe");
        inst.setStatus("completed");
        inst.setOwner(mock(User.class));
        inst.setCompletedAt(Instant.now());
        var serieId = UUID.randomUUID();
        // one done block with two players: one with userId, one anonymous
        inst.setStateJson("[{\"blockId\":\"" + blockId + "\",\"serieId\":\"" + serieId
            + "\",\"serieAlias\":\"Serie 1\",\"rangeId\":null,\"rangeName\":\"Platz 1\",\"steps\":[],"
            + "\"status\":\"done\",\"completedAt\":\"" + Instant.now() + "\",\"result\":{\"playerResults\":["
            + "{\"playerId\":\"p1\",\"displayName\":\"Anna\",\"totalPoints\":8,\"maxPoints\":10,"
            + "\"stepStates\":[],\"userId\":\"" + userId + "\"},"
            + "{\"playerId\":\"p2\",\"displayName\":\"Gast\",\"totalPoints\":3,\"maxPoints\":10,"
            + "\"stepStates\":[]}"
            + "]}}]");
        return inst;
    }

    @Test
    void recordTrainingInstance_writesRowOnlyForPlayersWithUserId() {
        var blockId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(Optional.empty());

        service().recordTrainingInstance(completedInstance(blockId, userId));

        var captor = ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository, times(1)).save(captor.capture());
        var row = captor.getValue();
        assertEquals(userId, row.getUserId());
        assertEquals("TRAINING", row.getContext());
        assertEquals(8, row.getTotalPoints());
        assertEquals(10, row.getMaxPoints());
        assertEquals(blockId, row.getSourceId());
        assertEquals("Serie 1", row.getSerieAlias());
        assertEquals("Trainings-Passe", row.getParentName());
        assertEquals("Platz 1", row.getRangeName());
        assertNotNull(row.getPlayInstanceId());
        assertNull(row.getSessionId());
    }

    @Test
    void recordTrainingInstance_isIdempotentPerSourceAndUser() {
        var blockId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var existing = new UserSerieScore();
        existing.setUserId(userId);
        existing.setSourceId(blockId);
        when(scoreRepository.findBySourceIdAndUserId(blockId, userId)).thenReturn(Optional.of(existing));

        service().recordTrainingInstance(completedInstance(blockId, userId));

        // existing row is updated (same object saved), no second row created
        var captor = ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository, times(1)).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertEquals(8, captor.getValue().getTotalPoints());
    }

    // ── Wettkampf ──

    private ShooterGroup groupWithMember(UUID memberId, UUID userId) {
        var user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        var member = mock(SessionPlayer.class);
        lenient().when(member.getId()).thenReturn(memberId);
        lenient().when(member.getUser()).thenReturn(user);
        var group = mock(ShooterGroup.class);
        lenient().when(group.getId()).thenReturn(UUID.randomUUID());
        lenient().when(group.getMembers()).thenReturn(List.of(member));
        return group;
    }

    private CompetitionSerieResult csr(ShooterGroup group) {
        var session = mock(LiveSession.class);
        lenient().when(session.getId()).thenReturn(UUID.randomUUID());
        lenient().when(session.getName()).thenReturn("Feldschiessen");
        var result = new CompetitionSerieResult(session, group, 1, UUID.randomUUID());
        result.setCompletedAt(Instant.now());
        return result;
    }

    @Test
    void recordCompetitionSerie_resolvesUserViaGroupMember() {
        var memberId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var group = groupWithMember(memberId, userId);
        var result = csr(group);
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(Optional.empty());

        var pr = new ch.jp.smartground.model.PlayerResult()
            .playerId(memberId.toString()).displayName("Anna")
            .totalPoints(7).maxPoints(10);
        service().recordCompetitionSerie(result, group, "Stich-Serie", List.of(pr), false);

        var captor = ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository).save(captor.capture());
        var row = captor.getValue();
        assertEquals(userId, row.getUserId());
        assertEquals("COMPETITION", row.getContext());
        assertEquals(7, row.getTotalPoints());
        assertEquals(1, row.getPasseIndex());
        assertEquals("Feldschiessen", row.getParentName());
        assertEquals("Stich-Serie", row.getSerieAlias());
    }

    @Test
    void recordCompetitionSerie_skipsGuestsWithoutUser() {
        var memberId = UUID.randomUUID();
        var member = mock(SessionPlayer.class);
        lenient().when(member.getId()).thenReturn(memberId);
        lenient().when(member.getUser()).thenReturn(null);
        var group = mock(ShooterGroup.class);
        lenient().when(group.getMembers()).thenReturn(List.of(member));
        var result = csr(group);

        var pr = new ch.jp.smartground.model.PlayerResult()
            .playerId(memberId.toString()).displayName("Gast").totalPoints(4).maxPoints(10);
        service().recordCompetitionSerie(result, group, "Serie", List.of(pr), false);

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void recordCompetitionSerie_correctionRemovesStaleRows() {
        var memberId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var group = groupWithMember(memberId, userId);
        var result = csr(group);
        // pretend the csr already has an id and one stale row of another user
        var stale = new UserSerieScore();
        stale.setUserId(UUID.randomUUID());
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(scoreRepository.findBySourceId(any())).thenReturn(List.of(stale));

        var pr = new ch.jp.smartground.model.PlayerResult()
            .playerId(memberId.toString()).displayName("Anna").totalPoints(9).maxPoints(10);
        service().recordCompetitionSerie(result, group, "Serie", List.of(pr), true);

        verify(scoreRepository).delete(stale);
        verify(scoreRepository).save(any(UserSerieScore.class));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=UserScoreServiceTest`
Expected: COMPILE ERROR — `UserScoreService` does not exist.

- [ ] **Step 3: Implement the service (write half)**

Check the actual package/import of `UserRepository` (`ch.jp.shooting.repository.auth.UserRepository` — verify, it is used by `CustomUserDetailsService`) and `SecurityHelper` (`ch.jp.shooting.config.SecurityHelper`, as imported by `PlayInstanceServiceTest`). Then:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.CompetitionSerieResult;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.SessionPlayer;
import ch.jp.shooting.model.ShooterGroup;
import ch.jp.shooting.model.UserSerieScore;
import ch.jp.shooting.repository.UserSerieScoreRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Schreibt und liest die User-Score-Projektion (user_serie_scores).
 * Eine Zeile pro User × abgeschlossener Serie; Upsert über (sourceId, userId).
 * Nur registrierte User (userId vorhanden) erhalten Zeilen.
 */
@Service
@Transactional
@NullMarked
public class UserScoreService {

    private final UserSerieScoreRepository scoreRepository;
    private final UserRepository userRepository;
    private final SecurityHelper securityHelper;
    private final ObjectMapper objectMapper;

    public UserScoreService(UserSerieScoreRepository scoreRepository,
                            UserRepository userRepository,
                            SecurityHelper securityHelper,
                            ObjectMapper objectMapper) {
        this.scoreRepository = scoreRepository;
        this.userRepository = userRepository;
        this.securityHelper = securityHelper;
        this.objectMapper = objectMapper;
    }

    /** Training: beim Abschluss der ganzen Instanz eine Zeile pro Block × User schreiben. */
    public void recordTrainingInstance(PlayInstance instance) {
        for (var block : PlayMapper.parseBlocks(instance.getStateJson())) {
            if (block.result() == null) continue;
            for (var pr : block.result().playerResults()) {
                if (pr.userId() == null) continue; // anonyme Spieler und Gäste überspringen
                var row = scoreRepository.findBySourceIdAndUserId(block.blockId(), pr.userId())
                    .orElseGet(UserSerieScore::new);
                row.setUserId(pr.userId());
                row.setContext("TRAINING");
                row.setTotalPoints(pr.totalPoints());
                row.setMaxPoints(pr.maxPoints());
                row.setStepStatesJson(writeJson(pr.stepStates()));
                row.setSerieId(block.serieId());
                row.setSerieAlias(block.serieAlias());
                row.setSourceId(block.blockId());
                row.setPlayInstanceId(instance.getInstanceId());
                row.setParentName(instance.getTemplateName());
                row.setRangeId(block.rangeId());
                row.setRangeName(block.rangeName());
                row.setCompletedAt(block.completedAt() != null ? block.completedAt()
                    : instance.getCompletedAt() != null ? instance.getCompletedAt() : java.time.Instant.now());
                scoreRepository.save(row);
            }
        }
    }

    /** Wettkampf: Zeilen beim Serie-Abschluss; Korrektur ersetzt per Upsert und löscht Verwaiste. */
    public void recordCompetitionSerie(CompetitionSerieResult csr, ShooterGroup group,
                                       String serieAlias,
                                       List<ch.jp.smartground.model.PlayerResult> results,
                                       boolean replaceExisting) {
        var writtenUserIds = new java.util.HashSet<UUID>();
        for (var pr : results) {
            UUID userId = resolveUserId(group, pr);
            if (userId == null) continue;
            writtenUserIds.add(userId);
            var row = scoreRepository.findBySourceIdAndUserId(csr.getId(), userId)
                .orElseGet(UserSerieScore::new);
            row.setUserId(userId);
            row.setContext("COMPETITION");
            row.setTotalPoints(pr.getTotalPoints() != null ? pr.getTotalPoints() : 0);
            row.setMaxPoints(pr.getMaxPoints() != null ? pr.getMaxPoints() : 0);
            row.setStepStatesJson(writeJson(pr.getStepStates()));
            row.setSerieId(csr.getSerieId());
            row.setSerieAlias(serieAlias);
            row.setSourceId(csr.getId());
            row.setSessionId(csr.getSession().getId());
            row.setGroupId(group.getId());
            row.setPasseIndex(csr.getPasseIndex());
            row.setParentName(csr.getSession().getName());
            row.setCompletedAt(csr.getCompletedAt());
            scoreRepository.save(row);
        }
        if (replaceExisting) {
            // Zeilen von Usern entfernen, die im korrigierten Resultat fehlen
            for (var stale : scoreRepository.findBySourceId(csr.getId())) {
                if (!writtenUserIds.contains(stale.getUserId())) {
                    scoreRepository.delete(stale);
                }
            }
        }
    }

    /** userId über das Gruppenmitglied auflösen; Fallback: userId aus dem Request. */
    @Nullable
    private UUID resolveUserId(ShooterGroup group, ch.jp.smartground.model.PlayerResult pr) {
        for (SessionPlayer member : group.getMembers()) {
            if (member.getId().toString().equals(pr.getPlayerId())) {
                return member.getUser() != null ? member.getUser().getId() : pr.getUserId();
            }
        }
        return pr.getUserId();
    }

    @Nullable
    private String writeJson(@Nullable Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null; // StepStates sind Zusatzinfo — Abschluss nicht daran scheitern lassen
        }
    }
}
```

Note: `recordCompetitionSerie` takes `serieAlias` as a parameter (the caller resolves it — Task 4). The test in Step 1 already passes it.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=UserScoreServiceTest`
Expected: PASS (5 tests). If `CompetitionSerieResult` mock setup fails because `getId()` returns null before persist, adjust the test to use a spy or set the id via reflection — but the code path only reads `csr.getId()` for the upsert key, and Mockito `Optional.empty()` stubbing tolerates null keys.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/service/UserScoreService.java smart-ground-backend/src/test/java/ch/jp/shooting/service/UserScoreServiceTest.java
git commit -m "[backend] UserScoreService: write user serie score rows for training and competition"
```

---

### Task 3: Backend — hook training completion in `PlayInstanceService`

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/PlayInstanceService.java` (constructor + `completeBlock`, around lines 178-219)
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java` (add tests)

**Interfaces:**
- Consumes: `UserScoreService.recordTrainingInstance(PlayInstance)` (Task 2).

- [ ] **Step 1: Add failing tests to `PlayInstanceServiceTest`**

Add a `@Mock UserScoreService userScoreService;` field next to the existing mocks (Mockito `@InjectMocks` picks it up once the constructor gains the parameter). Add these tests:

```java
@Test
void completeBlock_onLastBlock_recordsUserScores() {
    var instanceId = UUID.randomUUID();
    var blockId = UUID.randomUUID();
    var inst = new PlayInstance();
    inst.setInstanceId(instanceId);
    inst.setType("passe");
    inst.setTemplateId(UUID.randomUUID());
    inst.setTemplateName("T");
    inst.setStatus("active");
    inst.setOwner(mock(User.class));
    inst.setPlayersJson("[]");
    inst.setStateJson("[{\"blockId\":\"" + blockId + "\",\"serieId\":\"" + UUID.randomUUID()
        + "\",\"serieAlias\":\"S\",\"steps\":[],\"status\":\"in_progress\"}]");
    when(playInstanceRepository.findById(instanceId)).thenReturn(java.util.Optional.of(inst));
    when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    var request = new ch.jp.smartground.model.CompleteBlockRequest()
        .playerResults(List.of(new ch.jp.smartground.model.PlayerResult()
            .playerId("p1").displayName("Anna").totalPoints(5).maxPoints(10)
            .userId(UUID.randomUUID())));
    service.completeBlock(instanceId, blockId, request);

    verify(userScoreService).recordTrainingInstance(inst);
}

@Test
void completeBlock_whenBlocksRemain_doesNotRecordScores() {
    var instanceId = UUID.randomUUID();
    var blockId = UUID.randomUUID();
    var inst = new PlayInstance();
    inst.setInstanceId(instanceId);
    inst.setType("passe");
    inst.setTemplateId(UUID.randomUUID());
    inst.setTemplateName("T");
    inst.setStatus("active");
    inst.setOwner(mock(User.class));
    inst.setPlayersJson("[]");
    inst.setStateJson("[{\"blockId\":\"" + blockId + "\",\"serieId\":\"" + UUID.randomUUID()
        + "\",\"serieAlias\":\"S1\",\"steps\":[],\"status\":\"in_progress\"},"
        + "{\"blockId\":\"" + UUID.randomUUID() + "\",\"serieId\":\"" + UUID.randomUUID()
        + "\",\"serieAlias\":\"S2\",\"steps\":[],\"status\":\"pending\"}]");
    when(playInstanceRepository.findById(instanceId)).thenReturn(java.util.Optional.of(inst));
    when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    var request = new ch.jp.smartground.model.CompleteBlockRequest()
        .playerResults(List.of(new ch.jp.smartground.model.PlayerResult()
            .playerId("p1").displayName("Anna").totalPoints(5).maxPoints(10)));
    service.completeBlock(instanceId, blockId, request);

    verify(userScoreService, never()).recordTrainingInstance(any());
}
```

If `CompleteBlockRequest.playerResults` items use a different generated type than `PlayerResult` (check the generated `CompleteBlockRequest` under `target/generated-sources`), adapt the builder calls to that type — the fields (`playerId`, `displayName`, `totalPoints`, `maxPoints`, `userId`, `stepStates`) are the same.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=PlayInstanceServiceTest`
Expected: COMPILE ERROR (`userScoreService` unknown) or failure — service constructor lacks the dependency.

- [ ] **Step 3: Wire the hook**

In `PlayInstanceService`: add a `private final UserScoreService userScoreService;` field and constructor parameter (append last). In `completeBlock`, extend the completion branch:

```java
        instance.setStateJson(PlayMapper.writeBlocks(blocks));
        if (blocks.stream().allMatch(b -> "done".equals(b.status()))) {
            instance.setStatus("completed");
            instance.setCompletedAt(Instant.now());
            // Score-Projektion schreiben: eine Zeile pro Block × registriertem User
            userScoreService.recordTrainingInstance(instance);
        }

        return PlayMapper.toPlayInstanceResponse(playInstanceRepository.save(instance));
```

Note: `recordTrainingInstance` runs before `save`, on the managed/updated `instance` whose `stateJson` was just written — the parse sees all blocks `done`. Same transaction, so a score-write failure rolls back the completion (spec decision).

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=PlayInstanceServiceTest`
Expected: PASS (all existing + 2 new).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/service/PlayInstanceService.java smart-ground-backend/src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java
git commit -m "[backend] record user serie scores on training play completion"
```

---

### Task 4: Backend — hook Wettkampf completion in `CompetitionProgressService`

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/CompetitionProgressService.java` (constructor; `completeSerie` after line ~90; `correctSerie` after line ~144)
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java` (add tests)

**Interfaces:**
- Consumes: `UserScoreService.recordCompetitionSerie(CompetitionSerieResult, ShooterGroup, String serieAlias, List<PlayerResult>, boolean replaceExisting)` (Task 2).

- [ ] **Step 1: Add failing tests**

Open `CompetitionProgressServiceTest` and study how it constructs the service and stubs `sessionRepository`/`groupRepository`/`csrRepository` for the existing `completeSerie` tests. Add a `@Mock UserScoreService userScoreService;` and two tests reusing the test class's existing fixture helpers (session, group, request):

```java
@Test
void completeSerie_recordsUserScores() throws Exception {
    // reuse the existing arrange helpers of this test class for a valid completeSerie call
    // ... arrange as in the existing happy-path completeSerie test ...
    service.completeSerie(sessionId, groupId, serieId, request);
    verify(userScoreService).recordCompetitionSerie(
        any(CompetitionSerieResult.class), any(ShooterGroup.class), anyString(), anyList(), eq(false));
}

@Test
void correctSerie_recordsUserScoresWithReplace() throws Exception {
    // ... arrange as in the existing happy-path correctSerie test ...
    service.correctSerie(sessionId, groupId, serieId, request);
    verify(userScoreService).recordCompetitionSerie(
        any(CompetitionSerieResult.class), any(ShooterGroup.class), anyString(), anyList(), eq(true));
}
```

The arrange sections must copy the stubbing of the nearest existing happy-path test verbatim (session in the right status, group found, results present) — the only new assertion is the `verify` on `userScoreService`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=CompetitionProgressServiceTest`
Expected: COMPILE ERROR — constructor lacks `UserScoreService`.

- [ ] **Step 3: Wire the hooks**

Add `private final UserScoreService userScoreService;` + constructor parameter (append last; update the test's service construction if it calls the constructor manually).

In `completeSerie`, directly after `writePlayerResults(session, group, passeIndex, serieId, request.getResults(), false);`:

```java
        // Score-Projektion: eine Zeile pro registriertem User dieser Serie
        userScoreService.recordCompetitionSerie(csr, group, resolveSerieAlias(serieId),
            request.getResults(), false);
```

In `correctSerie`, directly after `writePlayerResults(session, group, passeIndex, serieId, request.getResults(), true);`:

```java
        userScoreService.recordCompetitionSerie(csr, group, resolveSerieAlias(serieId),
            request.getResults(), true);
```

Add the alias resolver helper (uses the already-injected `serieRepository`; check the `Serie` entity for its name accessor — `getAlias()` or `getName()` — and use that one):

```java
    /** Anzeigename der Serie zum Abschlusszeitpunkt; Fallback auf die Id. */
    private String resolveSerieAlias(UUID serieId) {
        return serieRepository.findById(serieId)
            .map(s -> s.getAlias() != null ? s.getAlias() : serieId.toString())
            .orElse(serieId.toString());
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=CompetitionProgressServiceTest`
Expected: PASS (all existing + 2 new).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/service/CompetitionProgressService.java smart-ground-backend/src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java
git commit -m "[backend] record user serie scores on competition serie completion and correction"
```

---

### Task 5: Backend — OpenAPI contract for score endpoints (and retire play-results-me)

**Files:**
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml`

**Interfaces:**
- Produces (generated after `mvn generate-sources`): interface `ScoreApi` with `listMyScores(String context, UUID serieId, OffsetDateTime from, OffsetDateTime to, Integer page, Integer size)` → `UserScorePage`; `getMyScoreSummary()` → `UserScoreSummary`; `getScoreLeaderboard(UUID serieId, UUID rangeId, String context, String metric, Integer limit, OffsetDateTime from)` → `LeaderboardResponse`. Models: `UserSerieScoreEntry`, `UserScorePage`, `ScoreContextSummary`, `GroupedScoreSummary`, `UserScoreSummary`, `LeaderboardEntry`, `LeaderboardResponse`, enum `ScoreContext`.
- Removes: path `/api/users/me/play-results` (operation `listMyPlayResults`) and schema `MyPlayResultEntry`.

- [ ] **Step 1: Add the new paths**

Insert after the `/api/users/me/qr/rotate` path block (before `/api/users/{id}`), replacing the entire `/api/users/me/play-results` block (delete it):

```yaml
  /api/users/me/scores:
    get:
      summary: List the calling user's Serie scores, newest first
      operationId: listMyScores
      tags: [Score]
      parameters:
        - name: context
          in: query
          required: false
          schema:
            $ref: '#/components/schemas/ScoreContext'
        - name: serieId
          in: query
          required: false
          schema: { type: string, format: uuid }
        - name: from
          in: query
          required: false
          schema: { type: string, format: date-time }
        - name: to
          in: query
          required: false
          schema: { type: string, format: date-time }
        - name: page
          in: query
          required: false
          schema: { type: integer, default: 0 }
        - name: size
          in: query
          required: false
          schema: { type: integer, default: 20 }
      responses:
        '200':
          description: Page of the user's Serie score rows
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserScorePage'

  /api/users/me/scores/summary:
    get:
      summary: Aggregate score statistics for the calling user
      operationId: getMyScoreSummary
      tags: [Score]
      responses:
        '200':
          description: Per-context stats plus grouped Passe/Wettkampf totals
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserScoreSummary'

  /api/scores/leaderboard:
    get:
      summary: Serie score leaderboard across users
      operationId: getScoreLeaderboard
      tags: [Score]
      parameters:
        - name: serieId
          in: query
          required: false
          schema: { type: string, format: uuid }
        - name: rangeId
          in: query
          required: false
          schema: { type: string, format: uuid }
        - name: context
          in: query
          required: false
          schema:
            $ref: '#/components/schemas/ScoreContext'
        - name: metric
          in: query
          required: false
          description: Ranking metric
          schema: { type: string, enum: [best, average], default: best }
        - name: limit
          in: query
          required: false
          schema: { type: integer, default: 10, maximum: 100 }
        - name: from
          in: query
          required: false
          description: Only count Serien completed at or after this instant
          schema: { type: string, format: date-time }
      responses:
        '200':
          description: Ranked entries, best first
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LeaderboardResponse'
```

- [ ] **Step 2: Add the schemas, remove `MyPlayResultEntry`**

Replace the `MyPlayResultEntry` schema block (lines ~3178-3195) with:

```yaml
    # ───── User Scores ────────────────────────────────────────────────────────

    ScoreContext:
      type: string
      enum: [TRAINING, COMPETITION]

    UserSerieScoreEntry:
      type: object
      description: One user's result for one completed Serie.
      properties:
        id: { type: string, format: uuid }
        context:
          $ref: '#/components/schemas/ScoreContext'
        totalPoints: { type: integer }
        maxPoints: { type: integer }
        serieId: { type: string, format: uuid }
        serieAlias: { type: string }
        playInstanceId: { type: string, format: uuid, nullable: true }
        sessionId: { type: string, format: uuid, nullable: true }
        passeIndex: { type: integer, nullable: true }
        parentName:
          type: string
          nullable: true
          description: Display label of the parent (training template or session name)
        rangeId: { type: string, format: uuid, nullable: true }
        rangeName: { type: string, nullable: true }
        completedAt: { type: string, format: date-time }
        stepStates:
          type: array
          description: Self-contained step breakdown copy
          items:
            $ref: '#/components/schemas/StepStateRecord'

    UserScorePage:
      type: object
      properties:
        content:
          type: array
          items:
            $ref: '#/components/schemas/UserSerieScoreEntry'
        meta:
          $ref: '#/components/schemas/PageMeta'

    ScoreContextSummary:
      type: object
      properties:
        context:
          $ref: '#/components/schemas/ScoreContext'
        serieCount: { type: integer }
        totalPoints: { type: integer }
        maxPoints: { type: integer }
        averagePercent:
          type: number
          format: double
          description: Mean of per-Serie hit percentages
        bestPercent:
          type: number
          format: double
          nullable: true

    GroupedScoreSummary:
      type: object
      description: Aggregated score of one Passe (training instance) or Wettkampf (session).
      properties:
        key:
          type: string
          format: uuid
          description: playInstanceId (training) or sessionId (competition)
        label: { type: string, nullable: true }
        context:
          $ref: '#/components/schemas/ScoreContext'
        serieCount: { type: integer }
        totalPoints: { type: integer }
        maxPoints: { type: integer }
        lastCompletedAt: { type: string, format: date-time }

    UserScoreSummary:
      type: object
      properties:
        contexts:
          type: array
          items:
            $ref: '#/components/schemas/ScoreContextSummary'
        passen:
          type: array
          description: Training Passen, newest first
          items:
            $ref: '#/components/schemas/GroupedScoreSummary'
        wettkaempfe:
          type: array
          description: Competition sessions, newest first
          items:
            $ref: '#/components/schemas/GroupedScoreSummary'

    LeaderboardEntry:
      type: object
      properties:
        userId: { type: string, format: uuid }
        displayName: { type: string }
        serieCount: { type: integer }
        bestPercent: { type: number, format: double }
        averagePercent: { type: number, format: double }
        totalPoints: { type: integer }
        maxPoints: { type: integer }

    LeaderboardResponse:
      type: object
      properties:
        metric: { type: string, enum: [best, average] }
        entries:
          type: array
          items:
            $ref: '#/components/schemas/LeaderboardEntry'
```

`PageMeta` and `StepStateRecord` already exist — reference, don't redefine.

- [ ] **Step 3: Generate and verify compilation fails only where expected**

Run: `mvn generate-sources`
Expected: SUCCESS; generated `ScoreApi` + models appear under `target/generated-sources`.

Run: `mvn test-compile`
Expected: FAIL — `PlayResultApi` (or the controller implementing `listMyPlayResults`) no longer has that operation; the implementing method in `PlayResultController`/`PlayInstanceController` now breaks. That is the Task 6 cleanup surface. If it *passes*, grep for `listMyPlayResults` to find the implementing controller anyway.

- [ ] **Step 4: Commit (contract only)**

```bash
git add smart-ground-backend/src/main/resources/static/openapi.yaml
git commit -m "[backend] openapi: add score endpoints, retire /users/me/play-results"
```

(Committing a temporarily red build is acceptable here only because Task 6 immediately follows in the same PR-sized unit; if you prefer green commits, squash Tasks 5+6 into one commit at the end of Task 6.)

---

### Task 6: Backend — read/aggregation endpoints + delete the LIKE-scan path

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/UserScoreService.java` (add read half)
- Create: `smart-ground-backend/src/main/java/ch/jp/shooting/api/ScoreController.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/PlayInstanceService.java` (delete `listMyPlayResults`, `toMyPlayResultEntry`)
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/repository/PlayInstanceRepository.java` (delete `findCompletedByParticipantUserId`)
- Modify: whichever controller implements `listMyPlayResults` (grep for it; delete the method)
- Delete: `smart-ground-backend/src/test/java/ch/jp/shooting/service/PlayInstanceMyResultsTest.java`
- Test: extend `smart-ground-backend/src/test/java/ch/jp/shooting/service/UserScoreServiceTest.java`

**Interfaces:**
- Consumes: generated `ScoreApi`, models from Task 5; `SecurityHelper.currentUser()` → `User` with `getId()`; `UserRepository.findAllById(Iterable<UUID>)`.
- Produces: `UserScoreService.listMyScores(...)`, `getMyScoreSummary()`, `getLeaderboard(...)` returning the generated models; `ScoreController implements ScoreApi`.

- [ ] **Step 1: Add failing read tests to `UserScoreServiceTest`**

```java
    private UserSerieScore row(UUID userId, String context, int points, int max,
                               @org.jspecify.annotations.Nullable UUID playInstanceId,
                               @org.jspecify.annotations.Nullable UUID sessionId,
                               Instant completedAt) {
        var s = new UserSerieScore();
        s.setUserId(userId);
        s.setContext(context);
        s.setTotalPoints(points);
        s.setMaxPoints(max);
        s.setSerieId(UUID.randomUUID());
        s.setSerieAlias("Serie");
        s.setSourceId(UUID.randomUUID());
        s.setPlayInstanceId(playInstanceId);
        s.setSessionId(sessionId);
        s.setParentName(playInstanceId != null ? "Passe X" : "Wettkampf Y");
        s.setCompletedAt(completedAt);
        return s;
    }

    @Test
    void getMyScoreSummary_aggregatesContextsAndGroups() {
        var userId = UUID.randomUUID();
        var me = mock(User.class);
        when(me.getId()).thenReturn(userId);
        when(securityHelper.currentUser()).thenReturn(me);

        var passe = UUID.randomUUID();
        var session = UUID.randomUUID();
        var now = Instant.now();
        when(scoreRepository.findByUserIdOrderByCompletedAtDesc(userId)).thenReturn(List.of(
            row(userId, "TRAINING", 8, 10, passe, null, now),
            row(userId, "TRAINING", 6, 10, passe, null, now.minusSeconds(60)),
            row(userId, "COMPETITION", 9, 10, null, session, now.minusSeconds(120))
        ));

        var summary = service().getMyScoreSummary();

        var training = summary.getContexts().stream()
            .filter(c -> "TRAINING".equals(c.getContext().getValue())).findFirst().orElseThrow();
        assertEquals(2, training.getSerieCount());
        assertEquals(14, training.getTotalPoints());
        assertEquals(70.0, training.getAveragePercent(), 0.01);
        assertEquals(80.0, training.getBestPercent(), 0.01);

        assertEquals(1, summary.getPassen().size());
        assertEquals(14, summary.getPassen().get(0).getTotalPoints());
        assertEquals(2, summary.getPassen().get(0).getSerieCount());
        assertEquals(1, summary.getWettkaempfe().size());
        assertEquals(9, summary.getWettkaempfe().get(0).getTotalPoints());
    }

    @Test
    void getLeaderboard_ranksByBestPercent() {
        var alice = UUID.randomUUID();
        var bob = UUID.randomUUID();
        var now = Instant.now();
        when(scoreRepository.findForLeaderboard(isNull(), isNull(), isNull(), any())).thenReturn(List.of(
            row(alice, "TRAINING", 9, 10, UUID.randomUUID(), null, now),
            row(alice, "TRAINING", 5, 10, UUID.randomUUID(), null, now),
            row(bob, "TRAINING", 8, 10, UUID.randomUUID(), null, now)
        ));
        var aliceUser = mock(User.class);
        when(aliceUser.getId()).thenReturn(alice);
        when(aliceUser.getDisplayName()).thenReturn("Alice");
        var bobUser = mock(User.class);
        when(bobUser.getId()).thenReturn(bob);
        when(bobUser.getDisplayName()).thenReturn("Bob");
        when(userRepository.findAllById(any())).thenReturn(List.of(aliceUser, bobUser));

        var board = service().getLeaderboard(null, null, null, "best", 10, null);

        assertEquals(2, board.getEntries().size());
        assertEquals("Alice", board.getEntries().get(0).getDisplayName());
        assertEquals(90.0, board.getEntries().get(0).getBestPercent(), 0.01);
        assertEquals(70.0, board.getEntries().get(0).getAveragePercent(), 0.01);
    }
```

Check the `User` entity (`ch.jp.shooting.model.auth.User`) for the display-name accessor — if there is no `getDisplayName()`, use what exists (e.g. `getVorname()`/`getNachname()` concatenated or `getUsername()`) and adjust test + implementation identically.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=UserScoreServiceTest`
Expected: COMPILE ERROR — read methods missing.

- [ ] **Step 3: Implement the read half in `UserScoreService`**

```java
    // ── Lesen / Aggregation ──────────────────────────────────────────────────

    private static double percent(int points, int max) {
        return max > 0 ? points * 100.0 / max : 0.0;
    }

    @Transactional(readOnly = true)
    public ch.jp.smartground.model.UserScorePage listMyScores(
            @Nullable String context, @Nullable UUID serieId,
            @Nullable java.time.OffsetDateTime from, @Nullable java.time.OffsetDateTime to,
            int page, int size) {
        var userId = securityHelper.currentUser().getId();
        var fromInstant = from != null ? from.toInstant() : java.time.Instant.EPOCH;
        var toInstant = to != null ? to.toInstant() : java.time.Instant.now().plusSeconds(60);
        var result = scoreRepository.findFiltered(userId, context, serieId,
            fromInstant, toInstant, org.springframework.data.domain.PageRequest.of(page, size));
        var meta = new ch.jp.smartground.model.PageMeta()
            .page(result.getNumber()).size(result.getSize())
            .totalPages(result.getTotalPages()).totalElements((int) result.getTotalElements());
        return new ch.jp.smartground.model.UserScorePage()
            .content(result.getContent().stream().map(this::toEntry).toList())
            .meta(meta);
    }

    @Transactional(readOnly = true)
    public ch.jp.smartground.model.UserScoreSummary getMyScoreSummary() {
        var userId = securityHelper.currentUser().getId();
        var rows = scoreRepository.findByUserIdOrderByCompletedAtDesc(userId);

        var summary = new ch.jp.smartground.model.UserScoreSummary();
        for (var context : List.of("TRAINING", "COMPETITION")) {
            var ctxRows = rows.stream().filter(r -> context.equals(r.getContext())).toList();
            var ctx = new ch.jp.smartground.model.ScoreContextSummary()
                .context(ch.jp.smartground.model.ScoreContext.fromValue(context))
                .serieCount(ctxRows.size())
                .totalPoints(ctxRows.stream().mapToInt(UserSerieScore::getTotalPoints).sum())
                .maxPoints(ctxRows.stream().mapToInt(UserSerieScore::getMaxPoints).sum())
                .averagePercent(ctxRows.stream()
                    .mapToDouble(r -> percent(r.getTotalPoints(), r.getMaxPoints())).average().orElse(0.0))
                .bestPercent(ctxRows.isEmpty() ? null : ctxRows.stream()
                    .mapToDouble(r -> percent(r.getTotalPoints(), r.getMaxPoints())).max().orElse(0.0));
            summary.addContextsItem(ctx);
        }

        // Gruppierung: Training nach playInstanceId, Wettkampf nach sessionId.
        // rows sind absteigend sortiert — LinkedHashMap erhält "neueste zuerst".
        var passen = new java.util.LinkedHashMap<UUID, ch.jp.smartground.model.GroupedScoreSummary>();
        var wettkaempfe = new java.util.LinkedHashMap<UUID, ch.jp.smartground.model.GroupedScoreSummary>();
        for (var r : rows) {
            UUID key = "TRAINING".equals(r.getContext()) ? r.getPlayInstanceId() : r.getSessionId();
            if (key == null) continue;
            var target = "TRAINING".equals(r.getContext()) ? passen : wettkaempfe;
            var g = target.computeIfAbsent(key, k -> new ch.jp.smartground.model.GroupedScoreSummary()
                .key(k).label(r.getParentName())
                .context(ch.jp.smartground.model.ScoreContext.fromValue(r.getContext()))
                .serieCount(0).totalPoints(0).maxPoints(0)
                .lastCompletedAt(java.time.OffsetDateTime.ofInstant(r.getCompletedAt(), java.time.ZoneOffset.UTC)));
            g.serieCount(g.getSerieCount() + 1)
             .totalPoints(g.getTotalPoints() + r.getTotalPoints())
             .maxPoints(g.getMaxPoints() + r.getMaxPoints());
        }
        summary.passen(new java.util.ArrayList<>(passen.values()));
        summary.wettkaempfe(new java.util.ArrayList<>(wettkaempfe.values()));
        return summary;
    }

    @Transactional(readOnly = true)
    public ch.jp.smartground.model.LeaderboardResponse getLeaderboard(
            @Nullable UUID serieId, @Nullable UUID rangeId, @Nullable String context,
            String metric, int limit, @Nullable java.time.OffsetDateTime from) {
        var fromInstant = from != null ? from.toInstant() : java.time.Instant.EPOCH;
        var rows = scoreRepository.findForLeaderboard(context, serieId, rangeId, fromInstant);

        var byUser = rows.stream().collect(java.util.stream.Collectors.groupingBy(UserSerieScore::getUserId));
        var names = new java.util.HashMap<UUID, String>();
        userRepository.findAllById(byUser.keySet())
            .forEach(u -> names.put(u.getId(), u.getDisplayName()));

        var entries = byUser.entrySet().stream().map(e -> {
            var userRows = e.getValue();
            return new ch.jp.smartground.model.LeaderboardEntry()
                .userId(e.getKey())
                .displayName(names.getOrDefault(e.getKey(), "Unbekannt"))
                .serieCount(userRows.size())
                .bestPercent(userRows.stream()
                    .mapToDouble(r -> percent(r.getTotalPoints(), r.getMaxPoints())).max().orElse(0.0))
                .averagePercent(userRows.stream()
                    .mapToDouble(r -> percent(r.getTotalPoints(), r.getMaxPoints())).average().orElse(0.0))
                .totalPoints(userRows.stream().mapToInt(UserSerieScore::getTotalPoints).sum())
                .maxPoints(userRows.stream().mapToInt(UserSerieScore::getMaxPoints).sum());
        })
        .sorted(java.util.Comparator.comparingDouble(
            (ch.jp.smartground.model.LeaderboardEntry le) ->
                "average".equals(metric) ? le.getAveragePercent() : le.getBestPercent()).reversed())
        .limit(limit)
        .toList();

        return new ch.jp.smartground.model.LeaderboardResponse()
            .metric(ch.jp.smartground.model.LeaderboardResponse.MetricEnum.fromValue(
                "average".equals(metric) ? "average" : "best"))
            .entries(entries);
    }

    private ch.jp.smartground.model.UserSerieScoreEntry toEntry(UserSerieScore s) {
        var entry = new ch.jp.smartground.model.UserSerieScoreEntry()
            .id(s.getId())
            .context(ch.jp.smartground.model.ScoreContext.fromValue(s.getContext()))
            .totalPoints(s.getTotalPoints())
            .maxPoints(s.getMaxPoints())
            .serieId(s.getSerieId())
            .serieAlias(s.getSerieAlias())
            .playInstanceId(s.getPlayInstanceId())
            .sessionId(s.getSessionId())
            .passeIndex(s.getPasseIndex())
            .parentName(s.getParentName())
            .rangeId(s.getRangeId())
            .rangeName(s.getRangeName())
            .completedAt(java.time.OffsetDateTime.ofInstant(s.getCompletedAt(), java.time.ZoneOffset.UTC));
        if (s.getStepStatesJson() != null) {
            try {
                entry.stepStates(java.util.Arrays.asList(objectMapper.readValue(
                    s.getStepStatesJson(), ch.jp.smartground.model.StepStateRecord[].class)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // Detailkopie unlesbar — Totale bleiben gültig
            }
        }
        return entry;
    }
```

Adjust generated-model setter/enum details to what the generator actually produced (`fromValue`, `MetricEnum` naming) — open the generated classes under `target/generated-sources` and match them. If the stored dto `StepStateRecord` JSON field names differ from the generated model, map field-by-field instead of direct deserialization (they were both derived from the same OpenAPI schema, so direct deserialization should work).

- [ ] **Step 4: Create `ScoreController`**

```java
package ch.jp.shooting.api;

import ch.jp.shooting.service.UserScoreService;
import ch.jp.smartground.api.ScoreApi;
import ch.jp.smartground.model.LeaderboardResponse;
import ch.jp.smartground.model.ScoreContext;
import ch.jp.smartground.model.UserScorePage;
import ch.jp.smartground.model.UserScoreSummary;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

// Implementiert ScoreApi (generierte Schnittstelle)
@RestController
@NullMarked
public class ScoreController implements ScoreApi {

    private final UserScoreService userScoreService;

    public ScoreController(UserScoreService userScoreService) {
        this.userScoreService = userScoreService;
    }

    @Override
    public ResponseEntity<UserScorePage> listMyScores(ScoreContext context, UUID serieId,
            OffsetDateTime from, OffsetDateTime to, Integer page, Integer size) {
        return ResponseEntity.ok(userScoreService.listMyScores(
            context != null ? context.getValue() : null, serieId, from, to,
            page != null ? page : 0, size != null ? size : 20));
    }

    @Override
    public ResponseEntity<UserScoreSummary> getMyScoreSummary() {
        return ResponseEntity.ok(userScoreService.getMyScoreSummary());
    }

    @Override
    public ResponseEntity<LeaderboardResponse> getScoreLeaderboard(UUID serieId, UUID rangeId,
            ScoreContext context, String metric, Integer limit, OffsetDateTime from) {
        return ResponseEntity.ok(userScoreService.getLeaderboard(
            serieId, rangeId, context != null ? context.getValue() : null,
            metric != null ? metric : "best",
            limit != null ? Math.min(limit, 100) : 10, from));
    }
}
```

Match the generated `ScoreApi` method signatures exactly (parameter order/types come from the generator — check the generated interface and adapt).

- [ ] **Step 5: Delete the retired play-results-me path**

- In `PlayInstanceService`: delete `listMyPlayResults()` and `toMyPlayResultEntry(...)` (lines ~276-315) and now-unused imports (`MyPlayResultEntry`, `Objects` if unused).
- In `PlayInstanceRepository`: delete `findCompletedByParticipantUserId` and its comment (lines ~25-30); remove unused `@Query`/`@Param` imports if nothing else uses them.
- Grep `smart-ground-backend/src/main/java` for `listMyPlayResults` — delete the controller override implementing it.
- Delete `smart-ground-backend/src/test/java/ch/jp/shooting/service/PlayInstanceMyResultsTest.java`.

- [ ] **Step 6: Run the full backend test suite**

Run: `mvn test`
Expected: PASS — everything compiles, new tests green, no references to `MyPlayResultEntry` remain (`grep -r MyPlayResultEntry smart-ground-backend/src` → empty).

- [ ] **Step 7: Commit**

```bash
git add -A smart-ground-backend/src
git commit -m "[backend] score read endpoints (my scores, summary, leaderboard); delete play-results LIKE scan"
```

---

### Task 7: UI — `scoreApi.js` service

**Files:**
- Create: `smart-ground-ui/src/services/scoreApi.js`
- Test: `smart-ground-ui/src/services/__tests__/scoreApi.test.js`

**Interfaces:**
- Consumes: `apiFetch` from `@/services/apiClient.js` (same import style as `userApi.js`).
- Produces: `fetchMyScores(params)`, `fetchMyScoreSummary()`, `fetchLeaderboard(params)`.

- [ ] **Step 1: Write the failing test**

Look at an existing service test in `smart-ground-ui/src/services/__tests__/` for the established `apiClient` mocking pattern and mirror it. Content:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as scoreApi from '@/services/scoreApi.js'
import { apiFetch } from '@/services/apiClient.js'

vi.mock('@/services/apiClient.js', () => ({
  apiFetch: vi.fn(),
}))

describe('scoreApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiFetch.mockResolvedValue({})
  })

  it('fetchMyScores builds the query string from params, skipping empty values', async () => {
    await scoreApi.fetchMyScores({ context: 'TRAINING', page: 0, serieId: '', from: null })
    expect(apiFetch).toHaveBeenCalledWith('/users/me/scores?context=TRAINING&page=0')
  })

  it('fetchMyScores without params calls the bare path', async () => {
    await scoreApi.fetchMyScores()
    expect(apiFetch).toHaveBeenCalledWith('/users/me/scores')
  })

  it('fetchMyScoreSummary calls the summary path', async () => {
    await scoreApi.fetchMyScoreSummary()
    expect(apiFetch).toHaveBeenCalledWith('/users/me/scores/summary')
  })

  it('fetchLeaderboard passes filters', async () => {
    await scoreApi.fetchLeaderboard({ serieId: 's1', metric: 'best' })
    expect(apiFetch).toHaveBeenCalledWith('/scores/leaderboard?serieId=s1&metric=best')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run from `smart-ground-ui/`: `npm run test src/services/__tests__/scoreApi.test.js`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the service**

```javascript
import { apiFetch } from './apiClient.js'

// Builds "?a=1&b=2" from an object, skipping null/undefined/empty values
function toQuery(params = {}) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, value)
  }
  const s = search.toString()
  return s ? `?${s}` : ''
}

export async function fetchMyScores(params) {
  return apiFetch(`/users/me/scores${toQuery(params)}`)
}

export async function fetchMyScoreSummary() {
  return apiFetch('/users/me/scores/summary')
}

export async function fetchLeaderboard(params) {
  return apiFetch(`/scores/leaderboard${toQuery(params)}`)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test src/services/__tests__/scoreApi.test.js`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-ui/src/services/scoreApi.js smart-ground-ui/src/services/__tests__/scoreApi.test.js
git commit -m "[ui] add scoreApi service for score endpoints"
```

---

### Task 8: UI — `scoreStore.js` + retire `myResults` from profileStore/userApi

**Files:**
- Create: `smart-ground-ui/src/stores/scoreStore.js`
- Test: `smart-ground-ui/src/stores/__tests__/scoreStore.test.js`
- Modify: `smart-ground-ui/src/stores/profileStore.js` (remove `myResults`, `loadMyResults`)
- Modify: `smart-ground-ui/src/services/userApi.js` (remove `fetchMyPlayResults`)
- Modify: `smart-ground-ui/src/stores/__tests__/profileStore.test.js` (remove `fetchMyPlayResults` mock + `loadMyResults` test)

**Interfaces:**
- Consumes: `scoreApi` (Task 7).
- Produces: Pinia store `useScoreStore` with state `scores`, `scoresMeta`, `summary`, `leaderboard`, `isLoading`, `error`; actions `loadScores(params)`, `loadSummary()`, `loadLeaderboard(params)`.

- [ ] **Step 1: Write the failing store test**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useScoreStore } from '@/stores/scoreStore.js'
import * as scoreApi from '@/services/scoreApi.js'

vi.mock('@/services/scoreApi.js', () => ({
  fetchMyScores: vi.fn(),
  fetchMyScoreSummary: vi.fn(),
  fetchLeaderboard: vi.fn(),
}))

describe('scoreStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadScores stores rows and paging meta', async () => {
    scoreApi.fetchMyScores.mockResolvedValue({
      content: [{ id: 'a', totalPoints: 8, maxPoints: 10 }],
      meta: { page: 0, totalPages: 1, totalElements: 1 },
    })
    const store = useScoreStore()
    await store.loadScores({ context: 'TRAINING' })
    expect(scoreApi.fetchMyScores).toHaveBeenCalledWith({ context: 'TRAINING' })
    expect(store.scores).toHaveLength(1)
    expect(store.scoresMeta.totalElements).toBe(1)
    expect(store.error).toBeNull()
  })

  it('loadScores captures errors', async () => {
    scoreApi.fetchMyScores.mockRejectedValue(new Error('boom'))
    const store = useScoreStore()
    await store.loadScores()
    expect(store.error).toBe('boom')
    expect(store.isLoading).toBe(false)
  })

  it('loadSummary stores the summary', async () => {
    scoreApi.fetchMyScoreSummary.mockResolvedValue({ contexts: [], passen: [], wettkaempfe: [] })
    const store = useScoreStore()
    await store.loadSummary()
    expect(store.summary).toEqual({ contexts: [], passen: [], wettkaempfe: [] })
  })

  it('loadLeaderboard stores entries', async () => {
    scoreApi.fetchLeaderboard.mockResolvedValue({ metric: 'best', entries: [{ userId: 'u1' }] })
    const store = useScoreStore()
    await store.loadLeaderboard({ metric: 'best' })
    expect(store.leaderboard.entries).toHaveLength(1)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/stores/__tests__/scoreStore.test.js`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the store**

```javascript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as scoreApi from '@/services/scoreApi.js'

// Personal score history/summary and cross-user leaderboard
export const useScoreStore = defineStore('score', () => {
  const scores = ref([])
  const scoresMeta = ref(null)
  const summary = ref(null)
  const leaderboard = ref(null)
  const isLoading = ref(false)
  const error = ref(null)

  const run = async (fn) => {
    isLoading.value = true
    error.value = null
    try {
      await fn()
    } catch (e) {
      error.value = e.message
    } finally {
      isLoading.value = false
    }
  }

  const loadScores = (params = {}) => run(async () => {
    const page = await scoreApi.fetchMyScores(params)
    scores.value = page.content ?? []
    scoresMeta.value = page.meta ?? null
  })

  const loadSummary = () => run(async () => {
    summary.value = await scoreApi.fetchMyScoreSummary()
  })

  const loadLeaderboard = (params = {}) => run(async () => {
    leaderboard.value = await scoreApi.fetchLeaderboard(params)
  })

  return { scores, scoresMeta, summary, leaderboard, isLoading, error, loadScores, loadSummary, loadLeaderboard }
})
```

- [ ] **Step 4: Remove the retired pieces**

- `profileStore.js`: delete `myResults` ref, `loadMyResults` action, and both from the returned object; keep QR + resolve logic. Update the header comment to `// Own QR check-in token (profile page) and QR resolve for group setup`.
- `userApi.js`: delete `fetchMyPlayResults`.
- `profileStore.test.js`: delete the `fetchMyPlayResults` mock entry and the `loadMyResults stores the result list` test.

- [ ] **Step 5: Run store tests + full UI suite compile check**

Run: `npm run test src/stores/__tests__/scoreStore.test.js src/stores/__tests__/profileStore.test.js`
Expected: PASS. (`ShooterProfilView` still references `loadMyResults` — its test may now fail; that is fixed in Task 9. If `npm run test` fully red on that view blocks you, do Tasks 8+9 back-to-back before running the full suite.)

- [ ] **Step 6: Commit**

```bash
git add smart-ground-ui/src/stores/scoreStore.js smart-ground-ui/src/stores/__tests__/scoreStore.test.js smart-ground-ui/src/stores/profileStore.js smart-ground-ui/src/stores/__tests__/profileStore.test.js smart-ground-ui/src/services/userApi.js
git commit -m "[ui] add scoreStore; retire fetchMyPlayResults path"
```

---

### Task 9: UI — rework the Ergebnisse tab in `ShooterProfilView`

**Files:**
- Modify: `smart-ground-ui/src/views/shooter/ShooterProfilView.vue` (template lines ~174-186, script: tab watcher line ~219, imports)
- Test: create `smart-ground-ui/src/views/shooter/__tests__/ShooterProfilView.scores.test.js`

**Interfaces:**
- Consumes: `useScoreStore` (Task 8): `summary` (`contexts[]`, `passen[]`, `wettkaempfe[]`), `scores` (rows with `serieAlias`, `parentName`, `rangeName`, `totalPoints`, `maxPoints`, `completedAt`), `loadSummary()`, `loadScores()`.

- [ ] **Step 1: Write the failing component test**

Mirror mounting/stubbing patterns from `ShooterProfilView.qr.test.js` (same auth store setup). Content:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterProfilView from '@/views/shooter/ShooterProfilView.vue'
import { useScoreStore } from '@/stores/scoreStore.js'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/services/scoreApi.js', () => ({
  fetchMyScores: vi.fn().mockResolvedValue({ content: [], meta: null }),
  fetchMyScoreSummary: vi.fn().mockResolvedValue(null),
  fetchLeaderboard: vi.fn(),
}))

async function openErgebnisseTab(wrapper) {
  const tab = wrapper.findAll('button').find((b) => b.text() === 'Ergebnisse')
  await tab.trigger('click')
  await new Promise((r) => setTimeout(r))
}

describe('ShooterProfilView scores tab', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows stats header and serie rows from the score store', async () => {
    const wrapper = mount(ShooterProfilView, { global: { plugins: [createPinia()] } })
    const store = useScoreStore()
    store.summary = {
      contexts: [
        { context: 'TRAINING', serieCount: 2, totalPoints: 14, maxPoints: 20, averagePercent: 70, bestPercent: 80 },
        { context: 'COMPETITION', serieCount: 0, totalPoints: 0, maxPoints: 0, averagePercent: 0, bestPercent: null },
      ],
      passen: [{ key: 'p1', label: 'Passe X', context: 'TRAINING', serieCount: 2, totalPoints: 14, maxPoints: 20, lastCompletedAt: '2026-07-01T10:00:00Z' }],
      wettkaempfe: [],
    }
    store.scores = [{
      id: 'a', context: 'TRAINING', serieAlias: 'Serie 1', parentName: 'Passe X',
      rangeName: 'Platz 1', totalPoints: 8, maxPoints: 10, completedAt: '2026-07-01T10:00:00Z',
    }]
    await openErgebnisseTab(wrapper)

    expect(wrapper.text()).toContain('Serie 1')
    expect(wrapper.text()).toContain('8/10')
    expect(wrapper.text()).toContain('70')
  })

  it('switches to grouped Passen view', async () => {
    const wrapper = mount(ShooterProfilView, { global: { plugins: [createPinia()] } })
    const store = useScoreStore()
    store.summary = {
      contexts: [],
      passen: [{ key: 'p1', label: 'Passe X', context: 'TRAINING', serieCount: 2, totalPoints: 14, maxPoints: 20, lastCompletedAt: '2026-07-01T10:00:00Z' }],
      wettkaempfe: [],
    }
    store.scores = []
    await openErgebnisseTab(wrapper)
    const passenTab = wrapper.find('[data-testid="score-group-passen"]')
    await passenTab.trigger('click')
    expect(wrapper.text()).toContain('Passe X')
    expect(wrapper.text()).toContain('14/20')
  })

  it('shows an empty state without scores', async () => {
    const wrapper = mount(ShooterProfilView, { global: { plugins: [createPinia()] } })
    const store = useScoreStore()
    store.summary = { contexts: [], passen: [], wettkaempfe: [] }
    store.scores = []
    await openErgebnisseTab(wrapper)
    expect(wrapper.text()).toContain('Noch keine Ergebnisse')
  })
})
```

Adjust the double-`createPinia` mounting to match how `ShooterProfilView.qr.test.js` actually wires Pinia (use its exact pattern — one active pinia, set store state after mount, before opening the tab).

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/views/shooter/__tests__/ShooterProfilView.scores.test.js`
Expected: FAIL — view still renders `profileStore.myResults` (now undefined) and lacks the group tabs.

- [ ] **Step 3: Rework the Ergebnisse tab**

Replace template lines ~174-186 with:

```html
    <!-- Tab: Ergebnisse -->
    <div v-if="activeTab === 'ergebnisse'" class="tab-content" role="tabpanel">
      <!-- Stats header -->
      <div v-if="statsContexts.length" class="score-stats">
        <div v-for="c in statsContexts" :key="c.context" class="score-stat-card">
          <span class="score-stat-label">{{ c.context === 'TRAINING' ? 'Training' : 'Wettkampf' }}</span>
          <span class="score-stat-value">{{ Math.round(c.averagePercent) }}<small>% Ø</small></span>
          <span class="score-stat-sub">Beste Serie {{ Math.round(c.bestPercent) }}% · {{ c.serieCount }} Serien</span>
        </div>
      </div>

      <!-- Grouping tabs -->
      <div class="score-group-tabs" role="tablist" aria-label="Ergebnis-Gruppierung">
        <button :class="['score-group-tab', { active: scoreGroup === 'serien' }]"
                data-testid="score-group-serien" role="tab" :aria-selected="scoreGroup === 'serien'"
                @click="scoreGroup = 'serien'">Serien</button>
        <button :class="['score-group-tab', { active: scoreGroup === 'passen' }]"
                data-testid="score-group-passen" role="tab" :aria-selected="scoreGroup === 'passen'"
                @click="scoreGroup = 'passen'">Passen</button>
        <button :class="['score-group-tab', { active: scoreGroup === 'wettkaempfe' }]"
                data-testid="score-group-wettkaempfe" role="tab" :aria-selected="scoreGroup === 'wettkaempfe'"
                @click="scoreGroup = 'wettkaempfe'">Wettkämpfe</button>
      </div>

      <p v-if="currentScoreRows.length === 0" class="empty-results">
        Noch keine Ergebnisse — checke dich am Stand per QR-Code ein.
      </p>

      <!-- Serien: individual rows -->
      <template v-if="scoreGroup === 'serien'">
        <div v-for="r in scoreStore.scores" :key="r.id" class="result-row">
          <div class="result-main">
            <span class="result-name">{{ r.serieAlias }}</span>
            <span class="result-meta">{{ r.parentName ?? '—' }} · {{ r.rangeName ?? '—' }} · {{ formatDate(r.completedAt) }}</span>
          </div>
          <span class="result-score">{{ r.totalPoints }}/{{ r.maxPoints }}</span>
        </div>
      </template>

      <!-- Passen / Wettkämpfe: grouped rows -->
      <template v-else>
        <div v-for="g in currentScoreRows" :key="g.key" class="result-row">
          <div class="result-main">
            <span class="result-name">{{ g.label ?? '—' }}</span>
            <span class="result-meta">{{ g.serieCount }} Serien · {{ formatDate(g.lastCompletedAt) }}</span>
          </div>
          <span class="result-score">{{ g.totalPoints }}/{{ g.maxPoints }}</span>
        </div>
      </template>

      <div v-if="scoreStore.error" class="save-error">{{ scoreStore.error }}</div>
    </div>
```

Script changes:

```javascript
// add import
import { useScoreStore } from '@/stores/scoreStore.js'
// add store + local state after profileStore
const scoreStore = useScoreStore()
const scoreGroup = ref('serien')

const statsContexts = computed(() =>
  (scoreStore.summary?.contexts ?? []).filter((c) => c.serieCount > 0))

const currentScoreRows = computed(() => {
  if (scoreGroup.value === 'passen') return scoreStore.summary?.passen ?? []
  if (scoreGroup.value === 'wettkaempfe') return scoreStore.summary?.wettkaempfe ?? []
  return scoreStore.scores
})
```

Change the tab watcher (line ~219) from `if (tab === 'ergebnisse') await profileStore.loadMyResults()` to:

```javascript
  if (tab === 'ergebnisse') await Promise.all([scoreStore.loadSummary(), scoreStore.loadScores()])
```

Add styles next to the existing `.result-row`/`.empty-results` rules, tokens only:

```css
.score-stats { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.score-stat-card {
  flex: 1 1 140px; display: flex; flex-direction: column; gap: 2px;
  padding: 12px 16px; border-radius: 12px; background: var(--sg-bg-panel);
}
.score-stat-label { font-size: 0.8rem; color: var(--sg-text-muted); }
.score-stat-value { font-size: 1.5rem; font-weight: 700; color: var(--sg-text-primary); }
.score-stat-value small { font-size: 0.9rem; font-weight: 500; color: var(--sg-text-muted); }
.score-stat-sub { font-size: 0.8rem; color: var(--sg-text-faint); }
.score-group-tabs { display: flex; gap: 8px; margin-bottom: 12px; }
.score-group-tab {
  min-height: 48px; padding: 0 18px; border: 1px solid transparent; border-radius: 10px;
  background: var(--sg-bg-panel); color: var(--sg-text-muted); font-size: 1rem; cursor: pointer;
}
.score-group-tab.active { color: var(--sg-text-primary); border-color: var(--sg-brand); }
.score-group-tab:focus-visible { outline: 2px solid var(--sg-brand); outline-offset: 2px; }
```

Verify the token names (`--sg-bg-panel`, `--sg-text-*`, `--sg-brand`) against `src/assets/main.css` and use the file's actual names.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm run test src/views/shooter/__tests__/`
Expected: PASS (new scores test + existing qr test).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-ui/src/views/shooter/ShooterProfilView.vue smart-ground-ui/src/views/shooter/__tests__/ShooterProfilView.scores.test.js
git commit -m "[ui] profil: score history with Serie/Passe/Wettkampf grouping and stats header"
```

---

### Task 10: UI — Bestenliste view + route + home tile

**Files:**
- Create: `smart-ground-ui/src/views/shooter/ShooterBestenlisteView.vue`
- Modify: `smart-ground-ui/src/router/index.js` (add route)
- Modify: `smart-ground-ui/src/views/shooter/ShooterHomeView.vue` (add tile after the profile tile, lines ~39-45)
- Test: `smart-ground-ui/src/views/shooter/__tests__/ShooterBestenlisteView.test.js`

**Interfaces:**
- Consumes: `useScoreStore.loadLeaderboard(params)` / `leaderboard` (Task 8); `useRangeStore` from `@/stores/rangeStore.js` — `ranges` (array of `{ id, name, … }`) and `initialize()` for the range filter; `usePasseStore` from `@/stores/passeStore.js` — `savedSerien` (array with `id` and a name/alias field — check `toUiSerie` in that store for the exact field name) and `loadSerienFromStorage()` for the Serie filter.

- [ ] **Step 1: Write the failing component test**

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import ShooterBestenlisteView from '@/views/shooter/ShooterBestenlisteView.vue'
import { useScoreStore } from '@/stores/scoreStore.js'

vi.mock('@/services/scoreApi.js', () => ({
  fetchMyScores: vi.fn(),
  fetchMyScoreSummary: vi.fn(),
  fetchLeaderboard: vi.fn().mockResolvedValue({ metric: 'best', entries: [] }),
}))

describe('ShooterBestenlisteView', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('renders leaderboard entries ranked with position numbers', async () => {
    const wrapper = mount(ShooterBestenlisteView)
    const store = useScoreStore()
    store.leaderboard = {
      metric: 'best',
      entries: [
        { userId: 'u1', displayName: 'Alice', serieCount: 4, bestPercent: 90, averagePercent: 75, totalPoints: 30, maxPoints: 40 },
        { userId: 'u2', displayName: 'Bob', serieCount: 2, bestPercent: 80, averagePercent: 80, totalPoints: 16, maxPoints: 20 },
      ],
    }
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('90%')
    const rows = wrapper.findAll('[data-testid="leaderboard-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('1')
  })

  it('shows an empty state without entries', async () => {
    const wrapper = mount(ShooterBestenlisteView)
    const store = useScoreStore()
    store.leaderboard = { metric: 'best', entries: [] }
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Noch keine Einträge')
  })

  it('reloads when the metric changes', async () => {
    const scoreApi = await import('@/services/scoreApi.js')
    const wrapper = mount(ShooterBestenlisteView)
    await wrapper.find('[data-testid="metric-average"]').trigger('click')
    expect(scoreApi.fetchLeaderboard).toHaveBeenLastCalledWith(
      expect.objectContaining({ metric: 'average' }))
  })
})
```

If the Serie-filter store import drags in more service mocks, add the needed `vi.mock` for that store's service module (empty list is fine for these tests).

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test src/views/shooter/__tests__/ShooterBestenlisteView.test.js`
Expected: FAIL — component does not exist.

- [ ] **Step 3: Implement the view**

```vue
<template>
  <div class="bestenliste">
    <h1 class="page-title">Bestenliste</h1>

    <!-- Filters -->
    <div class="filters">
      <div class="metric-tabs" role="tablist" aria-label="Wertung">
        <button :class="['metric-tab', { active: metric === 'best' }]" data-testid="metric-best"
                role="tab" :aria-selected="metric === 'best'" @click="setMetric('best')">Beste Serie</button>
        <button :class="['metric-tab', { active: metric === 'average' }]" data-testid="metric-average"
                role="tab" :aria-selected="metric === 'average'" @click="setMetric('average')">Durchschnitt</button>
      </div>
      <label class="filter-field">
        <span class="filter-label">Kontext</span>
        <select v-model="context" @change="reload">
          <option value="">Alle</option>
          <option value="TRAINING">Training</option>
          <option value="COMPETITION">Wettkampf</option>
        </select>
      </label>
      <label class="filter-field">
        <span class="filter-label">Platz</span>
        <select v-model="rangeId" data-testid="range-filter" @change="reload">
          <option value="">Alle Plätze</option>
          <option v-for="r in rangeStore.ranges" :key="r.id" :value="r.id">{{ r.name }}</option>
        </select>
      </label>
      <label class="filter-field">
        <span class="filter-label">Serie</span>
        <select v-model="serieId" data-testid="serie-filter" @change="reload">
          <option value="">Alle Serien</option>
          <option v-for="s in passeStore.savedSerien" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
      </label>
    </div>

    <p v-if="scoreStore.error" class="error-text">{{ scoreStore.error }}</p>
    <p v-else-if="entries.length === 0" class="empty-text">
      Noch keine Einträge — schiesse eine Serie, um auf der Bestenliste zu erscheinen.
    </p>

    <ol v-else class="board">
      <li v-for="(entry, index) in entries" :key="entry.userId" class="board-row" data-testid="leaderboard-row">
        <span class="board-rank">{{ index + 1 }}</span>
        <div class="board-main">
          <span class="board-name">{{ entry.displayName }}</span>
          <span class="board-meta">{{ entry.serieCount }} Serien · {{ entry.totalPoints }}/{{ entry.maxPoints }} Punkte</span>
        </div>
        <span class="board-score">{{ Math.round(metric === 'average' ? entry.averagePercent : entry.bestPercent) }}%</span>
      </li>
    </ol>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useScoreStore } from '@/stores/scoreStore.js'
import { useRangeStore } from '@/stores/rangeStore.js'
import { usePasseStore } from '@/stores/passeStore.js'

const scoreStore = useScoreStore()
const rangeStore = useRangeStore()
const passeStore = usePasseStore()
const metric = ref('best')
const context = ref('')
const rangeId = ref('')
const serieId = ref('')

const entries = computed(() => scoreStore.leaderboard?.entries ?? [])

function reload() {
  const params = { metric: metric.value, limit: 25 }
  if (context.value) params.context = context.value
  if (rangeId.value) params.rangeId = rangeId.value
  if (serieId.value) params.serieId = serieId.value
  scoreStore.loadLeaderboard(params)
}

function setMetric(value) {
  metric.value = value
  reload()
}

onMounted(() => {
  reload()
  if (rangeStore.ranges.length === 0) rangeStore.initialize()
  if (passeStore.savedSerien.length === 0) passeStore.loadSerienFromStorage()
})
</script>

<style scoped>
.bestenliste { max-width: 720px; margin: 0 auto; padding: 24px 16px; }
.page-title { color: var(--sg-text-primary); margin-bottom: 16px; }
.filters { display: flex; gap: 16px; flex-wrap: wrap; align-items: end; margin-bottom: 20px; }
.metric-tabs { display: flex; gap: 8px; }
.metric-tab {
  min-height: 48px; padding: 0 18px; border: 1px solid transparent; border-radius: 10px;
  background: var(--sg-bg-panel); color: var(--sg-text-muted); font-size: 1rem; cursor: pointer;
}
.metric-tab.active { color: var(--sg-text-primary); border-color: var(--sg-brand); }
.metric-tab:focus-visible, .board-row:focus-visible { outline: 2px solid var(--sg-brand); outline-offset: 2px; }
.filter-field { display: flex; flex-direction: column; gap: 4px; color: var(--sg-text-muted); }
.filter-label { font-size: 0.8rem; }
.filter-field select {
  min-height: 48px; padding: 0 12px; border-radius: 10px;
  background: var(--sg-bg-panel); color: var(--sg-text-primary); border: 1px solid var(--sg-bg-panel);
}
.error-text, .empty-text { color: var(--sg-text-muted); }
.board { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.board-row {
  display: flex; align-items: center; gap: 14px; min-height: 56px;
  padding: 10px 16px; border-radius: 12px; background: var(--sg-bg-card);
}
.board-rank { width: 28px; font-size: 1.2rem; font-weight: 700; color: var(--sg-text-faint); }
.board-main { flex: 1; display: flex; flex-direction: column; }
.board-name { color: var(--sg-text-primary); font-weight: 600; }
.board-meta { color: var(--sg-text-faint); font-size: 0.85rem; }
.board-score { font-size: 1.3rem; font-weight: 700; color: var(--sg-brand); }
</style>
```

Verify token names against `src/assets/main.css`; if the page wrapper/heading conventions of sibling shooter views (e.g. `PasseManagementView.vue`) differ, match them. Check `passeStore`'s `toUiSerie` for the Serie display-name field (`name` vs `alias`) and use it in the option label. The test mounts now touch `rangeStore` and `passeStore` — add `vi.mock` entries for the service modules those stores import (check their import lines; mock the fetch functions to resolve `[]`) so `initialize()`/`loadSerienFromStorage()` are inert in tests.

- [ ] **Step 4: Register the route and home tile**

In `router/index.js`, after the `/profil` route (line ~52):

```javascript
  { path: '/bestenliste',          component: () => import('@/views/shooter/ShooterBestenlisteView.vue'), meta: { layout: 'shooter', permission: 'VIEW_REMOTE' } },
```

In `ShooterHomeView.vue`, after the profile tile (line ~45), reusing the tile pattern:

```html
      <button class="app-tile app-tile--available app-tile--cyan" data-testid="open-bestenliste" @click="router.push('/bestenliste')">
        <div class="tile-icon-wrap">
          <Icons icon="trophy" :size="36" color="#1a1a2e" />
        </div>
        <span class="tile-label">Bestenliste</span>
        <span class="tile-desc">Ranglisten & Rekorde</span>
      </button>
```

Check `src/components/Icons.vue` for an existing trophy/ranking icon name; if none exists, use an existing fitting icon (e.g. `target`) — do not add a new icon in this task.

- [ ] **Step 5: Run tests**

Run: `npm run test src/views/shooter/__tests__/ShooterBestenlisteView.test.js`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add smart-ground-ui/src/views/shooter/ShooterBestenlisteView.vue smart-ground-ui/src/views/shooter/__tests__/ShooterBestenlisteView.test.js smart-ground-ui/src/router/index.js smart-ground-ui/src/views/shooter/ShooterHomeView.vue
git commit -m "[ui] add Bestenliste leaderboard view with metric and context filters"
```

---

### Task 11: Full verification

**Files:** none new.

- [ ] **Step 1: Backend suite**

From `smart-ground-backend/`: `mvn test`
Expected: PASS, zero failures.

- [ ] **Step 2: UI suite + lint + build**

From `smart-ground-ui/`:

```bash
npm run test
npm run lint:check
npm run build
```

Expected: all PASS, no ESLint warnings, build without warnings.

- [ ] **Step 3: Leftover-reference sweep**

```bash
grep -rn "MyPlayResultEntry\|listMyPlayResults\|fetchMyPlayResults\|findCompletedByParticipantUserId\|loadMyResults\|myResults" smart-ground-backend/src smart-ground-ui/src
```

Expected: no matches. Fix any stragglers, rerun the affected suite.

- [ ] **Step 4: Commit any verification fixes**

```bash
git add -A smart-ground-backend/src smart-ground-ui/src
git commit -m "[backend][ui] verification fixes for user score tracking" # only if there are changes
```

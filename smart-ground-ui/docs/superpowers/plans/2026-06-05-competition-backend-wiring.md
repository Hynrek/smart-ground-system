# Competition Backend Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the Wettkampf competition system to the backend: replace the localStorage-backed `competitionEventStore` with REST API calls, persist competition progress via a new `CompetitionSerieResult` table, and populate `PlayerResult`/`CareerStats` for cross-competition score history.

**Architecture:** Backend-first. Extend `SessionStatus` with `OPEN` and `PRE_COMPLETE`, add `CompetitionSerieResult` entity + `CompetitionProgressService` for progress tracking and `PlayerResult` population, expose new endpoints in `WettkampfController`. Frontend replaces localStorage with a new `wettkampfApi.js` service, rewrites `competitionEventStore.js`, removes the in-memory competition branch from `activePasseStore.js`, and adds a polling loop to `ActiveCompetitionPanel`.

**Tech Stack:** Java 25 / Spring Boot 4 / JPA (backend); Vue 3 / Pinia / Vitest (frontend)

---

## File Map

### Backend (`smart-ground-backend/src/main/java/ch/jp/shooting/`)

**Modified:**
- `model/SessionStatus.java` — add `OPEN`, `PRE_COMPLETE`
- `model/SessionPlayer.java` — add `paid` field + accessor
- `dto/SessionPlayerResponse.java` — add `paid`
- `dto/SessionPlayerCreateRequest.java` — add `paid`
- `dto/SessionResponse.java` — add `name`
- `dto/CreateSessionRequest.java` — add `name` + `passen`
- `service/SessionService.java` — extend `createSession`, `updateSessionStatus`, add `deleteSession` + member management methods

**Created:**
- `model/CompetitionSerieResult.java`
- `repository/CompetitionSerieResultRepository.java`
- `dto/PasseSnapshot.java`
- `dto/CompleteSerieRequest.java`
- `dto/CompetitionSerieResultResponse.java`
- `dto/CompetitionProgressResponse.java`
- `dto/PatchMemberRequest.java`
- `service/CompetitionProgressService.java`
- `api/WettkampfController.java`
- `src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java`

### Frontend (`smart-ground-ui/src/`)

**Created:**
- `services/wettkampfApi.js`
- `services/__tests__/wettkampfApi.test.js`

**Rewritten:**
- `stores/competitionEventStore.js`
- `stores/__tests__/competitionEventStore.test.js`

**Modified:**
- `stores/activePasseStore.js` — remove in-memory competition branch
- `views/admin/WettkampfDetailView.vue` — add OPEN state + Veröffentlichen button
- `views/admin/WettkampfListView.vue` — load from API, pass Passe snapshots
- `components/competition/ActiveCompetitionPanel.vue` — add 4s progress polling

---

## ⚠️ Important Notes

- **Backend must be running** for end-to-end frontend testing. Start with: `cd smart-ground-backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres`
- **No backend tests exist yet** — this plan creates the first ones.
- `SessionController` implements a generated `SessionApi`. `WettkampfController` deliberately uses non-overlapping paths. If you see startup errors about ambiguous handler mappings, check for path conflicts.

---

## Task 1: Extend SessionStatus + update transition validation

**Files:**
- `smart-ground-backend/src/main/java/ch/jp/shooting/model/SessionStatus.java`
- `smart-ground-backend/src/main/java/ch/jp/shooting/service/SessionService.java` (method `validateStatusTransition`)
- `smart-ground-backend/src/test/java/ch/jp/shooting/service/SessionServiceStatusTest.java` *(create)*

- [ ] **Create the test directory**
```bash
mkdir -p smart-ground-backend/src/test/java/ch/jp/shooting/service
```

- [ ] **Write the failing test** — create `src/test/java/ch/jp/shooting/service/SessionServiceStatusTest.java`:
```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.SessionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionServiceStatusTest {

    @Test
    void sessionStatus_hasOpenAndPreComplete() {
        assertNotNull(SessionStatus.valueOf("OPEN"));
        assertNotNull(SessionStatus.valueOf("PRE_COMPLETE"));
    }

    @Test
    void sessionStatus_hasSixValues() {
        assertEquals(6, SessionStatus.values().length);
    }
}
```

- [ ] **Run test — expect FAIL**
```bash
cd smart-ground-backend && ./mvnw test -Dtest=SessionServiceStatusTest 2>&1 | tail -15
```
Expected: `No enum constant ch.jp.shooting.model.SessionStatus.OPEN`

- [ ] **Replace entire `SessionStatus.java`:**
```java
package ch.jp.shooting.model;

public enum SessionStatus {
    SETUP, OPEN, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED
}
```

- [ ] **Replace `validateStatusTransition` in `SessionService.java`** (find it around line 227):
```java
private void validateStatusTransition(SessionStatus from, SessionStatus to) {
    if (to == SessionStatus.ABANDONED) return; // any status can be abandoned
    if (from == SessionStatus.COMPLETED || from == SessionStatus.ABANDONED) {
        throw new IllegalStateException("Cannot transition from terminal status " + from);
    }
    boolean valid = switch (from) {
        case SETUP -> to == SessionStatus.OPEN;
        case OPEN  -> to == SessionStatus.ACTIVE;
        case ACTIVE -> to == SessionStatus.PRE_COMPLETE;
        case PRE_COMPLETE -> to == SessionStatus.COMPLETED;
        default -> false;
    };
    if (!valid) throw new IllegalStateException("Invalid transition: " + from + " → " + to);
}
```

- [ ] **Run test — expect PASS**
```bash
cd smart-ground-backend && ./mvnw test -Dtest=SessionServiceStatusTest 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Commit**
```bash
cd smart-ground-backend
git add src/main/java/ch/jp/shooting/model/SessionStatus.java \
        src/main/java/ch/jp/shooting/service/SessionService.java \
        src/test/java/ch/jp/shooting/service/SessionServiceStatusTest.java
git commit -m "[backend] add OPEN and PRE_COMPLETE to SessionStatus; update transition validation"
```

---

## Task 2: Add `paid` to SessionPlayer + update DTOs + add `name` to SessionResponse

**Files:**
- `model/SessionPlayer.java`
- `dto/SessionPlayerCreateRequest.java`
- `dto/SessionPlayerResponse.java`
- `dto/SessionResponse.java`
- `service/SessionService.java` (mapper methods)

- [ ] **Add `paid` field + accessor to `SessionPlayer.java`** — after the `createdAt` field:
```java
@Column(nullable = false)
private boolean paid = false;
```
After the existing `setCreatedAt` method:
```java
public boolean isPaid() { return paid; }
public void setPaid(boolean paid) { this.paid = paid; }
```

- [ ] **Add `paid` to `SessionPlayerCreateRequest.java`** — after the `userId` field:
```java
@JsonProperty("paid")
public boolean paid = false;
```

- [ ] **Add `paid` to `SessionPlayerResponse.java`** — after the `createdAt` field:
```java
@JsonProperty("paid")
public boolean paid;
```

- [ ] **Add `name` to `SessionResponse.java`** — after the `id` field:
```java
@JsonProperty("name")
@Nullable
public String name;
```

- [ ] **Update `SessionService.addPlayerToGroup`** — after `player.setDisplayName(req.displayName)` add:
```java
player.setPaid(req.paid);
```

- [ ] **Update `SessionService.mapPlayerToResponse`** — after `resp.createdAt = player.getCreatedAt()` add:
```java
resp.paid = player.isPaid();
```

- [ ] **Update `SessionService.mapSessionToResponse`** — after `resp.id = session.getId()` add:
```java
resp.name = session.getName();
```

- [ ] **Compile to verify**
```bash
cd smart-ground-backend && ./mvnw compile 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Commit**
```bash
cd smart-ground-backend
git add src/main/java/ch/jp/shooting/model/SessionPlayer.java \
        src/main/java/ch/jp/shooting/dto/SessionPlayerCreateRequest.java \
        src/main/java/ch/jp/shooting/dto/SessionPlayerResponse.java \
        src/main/java/ch/jp/shooting/dto/SessionResponse.java \
        src/main/java/ch/jp/shooting/service/SessionService.java
git commit -m "[backend] add paid to SessionPlayer; add name to SessionResponse"
```

---

## Task 3: Create CompetitionSerieResult entity + repository

**Files:**
- `model/CompetitionSerieResult.java` *(create)*
- `repository/CompetitionSerieResultRepository.java` *(create)*

- [ ] **Create `CompetitionSerieResult.java`:**
```java
package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Abgeschlossene Serie für eine Rotte in einem Wettkampf.
 * Eine Zeile pro (session, group, passeIndex, serieId).
 */
@Entity
@Table(
    name = "competition_serie_results",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"session_id", "group_id", "passe_index", "serie_id"}
    )
)
@NullMarked
public class CompetitionSerieResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private LiveSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ShooterGroup group;

    @Column(name = "passe_index", nullable = false)
    private int passeIndex;

    @Column(name = "serie_id", nullable = false)
    private UUID serieId;

    @Column(name = "play_instance_id")
    @Nullable
    private UUID playInstanceId;

    /**
     * Rohergebnisse vom Play als JSON.
     * Format: { "players": [{ "playerId": "uuid", "totalPoints": 8, "maxPoints": 10 }] }
     */
    @Column(name = "results", columnDefinition = "TEXT")
    @Nullable
    private String results;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    public CompetitionSerieResult() {}

    public CompetitionSerieResult(LiveSession session, ShooterGroup group, int passeIndex, UUID serieId) {
        this.session = session;
        this.group = group;
        this.passeIndex = passeIndex;
        this.serieId = serieId;
    }

    public UUID getId() { return id; }
    public LiveSession getSession() { return session; }
    public void setSession(LiveSession session) { this.session = session; }
    public ShooterGroup getGroup() { return group; }
    public void setGroup(ShooterGroup group) { this.group = group; }
    public int getPasseIndex() { return passeIndex; }
    public void setPasseIndex(int passeIndex) { this.passeIndex = passeIndex; }
    public UUID getSerieId() { return serieId; }
    public void setSerieId(UUID serieId) { this.serieId = serieId; }
    @Nullable public UUID getPlayInstanceId() { return playInstanceId; }
    public void setPlayInstanceId(@Nullable UUID playInstanceId) { this.playInstanceId = playInstanceId; }
    @Nullable public String getResults() { return results; }
    public void setResults(@Nullable String results) { this.results = results; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
```

- [ ] **Create `CompetitionSerieResultRepository.java`:**
```java
package ch.jp.shooting.repository;

import ch.jp.shooting.model.CompetitionSerieResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompetitionSerieResultRepository extends JpaRepository<CompetitionSerieResult, UUID> {

    List<CompetitionSerieResult> findBySessionId(UUID sessionId);

    List<CompetitionSerieResult> findBySessionIdAndGroupId(UUID sessionId, UUID groupId);

    boolean existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
        UUID sessionId, UUID groupId, int passeIndex, UUID serieId
    );
}
```

- [ ] **Compile — Hibernate will auto-create the table on next startup**
```bash
cd smart-ground-backend && ./mvnw compile 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Commit**
```bash
cd smart-ground-backend
git add src/main/java/ch/jp/shooting/model/CompetitionSerieResult.java \
        src/main/java/ch/jp/shooting/repository/CompetitionSerieResultRepository.java
git commit -m "[backend] add CompetitionSerieResult entity and repository"
```

---

## Task 4: PasseSnapshot DTO + update CreateSessionRequest + createSession

**Files:**
- `dto/PasseSnapshot.java` *(create)*
- `dto/CreateSessionRequest.java`
- `service/SessionService.java`

- [ ] **Create `PasseSnapshot.java`:**
```java
package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot der Passen-Struktur beim Wettkampf-Start.
 * Gespeichert in LiveSession.programSnapshots als JSON-Array.
 */
@NullMarked
public class PasseSnapshot {
    @JsonProperty("id")
    public String id;       // Passe-UUID als String

    @JsonProperty("name")
    @Nullable
    public String name;

    @JsonProperty("serieIds")
    public List<String> serieIds = new ArrayList<>(); // geordnete Serie-UUIDs

    public PasseSnapshot() {}
}
```

- [ ] **Add fields to `CreateSessionRequest.java`** — after the `groups` field:
```java
@JsonProperty("name")
@Nullable
public String name;

@JsonProperty("passen")
@Nullable
public List<PasseSnapshot> passen;
```

- [ ] **Update `SessionService.createSession(CreateSessionRequest req)`** — in the existing method, after `session.setStatus(SessionStatus.SETUP)`, add:
```java
if (req.name != null && !req.name.isBlank()) {
    session.setName(req.name);
}
```
After the existing `programs` block (around `if (req.programs != null)`), add:
```java
if (req.passen != null) {
    try {
        session.setProgramSnapshots(objectMapper.writeValueAsString(req.passen));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new RuntimeException("Failed to serialize passen", e);
    }
}
```

- [ ] **Compile**
```bash
cd smart-ground-backend && ./mvnw compile 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`

- [ ] **Commit**
```bash
cd smart-ground-backend
git add src/main/java/ch/jp/shooting/dto/PasseSnapshot.java \
        src/main/java/ch/jp/shooting/dto/CreateSessionRequest.java \
        src/main/java/ch/jp/shooting/service/SessionService.java
git commit -m "[backend] add PasseSnapshot DTO; accept name and passen on session creation"
```

---

## Task 5: Create CompetitionProgressService + tests

**Files:**
- `dto/CompleteSerieRequest.java` *(create)*
- `dto/CompetitionSerieResultResponse.java` *(create)*
- `dto/CompetitionProgressResponse.java` *(create)*
- `service/CompetitionProgressService.java` *(create)*
- `src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java` *(create)*

- [ ] **Create `CompleteSerieRequest.java`:**
```java
package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@NullMarked
public class CompleteSerieRequest {

    @JsonProperty("passeIndex")
    public int passeIndex;

    @JsonProperty("playInstanceId")
    @Nullable
    public UUID playInstanceId;

    /**
     * Rohergebnisse vom Play.
     * Erwartetes Format: { "players": [{ "playerId": "uuid", "totalPoints": 8, "maxPoints": 10 }] }
     */
    @JsonProperty("results")
    @Nullable
    public Object results;

    public CompleteSerieRequest() {}
}
```

- [ ] **Create `CompetitionSerieResultResponse.java`:**
```java
package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@NullMarked
public class CompetitionSerieResultResponse {
    @JsonProperty("id")         public UUID id;
    @JsonProperty("sessionId")  public UUID sessionId;
    @JsonProperty("groupId")    public UUID groupId;
    @JsonProperty("passeIndex") public int passeIndex;
    @JsonProperty("serieId")    public UUID serieId;
    @JsonProperty("playInstanceId") @Nullable public UUID playInstanceId;
    @JsonProperty("completedAt") public Instant completedAt;

    public CompetitionSerieResultResponse() {}
}
```

- [ ] **Create `CompetitionProgressResponse.java`:**
```java
package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NullMarked
public class CompetitionProgressResponse {

    @JsonProperty("groups")
    public List<GroupProgress> groups = new ArrayList<>();

    public static class GroupProgress {
        @JsonProperty("groupId")           public UUID groupId;
        @JsonProperty("groupName")         public String groupName;
        @JsonProperty("currentPasseIndex") public int currentPasseIndex;
        @JsonProperty("completedSerien")   public List<SerieCompletion> completedSerien = new ArrayList<>();
    }

    public static class SerieCompletion {
        @JsonProperty("passeIndex")  public int passeIndex;
        @JsonProperty("serieId")     public UUID serieId;
        @JsonProperty("completedAt") public Instant completedAt;
    }
}
```

- [ ] **Write the failing test** — create `src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java`:
```java
package ch.jp.shooting.service;

import ch.jp.shooting.dto.CompleteSerieRequest;
import ch.jp.shooting.dto.CompetitionProgressResponse;
import ch.jp.shooting.dto.PasseSnapshot;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetitionProgressServiceTest {

    @Mock CompetitionSerieResultRepository csrRepository;
    @Mock LiveSessionRepository sessionRepository;
    @Mock ShooterGroupRepository groupRepository;
    @Mock PlayerResultRepository playerResultRepository;
    @InjectMocks CompetitionProgressService progressService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LiveSession session;
    private ShooterGroup group;
    private UUID sessionId;
    private UUID groupId;
    private UUID serieId;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper.findAndRegisterModules();
        // inject real ObjectMapper (not a @Mock)
        Field f = CompetitionProgressService.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(progressService, objectMapper);

        sessionId = UUID.randomUUID();
        groupId   = UUID.randomUUID();
        serieId   = UUID.randomUUID();

        session = new LiveSession(SessionType.COMPETITION, SessionStatus.ACTIVE);

        PasseSnapshot passe = new PasseSnapshot();
        passe.id = "passe-1";
        passe.name = "Passe 1";
        passe.serieIds = List.of(serieId.toString());
        session.setProgramSnapshots(objectMapper.writeValueAsString(List.of(passe)));

        group = new ShooterGroup(session, "Rotte A");
        group.setMembers(new ArrayList<>());
    }

    @Test
    void completeSerie_insertsRow() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
            any(), any(), anyInt(), any())).thenReturn(false);
        when(csrRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));

        CompleteSerieRequest req = new CompleteSerieRequest();
        req.passeIndex = 0;

        progressService.completeSerie(sessionId, groupId, serieId, req);

        verify(csrRepository).save(argThat(r ->
            r.getPasseIndex() == 0 && r.getSerieId().equals(serieId)
        ));
    }

    @Test
    void completeSerie_throwsIfAlreadyCompleted() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
            any(), any(), anyInt(), any())).thenReturn(true);

        CompleteSerieRequest req = new CompleteSerieRequest();
        req.passeIndex = 0;

        assertThrows(IllegalStateException.class,
            () -> progressService.completeSerie(sessionId, groupId, serieId, req));
    }

    @Test
    void getProgress_returnsGroupWithCurrentPasseIndexZero() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(sessionId)).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of());

        CompetitionProgressResponse resp = progressService.getProgress(sessionId);

        assertEquals(1, resp.groups.size());
        assertEquals("Rotte A", resp.groups.get(0).groupName);
        assertEquals(0, resp.groups.get(0).currentPasseIndex);
    }
}
```

- [ ] **Run test — expect FAIL** (class does not exist yet)
```bash
cd smart-ground-backend && ./mvnw test -Dtest=CompetitionProgressServiceTest 2>&1 | tail -15
```
Expected: compilation error — `cannot find symbol: class CompetitionProgressService`

- [ ] **Create `CompetitionProgressService.java`:**
```java
package ch.jp.shooting.service;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für Wettkampf-Fortschrittsverfolgung.
 * Speichert Serie-Abschlüsse, aktualisiert PlayerResult pro Schütze,
 * und löst den PRE_COMPLETE-Übergang aus wenn alle Serien abgeschlossen sind.
 */
@Service
@Transactional
@NullMarked
public class CompetitionProgressService {

    private final CompetitionSerieResultRepository csrRepository;
    private final LiveSessionRepository sessionRepository;
    private final ShooterGroupRepository groupRepository;
    private final PlayerResultRepository playerResultRepository;
    private final ObjectMapper objectMapper;

    public CompetitionProgressService(
            CompetitionSerieResultRepository csrRepository,
            LiveSessionRepository sessionRepository,
            ShooterGroupRepository groupRepository,
            PlayerResultRepository playerResultRepository,
            ObjectMapper objectMapper) {
        this.csrRepository = csrRepository;
        this.sessionRepository = sessionRepository;
        this.groupRepository = groupRepository;
        this.playerResultRepository = playerResultRepository;
        this.objectMapper = objectMapper;
    }

    public CompetitionSerieResultResponse completeSerie(
            UUID sessionId, UUID groupId, UUID serieId,
            CompleteSerieRequest request) throws Exception {

        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        if (csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
                sessionId, groupId, request.passeIndex, serieId)) {
            throw new IllegalStateException("Serie already completed for this group");
        }

        CompetitionSerieResult csr = new CompetitionSerieResult(session, group, request.passeIndex, serieId);
        csr.setPlayInstanceId(request.playInstanceId);
        if (request.results != null) {
            csr.setResults(objectMapper.writeValueAsString(request.results));
        }
        csr = csrRepository.save(csr);

        upsertPlayerResults(session, group, request.passeIndex, serieId, request.results);

        if (isAllPassenDoneForAllGroups(session)) {
            session.setStatus(SessionStatus.PRE_COMPLETE);
            sessionRepository.save(session);
        }

        return toResponse(csr);
    }

    @Transactional(readOnly = true)
    public CompetitionProgressResponse getProgress(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<ShooterGroup> groups = groupRepository.findBySessionId(sessionId);
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());

        CompetitionProgressResponse response = new CompetitionProgressResponse();
        for (ShooterGroup group : groups) {
            List<CompetitionSerieResult> completed =
                csrRepository.findBySessionIdAndGroupId(sessionId, group.getId());

            CompetitionProgressResponse.GroupProgress gp = new CompetitionProgressResponse.GroupProgress();
            gp.groupId   = group.getId();
            gp.groupName = group.getName();
            gp.currentPasseIndex = computeCurrentPasseIndex(completed, passen);

            for (CompetitionSerieResult c : completed) {
                CompetitionProgressResponse.SerieCompletion sc = new CompetitionProgressResponse.SerieCompletion();
                sc.passeIndex  = c.getPasseIndex();
                sc.serieId     = c.getSerieId();
                sc.completedAt = c.getCompletedAt();
                gp.completedSerien.add(sc);
            }
            response.groups.add(gp);
        }
        return response;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private boolean isAllPassenDoneForAllGroups(LiveSession session) throws Exception {
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());
        if (passen.length == 0) return false;

        List<ShooterGroup> groups = groupRepository.findBySessionId(session.getId());
        if (groups.isEmpty()) return false;

        for (ShooterGroup group : groups) {
            for (int pi = 0; pi < passen.length; pi++) {
                for (String serieIdStr : passen[pi].serieIds) {
                    if (!csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
                            session.getId(), group.getId(), pi, UUID.fromString(serieIdStr))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private int computeCurrentPasseIndex(List<CompetitionSerieResult> completed, PasseSnapshot[] passen) {
        for (int pi = 0; pi < passen.length; pi++) {
            final int index = pi;
            boolean allDone = passen[pi].serieIds.stream().allMatch(sid ->
                completed.stream().anyMatch(c ->
                    c.getPasseIndex() == index && c.getSerieId().toString().equals(sid)));
            if (!allDone) return pi;
        }
        return passen.length; // all passen done
    }

    private PasseSnapshot[] parsePassen(String json) throws Exception {
        if (json == null || json.isBlank()) return new PasseSnapshot[0];
        return objectMapper.readValue(json, PasseSnapshot[].class);
    }

    @SuppressWarnings("unchecked")
    private void upsertPlayerResults(LiveSession session, ShooterGroup group,
                                     int passeIndex, UUID serieId, Object results) throws Exception {
        if (group.getMembers().isEmpty()) return;

        List<Map<String, Object>> playerScores = List.of();
        if (results != null) {
            Map<String, Object> map = objectMapper.convertValue(results, Map.class);
            Object players = map.get("players");
            if (players instanceof List<?> list) {
                playerScores = (List<Map<String, Object>>) list;
            }
        }
        final List<Map<String, Object>> scores = playerScores;

        for (SessionPlayer member : group.getMembers()) {
            int totalPoints = 0, maxPoints = 0;
            for (Map<String, Object> ps : scores) {
                if (member.getId().toString().equals(String.valueOf(ps.get("playerId")))) {
                    totalPoints = ((Number) ps.getOrDefault("totalPoints", 0)).intValue();
                    maxPoints   = ((Number) ps.getOrDefault("maxPoints",   0)).intValue();
                    break;
                }
            }

            PlayerResult pr = playerResultRepository
                .findBySessionIdAndPlayerId(session.getId(), member.getId())
                .orElse(new PlayerResult(session, member));

            List<Map<String, Object>> existing = new ArrayList<>();
            if (pr.getProgramResults() != null) {
                existing = new ArrayList<>(Arrays.asList(
                    objectMapper.readValue(pr.getProgramResults(), Map[].class)));
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("passeIndex",  passeIndex);
            entry.put("serieId",     serieId.toString());
            entry.put("totalPoints", totalPoints);
            entry.put("maxPoints",   maxPoints);
            entry.put("completedAt", Instant.now().toString());
            existing.add(entry);

            pr.setProgramResults(objectMapper.writeValueAsString(existing));
            pr.setUpdatedAt(Instant.now());
            playerResultRepository.save(pr);
        }
    }

    private CompetitionSerieResultResponse toResponse(CompetitionSerieResult csr) {
        CompetitionSerieResultResponse r = new CompetitionSerieResultResponse();
        r.id             = csr.getId();
        r.sessionId      = csr.getSession().getId();
        r.groupId        = csr.getGroup().getId();
        r.passeIndex     = csr.getPasseIndex();
        r.serieId        = csr.getSerieId();
        r.playInstanceId = csr.getPlayInstanceId();
        r.completedAt    = csr.getCompletedAt();
        return r;
    }
}
```

- [ ] **Run tests — expect PASS**
```bash
cd smart-ground-backend && ./mvnw test -Dtest=CompetitionProgressServiceTest 2>&1 | tail -15
```
Expected: `BUILD SUCCESS`, 3 tests pass.

- [ ] **Commit**
```bash
cd smart-ground-backend
git add src/main/java/ch/jp/shooting/dto/CompleteSerieRequest.java \
        src/main/java/ch/jp/shooting/dto/CompetitionSerieResultResponse.java \
        src/main/java/ch/jp/shooting/dto/CompetitionProgressResponse.java \
        src/main/java/ch/jp/shooting/service/CompetitionProgressService.java \
        src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java
git commit -m "[backend] add CompetitionProgressService: completeSerie, getProgress, auto-PRE_COMPLETE"
```

---

## Task 6: Create WettkampfController — status, delete, group + member endpoints

**Files:**
- `dto/PatchMemberRequest.java` *(create)*
- `service/SessionService.java` — add `deleteSession`, `getSessionEntity`, `addMember`, `removeMember`, `patchMember`
- `api/WettkampfController.java` *(create)*

- [ ] **Create `PatchMemberRequest.java`:**
```java
package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class PatchMemberRequest {
    @JsonProperty("paid")
    public boolean paid;
    public PatchMemberRequest() {}
}
```

- [ ] **Add `deleteSession` + `getSessionEntity` to `SessionService.java`:**
```java
public void deleteSession(UUID sessionId) {
    LiveSession session = sessionRepository.findById(sessionId)
        .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    if (session.getStatus() != SessionStatus.SETUP) {
        throw new IllegalStateException("Can only delete sessions in SETUP status");
    }
    sessionRepository.delete(session);
}

public LiveSession getSessionEntity(UUID sessionId) {
    return sessionRepository.findById(sessionId)
        .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
}
```

Also wire `CareerStats` update when transitioning to COMPLETED — add this import at top of `SessionService.java`:
```java
import ch.jp.shooting.service.CompetitionService;
```
And inject via constructor (add field + constructor param):
```java
private final CompetitionService competitionService;
```
In `updateSessionStatus`, after `session = sessionRepository.save(session)` in the COMPLETED branch:
```java
if (status == SessionStatus.COMPLETED && session.getType() == SessionType.COMPETITION) {
    try {
        competitionService.updateCareerStatsForSession(sessionId);
    } catch (Exception e) {
        // log but don't fail the status transition
        System.err.println("[SessionService] Failed to update career stats: " + e.getMessage());
    }
}
```
*(Note: This creates a circular dependency risk if `CompetitionService` injects `SessionService`. Verify — if circular, move career stats update to a `@EventListener` or call from the controller after the status update.)*

- [ ] **Add member management methods to `SessionService.java`:**
```java
public SessionPlayerResponse addMember(UUID sessionId, UUID groupId, SessionPlayerCreateRequest req) {
    ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
        .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    SessionStatus st = group.getSession().getStatus();
    if (st != SessionStatus.SETUP && st != SessionStatus.OPEN) {
        throw new IllegalStateException("Cannot add members after session started");
    }
    SessionPlayer player = new SessionPlayer();
    player.setGroup(group);
    player.setType(PlayerType.valueOf(req.type.toUpperCase()));
    player.setDisplayName(req.displayName);
    player.setPaid(req.paid);
    if (req.userId != null) {
        userRepository.findById(req.userId).ifPresent(player::setUser);
    }
    player = playerRepository.save(player);
    return mapPlayerToResponse(player);
}

public void removeMember(UUID sessionId, UUID groupId, UUID memberId) {
    ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
        .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    SessionStatus st = group.getSession().getStatus();
    if (st != SessionStatus.SETUP && st != SessionStatus.OPEN) {
        throw new IllegalStateException("Cannot remove members after session started");
    }
    playerRepository.deleteById(memberId);
}

public SessionPlayerResponse patchMember(UUID sessionId, UUID groupId, UUID memberId, PatchMemberRequest req) {
    ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
        .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    SessionPlayer player = group.getMembers().stream()
        .filter(p -> p.getId().equals(memberId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
    player.setPaid(req.paid);
    return mapPlayerToResponse(playerRepository.save(player));
}
```

- [ ] **Create `WettkampfController.java`:**
```java
package ch.jp.shooting.api;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.service.CompetitionProgressService;
import ch.jp.shooting.service.SessionService;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Wettkampf-spezifische REST-Endpunkte.
 * Ergänzt SessionController um Status-Wechsel, Löschen, Mitglieder-Verwaltung
 * und Fortschritts-Tracking.
 */
@RestController
@RequestMapping("/api/sessions")
@NullMarked
public class WettkampfController {

    private final SessionService sessionService;
    private final CompetitionProgressService progressService;

    public WettkampfController(SessionService sessionService,
                                CompetitionProgressService progressService) {
        this.sessionService = sessionService;
        this.progressService = progressService;
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    public ResponseEntity<SessionResponse> patchStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(sessionService.updateSessionStatus(id, status.toUpperCase()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    // ── Gruppe (Rotte) management ─────────────────────────────────────────────

    @PutMapping("/{id}/groups/{groupId}")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @RequestBody GroupCreateRequest request) {
        return ResponseEntity.ok(sessionService.updateGroup(id, groupId, request));
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable UUID id,
            @PathVariable UUID groupId) {
        sessionService.deleteGroup(id, groupId);
        return ResponseEntity.noContent().build();
    }

    // ── Mitglieder (Schützen) management ─────────────────────────────────────

    @PostMapping("/{id}/groups/{groupId}/members")
    public ResponseEntity<SessionPlayerResponse> addMember(
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @RequestBody SessionPlayerCreateRequest request) {
        return ResponseEntity.status(201).body(sessionService.addMember(id, groupId, request));
    }

    @DeleteMapping("/{id}/groups/{groupId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        sessionService.removeMember(id, groupId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/groups/{groupId}/members/{memberId}")
    public ResponseEntity<SessionPlayerResponse> patchMember(
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @RequestBody PatchMemberRequest request) {
        return ResponseEntity.ok(sessionService.patchMember(id, groupId, memberId, request));
    }

    // ── Wettkampf-Fortschritt ─────────────────────────────────────────────────

    @PostMapping("/{sessionId}/groups/{groupId}/serien/{serieId}/complete")
    public ResponseEntity<CompetitionSerieResultResponse> completeSerie(
            @PathVariable UUID sessionId,
            @PathVariable UUID groupId,
            @PathVariable UUID serieId,
            @RequestBody CompleteSerieRequest request) throws Exception {
        return ResponseEntity.status(201)
            .body(progressService.completeSerie(sessionId, groupId, serieId, request));
    }

    @GetMapping("/{sessionId}/progress")
    public ResponseEntity<CompetitionProgressResponse> getProgress(
            @PathVariable UUID sessionId) throws Exception {
        return ResponseEntity.ok(progressService.getProgress(sessionId));
    }
}
```

- [ ] **Compile**
```bash
cd smart-ground-backend && ./mvnw compile 2>&1 | tail -15
```
Expected: `BUILD SUCCESS`. If you see a circular dependency error between `SessionService` and `CompetitionService`, remove the `CompetitionService` injection from `SessionService` and instead call `updateCareerStatsForSession` directly in the `patchStatus` endpoint of `WettkampfController` after the status update.

- [ ] **Run all backend tests**
```bash
cd smart-ground-backend && ./mvnw test 2>&1 | tail -15
```
Expected: `BUILD SUCCESS`

- [ ] **Commit**
```bash
cd smart-ground-backend
git add src/main/java/ch/jp/shooting/dto/PatchMemberRequest.java \
        src/main/java/ch/jp/shooting/api/WettkampfController.java \
        src/main/java/ch/jp/shooting/service/SessionService.java
git commit -m "[backend] add WettkampfController: status, delete, group/member management, progress endpoints"
```

---

## Task 7: Create `wettkampfApi.js` + tests

**Files:**
- `smart-ground-ui/src/services/wettkampfApi.js` *(create)*
- `smart-ground-ui/src/services/__tests__/wettkampfApi.test.js` *(create)*

- [ ] **Write the failing test** — create `src/services/__tests__/wettkampfApi.test.js`:
```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockApiFetch = vi.fn()
vi.mock('@/services/apiClient.js', () => ({ apiFetch: mockApiFetch }))

import * as api from '@/services/wettkampfApi.js'

describe('wettkampfApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockApiFetch.mockResolvedValue({})
  })

  it('createSession posts to /sessions with competition type', async () => {
    mockApiFetch.mockResolvedValue({ id: 's1', status: 'SETUP' })
    await api.createSession('Frühjahrspokal', [{ id: 'p1', serieIds: [] }], [])
    expect(mockApiFetch).toHaveBeenCalledWith('/sessions', expect.objectContaining({ method: 'POST' }))
    const body = JSON.parse(mockApiFetch.mock.calls[0][1].body)
    expect(body.type).toBe('competition')
    expect(body.name).toBe('Frühjahrspokal')
  })

  it('listSessions appends type query param', async () => {
    mockApiFetch.mockResolvedValue({ content: [] })
    await api.listSessions('competition')
    expect(mockApiFetch).toHaveBeenCalledWith(
      expect.stringContaining('type=competition'), undefined
    )
  })

  it('patchStatus patches /sessions/:id/status', async () => {
    await api.patchStatus('s1', 'open')
    expect(mockApiFetch).toHaveBeenCalledWith(
      '/sessions/s1/status', expect.objectContaining({ method: 'PATCH' })
    )
    const body = JSON.parse(mockApiFetch.mock.calls[0][1].body)
    expect(body.status).toBe('open')
  })

  it('deleteSession calls DELETE /sessions/:id', async () => {
    await api.deleteSession('s1')
    expect(mockApiFetch).toHaveBeenCalledWith('/sessions/s1', expect.objectContaining({ method: 'DELETE' }))
  })

  it('addMember posts to members endpoint', async () => {
    await api.addMember('s1', 'g1', { displayName: 'Max', type: 'USER', paid: false })
    expect(mockApiFetch).toHaveBeenCalledWith(
      '/sessions/s1/groups/g1/members', expect.objectContaining({ method: 'POST' })
    )
  })

  it('patchMember patches member paid status', async () => {
    await api.patchMember('s1', 'g1', 'm1', true)
    const body = JSON.parse(mockApiFetch.mock.calls[0][1].body)
    expect(body.paid).toBe(true)
  })

  it('completeSerie posts to serien complete endpoint', async () => {
    await api.completeSerie('s1', 'g1', 'ser1', 0, null, null)
    expect(mockApiFetch).toHaveBeenCalledWith(
      '/sessions/s1/groups/g1/serien/ser1/complete',
      expect.objectContaining({ method: 'POST' })
    )
  })

  it('getProgress calls GET progress endpoint', async () => {
    mockApiFetch.mockResolvedValue({ groups: [] })
    const result = await api.getProgress('s1')
    expect(mockApiFetch).toHaveBeenCalledWith('/sessions/s1/progress', undefined)
    expect(result.groups).toEqual([])
  })
})
```

- [ ] **Run test — expect FAIL**
```bash
cd smart-ground-ui && npm run test src/services/__tests__/wettkampfApi.test.js 2>&1 | tail -10
```
Expected: `Cannot find module '@/services/wettkampfApi.js'`

- [ ] **Create `src/services/wettkampfApi.js`:**
```javascript
// src/services/wettkampfApi.js
import { apiFetch } from './apiClient.js'

export const createSession = (name, passen, groups) =>
  apiFetch('/sessions', {
    method: 'POST',
    body: JSON.stringify({ type: 'competition', name, passen, groups }),
  })

export const listSessions = (type, status) => {
  const p = new URLSearchParams()
  if (type)   p.set('type', type)
  if (status) p.set('status', status)
  const q = p.toString() ? `?${p.toString()}` : ''
  return apiFetch(`/sessions${q}`)
}

export const getSession = (id) => apiFetch(`/sessions/${id}`)

export const patchStatus = (id, status) =>
  apiFetch(`/sessions/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })

export const deleteSession = (id) =>
  apiFetch(`/sessions/${id}`, { method: 'DELETE' })

export const createGroup = (sessionId, name, members = []) =>
  apiFetch(`/sessions/${sessionId}/groups`, {
    method: 'POST',
    body: JSON.stringify({ name, members }),
  })

export const updateGroup = (sessionId, groupId, name) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })

export const deleteGroup = (sessionId, groupId) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}`, { method: 'DELETE' })

export const addMember = (sessionId, groupId, member) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/members`, {
    method: 'POST',
    body: JSON.stringify(member),
  })

export const removeMember = (sessionId, groupId, memberId) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/members/${memberId}`, {
    method: 'DELETE',
  })

export const patchMember = (sessionId, groupId, memberId, paid) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/members/${memberId}`, {
    method: 'PATCH',
    body: JSON.stringify({ paid }),
  })

export const completeSerie = (sessionId, groupId, serieId, passeIndex, playInstanceId, results) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/serien/${serieId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ passeIndex, playInstanceId, results }),
  })

export const getProgress = (sessionId) =>
  apiFetch(`/sessions/${sessionId}/progress`)
```

- [ ] **Run tests — expect PASS**
```bash
cd smart-ground-ui && npm run test src/services/__tests__/wettkampfApi.test.js 2>&1 | tail -10
```
Expected: 8 tests pass.

- [ ] **Commit**
```bash
cd smart-ground-ui
git add src/services/wettkampfApi.js src/services/__tests__/wettkampfApi.test.js
git commit -m "[ui] add wettkampfApi service"
```

---

## Task 8: Rewrite `competitionEventStore.js` + tests

**Files:**
- `smart-ground-ui/src/stores/competitionEventStore.js` *(rewrite)*
- `smart-ground-ui/src/stores/__tests__/competitionEventStore.test.js` *(rewrite)*

- [ ] **Write new tests** — replace entire `src/stores/__tests__/competitionEventStore.test.js`:
```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCompetitionEventStore } from '../competitionEventStore.js'

vi.mock('@/services/wettkampfApi.js', () => ({
  createSession: vi.fn(),
  listSessions:  vi.fn(),
  patchStatus:   vi.fn(),
  deleteSession: vi.fn(),
  createGroup:   vi.fn(),
  updateGroup:   vi.fn(),
  deleteGroup:   vi.fn(),
  addMember:     vi.fn(),
  removeMember:  vi.fn(),
  patchMember:   vi.fn(),
}))

import * as api from '@/services/wettkampfApi.js'

const mkSession = (o = {}) => ({
  id: 's1', name: 'Frühjahrspokal', status: 'SETUP', groups: [], createdAt: '2026-06-05T00:00:00Z', ...o,
})

describe('useCompetitionEventStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadEvents populates events from API', async () => {
    api.listSessions.mockResolvedValue({ content: [mkSession()] })
    const store = useCompetitionEventStore()
    await store.loadEvents()
    expect(store.events).toHaveLength(1)
    expect(store.events[0].name).toBe('Frühjahrspokal')
  })

  it('createEvent calls API and returns new id', async () => {
    api.createSession.mockResolvedValue(mkSession({ id: 'new-1' }))
    const store = useCompetitionEventStore()
    const id = await store.createEvent('Frühjahrspokal', [{ id: 'p1', serieIds: [] }], [])
    expect(api.createSession).toHaveBeenCalledWith('Frühjahrspokal', [{ id: 'p1', serieIds: [] }], [])
    expect(id).toBe('new-1')
    expect(store.events).toHaveLength(1)
  })

  it('openEvent patches status to open and updates local event', async () => {
    api.patchStatus.mockResolvedValue(mkSession({ status: 'OPEN' }))
    const store = useCompetitionEventStore()
    store.events = [mkSession()]
    await store.openEvent('s1')
    expect(api.patchStatus).toHaveBeenCalledWith('s1', 'open')
    expect(store.events[0].status).toBe('OPEN')
  })

  it('startEvent patches status to active', async () => {
    api.patchStatus.mockResolvedValue(mkSession({ status: 'ACTIVE' }))
    const store = useCompetitionEventStore()
    store.events = [mkSession({ status: 'OPEN' })]
    await store.startEvent('s1')
    expect(api.patchStatus).toHaveBeenCalledWith('s1', 'active')
    expect(store.events[0].status).toBe('ACTIVE')
  })

  it('stopEvent patches status to abandoned', async () => {
    api.patchStatus.mockResolvedValue(mkSession({ status: 'ABANDONED' }))
    const store = useCompetitionEventStore()
    store.events = [mkSession({ status: 'ACTIVE' })]
    await store.stopEvent('s1')
    expect(api.patchStatus).toHaveBeenCalledWith('s1', 'abandoned')
  })

  it('deleteEvent calls API and removes from store', async () => {
    api.deleteSession.mockResolvedValue(undefined)
    const store = useCompetitionEventStore()
    store.events = [mkSession()]
    await store.deleteEvent('s1')
    expect(api.deleteSession).toHaveBeenCalledWith('s1')
    expect(store.events).toHaveLength(0)
  })

  it('addRotte calls createGroup and appends to event', async () => {
    api.createGroup.mockResolvedValue({ id: 'g1', name: 'Rotte A', members: [] })
    const store = useCompetitionEventStore()
    store.events = [mkSession()]
    await store.addRotte('s1')
    expect(api.createGroup).toHaveBeenCalledWith('s1', expect.stringContaining('Rotte'))
    expect(store.events[0].groups).toHaveLength(1)
  })

  it('addPlayer calls addMember API', async () => {
    api.addMember.mockResolvedValue({ id: 'm1', displayName: 'Max', paid: false })
    const store = useCompetitionEventStore()
    store.events = [mkSession({ groups: [{ id: 'g1', name: 'Rotte A', members: [] }] })]
    await store.addPlayer('s1', 'g1', { id: 'u1', displayName: 'Max' })
    expect(api.addMember).toHaveBeenCalledWith('s1', 'g1', expect.objectContaining({ displayName: 'Max' }))
    expect(store.events[0].groups[0].members).toHaveLength(1)
  })

  it('togglePlayerPaid calls patchMember with flipped paid value', async () => {
    api.patchMember.mockResolvedValue({ id: 'm1', paid: true })
    const store = useCompetitionEventStore()
    store.events = [mkSession({ groups: [{ id: 'g1', name: 'Rotte A', members: [{ id: 'm1', displayName: 'Max', paid: false }] }] })]
    await store.togglePlayerPaid('s1', 'g1', 'm1')
    expect(api.patchMember).toHaveBeenCalledWith('s1', 'g1', 'm1', true)
    expect(store.events[0].groups[0].members[0].paid).toBe(true)
  })

  it('planningEvents returns SETUP and OPEN', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'SETUP' }), mkSession({ id: 's2', status: 'OPEN' }), mkSession({ id: 's3', status: 'ACTIVE' })]
    expect(store.planningEvents).toHaveLength(2)
  })

  it('activeEvents returns ACTIVE and PRE_COMPLETE', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'ACTIVE' }), mkSession({ id: 's2', status: 'PRE_COMPLETE' }), mkSession({ id: 's3', status: 'COMPLETED' })]
    expect(store.activeEvents).toHaveLength(2)
  })

  it('completedEvents returns only COMPLETED', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'COMPLETED' }), mkSession({ id: 's2', status: 'ABANDONED' })]
    expect(store.completedEvents).toHaveLength(1)
  })
})
```

- [ ] **Run tests — expect FAIL** (old store uses localStorage)
```bash
cd smart-ground-ui && npm run test src/stores/__tests__/competitionEventStore.test.js 2>&1 | tail -10
```
Expected: multiple failures.

- [ ] **Rewrite `src/stores/competitionEventStore.js`:**
```javascript
// src/stores/competitionEventStore.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as wettkampfApi from '@/services/wettkampfApi.js'

export const useCompetitionEventStore = defineStore('competitionEvent', () => {
  const events  = ref([])
  const loading = ref(false)
  const error   = ref(null)

  // ── Computed ──────────────────────────────────────────────────────────────

  const planningEvents  = computed(() => events.value.filter(e => ['SETUP', 'OPEN'].includes(e.status?.toUpperCase())))
  const activeEvents    = computed(() => events.value.filter(e => ['ACTIVE', 'PRE_COMPLETE'].includes(e.status?.toUpperCase())))
  const completedEvents = computed(() => events.value.filter(e => e.status?.toUpperCase() === 'COMPLETED'))
  const getEvent        = (id) => events.value.find(e => e.id === id) ?? null

  // ── Load ──────────────────────────────────────────────────────────────────

  const loadEvents = async () => {
    loading.value = true
    error.value = null
    try {
      const res = await wettkampfApi.listSessions('competition')
      events.value = res.content ?? res ?? []
    } catch (e) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  const createEvent = async (name, passen, groups = []) => {
    const created = await wettkampfApi.createSession(name, passen, groups)
    events.value = [...events.value, created]
    return created.id
  }

  const openEvent  = async (id) => _replaceEvent(await wettkampfApi.patchStatus(id, 'open'))
  const startEvent = async (id) => _replaceEvent(await wettkampfApi.patchStatus(id, 'active'))
  const stopEvent  = async (id) => _replaceEvent(await wettkampfApi.patchStatus(id, 'abandoned'))

  const deleteEvent = async (id) => {
    await wettkampfApi.deleteSession(id)
    events.value = events.value.filter(e => e.id !== id)
  }

  // ── Rotte management ──────────────────────────────────────────────────────

  const addRotte = async (eventId) => {
    const ev = getEvent(eventId)
    if (!ev) return
    const letters = 'ABCDEFGH'
    const name = `Rotte ${letters[(ev.groups ?? []).length] ?? (ev.groups ?? []).length + 1}`
    const group = await wettkampfApi.createGroup(eventId, name)
    ev.groups = [...(ev.groups ?? []), group]
  }

  const removeRotte = async (eventId, groupId) => {
    await wettkampfApi.deleteGroup(eventId, groupId)
    const ev = getEvent(eventId)
    if (ev) ev.groups = (ev.groups ?? []).filter(g => g.id !== groupId)
  }

  const renameRotte = async (eventId, groupId, name) => {
    const updated = await wettkampfApi.updateGroup(eventId, groupId, name)
    const ev = getEvent(eventId)
    if (ev) ev.groups = (ev.groups ?? []).map(g => g.id === groupId ? { ...g, ...updated } : g)
  }

  // ── Player management ─────────────────────────────────────────────────────

  const addPlayer = async (eventId, groupId, user) => {
    if (!user?.displayName) return
    const member = await wettkampfApi.addMember(eventId, groupId, {
      displayName: user.displayName,
      userId: user.id ?? null,
      type: user.id ? 'USER' : 'GUEST',
      paid: false,
    })
    const ev = getEvent(eventId)
    const group = ev?.groups?.find(g => g.id === groupId)
    if (group) group.members = [...(group.members ?? []), member]
  }

  const removePlayer = async (eventId, groupId, memberId) => {
    await wettkampfApi.removeMember(eventId, groupId, memberId)
    const ev = getEvent(eventId)
    const group = ev?.groups?.find(g => g.id === groupId)
    if (group) group.members = (group.members ?? []).filter(m => m.id !== memberId)
  }

  const togglePlayerPaid = async (eventId, groupId, memberId) => {
    const ev = getEvent(eventId)
    const member = ev?.groups?.find(g => g.id === groupId)?.members?.find(m => m.id === memberId)
    if (!member) return
    const updated = await wettkampfApi.patchMember(eventId, groupId, memberId, !member.paid)
    member.paid = updated.paid
  }

  // ── Private ───────────────────────────────────────────────────────────────

  const _replaceEvent = (updated) => {
    events.value = events.value.map(e => e.id === updated.id ? updated : e)
  }

  return {
    events, loading, error,
    planningEvents, activeEvents, completedEvents, getEvent,
    loadEvents,
    createEvent, openEvent, startEvent, stopEvent, deleteEvent,
    addRotte, removeRotte, renameRotte,
    addPlayer, removePlayer, togglePlayerPaid,
  }
})
```

- [ ] **Run tests — expect PASS**
```bash
cd smart-ground-ui && npm run test src/stores/__tests__/competitionEventStore.test.js 2>&1 | tail -10
```
Expected: 12 tests pass.

- [ ] **Commit**
```bash
cd smart-ground-ui
git add src/stores/competitionEventStore.js src/stores/__tests__/competitionEventStore.test.js
git commit -m "[ui] rewrite competitionEventStore: replace localStorage with API calls"
```

---

## Task 9: Update `activePasseStore.js` — remove in-memory competition branch

**Files:**
- `smart-ground-ui/src/stores/activePasseStore.js`

- [ ] **Run existing tests before touching anything**
```bash
cd smart-ground-ui && npm run test src/stores/__tests__/activePasseStore.test.js 2>&1 | tail -10
```
Note the pass count — all must still pass after this task.

- [ ] **Remove `startCompetition` method entirely** from `activePasseStore.js` (the block from `// Competition instances remain in-memory only` through its closing `}`).

- [ ] **Replace the competition branch in `markBlockDone`** (lines `if (inst.type === 'competition') { ... }` that call `_completeCompetitionBlock`):
```javascript
if (inst.type === 'competition') {
  const { completeSerie } = await import('@/services/wettkampfApi.js')
  const passeIndex = inst.rotten?.find(r => r.rotteId === rotteId)?.currentPhaseIndex ?? 0
  await completeSerie(inst.sessionId, rotteId, blockId, passeIndex, null, playerResults).catch(console.error)
  _completeCompetitionBlock(inst, blockId, playerResults, rotteId)
  return
}
```

- [ ] **Update `stopCompetition`** to also call the API:
```javascript
const stopCompetition = async (instanceId) => {
  const inst = activeInstances.value.find(i => i.instanceId === instanceId)
  if (inst?.type === 'competition' && inst.sessionId) {
    const { patchStatus } = await import('@/services/wettkampfApi.js')
    await patchStatus(inst.sessionId, 'abandoned').catch(console.error)
  }
  activeInstances.value = activeInstances.value.filter(i => i.instanceId !== instanceId)
}
```

- [ ] **Run tests again**
```bash
cd smart-ground-ui && npm run test src/stores/__tests__/activePasseStore.test.js 2>&1 | tail -10
```
Expected: same pass count. If any competition-related test fails because it relied on `startCompetition`, update that test to mock `wettkampfApi` instead of `activePasseStore.startCompetition`.

- [ ] **Commit**
```bash
cd smart-ground-ui && git add src/stores/activePasseStore.js
git commit -m "[ui] remove in-memory competition branch from activePasseStore; delegate to API"
```

---

## Task 10: Update `WettkampfDetailView.vue` — OPEN state + Veröffentlichen button

**Files:**
- `smart-ground-ui/src/views/admin/WettkampfDetailView.vue`

- [ ] **Update `statusLabel` computed** in `<script setup>`:
```javascript
const statusLabel = computed(() => {
  const map = {
    SETUP: 'Planung', OPEN: 'Offen', ACTIVE: 'Aktiv',
    PRE_COMPLETE: 'Auswertung', COMPLETED: 'Abgeschlossen', ABANDONED: 'Abgebrochen',
  }
  return map[event.value?.status?.toUpperCase()] ?? '–'
})
```

- [ ] **Add `handleOpen` action** after `handleStart`:
```javascript
const handleOpen = () => store.openEvent(eventId.value)
```

- [ ] **Add `toRotteShape` helper** — converts API group shape to the shape `RotteEditorCard` expects:
```javascript
const toRotteShape = (group) => ({
  rotteId: group.id,
  name: group.name,
  players: (group.members ?? []).map(m => ({
    id: m.id,
    userId: m.userId ?? null,
    displayName: m.displayName,
    paid: m.paid ?? false,
  })),
})
```

- [ ] **Update `totalPlayers`, `unpaidPlayers`, `paidPlayers`, `assignedUserIds`** to read `event.value?.groups` instead of `event.value?.rotten`:
```javascript
const totalPlayers = computed(() => (event.value?.groups ?? []).reduce((s, g) => s + (g.members?.length ?? 0), 0))
const unpaidPlayers = computed(() => (event.value?.groups ?? []).flatMap(g => (g.members ?? []).filter(m => !m.paid)))
const paidPlayers = computed(() => (event.value?.groups ?? []).flatMap(g => (g.members ?? []).filter(m => m.paid)))
const assignedUserIds = computed(() => new Set((event.value?.groups ?? []).flatMap(g => (g.members ?? []).map(m => m.userId).filter(Boolean))))
```

- [ ] **Replace PLANNING template's start button with Veröffentlichen:**
```html
<button class="start-btn" :disabled="(event.groups ?? []).length === 0" @click="handleOpen">
  <Icons icon="play" :size="15" color="#fff" />
  Veröffentlichen
</button>
```

Also update `RotteEditorCard` bindings in PLANNING template to use group IDs and `toRotteShape`:
```html
<RotteEditorCard
  v-for="rotte in event.groups"
  :key="rotte.id"
  :rotte="toRotteShape(rotte)"
  :available-users="availableUsers"
  @rename="(name) => store.renameRotte(eventId, rotte.id, name)"
  @remove="confirmRemoveRotte(rotte)"
  @add-player="(user) => store.addPlayer(eventId, rotte.id, user)"
  @remove-player="(pid) => store.removePlayer(eventId, rotte.id, pid)"
  @toggle-paid="(pid) => store.togglePlayerPaid(eventId, rotte.id, pid)"
/>
```

Update `addRotte`, `removeRotte` calls to use `rotte.id` instead of `rotte.rotteId`.

- [ ] **Add OPEN state template** — after the `<!-- ══ PLANNING ══ -->` block and before `<!-- ══ ACTIVE ══ -->`:
```html
<!-- ══ OPEN ══ -->
<template v-else-if="event.status?.toUpperCase() === 'OPEN'">
  <div class="content">
    <div class="info-bar">
      <span class="info-chip">{{ totalThrows }} Würfe</span>
      <span class="info-chip">{{ totalPlayers }} Schützen</span>
    </div>
    <div v-if="unpaidPlayers.length > 0" class="payment-warning">
      <Icons icon="alert" :size="14" color="rgba(246,173,85,0.9)" />
      {{ unpaidPlayers.length }} Schützen haben noch nicht bezahlt
    </div>
    <section class="section">
      <div class="section-header">
        <h2 class="section-title">Rotten</h2>
        <button class="add-rotte-btn" :disabled="(event.groups ?? []).length >= 8"
                @click="store.addRotte(eventId)">
          <Icons icon="plus" :size="13" /> Rotte hinzufügen
        </button>
      </div>
      <div class="rotten-grid">
        <RotteEditorCard
          v-for="rotte in event.groups"
          :key="rotte.id"
          :rotte="toRotteShape(rotte)"
          :available-users="availableUsers"
          @rename="(name) => store.renameRotte(eventId, rotte.id, name)"
          @remove="confirmRemoveRotte(rotte)"
          @add-player="(user) => store.addPlayer(eventId, rotte.id, user)"
          @remove-player="(pid) => store.removePlayer(eventId, rotte.id, pid)"
          @toggle-paid="(pid) => store.togglePlayerPaid(eventId, rotte.id, pid)"
        />
      </div>
    </section>
    <div v-if="showPaymentWarning" class="modal-overlay" @click.self="showPaymentWarning = false">
      <div class="warning-modal">
        <h3 class="modal-title">Nicht alle haben bezahlt</h3>
        <p class="modal-desc">Folgende Schützen haben noch nicht bezahlt:</p>
        <ul class="unpaid-list">
          <li v-for="p in unpaidPlayers" :key="p.id">{{ p.displayName }}</li>
        </ul>
        <div class="modal-actions">
          <button class="action-btn action-btn--cancel" @click="showPaymentWarning = false">Zurück</button>
          <button class="action-btn action-btn--start" @click="confirmStart">Trotzdem starten</button>
        </div>
      </div>
    </div>
    <div class="start-section">
      <div class="payment-total">{{ paidPlayers.length }}/{{ totalPlayers }} bezahlt</div>
      <button class="start-btn" @click="handleStart">
        <Icons icon="play" :size="15" color="#fff" />
        Wettkampf starten
      </button>
    </div>
  </div>
</template>
```

- [ ] **Add CSS badge classes** in `<style scoped>`:
```css
.badge-open { background: var(--sg-color-info-bg); color: var(--sg-color-info-text); border: 1px solid var(--sg-accent); }
.badge-pre_complete { background: var(--sg-color-warning-bg); color: var(--sg-color-warning-text); border: 1px solid color-mix(in srgb, var(--sg-color-warning) 40%, transparent); }
.badge-abandoned { background: var(--sg-bg-panel); color: var(--sg-text-faint); border: 1px solid var(--sg-border); }
```

- [ ] **Update `onMounted`** — remove the `completionInterval` setInterval, add `loadEvents`:
```javascript
onMounted(async () => {
  try { await userStore.loadUsers() } catch { /* error handled by userStore */ }
  if (store.events.length === 0) await store.loadEvents()
})
// Remove: onUnmounted(() => clearInterval(completionInterval))
```

- [ ] **Commit**
```bash
cd smart-ground-ui && git add src/views/admin/WettkampfDetailView.vue
git commit -m "[ui] WettkampfDetailView: OPEN state, Veröffentlichen button, API-backed groups"
```

---

## Task 11: Update `WettkampfListView.vue` + `ActiveCompetitionPanel.vue`

**Files:**
- `smart-ground-ui/src/views/admin/WettkampfListView.vue`
- `smart-ground-ui/src/components/competition/ActiveCompetitionPanel.vue`

- [ ] **Update `WettkampfListView.vue`**

Add `onMounted` in `<script setup>`:
```javascript
import { ref, computed, onMounted } from 'vue'
// ...
onMounted(() => store.loadEvents())
```

Update `confirmCreate` to be `async` and pass Passe snapshots:
```javascript
const confirmCreate = async () => {
  if (!newName.value.trim() || selectedPassen.value.length === 0) return
  const passenSnapshots = selectedPassen.value.map(p => ({
    id: p.id,
    name: p.name,
    serieIds: (p.serien ?? []).map(s => s.id),
  }))
  const id = await store.createEvent(newName.value.trim(), passenSnapshots, [])
  cancelCreate()
  router.push('/admin/wettkampf/' + id)
}
```

Update `handleDelete` to be `async`:
```javascript
const handleDelete = async (ev) => {
  if (confirm(`"${ev.name}" löschen?`)) await store.deleteEvent(ev.id)
}
```

Update helper functions to use `groups`/`members` instead of `rotten`/`players`:
```javascript
const totalPlayers = (ev) => (ev.groups ?? []).reduce((s, g) => s + (g.members?.length ?? 0), 0)
const unpaidCount  = (ev) => (ev.groups ?? []).reduce((s, g) => s + (g.members ?? []).filter(m => !m.paid).length, 0)
```

Update event card template: replace `ev.rotten.length` → `(ev.groups ?? []).length`, `ev.passen.length` → `(ev.passen?.length ?? 0)`.

- [ ] **Add progress polling to `ActiveCompetitionPanel.vue`**

In `<script setup>`, add after existing imports:
```javascript
import { onMounted, onUnmounted } from 'vue'
import { getProgress } from '@/services/wettkampfApi.js'

const progressData = ref(null)
let pollInterval = null

const fetchProgress = async () => {
  if (!props.event?.id) return
  try {
    progressData.value = await getProgress(props.event.id)
  } catch (e) {
    console.error('[ActiveCompetitionPanel] progress poll failed:', e)
  }
}

onMounted(() => {
  fetchProgress()
  pollInterval = setInterval(fetchProgress, 4000)
})

onUnmounted(() => clearInterval(pollInterval))
```

The `progressData` ref is available for template use to show backend-derived group progress alongside the existing `useCompetitionProgress` composable output.

- [ ] **Run all frontend tests**
```bash
cd smart-ground-ui && npm run test 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, no failing tests.

- [ ] **Commit**
```bash
cd smart-ground-ui
git add src/views/admin/WettkampfListView.vue src/components/competition/ActiveCompetitionPanel.vue
git commit -m "[ui] update WettkampfListView and ActiveCompetitionPanel for API-backed competition"
```

---

## Self-Review Checklist

| Spec requirement | Task |
|---|---|
| `OPEN`, `PRE_COMPLETE` in `SessionStatus` | Task 1 |
| Valid status transition chain | Task 1 |
| `paid` on `SessionPlayer` | Task 2 |
| `name` on `SessionResponse` | Task 2 |
| `CompetitionSerieResult` entity + unique constraint | Task 3 |
| `PasseSnapshot` DTO + `CreateSessionRequest` | Task 4 |
| `completeSerie` inserts row + checks duplicate | Task 5 |
| `upsertPlayerResults` per member | Task 5 |
| Auto-transition to `PRE_COMPLETE` when all done | Task 5 |
| `getProgress` derives current Passe index | Task 5 |
| `PATCH /sessions/{id}/status` endpoint | Task 6 |
| `DELETE /sessions/{id}` (SETUP only) | Task 6 |
| Group CRUD endpoints | Task 6 |
| Member CRUD endpoints | Task 6 |
| CareerStats update on `COMPLETED` | Task 6 |
| Serie completion endpoint | Task 6 |
| Progress endpoint | Task 6 |
| `wettkampfApi.js` | Task 7 |
| `competitionEventStore` rewrite | Task 8 |
| `activePasseStore` competition branch | Task 9 |
| `WettkampfDetailView` OPEN state | Task 10 |
| `WettkampfListView` API-backed | Task 11 |
| `ActiveCompetitionPanel` polling | Task 11 |

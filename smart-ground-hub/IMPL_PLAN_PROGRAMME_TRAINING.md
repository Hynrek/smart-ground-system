# Implementation Plan — Programme, Training & Play Instances

> Covers all new endpoints added to `openapi.yaml`:
> `/api/guests`, `/api/ablaeufe`, `/api/programmes`, `/api/trainings`,
> `/api/play-instances`, `/api/play-results`

---

## Conventions (recap from CLAUDE.md)

- All new classes: `@NullMarked` + jspecify `@Nullable` where nullable
- UUID PKs: `@GeneratedValue(strategy = GenerationType.UUID)`
- Schema changes: edit JPA entities directly (Liquibase disabled pre-v1.0, `ddl-auto=update`)
- Comments in **German**
- Controllers implement the **generated OpenAPI interface** — never add `@RequestMapping` to the class
- Exceptions go in `ch.jp.shooting.exception`, mapped in `GlobalExceptionHandler`
- Complex nested structures → stored as `TEXT` JSON columns (established pattern: `LiveSession.programSnapshots`)

The JWT subject (`sub`) is the user's **email address**. Resolve the current user via `SecurityContextHolder` → email → `UserRepository.findByEmail()`.

---

## Overview of layers to create

```
Task 1  JPA Entities          Guest, Ablauf, Programm, Training, PlayInstance
Task 2  Repositories          One Spring Data interface per entity
Task 3  Exception classes      6 new domain exceptions
Task 4  SecurityHelper         Utility to resolve the current User from the JWT
Task 5  Mapper additions       toXxx() methods in EntityMappers (or AblaufMapper etc.)
Task 6  Services               GuestService, AblaufService, ProgrammService,
                               TrainingService, PlayInstanceService
Task 7  Controllers            One controller per API tag, implementing generated interface
Task 8  SecurityConfig patch   Permit the new routes
Task 9  GlobalExceptionHandler Add handlers for new exceptions
```

---

## Task 1 — JPA Entities

### 1a. `Guest`

**File:** `model/Guest.java`

```java
@Entity
@Table(name = "guests")
@NullMarked
public class Guest {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // Kein Owner — Gäste sind systemweit sichtbar
}
```

---

### 1b. `Ablauf`

**File:** `model/Ablauf.java`

Steps are serialised as JSON text — same pattern as `LiveSession.programSnapshots`.

```java
@Entity
@Table(name = "ablaeufe")
@NullMarked
public class Ablauf {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** 'user' = privat, 'range' = platzweit sichtbar */
    @Column(nullable = false, length = 10)
    private String ownership = "user";

    /** Optionale Platz-Zuordnung (zur Weiterleitung an Ranges) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "range_id")
    @Nullable
    private Range range;

    /** Besitzer (JWT-Subject = E-Mail) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Schritte als JSON-Array (Step[]).
     * Format: [{id, type, deviceId?, alias?, letter?,
     *           deviceId1?, deviceId2?, alias1?, alias2?, letter1?, letter2?}]
     */
    @Column(name = "steps_json", columnDefinition = "TEXT", nullable = false)
    private String stepsJson = "[]";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
```

---

### 1c. `Programm`

**File:** `model/Programm.java`

Abläufe are embedded as a JSON snapshot at creation time (no FK — immutable copy).

```java
@Entity
@Table(name = "programme")
@NullMarked
public class Programm {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Snapshot der Abläufe zum Erstellungszeitpunkt (EmbeddedAblauf[]).
     * Format: [{id, alias, rangeId, rangeName, steps:[...]}]
     * Nachträgliche Änderungen an Quell-Abläufen berühren diesen Snapshot nicht.
     */
    @Column(name = "ablaeufe_json", columnDefinition = "TEXT", nullable = false)
    private String ablaufeJson = "[]";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
```

---

### 1d. `Training`

**File:** `model/Training.java`

Same snapshot strategy, one level deeper.

```java
@Entity
@Table(name = "trainings")
@NullMarked
public class Training {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Snapshot der Programme zum Erstellungszeitpunkt (TrainingProgramme[]).
     * Format: [{id, name, ablaeufe:[{id, alias, rangeId, rangeName, steps:[...]}]}]
     */
    @Column(name = "programmes_json", columnDefinition = "TEXT", nullable = false)
    private String programmesJson = "[]";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
```

---

### 1e. `PlayInstance`

**File:** `model/PlayInstance.java`

Handles both `programm` and `training` instance types. Blocks/phases are stored as a
single JSON column — the service layer deserialises, mutates, and re-serialises on
every state transition (same pattern as `LiveSession.bracketStateJson`).

```java
@Entity
@Table(name = "play_instances")
@NullMarked
public class PlayInstance {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID instanceId;

    /** 'programm' | 'training' */
    @Column(nullable = false, length = 10)
    private String type;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    /** 'active' | 'completed' | 'cancelled' */
    @Column(nullable = false, length = 12)
    private String status = "active";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Spieler als JSON (PlayerRef[]).
     * Format: [{id, type:'user'|'guest', displayName}]
     */
    @Column(name = "players_json", columnDefinition = "TEXT", nullable = false)
    private String playersJson = "[]";

    /**
     * Für type='programm': Blöcke als JSON (PlayBlock[]).
     * Für type='training': Phasen als JSON (PlayPhase[]).
     * Dieses Feld enthält den vollständigen, veränderbaren Zustand.
     */
    @Column(name = "state_json", columnDefinition = "TEXT", nullable = false)
    private String stateJson = "[]";

    /** Nur für Training: Index der aktuell aktiven Phase */
    @Column(name = "current_phase_index")
    @Nullable
    private Integer currentPhaseIndex;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    @Nullable
    private Instant completedAt;
}
```

> **Why one `state_json` column for both types?**
> The service knows `type` and deserialises accordingly — either `List<PlayBlock>` or
> `List<PlayPhase>`. This keeps the schema simple and avoids nullable sibling columns.

---

## Task 2 — Repositories

Create one interface per entity in `ch.jp.shooting.repository`. All extend
`JpaRepository<Entity, UUID>`.

### `GuestRepository`
```java
public interface GuestRepository extends JpaRepository<Guest, UUID> {}
```

### `AblaufRepository`
```java
public interface AblaufRepository extends JpaRepository<Ablauf, UUID> {

    /** Eigene Abläufe ODER platzweit veröffentlichte */
    List<Ablauf> findByOwnerOrOwnership(User owner, String ownership);

    /** Eigene Abläufe eines Besitzers */
    List<Ablauf> findByOwner(User owner);

    /** Nur Abläufe mit ownership='range' */
    List<Ablauf> findByOwnership(String ownership);

    /** Abläufe nach Platz-Zuordnung */
    List<Ablauf> findByRange_Id(UUID rangeId);

    /** Kombiniert: Besitzer-Filter + Platz-Filter */
    List<Ablauf> findByOwnerAndRange_Id(User owner, UUID rangeId);
}
```

### `ProgrammRepository`
```java
public interface ProgrammRepository extends JpaRepository<Programm, UUID> {
    List<Programm> findByOwner(User owner);
}
```

### `TrainingRepository`
```java
public interface TrainingRepository extends JpaRepository<Training, UUID> {
    List<Training> findByOwner(User owner);
}
```

### `PlayInstanceRepository`
```java
public interface PlayInstanceRepository extends JpaRepository<PlayInstance, UUID> {

    List<PlayInstance> findByOwnerAndStatus(User owner, String status);

    /** Für Range-Live-View: alle aktiven Instanzen eines Owners */
    List<PlayInstance> findByOwnerAndStatusIn(User owner, List<String> statuses);

    /** Ergebnisliste: abgeschlossene Instanzen, neueste zuerst */
    Page<PlayInstance> findByOwnerAndStatusOrderByCompletedAtDesc(
        User owner, String status, Pageable pageable);
}
```

---

## Task 3 — Exception Classes

Create in `ch.jp.shooting.exception`. Extend `NotFoundException` (for 404) or
`RuntimeException` (for 409 — mapped in handler).

```java
// AblaufNotFoundException.java
public class AblaufNotFoundException extends NotFoundException {
    public AblaufNotFoundException(UUID id) {
        super("Ablauf nicht gefunden: " + id);
    }
}

// ProgrammNotFoundException.java
public class ProgrammNotFoundException extends NotFoundException {
    public ProgrammNotFoundException(UUID id) {
        super("Programm nicht gefunden: " + id);
    }
}

// TrainingNotFoundException.java
public class TrainingNotFoundException extends NotFoundException {
    public TrainingNotFoundException(UUID id) {
        super("Training nicht gefunden: " + id);
    }
}

// GuestNotFoundException.java
public class GuestNotFoundException extends NotFoundException {
    public GuestNotFoundException(UUID id) {
        super("Gast nicht gefunden: " + id);
    }
}

// PlayInstanceNotFoundException.java
public class PlayInstanceNotFoundException extends NotFoundException {
    public PlayInstanceNotFoundException(UUID id) {
        super("Play-Instanz nicht gefunden: " + id);
    }
}

// BlockStateException.java  — maps to 409
public class BlockStateException extends RuntimeException {
    public BlockStateException(String message) {
        super(message);
    }
}
```

Add to `GlobalExceptionHandler`:
```java
@ExceptionHandler(BlockStateException.class)
ProblemDetail handleBlockState(BlockStateException ex) {
    var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    detail.setType(URI.create("/errors/block-state-conflict"));
    return detail;
}
```

---

## Task 4 — SecurityHelper

**File:** `config/SecurityHelper.java`

Centralises "who is calling this endpoint?" so no controller duplicates this logic.

```java
@Component
@NullMarked
public class SecurityHelper {

    private final UserRepository userRepository;

    public SecurityHelper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Gibt den aktuell authentifizierten User zurück (wirft 401 wenn nicht angemeldet). */
    public User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String email = auth.getName(); // JWT sub = E-Mail
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    /** Prüft ob der aktuelle Nutzer ADMIN oder GROUND_OWNER ist. */
    public boolean isAdminOrOwner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_GROUND_OWNER"));
    }
}
```

> `UserRepository` needs `Optional<User> findByEmail(String email)` — check if it
> already exists (it is used in `CustomUserDetailsService`), if not add it.

---

## Task 5 — Mapper Additions

Add static methods to `EntityMappers` (or create a dedicated `PlayMapper` class if
the file gets too large).

The key challenge is serialising/deserialising the JSON columns. Inject or use a
shared `ObjectMapper` instance (already present as a field in `EntityMappers`).

### JSON record types (internal, package-private DTOs)

Create Java records in `dto/play/` to model the JSON structures stored in TEXT columns.
These are **not** the generated OpenAPI types — they are internal serialisation helpers.

```java
// dto/play/StepRecord.java
public record StepRecord(
    String id, String type,
    @Nullable UUID deviceId, @Nullable String alias, @Nullable String letter,
    @Nullable UUID deviceId1, @Nullable UUID deviceId2,
    @Nullable String alias1, @Nullable String alias2,
    @Nullable String letter1, @Nullable String letter2
) {}

// dto/play/EmbeddedAblaufRecord.java
public record EmbeddedAblaufRecord(
    UUID id, String alias,
    @Nullable UUID rangeId, @Nullable String rangeName,
    List<StepRecord> steps
) {}

// dto/play/TrainingProgrammeRecord.java
public record TrainingProgrammeRecord(
    UUID id, String name,
    List<EmbeddedAblaufRecord> ablaeufe
) {}

// dto/play/PlayerRefRecord.java
public record PlayerRefRecord(String id, String type, String displayName) {}

// dto/play/StepStateRecord.java
public record StepStateRecord(
    String playerId, int ablaufIndex, int stepIndex,
    String state, int pointValue, int noBirds, int pointsEarned
) {}

// dto/play/PlayerResultRecord.java
public record PlayerResultRecord(
    String playerId, String displayName,
    int totalPoints, int maxPoints,
    List<StepStateRecord> stepStates
) {}

// dto/play/BlockResultRecord.java
public record BlockResultRecord(List<PlayerResultRecord> playerResults) {}

// dto/play/PlayBlockRecord.java
public record PlayBlockRecord(
    UUID blockId, UUID ablaufId, String ablaufAlias,
    @Nullable UUID rangeId, @Nullable String rangeName,
    List<StepRecord> steps,
    String status,              // pending | in_progress | done
    @Nullable Instant completedAt,
    @Nullable BlockResultRecord result
) {}

// dto/play/PlayPhaseRecord.java
public record PlayPhaseRecord(
    int phaseIndex, UUID programmeId, String programmeName,
    String status,              // pending | active | done
    List<PlayBlockRecord> blocks
) {}
```

### Mapper methods to add

```java
// In EntityMappers or PlayMapper:

public static AblaufResponse toAblaufResponse(Ablauf ablauf) { ... }
public static ProgrammeResponse toProgrammeResponse(Programm programm) { ... }
public static TrainingResponse toTrainingResponse(Training training) { ... }
public static PlayInstanceResponse toPlayInstanceResponse(PlayInstance instance) { ... }
public static GuestResponse toGuestResponse(Guest guest) { ... }

// Helpers for JSON ↔ record conversion (use static ObjectMapper):
static List<StepRecord> parseSteps(String json) { ... }
static String writeSteps(List<StepRecord> steps) { ... }
static List<EmbeddedAblaufRecord> parseAblaeufe(String json) { ... }
static List<PlayBlockRecord> parseBlocks(String json) { ... }
static List<PlayPhaseRecord> parsePhases(String json) { ... }
```

---

## Task 6 — Services

### 6a. `GuestService`

**File:** `service/GuestService.java`

Simple CRUD, no owner scoping — guests are system-wide.

```
listGuests()              → guestRepository.findAll()
createGuest(displayName)  → new Guest, save, return
updateGuest(id, name)     → find or throw, set name, save
deleteGuest(id)           → find or throw, delete
```

---

### 6b. `AblaufService`

**File:** `service/AblaufService.java`

```
listAblaeufe(ownership?, rangeId?)
  owner = securityHelper.currentUser()
  if ownership == 'user'  → findByOwner(owner) filtered optionally by rangeId
  if ownership == 'range' → findByOwnership('range') filtered optionally by rangeId
  else                    → findByOwnerOrOwnership(owner, 'range') filtered by rangeId

createAblauf(request)
  owner = securityHelper.currentUser()
  ablauf = new Ablauf(name, ownership, owner)
  ablauf.stepsJson = writeSteps(request.steps)
  if request.rangeId != null → ablauf.range = rangeRepository.findById() or throw
  save, return toAblaufResponse

getAblauf(id)
  ablauf = findById or throw AblaufNotFoundException
  check visibility: ablauf.owner == currentUser OR ablauf.ownership == 'range' OR isAdmin
  else throw 403

updateAblauf(id, request)
  ablauf = findById or throw; check owner == currentUser or throw 403
  ablauf.name = request.name
  if request.rangeId present → resolve range or clear
  save, return

updateAblaufOwnership(id, request)
  ablauf = findById or throw; check owner == currentUser or throw 403
  if request.ownership == 'range' && !isAdminOrOwner() → throw 403
  ablauf.ownership = request.ownership; save, return

deleteAblauf(id)
  ablauf = findById or throw; check owner or throw 403
  ablaufRepository.delete(ablauf)
```

---

### 6c. `ProgrammService`

**File:** `service/ProgrammService.java`

```
listProgramme()
  owner = currentUser(); return findByOwner(owner).map(toProgrammeResponse)

createProgramm(name, ablaufIds)
  owner = currentUser()
  // Snapshot: resolve each Ablauf, build EmbeddedAblaufRecord list
  ablaeufe = ablaufIds.stream()
    .map(id -> ablaufRepository.findById(id).orElseThrow(...))
    .map(a -> new EmbeddedAblaufRecord(a.id, a.name, rangeId, rangeName, parseSteps(a.stepsJson)))
  programm = new Programm(name, owner, writeAblaeufe(ablaeufe))
  save, return toProgrammeResponse

getProgramm(id)
  find or throw; check owner == currentUser or throw 403; return

updateProgramm(id, name)
  find or throw; check owner; programm.name = name; save; return

deleteProgramm(id)
  find or throw; check owner; delete
```

---

### 6d. `TrainingService`

**File:** `service/TrainingService.java`

```
listTrainings()
  owner = currentUser(); return findByOwner(owner)

createTraining(name, programmeIds)
  owner = currentUser()
  // Snapshot: resolve each Programm, then snapshot its ablaeufe
  programmes = programmeIds.stream()
    .map(id -> programmRepository.findById(id).orElseThrow(...))
    .map(p -> new TrainingProgrammeRecord(
          p.id, p.name,
          parseAblaeufe(p.ablaufeJson)   // carry over the existing snapshot
        ))
  training = new Training(name, owner, writeProgrammes(programmes))
  save, return

getTraining / updateTraining / deleteTraining  → same pattern as Programm
```

---

### 6e. `PlayInstanceService`

**File:** `service/PlayInstanceService.java`

This is the most complex service. It owns the block/phase state machine.

#### Starting a Programme instance

```
startProgrammeInstance(programmeId, players)
  owner = currentUser()
  programm = programmRepository.findById(programmeId).orElseThrow(...)
  ablaeufe = parseAblaeufe(programm.ablaufeJson)

  blocks = ablaeufe.stream().map(abl ->
    new PlayBlockRecord(
      blockId = UUID.randomUUID(),
      ablaufId = abl.id, ablaufAlias = abl.alias,
      rangeId = abl.rangeId, rangeName = abl.rangeName,
      steps = abl.steps,
      status = "pending", completedAt = null, result = null
    )).toList()

  instance = new PlayInstance()
  instance.type = "programm"
  instance.templateId = programm.id
  instance.templateName = programm.name
  instance.owner = owner
  instance.playersJson = writePlayers(players)
  instance.stateJson = writeBlocks(blocks)
  instance.status = "active"
  save, return toPlayInstanceResponse(instance)
```

#### Starting a Training instance

```
startTrainingInstance(trainingId, players)
  owner = currentUser()
  training = trainingRepository.findById(trainingId).orElseThrow(...)
  programmes = parseProgrammes(training.programmesJson)

  phases = IntStream.range(0, programmes.size()).mapToObj(i -> {
    prog = programmes[i]
    blocks = prog.ablaeufe.stream().map(abl ->
      new PlayBlockRecord(UUID.randomUUID(), abl.id, abl.alias, abl.rangeId,
          abl.rangeName, abl.steps, "pending", null, null)).toList()
    return new PlayPhaseRecord(i, prog.id, prog.name,
        i == 0 ? "active" : "pending", blocks)
  }).toList()

  instance = new PlayInstance()
  instance.type = "training"
  instance.templateId = training.id
  instance.templateName = training.name
  instance.owner = owner
  instance.playersJson = writePlayers(players)
  instance.stateJson = writePhases(phases)
  instance.currentPhaseIndex = 0
  instance.status = "active"
  save, return
```

#### `listPlayInstances(status?, rangeId?)`

```
owner = currentUser()
instances = findByOwnerAndStatus(owner, status ?? "active")
if rangeId != null:
  // Filter: keep only instances that have at least one non-done block on this range
  instances = instances.filter(inst -> hasBlockOnRange(inst, rangeId))
return instances.map(toPlayInstanceResponse)
```

`hasBlockOnRange(instance, rangeId)`:
- For `programm`: check `blocks.any { it.rangeId == rangeId && it.status != "done" }`
- For `training`: check only `phases[currentPhaseIndex].blocks`

#### `startBlock(instanceId, blockId)`

```
instance = findById or throw
block = findBlock(instance, blockId)   // see helper below
if block.status == "done" → throw BlockStateException("Block bereits abgeschlossen")
block.status = "in_progress"
writeBack(instance, block)   // update stateJson
save, return
```

`findBlock(instance, blockId)`:
- If `type == "programm"`: deserialise `stateJson` as `List<PlayBlockRecord>`, find by blockId
- If `type == "training"`: deserialise as `List<PlayPhaseRecord>`, search only in `phases[currentPhaseIndex].blocks`
- Throw `PlayInstanceNotFoundException` (reuse with block message) if not found

#### `completeBlock(instanceId, blockId, playerResults)`

```
instance = findById or throw
[blocks or phases] = deserialise stateJson
block = findBlock(instance, blockId)
if block.status != "in_progress" → throw BlockStateException("Block nicht aktiv")

block = block.withStatus("done")
         .withCompletedAt(Instant.now())
         .withResult(new BlockResultRecord(playerResults))

if instance.type == "programm":
  allDone = blocks.all { it.status == "done" }
  if allDone:
    instance.status = "completed"
    instance.completedAt = Instant.now()
  instance.stateJson = writeBlocks(updatedBlocks)

if instance.type == "training":
  phases[currentPhaseIndex].blocks updated
  phaseAllDone = phases[currentPhaseIndex].blocks.all { status == "done" }
  if phaseAllDone:
    phases[currentPhaseIndex].status = "done"
    nextIdx = currentPhaseIndex + 1
    if nextIdx < phases.size():
      phases[nextIdx].status = "active"
      instance.currentPhaseIndex = nextIdx
    else:
      instance.status = "completed"
      instance.completedAt = Instant.now()
  instance.stateJson = writePhases(phases)

save, return toPlayInstanceResponse
```

#### `stopPlayInstance(instanceId)`

```
instance = findById or throw; check owner
instance.status = "cancelled"; save
```

#### `listPlayResults(rangeId?, from?, to?, page, size)`

```
owner = currentUser()
pageable = PageRequest.of(page, size)
results = playInstanceRepository
    .findByOwnerAndStatusOrderByCompletedAtDesc(owner, "completed", pageable)
// Apply from/to and rangeId filters (in-memory for now, or add @Query)
return PlayResultPage with PlayResultSummary items
```

#### `getPlayResult(resultId)`

```
instance = findById or throw
if instance.status != "completed" → throw NotFoundException
return toPlayResultResponse(instance)   // full detail
```

---

## Task 7 — Controllers

Each controller is a `@RestController @NullMarked` class implementing the **generated
OpenAPI interface**. No `@RequestMapping` on the class. Constructor-inject the
relevant service and `SecurityHelper`.

### 7a. `GuestController implements GuestApi`

| OpenAPI operationId | Service call |
|---|---|
| `listGuests` | `guestService.listGuests()` |
| `createGuest` | `guestService.createGuest(request.displayName)` → 201 |
| `updateGuest` | `guestService.updateGuest(id, request.displayName)` |
| `deleteGuest` | `guestService.deleteGuest(id)` → 204 |

---

### 7b. `AblaufController implements AblaufApi`

| operationId | Service call |
|---|---|
| `listAblaeufe` | `ablaufService.listAblaeufe(ownership, rangeId)` |
| `createAblauf` | `ablaufService.createAblauf(request)` → 201 |
| `getAblauf` | `ablaufService.getAblauf(id)` |
| `updateAblauf` | `ablaufService.updateAblauf(id, request)` |
| `updateAblaufOwnership` | `ablaufService.updateAblaufOwnership(id, request)` |
| `deleteAblauf` | `ablaufService.deleteAblauf(id)` → 204 |

---

### 7c. `ProgrammController implements ProgrammeApi`

| operationId | Service call |
|---|---|
| `listProgrammes` | `programmService.listProgramme()` |
| `createProgramme` | `programmService.createProgramm(request)` → 201 |
| `getProgramme` | `programmService.getProgramm(id)` |
| `updateProgramme` | `programmService.updateProgramm(id, request)` |
| `deleteProgramme` | `programmService.deleteProgramm(id)` → 204 |

---

### 7d. `TrainingController implements TrainingApi`

| operationId | Service call |
|---|---|
| `listTrainings` | `trainingService.listTrainings()` |
| `createTraining` | `trainingService.createTraining(request)` → 201 |
| `getTraining` | `trainingService.getTraining(id)` |
| `updateTraining` | `trainingService.updateTraining(id, request)` |
| `deleteTraining` | `trainingService.deleteTraining(id)` → 204 |

---

### 7e. `PlayInstanceController implements PlayInstanceApi`

| operationId | Service call |
|---|---|
| `startProgrammeInstance` | `playInstanceService.startProgrammeInstance(request)` → 201 |
| `startTrainingInstance` | `playInstanceService.startTrainingInstance(request)` → 201 |
| `listPlayInstances` | `playInstanceService.listPlayInstances(status, rangeId)` |
| `getPlayInstance` | `playInstanceService.getPlayInstance(instanceId)` |
| `stopPlayInstance` | `playInstanceService.stopPlayInstance(instanceId)` → 204 |
| `startBlock` | `playInstanceService.startBlock(instanceId, blockId)` |
| `completeBlock` | `playInstanceService.completeBlock(instanceId, blockId, request)` |

---

### 7f. `PlayResultController implements PlayResultApi`

| operationId | Service call |
|---|---|
| `listPlayResults` | `playInstanceService.listPlayResults(rangeId, from, to, page, size)` |
| `getPlayResult` | `playInstanceService.getPlayResult(resultId)` |

---

## Task 8 — SecurityConfig patch

In `SecurityConfig`, add the new routes. Pattern follows the existing permit rules:

```java
.requestMatchers(HttpMethod.GET,  "/api/guests/**").authenticated()
.requestMatchers(HttpMethod.POST, "/api/guests/**").authenticated()
.requestMatchers(HttpMethod.PUT,  "/api/guests/**").authenticated()
.requestMatchers(HttpMethod.DELETE, "/api/guests/**").authenticated()

.requestMatchers("/api/ablaeufe/**").authenticated()
.requestMatchers("/api/programmes/**").authenticated()
.requestMatchers("/api/trainings/**").authenticated()
.requestMatchers("/api/play-instances/**").authenticated()
.requestMatchers("/api/play-results/**").authenticated()
```

---

## Task 9 — GlobalExceptionHandler additions

```java
@ExceptionHandler(AblaufNotFoundException.class)
ProblemDetail handleAblaufNotFound(AblaufNotFoundException ex) { ... 404 }

@ExceptionHandler(ProgrammNotFoundException.class)
ProblemDetail handleProgrammNotFound(ProgrammNotFoundException ex) { ... 404 }

@ExceptionHandler(TrainingNotFoundException.class)
ProblemDetail handleTrainingNotFound(TrainingNotFoundException ex) { ... 404 }

@ExceptionHandler(GuestNotFoundException.class)
ProblemDetail handleGuestNotFound(GuestNotFoundException ex) { ... 404 }

@ExceptionHandler(PlayInstanceNotFoundException.class)
ProblemDetail handlePlayInstanceNotFound(PlayInstanceNotFoundException ex) { ... 404 }

@ExceptionHandler(BlockStateException.class)
ProblemDetail handleBlockState(BlockStateException ex) { ... 409 }
```

---

## Recommended implementation order

Work through the tasks in this sequence — each step compiles before the next starts:

1. **Entities** (JPA only, no logic)
2. **Repositories** (trivial interfaces — verify JPA creates the tables on startup)
3. **Exception classes + GlobalExceptionHandler additions**
4. **SecurityHelper**
5. **Internal DTO records** (`dto/play/*.java`)
6. **Mapper additions** (compile-check against generated OpenAPI types)
7. **GuestService + GuestController** (simplest end-to-end path — smoke test first)
8. **AblaufService + AblaufController**
9. **ProgrammService + ProgrammController**
10. **TrainingService + TrainingController**
11. **PlayInstanceService + PlayInstanceController + PlayResultController** (save for last — most complex, depends on all templates)
12. **SecurityConfig patch**
13. **Integration tests** for each service (H2, `@SpringBootTest`)

---

## Testing checklist (per endpoint group)

### Guests
- [ ] `POST /api/guests` → 201, body contains id + displayName
- [ ] `GET /api/guests` → list includes the new guest
- [ ] `PUT /api/guests/{id}` → displayName updated
- [ ] `DELETE /api/guests/{id}` → 204, subsequent GET returns 404

### Abläufe
- [ ] Create user-scoped Ablauf → not visible to other users
- [ ] Promote to range (`PATCH /ownership`) → visible to all authenticated users
- [ ] Non-owner `DELETE` → 403
- [ ] Non-admin promotes to range → 403
- [ ] `GET` with `?ownership=range` only returns published Abläufe

### Programme
- [ ] Creating a Programme snapshots step content — mutating the source Ablauf afterward does not change the Programme
- [ ] Owner of source Ablauf != owner of Programme → snapshot still works (owner check on Ablauf is for write ops, not reads used internally during snapshot)

### Training
- [ ] Snapshot is two levels deep: Training → Programme snapshot → Ablauf snapshot

### Play Instances
- [ ] `POST /play-instances/programme` → all blocks `pending`, status `active`
- [ ] `POST /play-instances/training` → first phase `active`, others `pending`
- [ ] `startBlock` idempotent on already-`in_progress` block
- [ ] `startBlock` on `done` block → 409
- [ ] `completeBlock` on `pending` block → 409
- [ ] Completing last block of a Programme → instance status becomes `completed`
- [ ] Completing last block of a Training phase → next phase activates
- [ ] Completing last block of last Training phase → instance `completed`
- [ ] `GET /play-results` only returns `completed` instances
- [ ] `?rangeId=` filter on `listPlayInstances` returns only instances with live blocks on that range

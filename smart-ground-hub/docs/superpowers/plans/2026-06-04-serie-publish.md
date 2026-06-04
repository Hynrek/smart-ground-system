# Serie Publish/Unpublish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `published` boolean flag to range-owned Serien so they are hidden from regular users until explicitly published by an admin or range owner.

**Architecture:** A new `published` column defaults to `false` on the `Serie` entity. Visibility filtering in `SerieService` is split by role: admins see all range-owned Serien; regular users only see those where `published = true`. A new `PATCH /api/serien/{id}/published` endpoint (admin-only) toggles the flag.

**Tech Stack:** Java 25, Spring Boot 4, JPA/Hibernate (H2 for tests), OpenAPI code generation (`./mvnw generate-sources`), Mockito for unit tests.

---

## File Map

| File | Change |
|---|---|
| `src/main/resources/static/openapi.yaml` | Add `published` to `SerieResponse`; add `UpdateSeriePublishedRequest` schema; add `PATCH /api/serien/{id}/published` path |
| `src/main/java/ch/jp/shooting/model/Serie.java` | Add `published boolean` field + getter/setter |
| `src/main/java/ch/jp/shooting/repository/SerieRepository.java` | Add `findByOwnershipAndPublished` and `findByOwnerOrPublishedRange` |
| `src/main/java/ch/jp/shooting/mapper/PlayMapper.java` | Map `published` in `toSerieResponse` |
| `src/main/java/ch/jp/shooting/service/SerieService.java` | Update `listSerien` / `getSerie` visibility; add `updateSeriePublished` |
| `src/main/java/ch/jp/shooting/api/SerieController.java` | Implement `updateSeriePublished` |
| `src/test/java/ch/jp/shooting/service/SerieServiceTest.java` | Unit tests for all new/changed logic |

---

## Task 1: Update openapi.yaml

**Files:**
- Modify: `src/main/resources/static/openapi.yaml`

- [ ] **Step 1: Add `published` field to `SerieResponse` schema**

Find the `SerieResponse` schema block (around line 2303) and add `published` after `ownerUsername`:

```yaml
    SerieResponse:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        ownership:
          $ref: '#/components/schemas/SerieOwnership'
        rangeId:
          type: string
          format: uuid
          nullable: true
          description: Range this Serie is designed for (used for block routing)
        rangeName:
          type: string
          nullable: true
        steps:
          type: array
          items:
            $ref: '#/components/schemas/Step'
        createdAt:
          type: string
          format: date-time
        ownerUsername:
          type: string
          description: Username of the user who created this Serie
        published:
          type: boolean
          description: Whether this range-owned Serie is visible to regular users
```

- [ ] **Step 2: Add `UpdateSeriePublishedRequest` schema**

Add after the `UpdateSerieOwnershipRequest` block (around line 2370), before the `# ───── Passe` comment:

```yaml
    UpdateSeriePublishedRequest:
      type: object
      required: [published]
      properties:
        published:
          type: boolean
```

- [ ] **Step 3: Add `PATCH /api/serien/{id}/published` path**

Add after the `/api/serien/{id}/ownership` block (after line 1695 approximately). Find the closing of the ownership patch block and add:

```yaml
  /api/serien/{id}/published:
    patch:
      summary: Publish or unpublish a range-owned Serie
      description: >
        Sets the published flag on a Serie. Only ADMIN and GROUND_OWNER roles
        may call this endpoint. When published=false, regular users cannot see
        or access this Serie even if its ownership is 'range'.
      operationId: updateSeriePublished
      tags: [Serie]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateSeriePublishedRequest'
      responses:
        '200':
          description: Serie with updated published flag
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SerieResponse'
        '403':
          description: Caller is not an admin or range owner
        '404':
          description: Serie not found
```

- [ ] **Step 4: Regenerate sources**

```bash
./mvnw generate-sources
```

Expected: BUILD SUCCESS. Verify that `target/generated-sources/openapi/ch/jp/smartground/api/SerieApi.java` now contains a method `updateSeriePublished`.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] Add published field and PATCH /api/serien/{id}/published to openapi.yaml"
```

---

## Task 2: Add `published` field to `Serie` entity

**Files:**
- Modify: `src/main/java/ch/jp/shooting/model/Serie.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/ch/jp/shooting/service/SerieServiceTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.SerieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SerieServiceTest {

    @Mock SerieRepository serieRepository;
    @Mock RangeRepository rangeRepository;
    @Mock SecurityHelper securityHelper;

    @InjectMocks SerieService serieService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        when(securityHelper.currentUser()).thenReturn(user);
    }

    private Serie rangeOwnedSerie(boolean published) {
        var serie = new Serie();
        serie.setId(UUID.randomUUID());
        serie.setName("Test");
        serie.setOwnership("range");
        serie.setPublished(published);
        serie.setStepsJson("[]");
        serie.setCreatedAt(Instant.now());
        serie.setOwner(user);
        return serie;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw test -Dtest=SerieServiceTest -pl . 2>&1 | tail -20
```

Expected: FAIL — `setPublished` method does not exist on `Serie`.

- [ ] **Step 3: Add `published` field to `Serie` entity**

In `src/main/java/ch/jp/shooting/model/Serie.java`, add after the `createdAt` field and before the getters:

```java
@Column(nullable = false)
private boolean published = false;
```

And add getter/setter after the `getCreatedAt`/`setCreatedAt` pair:

```java
public boolean isPublished() { return published; }
public void setPublished(boolean published) { this.published = published; }
```

- [ ] **Step 4: Run test to verify it compiles and passes**

```bash
./mvnw test -Dtest=SerieServiceTest -pl . 2>&1 | tail -20
```

Expected: PASS (no test methods yet that could fail after the field exists).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/model/Serie.java \
        src/test/java/ch/jp/shooting/service/SerieServiceTest.java
git commit -m "[backend] Add published field to Serie entity"
```

---

## Task 3: Update `SerieRepository` with new query methods

**Files:**
- Modify: `src/main/java/ch/jp/shooting/repository/SerieRepository.java`

- [ ] **Step 1: Add repository methods**

Replace the full contents of `src/main/java/ch/jp/shooting/repository/SerieRepository.java` with:

```java
package ch.jp.shooting.repository;

import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@NullMarked
public interface SerieRepository extends JpaRepository<Serie, UUID> {
    /** Eigene Serien ODER platzweit veröffentlichte (für Admins) */
    List<Serie> findByOwnerOrOwnership(User owner, String ownership);

    /** Eigene Serien ODER publizierte Platz-Serien (für reguläre Nutzer) */
    @Query("SELECT s FROM Serie s WHERE s.owner = :owner OR (s.ownership = 'range' AND s.published = true)")
    List<Serie> findByOwnerOrPublishedRange(@Param("owner") User owner);

    /** Eigene Serien eines Besitzers */
    List<Serie> findByOwner(User owner);

    /** Nur Serien mit ownership='range' */
    List<Serie> findByOwnership(String ownership);

    /** Nur Serien mit ownership='range' und gesetztem published-Flag */
    List<Serie> findByOwnershipAndPublished(String ownership, boolean published);

    /** Serien nach Platz-Zuordnung */
    List<Serie> findByRange_Id(UUID rangeId);

    /** Kombiniert: Besitzer-Filter + Platz-Filter */
    List<Serie> findByOwnerAndRange_Id(User owner, UUID rangeId);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/ch/jp/shooting/repository/SerieRepository.java
git commit -m "[backend] Add findByOwnerOrPublishedRange and findByOwnershipAndPublished to SerieRepository"
```

---

## Task 4: Update `PlayMapper.toSerieResponse` to map `published`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/mapper/PlayMapper.java`

- [ ] **Step 1: Write the failing test**

Add this test to `SerieServiceTest` (inside the class, after the `rangeOwnedSerie` helper):

```java
@Test
void toSerieResponse_mapsPublishedField() {
    var serie = rangeOwnedSerie(true);
    var response = ch.jp.shooting.mapper.PlayMapper.toSerieResponse(serie);
    assertThat(response.getPublished()).isTrue();
}

@Test
void toSerieResponse_mapsUnpublishedField() {
    var serie = rangeOwnedSerie(false);
    var response = ch.jp.shooting.mapper.PlayMapper.toSerieResponse(serie);
    assertThat(response.getPublished()).isFalse();
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw test -Dtest=SerieServiceTest -pl . 2>&1 | tail -20
```

Expected: FAIL — `getPublished()` returns null (field not mapped yet).

- [ ] **Step 3: Map `published` in `PlayMapper.toSerieResponse`**

In `src/main/java/ch/jp/shooting/mapper/PlayMapper.java`, update the `toSerieResponse` method to add `.published(serie.isPublished())`:

```java
public static SerieResponse toSerieResponse(Serie serie) {
    var range = serie.getRange();
    return new SerieResponse()
        .id(serie.getId())
        .name(serie.getName())
        .ownership(SerieOwnership.fromValue(serie.getOwnership()))
        .rangeId(range != null ? range.getId() : null)
        .rangeName(range != null ? range.getName() : null)
        .steps(parseSteps(serie.getStepsJson()).stream()
            .map(PlayMapper::toStep)
            .toList())
        .createdAt(OffsetDateTime.ofInstant(serie.getCreatedAt(), ZoneOffset.UTC))
        .ownerUsername(serie.getOwner().getEmail())
        .published(serie.isPublished());
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./mvnw test -Dtest=SerieServiceTest -pl . 2>&1 | tail -20
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/mapper/PlayMapper.java \
        src/test/java/ch/jp/shooting/service/SerieServiceTest.java
git commit -m "[backend] Map published field in PlayMapper.toSerieResponse"
```

---

## Task 5: Update `SerieService` visibility logic and add `updateSeriePublished`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/SerieService.java`

- [ ] **Step 1: Write the failing tests**

Add these tests to `SerieServiceTest`:

```java
@Test
void listSerien_regularUser_rangeOwnership_onlyReturnsPublished() {
    var published = rangeOwnedSerie(true);
    var draft = rangeOwnedSerie(false);
    when(securityHelper.isAdminOrOwner()).thenReturn(false);
    when(serieRepository.findByOwnershipAndPublished("range", true))
        .thenReturn(List.of(published));

    var result = serieService.listSerien("range", null);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getPublished()).isTrue();
}

@Test
void listSerien_admin_rangeOwnership_returnsAllIncludingDrafts() {
    var published = rangeOwnedSerie(true);
    var draft = rangeOwnedSerie(false);
    when(securityHelper.isAdminOrOwner()).thenReturn(true);
    when(serieRepository.findByOwnership("range")).thenReturn(List.of(published, draft));

    var result = serieService.listSerien("range", null);

    assertThat(result).hasSize(2);
}

@Test
void listSerien_regularUser_noFilter_hidesUnpublishedRangeSerien() {
    var published = rangeOwnedSerie(true);
    when(securityHelper.isAdminOrOwner()).thenReturn(false);
    when(serieRepository.findByOwnerOrPublishedRange(user)).thenReturn(List.of(published));

    var result = serieService.listSerien(null, null);

    assertThat(result).hasSize(1);
}

@Test
void getSerie_regularUser_unpublishedRangeSerie_throws404() {
    var draft = rangeOwnedSerie(false);
    var otherId = UUID.randomUUID();
    draft.setOwner(new User()); // different owner
    draft.getOwner().setId(otherId);
    when(serieRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
    when(securityHelper.isAdminOrOwner()).thenReturn(false);

    org.junit.jupiter.api.Assertions.assertThrows(
        ch.jp.shooting.exception.SerieNotFoundException.class,
        () -> serieService.getSerie(draft.getId())
    );
}

@Test
void updateSeriePublished_adminPublishes_setsPublishedTrue() {
    var serie = rangeOwnedSerie(false);
    when(securityHelper.isAdminOrOwner()).thenReturn(true);
    when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));
    when(serieRepository.save(serie)).thenReturn(serie);

    var request = new ch.jp.smartground.model.UpdateSeriePublishedRequest().published(true);
    var result = serieService.updateSeriePublished(serie.getId(), request);

    assertThat(result.getPublished()).isTrue();
}

@Test
void updateSeriePublished_regularUser_throws403() {
    var serie = rangeOwnedSerie(false);
    when(securityHelper.isAdminOrOwner()).thenReturn(false);
    when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));

    var request = new ch.jp.smartground.model.UpdateSeriePublishedRequest().published(true);
    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> serieService.updateSeriePublished(serie.getId(), request)
    );
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./mvnw test -Dtest=SerieServiceTest -pl . 2>&1 | tail -30
```

Expected: FAIL — `updateSeriePublished` does not exist, and `listSerien`/`getSerie` don't yet apply the new filtering.

- [ ] **Step 3: Update `SerieService`**

Replace `src/main/java/ch/jp/shooting/service/SerieService.java` with:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.StepRecord;
import ch.jp.shooting.exception.RangeNotFoundException;
import ch.jp.shooting.exception.SerieNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.smartground.model.CreateSerieRequest;
import ch.jp.smartground.model.SerieResponse;
import ch.jp.smartground.model.UpdateSerieOwnershipRequest;
import ch.jp.smartground.model.UpdateSeriePublishedRequest;
import ch.jp.smartground.model.UpdateSerieRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// Geschäftslogik für Serien (Wurfsequenzen)
@Service
@NullMarked
public class SerieService {

    private final SerieRepository serieRepository;
    private final RangeRepository rangeRepository;
    private final SecurityHelper securityHelper;

    public SerieService(SerieRepository serieRepository,
                        RangeRepository rangeRepository,
                        SecurityHelper securityHelper) {
        this.serieRepository = serieRepository;
        this.rangeRepository = rangeRepository;
        this.securityHelper = securityHelper;
    }

    /**
     * Listet sichtbare Serien auf – optional gefiltert nach Ownership und Platz.
     * Admins sehen alle Platz-Serien; reguläre Nutzer nur publizierte.
     */
    public List<SerieResponse> listSerien(@Nullable String ownership, @Nullable UUID rangeId) {
        var owner = securityHelper.currentUser();
        boolean isAdmin = securityHelper.isAdminOrOwner();
        List<Serie> result;

        if ("user".equals(ownership)) {
            result = rangeId != null
                    ? serieRepository.findByOwnerAndRange_Id(owner, rangeId)
                    : serieRepository.findByOwner(owner);
        } else if ("range".equals(ownership)) {
            // Admins sehen alle, reguläre Nutzer nur publizierte
            result = isAdmin
                    ? serieRepository.findByOwnership("range")
                    : serieRepository.findByOwnershipAndPublished("range", true);
            if (rangeId != null) {
                final UUID finalRangeId = rangeId;
                result = result.stream()
                        .filter(s -> s.getRange() != null && finalRangeId.equals(s.getRange().getId()))
                        .toList();
            }
        } else {
            // Kein Filter: eigene + sichtbare Platz-Serien
            result = isAdmin
                    ? serieRepository.findByOwnerOrOwnership(owner, "range")
                    : serieRepository.findByOwnerOrPublishedRange(owner);
            if (rangeId != null) {
                final UUID finalRangeId = rangeId;
                result = result.stream()
                        .filter(s -> s.getRange() != null && finalRangeId.equals(s.getRange().getId()))
                        .toList();
            }
        }

        return result.stream().map(PlayMapper::toSerieResponse).toList();
    }

    /** Erstellt eine neue Serie für den aktuellen Nutzer. */
    public SerieResponse createSerie(CreateSerieRequest request) {
        var owner = securityHelper.currentUser();
        var serie = new Serie();
        serie.setName(request.getName());
        serie.setOwner(owner);
        serie.setOwnership(
                request.getOwnership() != null ? request.getOwnership().getValue() : "user"
        );
        serie.setStepsJson(PlayMapper.writeSteps(
                request.getSteps().stream()
                        .map(step -> new StepRecord(
                                step.getId(),
                                step.getType().getValue(),
                                stringOrNull(step.getPosId()),
                                stringOrNull(step.getAlias()),
                                stringOrNull(step.getPosId1()),
                                stringOrNull(step.getPosId2()),
                                stringOrNull(step.getAlias1()),
                                stringOrNull(step.getAlias2()),
                                step.getLetter()
                        ))
                        .toList()
        ));
        var rangeIdJn = request.getRangeId();
        if (rangeIdJn != null && rangeIdJn.isPresent()) {
            UUID rangeId = rangeIdJn.get();
            if (rangeId != null) {
                serie.setRange(rangeRepository.findById(rangeId)
                        .orElseThrow(() -> new RangeNotFoundException(rangeId)));
            }
        }
        return PlayMapper.toSerieResponse(serieRepository.save(serie));
    }

    /**
     * Gibt eine Serie zurück, wenn der Nutzer Zugriff hat.
     * Unpublizierte Platz-Serien erscheinen für reguläre Nutzer als nicht gefunden (404).
     */
    public SerieResponse getSerie(UUID id) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        var owner = securityHelper.currentUser();
        boolean isOwner = serie.getOwner().getId().equals(owner.getId());
        boolean isAdmin = securityHelper.isAdminOrOwner();
        boolean isVisibleRangeSerie = "range".equals(serie.getOwnership()) && serie.isPublished();

        if (!isOwner && !isAdmin && !isVisibleRangeSerie) {
            // Unpublizierte Platz-Serie: 404 statt 403, um Existenz nicht preiszugeben
            throw new SerieNotFoundException(id);
        }
        return PlayMapper.toSerieResponse(serie);
    }

    /** Aktualisiert Name und optionale Platz-Zuordnung einer Serie. */
    public SerieResponse updateSerie(UUID id, UpdateSerieRequest request) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!serie.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        serie.setName(request.getName());
        var rangeIdJn = request.getRangeId();
        if (rangeIdJn != null && rangeIdJn.isPresent()) {
            UUID rangeId = rangeIdJn.get();
            if (rangeId != null) {
                serie.setRange(rangeRepository.findById(rangeId)
                        .orElseThrow(() -> new RangeNotFoundException(rangeId)));
            } else {
                serie.setRange(null);
            }
        }
        return PlayMapper.toSerieResponse(serieRepository.save(serie));
    }

    /**
     * Ändert die Ownership einer Serie.
     * Nur ADMIN/GROUND_OWNER dürfen auf "range" setzen.
     */
    public SerieResponse updateSerieOwnership(UUID id, UpdateSerieOwnershipRequest request) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!serie.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if ("range".equals(request.getOwnership().getValue()) && !securityHelper.isAdminOrOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        serie.setOwnership(request.getOwnership().getValue());
        return PlayMapper.toSerieResponse(serieRepository.save(serie));
    }

    /**
     * Publiziert oder versteckt eine Platz-Serie.
     * Nur ADMIN/GROUND_OWNER dürfen diese Aktion ausführen.
     */
    public SerieResponse updateSeriePublished(UUID id, UpdateSeriePublishedRequest request) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        if (!securityHelper.isAdminOrOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        serie.setPublished(request.getPublished());
        return PlayMapper.toSerieResponse(serieRepository.save(serie));
    }

    /** Löscht eine Serie (nur Besitzer). */
    public void deleteSerie(UUID id) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!serie.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        serieRepository.delete(serie);
    }

    @Nullable
    private static String stringOrNull(@Nullable JsonNullable<String> jn) {
        if (jn == null || !jn.isPresent()) return null;
        return jn.get();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./mvnw test -Dtest=SerieServiceTest -pl . 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 5: Run the full test suite**

```bash
./mvnw test 2>&1 | tail -20
```

Expected: BUILD SUCCESS, no failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/SerieService.java \
        src/test/java/ch/jp/shooting/service/SerieServiceTest.java
git commit -m "[backend] Update SerieService: publish visibility filtering and updateSeriePublished"
```

---

## Task 6: Implement `updateSeriePublished` in `SerieController`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/api/SerieController.java`

- [ ] **Step 1: Add `updateSeriePublished` override**

Add the following method to `SerieController` (after `updateSerieOwnership`):

```java
@Override
public ResponseEntity<SerieResponse> updateSeriePublished(UUID id,
        UpdateSeriePublishedRequest updateSeriePublishedRequest) {
    return ResponseEntity.ok(serieService.updateSeriePublished(id, updateSeriePublishedRequest));
}
```

Also add the import at the top if not already present (the wildcard import `ch.jp.smartground.model.*` already covers it):

No additional import needed — `UpdateSeriePublishedRequest` is in `ch.jp.smartground.model.*`.

- [ ] **Step 2: Run the full test suite**

```bash
./mvnw test 2>&1 | tail -20
```

Expected: BUILD SUCCESS, no failures.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ch/jp/shooting/api/SerieController.java
git commit -m "[backend] Implement updateSeriePublished in SerieController"
```

---

## Task 7: Final verification

- [ ] **Step 1: Run full test suite and package**

```bash
./mvnw clean package -DskipTests 2>&1 | tail -10
./mvnw test 2>&1 | tail -20
```

Expected: both commands succeed with no errors or warnings.

- [ ] **Step 2: Update CLAUDE.md**

In `smart-ground-backend/CLAUDE.md`, under the `Serie / Passe / Training / Play` domain section, add a note under the `Serie` bullet:

```
- **`Serie`**: ... `published`: `false` by default; range-owned Serien are hidden from regular users until an admin sets `published = true` via `PATCH /api/serien/{id}/published`.
```

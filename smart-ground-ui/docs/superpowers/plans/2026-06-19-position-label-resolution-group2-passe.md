# Position Label Resolution — Group 2: Passe becomes a live reference

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Passe stops snapshotting its Serien and instead stores an **ordered list of `serieId`s**, joined live (with Group 1 label resolution) on every read. The Serie `PUT` endpoint accepts `steps` for in-place edit, so editing a Serie keeps its stable ID and a referencing Passe never needs "repair".

**Architecture:** `Passe.serienJson` (embedded `EmbeddedSerie[]` snapshot) is replaced by `Passe.serieIdsJson` (an ordered JSON array of UUID strings). On read, `PasseService.resolveLiveSerien(passe)` loads the referenced Serien live, resolves each step's `letter`/`alias` from the current `RangePosition` (reusing `PositionLabelResolver` from Group 1), and returns `EmbeddedSerieRecord[]`. A referenced Serie that was deleted resolves to a placeholder record (`missing = true`, empty steps). The same `resolveLiveSerien` join is reused by `PlayInstanceService.startPasseInstance` and `SessionService` so a Passe behaves as a live reference everywhere it is consumed. Separately, `UpdateSerieRequest` gains an optional `steps` array; when present, `SerieService.updateSerie` replaces `stepsJson` in place (stable Serie ID).

**Tech Stack:** Java 25, Spring Boot 4, JPA, JUnit 5 + Mockito + AssertJ. OpenAPI contract-first (`openapi.yaml` → `mvn generate-sources`). Sibling repo: `../smart-ground-backend`.

**Spec:** `docs/superpowers/specs/2026-06-19-position-label-resolution-design.md` (Group 2).

---

## Build / environment notes (Windows)

- Run all commands from **PowerShell**, in `../smart-ground-backend`.
- The `./mvnw` / `mvnw.cmd` wrapper is a **broken stub — do NOT use it.** Use system Maven `mvn` (Chocolatey apache-maven 3.9.x) with Java 25 (`JAVA_HOME=C:\Users\hynre\.jdks\jbr-25.0.2`).
- Single test class: `mvn -Dtest=SomeTest test`. Full suite: `mvn test` (~75 tests). Regenerate OpenAPI: `mvn generate-sources`.
- **Do not pipe `2>&1` on `mvn` in PowerShell 5.1** (it wraps stderr as NativeCommandError and falsely reports failure).
- Generated model getters for `Step` fields `posId`/`alias`/`posId1`/`posId2`/`alias1`/`alias2` return `JsonNullable<String>` — unwrap with the existing `SerieService.stringOrNull` pattern. `Step.getId()`, `Step.getType()`, `Step.getLetter()/getLetter1()/getLetter2()` return plain `String`/enum (see `createSerie`).

## Scope notes (deliberate boundaries)

- **Migration is deferred to Group 5.** Pre-v1.0, the column rename leaves old `passen.serien_json` data orphaned; existing Passen rows get an empty `serie_ids_json` and resolve to zero serien. Group 5 resets/drops existing Passen. This plan does **not** migrate data.
- **Active play-instance re-resolution stays frozen-at-start.** `startPasseInstance` resolves labels **once** when the run starts (the block snapshot then lives in `play_instances.state_json`). Live re-resolution of an *in-progress* instance on read, and the cross-stack active-session propagation test, belong to Group 5.
- **Stechen (`startSerieInstance` / `TiebreakerService.buildSerieSnapshot`) is untouched.** It builds a transient single-Serie snapshot directly from a live `Serie` and does not read `Passe.serienJson`, so the storage change does not affect it. Resolving its labels is out of scope for Group 2.

---

## File Structure

**Modify:**
- `src/main/resources/static/openapi.yaml` — add optional `steps` to `UpdateSerieRequest`; add `missing` to `EmbeddedSerie`.
- `src/main/java/ch/jp/shooting/service/SerieService.java` — `updateSerie` accepts `steps`; extract `writeStepsJson`; expose nothing new.
- `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java` — make the alias rule reusable (`public static String aliasOf(RangePosition)`).
- `src/main/java/ch/jp/shooting/model/Passe.java` — replace `serienJson` field with `serieIdsJson`.
- `src/main/java/ch/jp/shooting/service/PasseService.java` — store serieId list on create; add `resolveLiveSerien`; build responses from the live join.
- `src/main/java/ch/jp/shooting/dto/play/EmbeddedSerieRecord.java` — add `boolean missing`.
- `src/main/java/ch/jp/shooting/mapper/PlayMapper.java` — `toPasseResponse(Passe, List<EmbeddedSerie>)`; `toEmbeddedSerie` carries `missing`; add `writeSerieIds`/`parseSerieIds`.
- `src/main/java/ch/jp/shooting/service/PlayInstanceService.java` — `startPasseInstance` uses `resolveLiveSerien`.
- `src/main/java/ch/jp/shooting/service/SessionService.java` — `mapToApiSessionResponse` uses `resolveLiveSerien`.

**Test (modify/create):**
- `src/test/java/ch/jp/shooting/service/SerieServiceTest.java` — Serie PUT-with-steps tests.
- `src/test/java/ch/jp/shooting/service/PasseServiceTest.java` — **create** — Passe live-join tests.
- `src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java` — wire the new `PasseService` mock; add a `startPasseInstance` test.
- `src/test/java/ch/jp/shooting/service/SessionServiceFinishGuardTest.java`, `SessionServiceTransitionTest.java` — add the new `PasseService` constructor arg.

---

### Task 1: Serie `PUT` accepts steps (in-place edit, stable ID)

**Files:**
- Modify: `src/main/resources/static/openapi.yaml` (`UpdateSerieRequest`)
- Modify: `src/main/java/ch/jp/shooting/service/SerieService.java`
- Test: `src/test/java/ch/jp/shooting/service/SerieServiceTest.java`

- [ ] **Step 1: Update the OpenAPI contract**

In `src/main/resources/static/openapi.yaml`, replace the `UpdateSerieRequest` schema (currently):

```yaml
    UpdateSerieRequest:
      type: object
      required: [name]
      properties:
        name:
          type: string
          minLength: 1
        rangeId:
          type: string
          format: uuid
          nullable: true
```

with:

```yaml
    UpdateSerieRequest:
      type: object
      required: [name]
      properties:
        name:
          type: string
          minLength: 1
        rangeId:
          type: string
          format: uuid
          nullable: true
        steps:
          type: array
          items:
            $ref: '#/components/schemas/Step'
          description: >
            When present and non-empty, replaces the Serie's steps in place,
            keeping the same Serie ID. When omitted, the existing steps are kept
            (rename / range-only edit).
```

- [ ] **Step 2: Regenerate sources**

Run: `mvn generate-sources`
Expected: BUILD SUCCESS. `UpdateSerieRequest` in `target/generated-sources/openapi/ch/jp/smartground/model/` now has a `getSteps()` returning `List<Step>` (initialised to an empty list when omitted).

- [ ] **Step 3: Write the failing tests**

Add to `src/test/java/ch/jp/shooting/service/SerieServiceTest.java`. These reuse the existing test fixtures (`user`, `serieRepository`, `securityHelper`, `positionLabelResolver` mocks, and the `pos(...)` / `soloSerieWithStaleLetter(...)` helpers already added in Group 1). Import `ch.jp.smartground.model.Step`, `ch.jp.smartground.model.StepType`, and `ch.jp.smartground.model.UpdateSerieRequest` if not already imported.

```java
    @Test
    void updateSerie_withSteps_replacesStepsAndKeepsSameId() {
        var posId = UUID.randomUUID();
        var serie = soloSerieWithStaleLetter(posId); // existing helper: one solo step, stale letter
        var serieId = serie.getId();
        when(serieRepository.findById(serieId)).thenReturn(Optional.of(serie));
        when(serieRepository.save(org.mockito.ArgumentMatchers.any(Serie.class)))
            .thenAnswer(i -> i.getArgument(0));
        when(positionLabelResolver.byPosIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(java.util.Map.of(posId.toString(), pos(posId, "B2")));

        var newPosId = UUID.randomUUID();
        var request = new UpdateSerieRequest()
            .name("Renamed")
            .steps(List.of(new Step()
                .id("9")
                .type(StepType.SOLO)
                .posId(org.openapitools.jackson.nullable.JsonNullable.of(newPosId.toString()))));

        var result = serieService.updateSerie(serieId, request);

        // stable ID
        assertThat(result.getId()).isEqualTo(serieId);
        // steps replaced: the persisted stepsJson now references newPosId, not the old one
        assertThat(serie.getStepsJson()).contains(newPosId.toString());
        assertThat(serie.getStepsJson()).doesNotContain(posId.toString());
        assertThat(result.getSteps()).hasSize(1);
    }

    @Test
    void updateSerie_withoutSteps_keepsExistingSteps() {
        var posId = UUID.randomUUID();
        var serie = soloSerieWithStaleLetter(posId);
        var originalStepsJson = serie.getStepsJson();
        when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));
        when(serieRepository.save(org.mockito.ArgumentMatchers.any(Serie.class)))
            .thenAnswer(i -> i.getArgument(0));
        when(positionLabelResolver.byPosIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(java.util.Map.of(posId.toString(), pos(posId, "A1")));

        var request = new UpdateSerieRequest().name("Renamed only");

        var result = serieService.updateSerie(serie.getId(), request);

        assertThat(result.getName()).isEqualTo("Renamed only");
        assertThat(serie.getStepsJson()).isEqualTo(originalStepsJson); // steps untouched
    }
```

> **Note on `soloSerieWithStaleLetter`:** the Group 1 helper sets `serie.setOwner(user)`. `updateSerie` checks `serie.getOwner().getId().equals(owner.getId())`. Confirm the existing test fixture wires `securityHelper.currentUser()` → `user` and `user.getId()` is non-null (it does for the Group 1 read-path tests). If `currentUser()` is not already stubbed in these two tests, add `when(securityHelper.currentUser()).thenReturn(user);` at the top of each.

- [ ] **Step 4: Run the tests to verify they fail**

Run: `mvn -Dtest=SerieServiceTest test`
Expected: FAIL — `updateSerie` ignores `steps`, so `updateSerie_withSteps_replacesStepsAndKeepsSameId` fails (stepsJson still contains the old `posId`).

- [ ] **Step 5: Implement step replacement in `SerieService`**

In `src/main/java/ch/jp/shooting/service/SerieService.java`:

(a) Extract the step-mapping logic shared by `createSerie` into a private helper. Add this method (near `stringOrNull`):

```java
    /** Serialisiert die eingehenden Step-DTOs in die persistierte stepsJson-Form. */
    private static String writeStepsJson(List<Step> steps) {
        return PlayMapper.writeSteps(
            steps.stream()
                .map(step -> new StepRecord(
                    step.getId(),
                    step.getType().getValue(),
                    stringOrNull(step.getPosId()),
                    stringOrNull(step.getAlias()),
                    stringOrNull(step.getPosId1()),
                    stringOrNull(step.getPosId2()),
                    stringOrNull(step.getAlias1()),
                    stringOrNull(step.getAlias2()),
                    step.getLetter(),
                    step.getLetter1(),
                    step.getLetter2()
                ))
                .toList()
        );
    }
```

(b) Replace the `serie.setStepsJson(PlayMapper.writeSteps( ... ));` block inside `createSerie` with:

```java
        serie.setStepsJson(writeStepsJson(request.getSteps()));
```

(c) In `updateSerie`, after the `rangeId` handling block and before the `return`, add the step replacement, then resolve labels on the response:

```java
        var steps = request.getSteps();
        if (steps != null && !steps.isEmpty()) {
            serie.setStepsJson(writeStepsJson(steps));
        }
        return withResolvedLabels(PlayMapper.toSerieResponse(serieRepository.save(serie)));
```

(Remove the old `return PlayMapper.toSerieResponse(serieRepository.save(serie));` line.)

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -Dtest=SerieServiceTest test`
Expected: PASS — including all pre-existing Group 1 tests (`createSerie_*` still pass; `createSerie` now routes through `writeStepsJson` but produces identical JSON).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/openapi.yaml \
        src/main/java/ch/jp/shooting/service/SerieService.java \
        src/test/java/ch/jp/shooting/service/SerieServiceTest.java
git commit -m "[backend] Serie PUT accepts steps for in-place edit (stable ID)

UpdateSerieRequest gains an optional steps array; when present, updateSerie
replaces stepsJson in place keeping the same Serie ID, and resolves the
response labels live. Enables editing a Serie without changing its identity.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Expose the shared alias rule on `PositionLabelResolver`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java`
- Modify: `src/main/java/ch/jp/shooting/service/SerieService.java`
- Test: `src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java`

Rationale: Both `SerieService` (Group 1) and the new `PasseService` join need the same "device alias, falling back to position label" rule. Move it to `PositionLabelResolver` as the single source of truth and have `SerieService` delegate.

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java`. Add imports `ch.jp.shooting.model.Device` if needed (check the actual `Device` package — it is `ch.jp.shooting.model.Device`).

```java
    @Test
    void aliasOf_withDeviceAlias_returnsDeviceAlias() {
        var p = new RangePosition();
        p.setLabel("A1");
        var device = new ch.jp.shooting.model.Device();
        device.setAlias("Werfer 3");
        p.setDevice(device);

        assertThat(PositionLabelResolver.aliasOf(p)).isEqualTo("Werfer 3");
    }

    @Test
    void aliasOf_withoutDevice_fallsBackToLabel() {
        var p = new RangePosition();
        p.setLabel("A1");

        assertThat(PositionLabelResolver.aliasOf(p)).isEqualTo("A1");
    }

    @Test
    void aliasOf_blankDeviceAlias_fallsBackToLabel() {
        var p = new RangePosition();
        p.setLabel("A1");
        var device = new ch.jp.shooting.model.Device();
        device.setAlias("   ");
        p.setDevice(device);

        assertThat(PositionLabelResolver.aliasOf(p)).isEqualTo("A1");
    }
```

> **Pre-check:** confirm `Device` has a no-arg constructor and `setAlias(String)` / `getAlias()`. If `RangePosition.setDevice(...)` requires a non-null or has a different setter name, adjust the fixture accordingly (read `model/RangePosition.java` and `model/Device.java`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -Dtest=PositionLabelResolverTest test`
Expected: FAIL — `PositionLabelResolver.aliasOf` does not exist (compilation error).

- [ ] **Step 3: Add `aliasOf` to `PositionLabelResolver`**

In `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java`, add the import `import ch.jp.shooting.model.RangePosition;` (already present) and add this public static method to the class body:

```java
    /**
     * Anzeige-Alias einer Position: Geräte-Alias, sonst der Positions-Buchstabe (label).
     * Einzige Quelle der Wahrheit für die Alias-Auflösung (von SerieService und PasseService genutzt).
     */
    public static String aliasOf(RangePosition position) {
        var device = position.getDevice();
        return device != null && device.getAlias() != null && !device.getAlias().isBlank()
            ? device.getAlias()
            : position.getLabel();
    }
```

- [ ] **Step 4: Delegate from `SerieService`**

In `src/main/java/ch/jp/shooting/service/SerieService.java`, delete the private `aliasOf` method (the one ending the class) and update `applyLabels` to call the resolver's version: replace the three `aliasOf(p)` / `aliasOf(p1)` / `aliasOf(p2)` calls with `PositionLabelResolver.aliasOf(p)` / `...(p1)` / `...(p2)`.

- [ ] **Step 5: Run the resolver + Serie tests**

Run: `mvn -Dtest=PositionLabelResolverTest,SerieServiceTest test`
Expected: PASS — resolver tests pass; SerieService behaviour unchanged (delegation only).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/PositionLabelResolver.java \
        src/main/java/ch/jp/shooting/service/SerieService.java \
        src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java
git commit -m "[backend] expose shared aliasOf rule on PositionLabelResolver

SerieService now delegates its device-alias-or-label rule to
PositionLabelResolver.aliasOf so PasseService can reuse the same source of truth.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Passe stores `serieId`s and joins live on read

**Files:**
- Modify: `src/main/resources/static/openapi.yaml` (`EmbeddedSerie`)
- Modify: `src/main/java/ch/jp/shooting/model/Passe.java`
- Modify: `src/main/java/ch/jp/shooting/dto/play/EmbeddedSerieRecord.java`
- Modify: `src/main/java/ch/jp/shooting/mapper/PlayMapper.java`
- Modify: `src/main/java/ch/jp/shooting/service/PasseService.java`
- Test: `src/test/java/ch/jp/shooting/service/PasseServiceTest.java` (create)

- [ ] **Step 1: Add `missing` to the `EmbeddedSerie` contract**

In `src/main/resources/static/openapi.yaml`, in the `EmbeddedSerie` schema add a `missing` property after `steps`:

```yaml
        steps:
          type: array
          items:
            $ref: '#/components/schemas/Step'
        missing:
          type: boolean
          description: >
            True when the referenced Serie no longer exists (deleted). In that
            case steps is empty and the client should render a placeholder.
```

- [ ] **Step 2: Regenerate sources**

Run: `mvn generate-sources`
Expected: BUILD SUCCESS. `EmbeddedSerie` now has `missing(Boolean)` / `getMissing()`.

- [ ] **Step 3: Write the failing tests**

Create `src/test/java/ch/jp/shooting/service/PasseServiceTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.model.Passe;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.smartground.model.CreatePasseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasseServiceTest {

    @Mock PasseRepository passeRepository;
    @Mock SerieRepository serieRepository;
    @Mock SecurityHelper securityHelper;
    @Mock PositionLabelResolver positionLabelResolver;

    @InjectMocks PasseService passeService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@example.com");
    }

    private RangePosition pos(UUID id, String label) {
        var p = new RangePosition();
        p.setId(id);
        p.setLabel(label);
        return p;
    }

    private Serie soloSerie(UUID serieId, UUID posId, String name) {
        var serie = new Serie();
        serie.setId(serieId);
        serie.setName(name);
        serie.setOwnership("user");
        serie.setOwner(user);
        serie.setCreatedAt(Instant.now());
        serie.setStepsJson(
            "[{\"id\":\"1\",\"type\":\"solo\",\"posId\":\"" + posId + "\","
            + "\"alias\":\"STALE\",\"letter\":\"OLD\"}]");
        return serie;
    }

    private Passe passeReferencing(UUID... serieIds) {
        var passe = new Passe();
        passe.setId(UUID.randomUUID());
        passe.setName("Meine Passe");
        passe.setOwner(user);
        var json = "[" + java.util.Arrays.stream(serieIds)
            .map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(",")) + "]";
        passe.setSerieIdsJson(json);
        passe.setCreatedAt(Instant.now());
        return passe;
    }

    @Test
    void getPasse_joinsSerienLiveAndResolvesLabels() {
        var serieId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var serie = soloSerie(serieId, posId, "Serie 1");
        var passe = passeReferencing(serieId);

        when(passeRepository.findById(passe.getId())).thenReturn(Optional.of(passe));
        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.findAllById(anyIterable())).thenReturn(List.of(serie));
        when(positionLabelResolver.byPosIds(anyCollection()))
            .thenReturn(Map.of(posId.toString(), pos(posId, "A1")));

        var result = passeService.getPasse(passe.getId());

        assertThat(result.getSerien()).hasSize(1);
        var embedded = result.getSerien().get(0);
        assertThat(embedded.getId()).isEqualTo(serieId);
        assertThat(embedded.getAlias()).isEqualTo("Serie 1"); // live serie name
        assertThat(embedded.getMissing()).isFalse();
        assertThat(embedded.getSteps()).hasSize(1);
        assertThat(embedded.getSteps().get(0).getLetter()).isEqualTo("A1"); // resolved, not "OLD"
    }

    @Test
    void getPasse_deletedSerie_returnsMissingPlaceholderPreservingOrder() {
        var keptId = UUID.randomUUID();
        var deletedId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var kept = soloSerie(keptId, posId, "Kept");
        var passe = passeReferencing(deletedId, keptId); // deleted first, kept second

        when(passeRepository.findById(passe.getId())).thenReturn(Optional.of(passe));
        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.findAllById(anyIterable())).thenReturn(List.of(kept)); // deletedId absent
        when(positionLabelResolver.byPosIds(anyCollection()))
            .thenReturn(Map.of(posId.toString(), pos(posId, "A1")));

        var result = passeService.getPasse(passe.getId());

        assertThat(result.getSerien()).hasSize(2);
        assertThat(result.getSerien().get(0).getMissing()).isTrue();   // order preserved
        assertThat(result.getSerien().get(0).getSteps()).isEmpty();
        assertThat(result.getSerien().get(1).getId()).isEqualTo(keptId);
        assertThat(result.getSerien().get(1).getMissing()).isFalse();
    }

    @Test
    void createPasse_storesOrderedSerieIds() {
        var serieId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var serie = soloSerie(serieId, posId, "Serie 1");

        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.existsById(serieId)).thenReturn(true);
        when(passeRepository.save(any(Passe.class))).thenAnswer(i -> i.getArgument(0));
        when(serieRepository.findAllById(anyIterable())).thenReturn(List.of(serie));
        when(positionLabelResolver.byPosIds(anyCollection()))
            .thenReturn(Map.of(posId.toString(), pos(posId, "A1")));

        var request = new CreatePasseRequest().name("P").serieIds(List.of(serieId));
        var result = passeService.createPasse(request);

        assertThat(result.getSerien()).hasSize(1);
        assertThat(result.getSerien().get(0).getId()).isEqualTo(serieId);
    }

    @Test
    void createPasse_unknownSerie_throws() {
        var serieId = UUID.randomUUID();
        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.existsById(serieId)).thenReturn(false);

        var request = new CreatePasseRequest().name("P").serieIds(List.of(serieId));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> passeService.createPasse(request))
            .isInstanceOf(ch.jp.shooting.exception.SerieNotFoundException.class);
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `mvn -Dtest=PasseServiceTest test`
Expected: FAIL — compilation errors (`Passe.setSerieIdsJson`, `PasseService` API, `EmbeddedSerie.getMissing` consumed against the not-yet-updated service).

- [ ] **Step 5: Change the `Passe` entity**

In `src/main/java/ch/jp/shooting/model/Passe.java`, replace the `serienJson` field and its accessors:

```java
    /**
     * Geordnete Liste der referenzierten Serie-IDs (JSON-Array von UUID-Strings).
     * Die Serien werden beim Lesen live verbunden (kein Snapshot mehr).
     */
    @Column(name = "serie_ids_json", columnDefinition = "TEXT", nullable = false)
    private String serieIdsJson = "[]";
```

and the accessors:

```java
    public String getSerieIdsJson() { return serieIdsJson; }
    public void setSerieIdsJson(String serieIdsJson) { this.serieIdsJson = serieIdsJson; }
```

(Delete the old `serienJson` field, `getSerienJson()`, `setSerienJson()`.)

- [ ] **Step 6: Add `missing` to `EmbeddedSerieRecord`**

Replace `src/main/java/ch/jp/shooting/dto/play/EmbeddedSerieRecord.java`:

```java
package ch.jp.shooting.dto.play;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

public record EmbeddedSerieRecord(
    UUID id,
    String alias,
    @Nullable UUID rangeId,
    @Nullable String rangeName,
    List<StepRecord> steps,
    boolean missing
) {}
```

- [ ] **Step 7: Update `PlayMapper`**

In `src/main/java/ch/jp/shooting/mapper/PlayMapper.java`:

(a) Replace the single-arg `toPasseResponse(Passe passe)` with a version that takes the pre-joined serien (it no longer reads `serienJson`):

```java
    public static PasseResponse toPasseResponse(Passe passe, List<EmbeddedSerie> serien) {
        return new PasseResponse()
            .id(passe.getId())
            .name(passe.getName())
            .serien(serien)
            .createdAt(OffsetDateTime.ofInstant(passe.getCreatedAt(), ZoneOffset.UTC))
            .ownerUsername(passe.getOwner().getEmail());
    }
```

(b) Carry `missing` in `toEmbeddedSerie`:

```java
    public static EmbeddedSerie toEmbeddedSerie(EmbeddedSerieRecord r) {
        return new EmbeddedSerie()
            .id(r.id())
            .alias(r.alias())
            .rangeId(r.rangeId())
            .rangeName(r.rangeName())
            .steps(r.steps().stream().map(PlayMapper::toStep).toList())
            .missing(r.missing());
    }
```

(c) Add serieId JSON helpers (next to `writeEmbeddedSerien` / `parseEmbeddedSerien`):

```java
    public static String writeSerieIds(List<java.util.UUID> ids) {
        return writeValue(ids.stream().map(java.util.UUID::toString).toList());
    }

    public static List<java.util.UUID> parseSerieIds(String json) {
        List<String> raw = parseList(json, new TypeReference<>() {});
        return raw.stream().map(java.util.UUID::fromString).toList();
    }
```

> Any existing call to `parseEmbeddedSerien` that fed a `StepRecord` list with the old 5-arg `EmbeddedSerieRecord` constructor now needs the 6th arg. `parseEmbeddedSerien` deserializes JSON where `missing` is absent → Jackson sets the primitive `boolean` to `false`. The only producers of that JSON are the transient Stechen snapshot (`TiebreakerService.buildSerieSnapshot`, no `missing` key) — fine.

- [ ] **Step 8: Rewrite `PasseService`**

Replace `src/main/java/ch/jp/shooting/service/PasseService.java` with:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.EmbeddedSerieRecord;
import ch.jp.shooting.dto.play.StepRecord;
import ch.jp.shooting.exception.PasseNotFoundException;
import ch.jp.shooting.exception.SerieNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Passe;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.smartground.model.CreatePasseRequest;
import ch.jp.smartground.model.EmbeddedSerie;
import ch.jp.smartground.model.PasseResponse;
import ch.jp.smartground.model.UpdatePasseRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Geschäftslogik für Passen (geordnete Referenzen auf Serien, live verbunden)
@Service
@NullMarked
public class PasseService {

    /** Anzeige-Platzhalter für eine referenzierte, aber gelöschte Serie. */
    static final String MISSING_SERIE_ALIAS = "—";

    private final PasseRepository passeRepository;
    private final SerieRepository serieRepository;
    private final SecurityHelper securityHelper;
    private final PositionLabelResolver positionLabelResolver;

    public PasseService(PasseRepository passeRepository,
                        SerieRepository serieRepository,
                        SecurityHelper securityHelper,
                        PositionLabelResolver positionLabelResolver) {
        this.passeRepository = passeRepository;
        this.serieRepository = serieRepository;
        this.securityHelper = securityHelper;
        this.positionLabelResolver = positionLabelResolver;
    }

    /** Listet alle Passen des aktuellen Nutzers. */
    public List<PasseResponse> listPassen() {
        var owner = securityHelper.currentUser();
        return passeRepository.findByOwner(owner).stream()
            .map(this::toResponse)
            .toList();
    }

    /** Erstellt eine neue Passe als geordnete Referenz auf bestehende Serien. */
    public PasseResponse createPasse(CreatePasseRequest request) {
        var owner = securityHelper.currentUser();
        // Validierung: alle referenzierten Serien müssen existieren
        request.getSerieIds().forEach(id -> {
            if (!serieRepository.existsById(id)) throw new SerieNotFoundException(id);
        });

        var passe = new Passe();
        passe.setName(request.getName());
        passe.setOwner(owner);
        passe.setSerieIdsJson(PlayMapper.writeSerieIds(request.getSerieIds()));

        return toResponse(passeRepository.save(passe));
    }

    /** Gibt eine Passe zurück – nur der Besitzer darf sie sehen. */
    public PasseResponse getPasse(UUID id) {
        var passe = passeRepository.findById(id)
            .orElseThrow(() -> new PasseNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!passe.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return toResponse(passe);
    }

    /** Benennt eine Passe um. */
    public PasseResponse updatePasse(UUID id, UpdatePasseRequest request) {
        var passe = passeRepository.findById(id)
            .orElseThrow(() -> new PasseNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!passe.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        passe.setName(request.getName());
        return toResponse(passeRepository.save(passe));
    }

    /** Löscht eine Passe (nur Besitzer). */
    public void deletePasse(UUID id) {
        var passe = passeRepository.findById(id)
            .orElseThrow(() -> new PasseNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!passe.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        passeRepository.delete(passe);
    }

    /**
     * Verbindet die referenzierten Serie-IDs einer Passe live mit den aktuellen Serien
     * und löst die Step-Labels über die aktuellen Positionen auf (Reihenfolge bleibt erhalten).
     * Eine gelöschte Serie wird als Platzhalter ({@code missing = true}, keine Steps) zurückgegeben.
     * Wiederverwendet von PlayInstanceService und SessionService.
     */
    public List<EmbeddedSerieRecord> resolveLiveSerien(Passe passe) {
        var ids = PlayMapper.parseSerieIds(passe.getSerieIdsJson());
        var serienById = serieRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Serie::getId, s -> s));

        // Steps je vorhandener Serie parsen (Reihenfolge der IDs bleibt erhalten)
        Map<UUID, List<StepRecord>> stepsBySerie = ids.stream()
            .filter(serienById::containsKey)
            .collect(Collectors.toMap(
                id -> id,
                id -> PlayMapper.parseSteps(serienById.get(id).getStepsJson()),
                (a, b) -> a,
                LinkedHashMap::new));

        // Alle Positions-IDs in EINEM Lookup auflösen
        var posIds = stepsBySerie.values().stream()
            .flatMap(List::stream)
            .flatMap(s -> Stream.of(s.posId(), s.posId1(), s.posId2()))
            .filter(Objects::nonNull)
            .toList();
        var positions = positionLabelResolver.byPosIds(posIds);

        return ids.stream().map(id -> {
            var serie = serienById.get(id);
            if (serie == null) {
                return new EmbeddedSerieRecord(id, MISSING_SERIE_ALIAS, null, null, List.of(), true);
            }
            var resolved = stepsBySerie.get(id).stream()
                .map(step -> applyLabels(step, positions))
                .toList();
            var range = serie.getRange();
            return new EmbeddedSerieRecord(
                serie.getId(),
                serie.getName(),
                range != null ? range.getId() : null,
                range != null ? range.getName() : null,
                resolved,
                false);
        }).toList();
    }

    private PasseResponse toResponse(Passe passe) {
        var serien = resolveLiveSerien(passe).stream()
            .map(PlayMapper::toEmbeddedSerie)
            .toList();
        return PlayMapper.toPasseResponse(passe, serien);
    }

    /** Erzeugt einen Step mit live aufgelösten letter/alias-Werten. */
    private static StepRecord applyLabels(StepRecord s, Map<String, RangePosition> positions) {
        var p  = s.posId()  != null ? positions.get(s.posId())  : null;
        var p1 = s.posId1() != null ? positions.get(s.posId1()) : null;
        var p2 = s.posId2() != null ? positions.get(s.posId2()) : null;
        return new StepRecord(
            s.id(),
            s.type(),
            s.posId(),
            p  != null ? PositionLabelResolver.aliasOf(p)  : null,
            s.posId1(),
            s.posId2(),
            p1 != null ? PositionLabelResolver.aliasOf(p1) : null,
            p2 != null ? PositionLabelResolver.aliasOf(p2) : null,
            p  != null ? p.getLabel()  : null,
            p1 != null ? p1.getLabel() : null,
            p2 != null ? p2.getLabel() : null
        );
    }
}
```

> **Note:** `@Nullable` import is retained for symmetry but may be unused — if the compiler/lint flags it, remove the `import org.jspecify.annotations.Nullable;` line. Do not leave an unused import (project convention).

- [ ] **Step 9: Run the Passe tests**

Run: `mvn -Dtest=PasseServiceTest test`
Expected: PASS (4 tests).

- [ ] **Step 10: Commit**

```bash
git add src/main/resources/static/openapi.yaml \
        src/main/java/ch/jp/shooting/model/Passe.java \
        src/main/java/ch/jp/shooting/dto/play/EmbeddedSerieRecord.java \
        src/main/java/ch/jp/shooting/mapper/PlayMapper.java \
        src/main/java/ch/jp/shooting/service/PasseService.java \
        src/test/java/ch/jp/shooting/service/PasseServiceTest.java
git commit -m "[backend] Passe stores ordered serieIds, joins Serien live on read

Passe.serienJson (embedded snapshot) becomes serie_ids_json (ordered UUID list).
PasseService.resolveLiveSerien joins the referenced Serien live, resolves step
labels via PositionLabelResolver, and marks deleted Serien missing=true.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Route Passe consumers through the live join

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/PlayInstanceService.java`
- Modify: `src/main/java/ch/jp/shooting/service/SessionService.java`
- Test: `src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java`
- Test: `src/test/java/ch/jp/shooting/service/SessionServiceFinishGuardTest.java`, `SessionServiceTransitionTest.java`

- [ ] **Step 1: Write the failing `PlayInstanceService` test**

In `src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java`:

(a) Add the new mock field after the existing mocks:

```java
    @Mock PasseService passeService;
```

(b) Add a test that `startPasseInstance` builds blocks from the live join:

```java
    @Test
    void startPasseInstance_buildsBlocksFromLiveResolvedSerien() {
        var passeId = UUID.randomUUID();
        var passe = new ch.jp.shooting.model.Passe();
        passe.setId(passeId);
        passe.setName("Passe X");

        var serieId = UUID.randomUUID();
        var liveSerien = List.of(new ch.jp.shooting.dto.play.EmbeddedSerieRecord(
            serieId, "Serie 1", null, null, List.of(), false));

        when(passeRepository.findById(passeId)).thenReturn(java.util.Optional.of(passe));
        when(passeService.resolveLiveSerien(passe)).thenReturn(liveSerien);
        when(securityHelper.currentUser()).thenReturn(mock(User.class));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = new ch.jp.smartground.model.StartPasseInstanceRequest()
            .passeId(passeId)
            .players(List.of(new PlayerRef()
                .id(UUID.randomUUID().toString())
                .type(PlayerRef.TypeEnum.USER)
                .displayName("Anna")));

        service.startPasseInstance(request);

        ArgumentCaptor<PlayInstance> captor = ArgumentCaptor.forClass(PlayInstance.class);
        verify(playInstanceRepository).save(captor.capture());
        var blocks = PlayMapper.parseBlocks(captor.getValue().getStateJson());
        assertEquals(1, blocks.size());
        assertEquals("Serie 1", blocks.get(0).serieAlias());
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -Dtest=PlayInstanceServiceTest test`
Expected: FAIL — compilation error (`passeService` mock unused by the not-yet-updated constructor; `startPasseInstance` still calls `parseEmbeddedSerien(passe.getSerienJson())` which no longer compiles since `getSerienJson` is gone).

- [ ] **Step 3: Update `PlayInstanceService`**

In `src/main/java/ch/jp/shooting/service/PlayInstanceService.java`:

(a) Add the `PasseService` dependency to the constructor:

```java
    private final PlayInstanceRepository playInstanceRepository;
    private final PasseRepository passeRepository;
    private final PasseService passeService;
    private final SecurityHelper securityHelper;

    public PlayInstanceService(PlayInstanceRepository playInstanceRepository,
                               PasseRepository passeRepository,
                               PasseService passeService,
                               SecurityHelper securityHelper) {
        this.playInstanceRepository = playInstanceRepository;
        this.passeRepository = passeRepository;
        this.passeService = passeService;
        this.securityHelper = securityHelper;
    }
```

(b) Replace the body of `startPasseInstance`:

```java
    public PlayInstanceResponse startPasseInstance(StartPasseInstanceRequest request) {
        var passe = passeRepository.findById(request.getPasseId())
            .orElseThrow(() -> new PasseNotFoundException(request.getPasseId()));
        var serien = passeService.resolveLiveSerien(passe);
        return buildAndSaveInstance(passe.getId(), passe.getName(), serien, request.getPlayers());
    }
```

(The `import ch.jp.shooting.mapper.PlayMapper;` is still used elsewhere; leave it. `buildAndSaveInstance` is unchanged.)

- [ ] **Step 4: Update `SessionService`**

In `src/main/java/ch/jp/shooting/service/SessionService.java`:

(a) Add a `PasseService` field and constructor parameter (append it last to minimise reordering):

```java
            PasseRepository passeRepository,
            ObjectMapper objectMapper,
            TiebreakerService tiebreakerService,
            PasseService passeService) {
```

and in the body:

```java
        this.passeService = passeService;
```

plus the field declaration alongside the others:

```java
    private final PasseService passeService;
```

(b) In `mapToApiSessionResponse`, replace the live-snapshot join:

```java
                                    List<ch.jp.smartground.model.EmbeddedSerie> serien =
                                        passeService.resolveLiveSerien(passe).stream()
                                            .map(PlayMapper::toEmbeddedSerie)
                                            .collect(Collectors.toList());
                                    ref.setSerien(serien);
```

- [ ] **Step 5: Update the SessionService unit-test constructors**

In `SessionServiceFinishGuardTest.java` and `SessionServiceTransitionTest.java`:

(a) Add a mock field next to the others:

```java
    @Mock PasseService passeService;
```

(b) Add `passeService` as the final argument of each `new SessionService(...)` call (after `tiebreakerService`).

- [ ] **Step 6: Run the affected tests**

Run: `mvn -Dtest=PlayInstanceServiceTest,SessionServiceFinishGuardTest,SessionServiceTransitionTest test`
Expected: PASS — including the two pre-existing `startSerieInstance` tests (Stechen path unchanged).

- [ ] **Step 7: Run the full backend suite**

Run: `mvn test`
Expected: PASS — confirms no other consumer of `Passe.getSerienJson()` / `PlayMapper.toPasseResponse(Passe)` / the `SessionService`/`PlayInstanceService` constructors remains broken, and `@SpringBootTest` wiring picks up the new `PasseService` constructor args automatically.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/PlayInstanceService.java \
        src/main/java/ch/jp/shooting/service/SessionService.java \
        src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java \
        src/test/java/ch/jp/shooting/service/SessionServiceFinishGuardTest.java \
        src/test/java/ch/jp/shooting/service/SessionServiceTransitionTest.java
git commit -m "[backend] route Passe consumers through live serien join

PlayInstanceService.startPasseInstance and SessionService now resolve a Passe's
serien via PasseService.resolveLiveSerien instead of reading the removed embedded
snapshot, so a Passe behaves as a live reference everywhere it is consumed.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Record the design decisions in backend CLAUDE.md

**Files:**
- Modify: `../smart-ground-backend/CLAUDE.md`

- [ ] **Step 1: Update the domain + schema sections**

In `CLAUDE.md`:

(a) In the **Template hierarchy** block, change the Passe line from
`└─ Passe  ─────────────────▶  list of Serie snapshots (JSON)` to
`└─ Passe  ─────────────────▶  ordered list of Serie IDs (live-joined)`.

(b) Under the `Passe` bullet, replace "Named collection of Serie snapshots. Owner-scoped." with:
"Named, **ordered reference** to existing Serien (`serie_ids_json`). Serien are joined live on read (`PasseService.resolveLiveSerien`), with step labels resolved from current positions via `PositionLabelResolver`; a deleted Serie resolves to a placeholder (`missing=true`). Owner-scoped."

(c) In the **Serie / Passe / Training / Play tables** schema block, change
`passen   id, name, owner_id→users, serien_json TEXT, created_at`
to
`passen   id, name, owner_id→users, serie_ids_json TEXT, created_at`.

(d) Add a short decision note under the Serie bullet: "Serie `PUT` (`UpdateSerieRequest`) accepts an optional `steps` array → in-place step edit keeping the stable Serie ID; this is why a Passe can safely reference Serien by ID."

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "[backend] document Passe live-reference + Serie PUT steps (Group 2)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Manual verification (end of Group 2)

1. Start backend (`mvn spring-boot:run -Dspring-boot.run.profiles=postgres`) — Hibernate adds the `serie_ids_json` column. (Existing Passen rows are orphaned; reset deferred to Group 5.)
2. Create a range with position `A`, build Serie S1 using position `A`, create a Passe referencing S1.
3. `GET /api/passen/{id}` → the embedded serie shows step letter `A`.
4. Rename position `A` → `A1`. `GET /api/passen/{id}` again → step letter is now `A1` (live, not snapshot).
5. `PUT /api/serien/{S1}` with a new `steps` array → response keeps the same Serie ID; `GET /api/passen/{id}` reflects the edited steps (no Passe "repair" needed).
6. Delete S1 → `GET /api/passen/{id}` returns the serie entry with `missing: true` and empty steps; no 500.

---

## Self-review checklist (to complete during execution)

- **Spec coverage (Group 2):**
  - Passe → ordered `serieId` list joined live ✅ (Task 3).
  - Serie `PUT` accepts steps, stable ID ✅ (Task 1).
  - Deleted referenced Serie → placeholder ✅ (Task 3, `missing=true`).
  - `PositionLabelResolver` reused for posId→label ✅ (Task 2 + Task 3).
- **Placeholders:** none — every step has concrete code/commands.
- **Type consistency:** `Passe.getSerieIdsJson()` ↔ `PlayMapper.parseSerieIds/writeSerieIds` (UUID strings); `EmbeddedSerieRecord` 6-arg constructor used identically in `PasseService.resolveLiveSerien` and the `PlayInstanceServiceTest` fixture; `EmbeddedSerie.missing` set by `toEmbeddedSerie`, read by `getMissing()` in tests; `StepRecord` 11-arg order matches `writeStepsJson` and `applyLabels`.
- **Out of scope (later groups):** completed-results snapshot (Group 3); frontend consumption + `updateSerie` in-place PUT wiring (Group 4); migration/reset of existing Passen + active-session propagation tests (Group 5).
- **Pre-execution reads to confirm fixtures:** `model/Device.java` (alias accessors), `model/RangePosition.java` (`setDevice`), and the current top-of-file mocks/helpers in `SerieServiceTest.java` (ensure `securityHelper.currentUser()` is stubbed where the new `updateSerie` tests need it).
</content>
</invoke>

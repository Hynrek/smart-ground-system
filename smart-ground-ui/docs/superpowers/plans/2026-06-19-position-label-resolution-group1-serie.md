# Position Label Resolution — Group 1: Live-resolve Serie step labels

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a Serie is read, its step `letter`/`alias` are resolved live from the current Position (by `posId`), so renaming a position propagates to standalone Serie views instead of showing a frozen snapshot.

**Architecture:** Resolution happens in the backend on the **read path only** (`SerieService.listSerien` / `getSerie`). A new reusable `PositionLabelResolver` batch-loads `RangePosition` entities by their UUID `posId`s; `SerieService` overrides each step's `letter`/`alias` from the resolved positions after `PlayMapper` builds the response. Create/update responses are left unchanged (at write time the snapshot equals the current label). The frontend already keeps `posId` on every step and renders `step.letter`, so it needs no change for this group — the values it receives simply become live.

**Tech Stack:** Java 25, Spring Boot 4, JPA, JUnit 5 + Mockito + AssertJ. Sibling repo: `../smart-ground-backend`.

**Spec:** `docs/superpowers/specs/2026-06-19-position-label-resolution-design.md` (Group 1).

**Scope note (deliberate deviation from spec):** The spec's Group 1 also suggested *dropping* stored `letter`/`alias` from `stepsJson`. This plan **keeps** the stored fields (they become ignored on read) because dropping them changes `createSerie` serialization and the existing `createSerie_pairStep_preservesLetter1AndLetter2RoundTrip` round-trip test for no functional gain — the read-path resolution already fixes the bug. Removing the dead stored fields is deferred to a later cleanup.

---

## File Structure

- Create: `../smart-ground-backend/src/main/java/ch/jp/shooting/service/PositionLabelResolver.java` — batch posId(UUID) → `RangePosition` lookup; one responsibility.
- Modify: `../smart-ground-backend/src/main/java/ch/jp/shooting/service/SerieService.java` — inject the resolver; apply resolved labels on read paths.
- Create: `../smart-ground-backend/src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java` — resolver unit tests.
- Modify: `../smart-ground-backend/src/test/java/ch/jp/shooting/service/SerieServiceTest.java` — add read-path resolution tests; wire the new mock.

All commands run from `../smart-ground-backend` unless noted. Build tool: `./mvnw`.

---

### Task 1: `PositionLabelResolver` — batch posId → RangePosition

**Files:**
- Create: `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java`
- Test: `src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.repository.RangePositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionLabelResolverTest {

    @Mock RangePositionRepository positionRepository;
    @InjectMocks PositionLabelResolver resolver;

    private RangePosition position(UUID id, String label) {
        var p = new RangePosition();
        p.setId(id);
        p.setLabel(label);
        return p;
    }

    @Test
    void byPosIds_mapsUuidStringsToEntities() {
        var id = UUID.randomUUID();
        when(positionRepository.findAllById(anyIterable())).thenReturn(List.of(position(id, "A1")));

        var result = resolver.byPosIds(List.of(id.toString()));

        assertThat(result).containsKey(id.toString());
        assertThat(result.get(id.toString()).getLabel()).isEqualTo("A1");
    }

    @Test
    void byPosIds_ignoresNullBlankAndInvalidUuids() {
        var result = resolver.byPosIds(java.util.Arrays.asList(null, "", "not-a-uuid"));
        assertThat(result).isEmpty();
    }

    @Test
    void byPosIds_emptyInput_returnsEmptyWithoutQuery() {
        var result = resolver.byPosIds(List.of());
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=PositionLabelResolverTest test`
Expected: FAIL — `PositionLabelResolver` does not exist (compilation error).

- [ ] **Step 3: Write the minimal implementation**

`src/main/java/ch/jp/shooting/service/PositionLabelResolver.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.repository.RangePositionRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Löst Positions-IDs (UUID-Strings aus Step.posId) auf die aktuellen
 * RangePosition-Entitäten auf. Quelle der Wahrheit für Buchstabe (label)
 * und Geräte-Alias zur Anzeige-Zeit.
 */
@Service
@NullMarked
public class PositionLabelResolver {

    private final RangePositionRepository positionRepository;

    public PositionLabelResolver(RangePositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    /**
     * Lädt die Positionen für die gegebenen posId-Strings (UUIDs) als Map
     * posIdString → RangePosition. Null/leer/ungültige IDs werden ignoriert;
     * unbekannte IDs fehlen schlicht in der Map (Anzeige fällt auf Platzhalter zurück).
     */
    public Map<String, RangePosition> byPosIds(Collection<String> posIds) {
        var ids = posIds.stream()
            .filter(s -> s != null && !s.isBlank())
            .map(PositionLabelResolver::parseOrNull)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (ids.isEmpty()) return Map.of();
        return positionRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(p -> p.getId().toString(), p -> p));
    }

    private static @Nullable UUID parseOrNull(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -q -Dtest=PositionLabelResolverTest test`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/PositionLabelResolver.java \
        src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java
git commit -m "[backend] add PositionLabelResolver for posId -> live RangePosition

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Resolve Serie step labels on the read path

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/SerieService.java`
- Test: `src/test/java/ch/jp/shooting/service/SerieServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `SerieServiceTest.java`. First add the new mock field alongside the existing mocks (after line 33, `@Mock SecurityHelper securityHelper;`):

```java
    @Mock ch.jp.shooting.repository.RangePositionRepository positionRepository;
    @Mock PositionLabelResolver positionLabelResolver;
```

Note: keep `@InjectMocks SerieService serieService;`. Mockito injects the new `PositionLabelResolver` mock into the constructor (see Task 2 Step 3).

Add these tests to the class body:

```java
    private ch.jp.shooting.model.RangePosition pos(UUID id, String label) {
        var p = new ch.jp.shooting.model.RangePosition();
        p.setId(id);
        p.setLabel(label);
        return p;
    }

    /** A user-owned serie with one solo step whose posId references the given position id,
     *  but whose stored letter is intentionally stale. */
    private Serie soloSerieWithStaleLetter(UUID posId) {
        var serie = new Serie();
        serie.setId(UUID.randomUUID());
        serie.setName("Solo");
        serie.setOwnership("user");
        serie.setStepsJson(
            "[{\"id\":\"1\",\"type\":\"solo\",\"posId\":\"" + posId + "\","
            + "\"alias\":\"STALE_ALIAS\",\"letter\":\"OLD\"}]");
        serie.setCreatedAt(Instant.now());
        serie.setOwner(user);
        return serie;
    }

    @Test
    void listSerien_resolvesStepLetterFromCurrentPosition() {
        var posId = UUID.randomUUID();
        var serie = soloSerieWithStaleLetter(posId);
        when(securityHelper.isAdminOrOwner()).thenReturn(false);
        when(serieRepository.findByOwnerOrPublishedRange(user)).thenReturn(List.of(serie));
        when(positionLabelResolver.byPosIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(java.util.Map.of(posId.toString(), pos(posId, "A1")));

        var result = serieService.listSerien(null, null);

        assertThat(result.get(0).getSteps().get(0).getLetter()).isEqualTo("A1");
    }

    @Test
    void getSerie_unknownPosition_resolvesLetterToNull() {
        var posId = UUID.randomUUID();
        var serie = soloSerieWithStaleLetter(posId);
        when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));
        when(securityHelper.isAdminOrOwner()).thenReturn(true);
        when(positionLabelResolver.byPosIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(java.util.Map.of()); // position was deleted

        var result = serieService.getSerie(serie.getId());

        assertThat(result.getSteps().get(0).getLetter()).isNull();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw -q -Dtest=SerieServiceTest test`
Expected: FAIL — `getLetter()` returns `"OLD"` (stale) for the first test; compilation may also require the constructor change. The existing tests must still be present.

- [ ] **Step 3: Write the implementation**

In `SerieService.java`:

(a) Add imports near the top (after existing imports):

```java
import ch.jp.shooting.model.RangePosition;
import ch.jp.smartground.model.SerieResponse;
import ch.jp.smartground.model.Step;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
```

(`SerieResponse` is already imported — do not duplicate it.)

(b) Add the field and extend the constructor:

```java
    private final SerieRepository serieRepository;
    private final RangeRepository rangeRepository;
    private final SecurityHelper securityHelper;
    private final PositionLabelResolver positionLabelResolver;

    public SerieService(SerieRepository serieRepository,
                        RangeRepository rangeRepository,
                        SecurityHelper securityHelper,
                        PositionLabelResolver positionLabelResolver) {
        this.serieRepository = serieRepository;
        this.rangeRepository = rangeRepository;
        this.securityHelper = securityHelper;
        this.positionLabelResolver = positionLabelResolver;
    }
```

(c) In `listSerien`, replace the final return line

```java
        return result.stream().map(PlayMapper::toSerieResponse).toList();
```

with:

```java
        return withResolvedLabels(result.stream().map(PlayMapper::toSerieResponse).toList());
```

(d) In `getSerie`, replace

```java
        return PlayMapper.toSerieResponse(serie);
```

with:

```java
        return withResolvedLabels(PlayMapper.toSerieResponse(serie));
```

(e) Add the private helpers at the end of the class (before the closing brace, after `stringOrNull`):

```java
    /** Resolves one serie's step letters/aliases live from current positions. */
    private SerieResponse withResolvedLabels(SerieResponse response) {
        return withResolvedLabels(List.of(response)).get(0);
    }

    /** Batch-resolves step letters/aliases for many serien with a single position lookup. */
    private List<SerieResponse> withResolvedLabels(List<SerieResponse> responses) {
        var posIds = responses.stream()
            .flatMap(r -> r.getSteps().stream())
            .flatMap(s -> Stream.of(s.getPosId(), s.getPosId1(), s.getPosId2()))
            .filter(Objects::nonNull)
            .toList();
        var positions = positionLabelResolver.byPosIds(posIds);
        responses.forEach(r -> r.getSteps().forEach(step -> applyLabels(step, positions)));
        return responses;
    }

    private static void applyLabels(Step step, Map<String, RangePosition> positions) {
        if (step.getPosId() != null) {
            var p = positions.get(step.getPosId());
            step.letter(p != null ? p.getLabel() : null);
            step.alias(p != null ? aliasOf(p) : null);
        }
        if (step.getPosId1() != null) {
            var p1 = positions.get(step.getPosId1());
            step.letter1(p1 != null ? p1.getLabel() : null);
            step.alias1(p1 != null ? aliasOf(p1) : null);
        }
        if (step.getPosId2() != null) {
            var p2 = positions.get(step.getPosId2());
            step.letter2(p2 != null ? p2.getLabel() : null);
            step.alias2(p2 != null ? aliasOf(p2) : null);
        }
    }

    private static String aliasOf(RangePosition position) {
        var device = position.getDevice();
        return device != null && device.getAlias() != null && !device.getAlias().isBlank()
            ? device.getAlias()
            : position.getLabel();
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw -q -Dtest=SerieServiceTest test`
Expected: PASS — including all pre-existing tests (the `createSerie_*` tests are unaffected because create does not resolve; the field-mapping tests use empty steps so `withResolvedLabels` is a no-op on them).

- [ ] **Step 5: Run the full backend test suite**

Run: `./mvnw -q test`
Expected: PASS — confirms no other caller of `SerieService`'s constructor broke (e.g. existing `@SpringBootTest` wiring picks up the new bean automatically).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/SerieService.java \
        src/test/java/ch/jp/shooting/service/SerieServiceTest.java
git commit -m "[backend] resolve Serie step letters live from current positions

Serie read paths (list/get) now override each step's letter/alias from the
current RangePosition by posId, so renaming a position propagates instead of
showing the frozen snapshot. Deleted position -> null letter (UI placeholder).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Frontend — tolerate null letters (placeholder), verify propagation

**Files:**
- Modify: `src/components/SerieDrawer.vue` (UI repo, `smart-ground-ui`)
- Test: `src/components/__tests__/SerieDrawer.test.js`

Rationale: After Task 2 a deleted position yields `letter: null`. The display helper `stepLabel` (`SerieDrawer.vue:290-295`) interpolates `step.letter` directly, rendering `"null — ..."` or `"undefined"`. Render a placeholder instead. (No store/service change is needed — `fromApiStep` already passes `letter` through, and the values are now live.)

- [ ] **Step 1: Write the failing test**

Add to `src/components/__tests__/SerieDrawer.test.js` (UI repo). Match the file's existing import/mount style; this test asserts the placeholder for a null-letter solo step:

```javascript
it('renders a placeholder for a step whose position was deleted (null letter)', async () => {
  const serie = {
    id: 's1',
    name: 'Solo',
    rangeId: 'r1',
    rangeName: 'Stand 1',
    steps: [{ id: 1, type: 'solo', positionId: 'p1', alias: null, letter: null }],
    published: false,
  };
  const wrapper = mount(SerieDrawer, {
    props: { open: true, mode: 'edit', serie },
    global: { stubs: { Teleport: true } },
  });
  await wrapper.vm.$nextTick();
  expect(wrapper.text()).toContain('—');
  expect(wrapper.text()).not.toContain('null');
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run (UI repo): `npm run test -- src/components/__tests__/SerieDrawer.test.js`
Expected: FAIL — output contains `"null — "` rather than the placeholder.

- [ ] **Step 3: Implement the placeholder**

In `src/components/SerieDrawer.vue`, replace `stepLabel` (lines 290-295):

```javascript
const stepLabel = (step) => {
  const dash = (v) => v ?? '—';
  if (step.type === 'solo' || step.type === 'raffale') {
    return `${dash(step.letter)} — ${dash(step.alias)}`;
  }
  return `${dash(step.letter1)}+${dash(step.letter2)} — ${dash(step.alias1)} & ${dash(step.alias2)}`;
};
```

- [ ] **Step 4: Run the test to verify it passes**

Run (UI repo): `npm run test -- src/components/__tests__/SerieDrawer.test.js`
Expected: PASS.

- [ ] **Step 5: Run lint + the related test files**

Run (UI repo): `npm run lint:check && npm run test -- src/components/__tests__/SerieDrawer.test.js`
Expected: no lint errors; tests PASS.

- [ ] **Step 6: Commit**

```bash
git add src/components/SerieDrawer.vue src/components/__tests__/SerieDrawer.test.js
git commit -m "[ui] SerieDrawer: render placeholder for steps with no resolved position

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Manual verification (end of Group 1)

1. Start backend (`./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres`) and UI (`npm run dev`).
2. As admin, create a range with position `A`, build a Serie using position `A`.
3. Rename position `A` → `A1` in the range detail view.
4. Reopen / reload the Serie list (Platz-Serie list / SerieDrawer): the step now shows `A1`, not `A`.
5. Delete the position: the step shows `—`, not a broken/`null` label.

---

## Self-review checklist (done)

- **Spec coverage (Group 1):** live-resolve Serie step letters ✅ (Task 2); deleted-position placeholder ✅ (Task 2 null + Task 3 UI). Stored-field drop intentionally deferred (see Scope note).
- **Placeholders:** none — all steps have concrete code/commands.
- **Type consistency:** `byPosIds(Collection<String>)` returns `Map<String, RangePosition>`, consumed identically in `SerieService.applyLabels`; `RangePosition.getId()/getLabel()/getDevice()` and `Device.getAlias()` all verified against source; generated `Step` fluent setters `.letter/.alias/.letter1/.letter2/.alias1/.alias2` match `PlayMapper.toStep` usage.
- **Out of scope for Group 1 (later groups):** Passe live-reference (Group 2), completed-results snapshot (Group 3), frontend completed-results read change (Group 4), migration/cross-stack tests (Group 5).

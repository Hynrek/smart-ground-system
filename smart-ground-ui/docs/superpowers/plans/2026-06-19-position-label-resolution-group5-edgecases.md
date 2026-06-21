# Position Label Resolution — Group 5: Edge cases, active-session resolution, migration & tests

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the cross-stack feature: (1) active in-progress play sessions resolve step labels live on read (a reload reflects a rename); (2) the deleted-position placeholder renders consistently as `—` with a German aria-label; (3) document the pre-v1.0 reset of existing Passen; (4) lock the cross-stack behaviours (propagation, isolation, deleted-position-no-crash, stable Serie ID) under test.

**Architecture:** Backend — the generated-`Step` label resolution (currently private in `SerieService`) is promoted to a **static** helper on `PositionLabelResolver` so `PlayInstanceService` reuses it; callers still call the mockable instance `byPosIds`, then apply. `PlayInstanceService.getPlayInstance`/`listPlayInstances` re-resolve each running block's step letters from current positions (display-only; `state_json` is never mutated). Frontend — `stepNotation` (the single source of truth for step display) renders `—` instead of `?` for a missing position letter/alias, and a shared `stepAriaLabel` describes the step in German, naming a deleted position.

**Tech Stack:** Backend: Java 25, Spring Boot 4, JUnit 5 + Mockito + AssertJ. Frontend: Vue 3, Vitest. Both repos on `master`.

**Spec:** `docs/superpowers/specs/2026-06-19-position-label-resolution-design.md` (Group 5).

**Settled scope (confirmed with user):** Include active-session live re-resolution. **Exclude** the Passe `missing`-serie UI placeholder (the `missing` flag stays on the API for a later follow-up; an empty/deleted serie simply renders no step chips, no crash).

---

## Build / environment notes

- Backend: PowerShell, `../smart-ground-backend`, `JAVA_HOME=C:\Users\hynre\.jdks\jbr-25.0.2`, system `mvn` (the `./mvnw` wrapper is a broken stub). Quote `-Dtest` lists: `mvn -q "-Dtest=A,B" test`. Don't pipe `2>&1` on `mvn` in PS 5.1.
- Frontend: `smart-ground-ui`; `npm run test -- --run [file]`; `npm run lint:check`.
- Generated `Step` getters (`getPosId`/`getPosId1`/`getPosId2`) return `JsonNullable<String>`; `getLetter*` return plain `String`; fluent setters `letter/alias/letter1/...` take plain values.

---

## File Structure

**Backend — modify:**
- `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java` — add static `applyResolvedLabels(Step, Map)` + `posIdsOf(Collection<Step>)` (generated-Step resolution hub).
- `src/main/java/ch/jp/shooting/service/SerieService.java` — delegate its private `applyLabels` to the resolver's static helper.
- `src/main/java/ch/jp/shooting/service/PlayInstanceService.java` — inject `PositionLabelResolver`; re-resolve block step labels in `getPlayInstance`/`listPlayInstances`.
- `CLAUDE.md` — migration/reset note.

**Backend — test:**
- `src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java` — add the `PositionLabelResolver` mock + active re-resolution tests.

**Frontend — modify:**
- `src/constants/stepModes.js` — `stepNotation` placeholder `—`; new `stepAriaLabel`.
- `src/components/SerieDrawer.vue` — aria-label on the step row.

**Frontend — test:**
- `src/constants/__tests__/stepModes.test.js` — **create** — placeholder + aria tests.

---

### Task 1: Promote generated-`Step` resolution to `PositionLabelResolver`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java`
- Modify: `src/main/java/ch/jp/shooting/service/SerieService.java`
- Test: `src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java`

Rationale: `SerieService.applyLabels` (generated-`Step`) and the soon-to-be-added `PlayInstanceService` re-resolution are the same operation. Move the apply to `PositionLabelResolver` as **static** helpers so callers keep calling the mockable instance `byPosIds` (preserving every existing test's stubbing convention) but share the apply.

- [ ] **Step 1: Write the failing tests**

Add to `src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java`. Import `ch.jp.smartground.model.Step`, `ch.jp.smartground.model.StepType`, `java.util.Map`.

```java
    @Test
    void posIdsOf_collectsAllNonNullPositionIds() {
        var solo = new ch.jp.smartground.model.Step().id("1")
            .type(ch.jp.smartground.model.StepType.SOLO).posId("p1");
        var pair = new ch.jp.smartground.model.Step().id("2")
            .type(ch.jp.smartground.model.StepType.PAIR).posId1("p2").posId2("p3");

        var ids = PositionLabelResolver.posIdsOf(java.util.List.of(solo, pair));

        assertThat(ids).containsExactlyInAnyOrder("p1", "p2", "p3");
    }

    @Test
    void applyResolvedLabels_overridesSoloLetterAndAlias() {
        var id = UUID.randomUUID();
        var step = new ch.jp.smartground.model.Step().id("1")
            .type(ch.jp.smartground.model.StepType.SOLO).posId(id.toString()).letter("OLD");

        PositionLabelResolver.applyResolvedLabels(step,
            java.util.Map.of(id.toString(), position(id, "A1")));

        assertThat(step.getLetter()).isEqualTo("A1");
        assertThat(step.getAlias()).isEqualTo("A1"); // no device -> alias falls back to label
    }

    @Test
    void applyResolvedLabels_missingPosition_nullsLetterAndAlias() {
        var step = new ch.jp.smartground.model.Step().id("1")
            .type(ch.jp.smartground.model.StepType.SOLO).posId(UUID.randomUUID().toString()).letter("OLD");

        PositionLabelResolver.applyResolvedLabels(step, java.util.Map.of()); // deleted

        assertThat(step.getLetter()).isNull();
        assertThat(step.getAlias()).isNull();
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -Dtest=PositionLabelResolverTest test`
Expected: FAIL — `posIdsOf` / `applyResolvedLabels(Step, Map)` do not exist (compilation error).

- [ ] **Step 3: Add the static helpers to `PositionLabelResolver`**

In `src/main/java/ch/jp/shooting/service/PositionLabelResolver.java`, add imports:

```java
import ch.jp.smartground.model.Step;
import org.openapitools.jackson.nullable.JsonNullable;
```

Add these methods to the class body:

```java
    /** Sammelt alle nicht-null posId/posId1/posId2 aus den (generierten) Steps. */
    public static List<String> posIdsOf(Collection<Step> steps) {
        return steps.stream()
            .flatMap(s -> Stream.of(jn(s.getPosId()), jn(s.getPosId1()), jn(s.getPosId2())))
            .filter(Objects::nonNull)
            .toList();
    }

    /** Überschreibt letter/alias eines (generierten) Steps live aus der Positions-Map. */
    public static void applyResolvedLabels(Step step, Map<String, RangePosition> positions) {
        var posId = jn(step.getPosId());
        if (posId != null) {
            var p = positions.get(posId);
            step.letter(p != null ? p.getLabel() : null);
            step.alias(p != null ? aliasOf(p) : null);
        }
        var posId1 = jn(step.getPosId1());
        if (posId1 != null) {
            var p1 = positions.get(posId1);
            step.letter1(p1 != null ? p1.getLabel() : null);
            step.alias1(p1 != null ? aliasOf(p1) : null);
        }
        var posId2 = jn(step.getPosId2());
        if (posId2 != null) {
            var p2 = positions.get(posId2);
            step.letter2(p2 != null ? p2.getLabel() : null);
            step.alias2(p2 != null ? aliasOf(p2) : null);
        }
    }

    private static @Nullable String jn(@Nullable JsonNullable<String> v) {
        return v != null && v.isPresent() ? v.get() : null;
    }
```

- [ ] **Step 4: Delegate from `SerieService`**

In `src/main/java/ch/jp/shooting/service/SerieService.java`, replace the `withResolvedLabels(List<SerieResponse>)` body and delete the private `applyLabels`:

```java
    private List<SerieResponse> withResolvedLabels(List<SerieResponse> responses) {
        var steps = responses.stream().flatMap(r -> r.getSteps().stream()).toList();
        var positions = positionLabelResolver.byPosIds(PositionLabelResolver.posIdsOf(steps));
        steps.forEach(step -> PositionLabelResolver.applyResolvedLabels(step, positions));
        return responses;
    }
```

Delete the entire private `applyLabels(Step, Map<String, RangePosition>)` method. Then remove any now-unused imports in `SerieService` (`Step`, `RangePosition`, `Map`, `Objects`, `Stream` may become unused — **verify each**: `Step` is still used by `writeStepsJson`; `Map`/`Objects`/`Stream`/`RangePosition` are likely now unused → remove them; do not leave unused imports).

- [ ] **Step 5: Run resolver + Serie tests**

Run: `mvn -q "-Dtest=PositionLabelResolverTest,SerieServiceTest" test`
Expected: PASS — `SerieServiceTest` still stubs `byPosIds` (unchanged); resolution now flows through the shared static apply.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/PositionLabelResolver.java \
        src/main/java/ch/jp/shooting/service/SerieService.java \
        src/test/java/ch/jp/shooting/service/PositionLabelResolverTest.java
git commit -m "[backend] promote generated-Step resolution to PositionLabelResolver

Static posIdsOf/applyResolvedLabels move SerieService's per-Step apply into the
resolver hub so PlayInstanceService can reuse it; callers still call the mockable
byPosIds then apply.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Active sessions re-resolve block labels on read (propagation)

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/PlayInstanceService.java`
- Test: `src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java`

- [ ] **Step 1: Write the failing tests**

In `src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java`:

(a) Add the mock field after the existing mocks:

```java
    @Mock PositionLabelResolver positionLabelResolver;
```

(b) Add these tests (they build an instance whose `state_json` block step carries a STALE letter, then assert `getPlayInstance` returns the live-resolved letter, and a deleted position yields null without crashing):

```java
    private PlayInstance instanceWithSoloBlock(UUID instanceId, String posId, String staleLetter) {
        var inst = new PlayInstance();
        inst.setInstanceId(instanceId);
        inst.setType("passe");
        inst.setTemplateId(UUID.randomUUID());
        inst.setTemplateName("T");
        inst.setStatus("active");
        inst.setOwner(mock(User.class));
        inst.setStartedAt(java.time.Instant.now());
        inst.setPlayersJson("[]");
        // one block, one solo step with a stale letter
        inst.setStateJson("[{\"blockId\":\"" + UUID.randomUUID() + "\",\"serieId\":\"" + UUID.randomUUID()
            + "\",\"serieAlias\":\"S\",\"steps\":[{\"id\":\"1\",\"type\":\"solo\",\"posId\":\"" + posId
            + "\",\"letter\":\"" + staleLetter + "\"}],\"status\":\"pending\"}]");
        return inst;
    }

    @Test
    void getPlayInstance_reresolvesBlockStepLettersFromCurrentPositions() {
        var instanceId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var inst = instanceWithSoloBlock(instanceId, posId.toString(), "OLD");
        when(playInstanceRepository.findById(instanceId)).thenReturn(java.util.Optional.of(inst));
        var pos = new ch.jp.shooting.model.RangePosition();
        pos.setId(posId); pos.setLabel("A1");
        when(positionLabelResolver.byPosIds(any())).thenReturn(java.util.Map.of(posId.toString(), pos));

        var resp = service.getPlayInstance(instanceId);

        assertEquals("A1", resp.getBlocks().get(0).getSteps().get(0).getLetter());
    }

    @Test
    void getPlayInstance_deletedPosition_yieldsNullLetterNoCrash() {
        var instanceId = UUID.randomUUID();
        var inst = instanceWithSoloBlock(instanceId, UUID.randomUUID().toString(), "OLD");
        when(playInstanceRepository.findById(instanceId)).thenReturn(java.util.Optional.of(inst));
        when(positionLabelResolver.byPosIds(any())).thenReturn(java.util.Map.of());

        var resp = service.getPlayInstance(instanceId);

        assertNull(resp.getBlocks().get(0).getSteps().get(0).getLetter());
    }
```

> Verify the `PlayInstance` setters used (`setInstanceId/setType/setTemplateId/setTemplateName/setStatus/setOwner/setStartedAt/setPlayersJson/setStateJson`) against `model/PlayInstance.java` and adjust names if needed. `PlayMapper.toPlayInstanceResponse` requires non-null `startedAt`, `playersJson`, `stateJson`.

- [ ] **Step 2: Run to verify failure**

Run: `mvn -Dtest=PlayInstanceServiceTest test`
Expected: FAIL — constructor arity (new mock) and `getLetter()` returns `"OLD"` (no re-resolution yet).

- [ ] **Step 3: Inject the resolver and re-resolve on read**

In `src/main/java/ch/jp/shooting/service/PlayInstanceService.java`:

(a) Add the field + constructor param (append last):

```java
    private final PositionLabelResolver positionLabelResolver;
```

```java
    public PlayInstanceService(PlayInstanceRepository playInstanceRepository,
                               PasseRepository passeRepository,
                               PasseService passeService,
                               SecurityHelper securityHelper,
                               PositionLabelResolver positionLabelResolver) {
        this.playInstanceRepository = playInstanceRepository;
        this.passeRepository = passeRepository;
        this.passeService = passeService;
        this.securityHelper = securityHelper;
        this.positionLabelResolver = positionLabelResolver;
    }
```

(b) Wrap the two read paths. In `getPlayInstance`:

```java
        return withResolvedBlockLabels(PlayMapper.toPlayInstanceResponse(instance));
```

In `listPlayInstances`, change the final map:

```java
        return instances.stream()
            .map(PlayMapper::toPlayInstanceResponse)
            .map(this::withResolvedBlockLabels)
            .toList();
```

(c) Add the private helper (imports: `ch.jp.smartground.model.PlayInstanceResponse` is already imported; add `ch.jp.smartground.model.Step` if not present):

```java
    /**
     * Löst die Step-Buchstaben aller Blöcke einer (aktiven) Instanz live aus den
     * aktuellen Positionen auf — nur fürs Anzeigen; state_json bleibt unverändert.
     */
    private PlayInstanceResponse withResolvedBlockLabels(PlayInstanceResponse response) {
        var steps = response.getBlocks().stream()
            .flatMap(b -> b.getSteps().stream())
            .toList();
        var positions = positionLabelResolver.byPosIds(PositionLabelResolver.posIdsOf(steps));
        steps.forEach(step -> PositionLabelResolver.applyResolvedLabels(step, positions));
        return response;
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -Dtest=PlayInstanceServiceTest test`
Expected: PASS — including the pre-existing `startSerieInstance`/`startPasseInstance` tests (they don't call the read paths; the new mock is simply present).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/PlayInstanceService.java \
        src/test/java/ch/jp/shooting/service/PlayInstanceServiceTest.java
git commit -m "[backend] active play instances re-resolve block labels on read

getPlayInstance/listPlayInstances re-resolve each running block's step letters
from current positions (display-only; state_json untouched), so an in-progress
session reflects a position rename on reload.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Frontend — consistent deleted-position placeholder (`—`) + German aria-label

**Files:**
- Modify: `src/constants/stepModes.js`
- Modify: `src/components/SerieDrawer.vue`
- Test: `src/constants/__tests__/stepModes.test.js` (create)

- [ ] **Step 1: Write the failing tests**

Create `src/constants/__tests__/stepModes.test.js`:

```javascript
import { describe, it, expect } from 'vitest'
import { stepNotation, stepAriaLabel } from '@/constants/stepModes.js'

describe('stepNotation — deleted-position placeholder', () => {
  it('renders an em dash for a missing solo letter (deleted position)', () => {
    expect(stepNotation({ type: 'solo', letter: null })).toBe('—')
    expect(stepNotation({ type: 'solo', letter: null })).not.toContain('?')
  })

  it('renders an em dash for a missing side of a pair', () => {
    expect(stepNotation({ type: 'pair', letter1: 'A', letter2: null })).toBe('A + —')
  })

  it('still renders present letters normally', () => {
    expect(stepNotation({ type: 'a_schuss', letter1: 'A', letter2: 'B' })).toBe('A → B')
    expect(stepNotation({ type: 'raffale', letter: 'C' })).toBe('C×2')
  })
})

describe('stepAriaLabel', () => {
  it('describes a normal solo step in German', () => {
    expect(stepAriaLabel({ type: 'solo', letter: 'A' })).toBe('Solo Position A')
  })

  it('names a deleted position', () => {
    expect(stepAriaLabel({ type: 'solo', letter: null })).toBe('Solo gelöschte Position')
  })

  it('describes a pair with one deleted side', () => {
    expect(stepAriaLabel({ type: 'pair', letter1: 'A', letter2: null }))
      .toBe('Pair Position A und gelöschte Position')
  })
})
```

- [ ] **Step 2: Run to verify failure**

Run: `npm run test -- --run src/constants/__tests__/stepModes.test.js`
Expected: FAIL — `stepNotation` returns `?` for missing letters; `stepAriaLabel` is not exported.

- [ ] **Step 3: Implement the placeholder + aria helper**

In `src/constants/stepModes.js`:

(a) In `stepNotation`, change the two fallback literals from `'?'` to the placeholder. Replace:

```javascript
  const one = step[key] ?? step[`${key}1`] ?? '?';
  const two = step[`${key}2`] ?? '?';
```

with:

```javascript
  const one = step[key] ?? step[`${key}1`] ?? MISSING_POSITION;
  const two = step[`${key}2`] ?? MISSING_POSITION;
```

and add the constant near the top of the file (after the imports/`STEP_MODES` block):

```javascript
// Rendered placeholder for a step whose position was deleted (null letter/alias).
export const MISSING_POSITION = '—';
```

(b) Add the aria helper (after `stepNotation`):

```javascript
/**
 * German aria-label for a step: mode name + position letter(s), naming a deleted
 * position explicitly. Use on the element that renders a step so assistive tech
 * conveys what a bare "—" cannot.
 */
export function stepAriaLabel(step) {
  if (!step) return '';
  const mode = stepModeLabel(step.type);
  const name = (v) => (v == null ? 'gelöschte Position' : `Position ${v}`);
  const { first, second } = stepLetters(step);
  const firstV = first === '' ? null : first;
  const secondV = second === '' ? null : second;
  if (step.type === StepType.PAIR || step.type === StepType.A_SCHUSS) {
    return `${mode} ${name(firstV)} und ${name(secondV)}`;
  }
  return `${mode} ${name(firstV)}`;
}
```

> `stepLetters` returns `''` for a missing letter; the helper maps `''`→`null`→"gelöschte Position". `stepModeLabel` already returns the German mode label (`Solo`, `Pair`, `a.Schuss`, `Raffale`).

- [ ] **Step 4: Wire the aria-label into `SerieDrawer`**

In `src/components/SerieDrawer.vue`, import `stepAriaLabel` alongside the existing `stepModes` imports, and add `:aria-label` to the step label span (line ~109):

```vue
                <span class="step-label" :aria-label="stepAriaLabel(step)">{{ stepLabel(step) }}</span>
```

- [ ] **Step 5: Run tests + lint**

Run: `npm run test -- --run src/constants/__tests__/stepModes.test.js src/components/__tests__/SerieDrawer.test.js`
Expected: PASS.

Run: `npm run lint:check`
Expected: no errors.

- [ ] **Step 6: Run the full UI suite**

Run: `npm run test -- --run`
Expected: PASS — confirms no other view that calls `stepNotation` regressed on the `?`→`—` change (search the suite for any test asserting `'?'`; none expected).

- [ ] **Step 7: Commit**

```bash
git add src/constants/stepModes.js src/components/SerieDrawer.vue src/constants/__tests__/stepModes.test.js
git commit -m "[ui] render deleted-position placeholder consistently as em dash

stepNotation renders '—' (was '?') for a missing position letter/alias, applied
everywhere via the shared notation; new stepAriaLabel names a deleted position in
German and is wired into SerieDrawer's step rows.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Document the pre-v1.0 Passe reset (migration)

**Files:**
- Modify: `../smart-ground-backend/CLAUDE.md`

- [ ] **Step 1: Add the migration note**

In `CLAUDE.md`, under the `passen` schema line / the Passe domain bullet (added in Group 2), append a **Migration** note:

> **Migration (pre-v1.0 — no data migration):** Group 2 replaced `passen.serien_json` (embedded snapshot) with `serie_ids_json` (ordered Serie-ID references). Existing snapshot-based Passen **cannot** be reliably converted (their source Serien may have diverged or been deleted), so they are **reset, not migrated**: on H2 (tests) the schema is recreated each run; on a dev PostgreSQL DB, truncate the table and drop the dead column once — `DELETE FROM passen; ALTER TABLE passen DROP COLUMN IF EXISTS serien_json;` (Hibernate `ddl-auto=update` leaves the orphaned `serien_json` column in place otherwise; it is harmless but should be dropped). No production release exists, so no upgrade path is provided.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "[backend] document pre-v1.0 Passe reset (no data migration, Group 5)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification (whole feature)

- [ ] **Backend full suite:** `mvn test` → all green (PlayInstance re-resolution + everything from Groups 1–3).
- [ ] **Frontend full suite + lint:** `npm run test -- --run` and `npm run lint:check` → all green.

## Manual verification (end-to-end, cross-stack)

1. **Propagation — Serie/Passe:** position `A` → Serie + Passe show `A`; rename `A→A1`; reload → both show `A1`.
2. **Propagation — active session:** start a Passe run; rename a used position; reload the running session → the block step shows the new letter.
3. **Isolation — completed:** finish a competition; rename a used position; reload completed results → letters **unchanged** (Group 3 snapshot).
4. **Deleted position:** delete a position used by a Serie/Passe/active block → step renders `—` (aria "… gelöschte Position"), no crash.
5. **Stable Serie ID:** edit a Serie's steps (PUT) → same Serie ID; a referencing Passe still resolves with no "repair".

---

## Self-review checklist (to complete during execution)

- **Spec coverage (Group 5):**
  - Deleted-position placeholder `—` + German aria-label, applied consistently (shared `stepNotation`) ✅ (Task 3).
  - Migration: reset existing Passen, no migration, documented ✅ (Task 4).
  - Cross-stack tests — propagation: Serie (Group 1 `SerieServiceTest`), Passe (Group 2 `PasseServiceTest`), **active session** (Task 2) ✅; isolation: completed snapshot (Group 3 `CompetitionServiceSerieResultsTest`) ✅; deleted-position no crash (Task 2 backend + Task 3 frontend) ✅; Serie `PUT` stable ID + Passe resolves (Group 2 `SerieServiceTest.updateSerie_*` + `PasseServiceTest`, manual step 5) ✅.
- **Placeholders:** none — every step has concrete code/commands.
- **Type consistency:** `PositionLabelResolver.posIdsOf(Collection<Step>)`/`applyResolvedLabels(Step, Map<String,RangePosition>)` are consumed identically by `SerieService.withResolvedLabels` and `PlayInstanceService.withResolvedBlockLabels`; `stepNotation`/`stepAriaLabel` read the same `letter`/`letter1`/`letter2` fields the backend now resolves.
- **Reused `PositionLabelResolver`** for every posId→label lookup across all five groups ✅.
- **Pre-execution reads to confirm:** `model/PlayInstance.java` setter names; that removing `SerieService.applyLabels` leaves no other caller; the exact `stepModes.js` import list in `SerieDrawer.vue`.
</content>

# PRE_COMPLETE Score Correction (Task E) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. Checkbox steps. **Cross-stack:** Tasks 1–4 in `smart-ground-backend` (branch `feat/competition-score-correction`); Tasks 5–8 in `smart-ground-ui` (same branch name). Backend first.

**Goal:** Let the admin correct a shooter's per-step results in the PRE_COMPLETE Auswertung (Fortschritt tab, per Passe), persisted so the Rangliste updates.

**Architecture:** A `PUT …/serien/{id}/results` endpoint overwrites a completed Serie's `stepStates` and replaces that passe/serie entry in each player's `programResults`. The admin UI lives in the Fortschritt tab (PRE_COMPLETE only): a Passe switcher + editable step-scorecards + a state picker; corrections recompute Serie totals client-side and POST the whole Serie.

**Spec:** `docs/superpowers/specs/2026-06-18-score-correction-design.md`

---

## BACKEND (repo: smart-ground-backend)

### Task 1: OpenAPI — correctSerieResult endpoint

**Files:** Modify `src/main/resources/static/openapi.yaml`

- [ ] **Step 1:** Add the path immediately after the existing `…/serien/{serieId}/complete` path block:

```yaml
  /api/sessions/{sessionId}/groups/{groupId}/serien/{serieId}/results:
    put:
      summary: Correct a completed Serie's results (admin, PRE_COMPLETE)
      operationId: correctSerieResult
      tags: [Session]
      parameters:
        - { name: sessionId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: groupId, in: path, required: true, schema: { type: string, format: uuid } }
        - { name: serieId, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CompleteSerieRequest'
      responses:
        '200':
          description: Progress after correction
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SessionProgressResponse'
        '404':
          description: Session or group not found
        '409':
          description: Serie not completed, or session not in PRE_COMPLETE
```

- [ ] **Step 2:** `mvn -q generate-sources` → verify `grep -rn "correctSerieResult" target/generated-sources` finds `SessionApi.correctSerieResult(UUID sessionId, UUID groupId, UUID serieId, CompleteSerieRequest ...)`.

- [ ] **Step 3:** Commit.
```bash
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] api: correctSerieResult endpoint (PRE_COMPLETE score correction)"
```

---

### Task 2: Repository finder

**Files:** Modify `src/main/java/ch/jp/shooting/repository/CompetitionSerieResultRepository.java`

- [ ] **Step 1:** Add:
```java
    Optional<CompetitionSerieResult> findBySessionIdAndGroupIdAndPasseIndexAndSerieId(
            UUID sessionId, UUID groupId, int passeIndex, UUID serieId);
```
(Ensure `java.util.Optional` and `java.util.UUID` are imported.)

- [ ] **Step 2:** `mvn -q compile` → success.

- [ ] **Step 3:** Commit.
```bash
git add src/main/java/ch/jp/shooting/repository/CompetitionSerieResultRepository.java
git commit -m "[backend] competition: CSR finder by session/group/passe/serie"
```

---

### Task 3: Service — correctSerieResult + replace-or-append refactor

**Files:** Modify `src/main/java/ch/jp/shooting/service/CompetitionProgressService.java`; Test `src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java`

- [ ] **Step 1: Write failing tests.** Append to the test class:

```java
    @Test
    void correctSerieResult_overwritesRowAndReplacesPlayerResultEntry() throws Exception {
        // session ACTIVE→PRE_COMPLETE; one group with member m1; passe 0 serie serieId already completed.
        session.setStatus(SessionStatus.PRE_COMPLETE);
        UUID memberId = UUID.randomUUID();
        SessionPlayer m1 = new SessionPlayer(); setPlayerId(m1, memberId); m1.setDisplayName("Max");
        group.getMembers().add(m1);
        var existingCsr = new CompetitionSerieResult(session, group, 0, serieId);
        existingCsr.setResults("[]");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.findBySessionIdAndGroupIdAndPasseIndexAndSerieId(any(), any(), anyInt(), any()))
            .thenReturn(Optional.of(existingCsr));
        when(csrRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // pre-existing PlayerResult with an entry for this passe/serie (to be replaced)
        var pr = new PlayerResult(session, m1);
        pr.setProgramResults(objectMapper.writeValueAsString(List.of(
            java.util.Map.of("passeIndex", 0, "serieId", serieId.toString(), "totalPoints", 1, "maxPoints", 2))));
        when(playerResultRepository.findBySessionIdAndPlayerId(any(), eq(memberId))).thenReturn(Optional.of(pr));
        when(playerResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of(existingCsr));

        var req = new ch.jp.smartground.model.CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));
        var pres = new ch.jp.smartground.model.PlayerResult();
        pres.setPlayerId(memberId.toString()); pres.setTotalPoints(2); pres.setMaxPoints(2);
        req.setResults(List.of(pres));

        progressService.correctSerieResult(sessionId, groupId, serieId, req);

        // CSR row overwritten with the new results JSON
        verify(csrRepository).save(argThat(c -> c.getResults() != null && c.getResults().contains("\"totalPoints\":2")));
        // programResults entry replaced (not duplicated): still one entry, now 2/2
        verify(playerResultRepository).save(argThat(saved -> {
            try {
                var arr = objectMapper.readValue(saved.getProgramResults(), java.util.Map[].class);
                long forSerie = java.util.Arrays.stream(arr)
                    .filter(m -> serieId.toString().equals(m.get("serieId"))).count();
                return forSerie == 1
                    && ((Number) java.util.Arrays.stream(arr)
                        .filter(m -> serieId.toString().equals(m.get("serieId"))).findFirst().get()
                        .get("totalPoints")).intValue() == 2;
            } catch (Exception e) { return false; }
        }));
    }

    @Test
    void correctSerieResult_rejectsWhenNotPreComplete() {
        session.setStatus(SessionStatus.ACTIVE);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        var req = new ch.jp.smartground.model.CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));
        assertThrows(ch.jp.shooting.exception.ConflictException.class,
            () -> progressService.correctSerieResult(sessionId, groupId, serieId, req));
    }

    @Test
    void correctSerieResult_rejectsWhenSerieNotCompleted() {
        session.setStatus(SessionStatus.PRE_COMPLETE);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.findBySessionIdAndGroupIdAndPasseIndexAndSerieId(any(), any(), anyInt(), any()))
            .thenReturn(Optional.empty());
        var req = new ch.jp.smartground.model.CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));
        assertThrows(ch.jp.shooting.exception.ConflictException.class,
            () -> progressService.correctSerieResult(sessionId, groupId, serieId, req));
    }
```

Add a reflection id-setter helper for `SessionPlayer` if the test class lacks one:
```java
    private void setPlayerId(SessionPlayer p, UUID id) {
        try { var f = SessionPlayer.class.getDeclaredField("id"); f.setAccessible(true); f.set(p, id); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
```
(Check the existing test for an equivalent helper first; reuse it. `import static org.mockito.ArgumentMatchers.eq;` if not present.)

- [ ] **Step 2:** `mvn -q -Dtest=CompetitionProgressServiceTest test` → FAIL (`correctSerieResult` missing).

- [ ] **Step 3: Implement.** Refactor `upsertPlayerResultsFromApi` into a replace-or-append helper and add the action:

Rename the body of `upsertPlayerResultsFromApi` to `writePlayerResults(..., boolean replaceExisting)` and have `completeSerie`'s call pass `false`. The only behavioral change inside the loop: before `existing.add(entry)`, when `replaceExisting`:
```java
            if (replaceExisting) {
                existing.removeIf(m ->
                    serieId.toString().equals(m.get("serieId"))
                    && m.get("passeIndex") instanceof Number n && n.intValue() == passeIndex);
            }
```
Keep `completeSerie` calling `writePlayerResults(session, group, passeIndex, serieId, request.getResults(), false)`.

Add the action:
```java
    /** Korrigiert die Ergebnisse einer bereits abgeschlossenen Serie (Admin, PRE_COMPLETE). */
    public SessionProgressResponse correctSerieResult(
            UUID sessionId, UUID groupId, UUID serieId,
            ch.jp.smartground.model.CompleteSerieRequest request) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        ShooterGroup group = groupRepository.findByIdAndSessionId(groupId, sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        if (session.getStatus() != SessionStatus.PRE_COMPLETE) {
            throw new ConflictException("Korrektur nur im Status PRE_COMPLETE möglich");
        }
        int passeIndex = request.getPasseIndex() != null && request.getPasseIndex().isPresent()
                ? request.getPasseIndex().get() : 0;
        CompetitionSerieResult csr = csrRepository
            .findBySessionIdAndGroupIdAndPasseIndexAndSerieId(sessionId, groupId, passeIndex, serieId)
            .orElseThrow(() -> new ConflictException("Serie noch nicht abgeschlossen"));
        csr.setResults(objectMapper.writeValueAsString(request.getResults()));
        csrRepository.save(csr);
        writePlayerResults(session, group, passeIndex, serieId, request.getResults(), true);
        return buildSessionProgressResponse(sessionId);
    }
```

- [ ] **Step 4:** `mvn -q -Dtest=CompetitionProgressServiceTest test` → PASS.

- [ ] **Step 5:** Commit.
```bash
git add src/main/java/ch/jp/shooting/service/CompetitionProgressService.java src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java
git commit -m "[backend] competition: correctSerieResult overwrites Serie + replaces player entry"
```

---

### Task 4: SessionController.correctSerieResult

**Files:** Modify `src/main/java/ch/jp/shooting/api/SessionController.java`

- [ ] **Step 1:** Add:
```java
    @Override
    public ResponseEntity<SessionProgressResponse> correctSerieResult(
            UUID sessionId, UUID groupId, UUID serieId, CompleteSerieRequest completeSerieRequest) {
        try {
            return ResponseEntity.ok(
                    progressService.correctSerieResult(sessionId, groupId, serieId, completeSerieRequest));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
```

- [ ] **Step 2:** `mvn -q test` → BUILD SUCCESS.

- [ ] **Step 3:** Commit.
```bash
git add src/main/java/ch/jp/shooting/api/SessionController.java
git commit -m "[backend] api: wire correctSerieResult endpoint"
```

---

## FRONTEND (repo: smart-ground-ui)

### Task 5: API + store action

**Files:** Modify `src/services/wettkampfApi.js`, `src/stores/competitionEventStore.js`; Test `src/stores/__tests__/competitionEventStore.test.js`

- [ ] **Step 1: Failing test.** Add `correctSerieResult: vi.fn()` to the wettkampfApi mock list. Append a test:
```javascript
  it('correctSerieResult posts the corrected serie then reloads results', async () => {
    api.correctSerieResult.mockResolvedValue({})
    api.getLeaderboard.mockResolvedValue({ playerScores: [] })
    api.getSession.mockResolvedValue({ id: 's1', groups: [], passen: [] })
    api.getSerieResults.mockResolvedValue([])
    const store = useCompetitionEventStore()
    const results = [{ playerId: 'm1', totalPoints: 2, maxPoints: 2, stepStates: [] }]
    await store.correctSerieResult('s1', 'g1', 'se1', 0, results)
    expect(api.correctSerieResult).toHaveBeenCalledWith('s1', 'g1', 'se1', 0, results)
    expect(api.getSerieResults).toHaveBeenCalledWith('s1') // reloaded
  })
```

- [ ] **Step 2:** Run the store test file → FAIL.

- [ ] **Step 3: Implement.** In `wettkampfApi.js`:
```javascript
export const correctSerieResult = (sessionId, groupId, serieId, passeIndex, results) =>
  apiFetch(`/sessions/${sessionId}/groups/${groupId}/serien/${serieId}/results`, {
    method: 'PUT',
    body: JSON.stringify({ passeIndex, results }),
  })
```
In `competitionEventStore.js` add the action (near `loadCompletedResults`) and export it:
```javascript
  const correctSerieResult = async (sessionId, groupId, serieId, passeIndex, playerResults) => {
    await wettkampfApi.correctSerieResult(sessionId, groupId, serieId, passeIndex, playerResults)
    await loadCompletedResults(sessionId)
  }
```

- [ ] **Step 4:** Run the store test file → PASS.

- [ ] **Step 5:** Commit.
```bash
git add src/services/wettkampfApi.js src/stores/competitionEventStore.js src/stores/__tests__/competitionEventStore.test.js
git commit -m "[ui] competition: correctSerieResult api + store action"
```

---

### Task 6: Correction selector + recompute helper

**Files:** Modify `src/composables/useCompletedResults.js`; Test `src/composables/__tests__/useCompletedResults.test.js`

- [ ] **Step 1: Failing tests.** Append (the `seed` already has `serieDefs` + `serieResults`):
```javascript
  it('recomputeSerieTotals sums earned/value by state', () => {
    const { recomputeSerieTotals } = useCompletedResults('s1')
    const totals = recomputeSerieTotals([
      { state: 'done', pointValue: 2 },
      { state: 'failed-a', pointValue: 2 },
      { state: 'failed-both', pointValue: 2 },
      { state: 'pending', pointValue: 1 },
    ])
    expect(totals).toEqual({ totalPoints: 3, maxPoints: 7 }) // 2 + 1 + 0 + 0 ; max 2+2+2+1
  })

  it('getCorrectionData groups the chosen passe by serie with per-player steps', () => {
    const store = useCompetitionEventStore(); seed(store)
    const { getCorrectionData } = useCompletedResults('s1')
    const data = getCorrectionData(0) // se1 is passeIndex 0 in the seed
    expect(data.serien[0]).toMatchObject({ serieId: 'se1', rangeName: 'Stand 1', serieName: 'Morgen' })
    expect(data.serien[0].steps[0]).toMatchObject({ stepIndex: 0, type: 'solo', letter: 'A' })
    expect(data.serien[0].players[0]).toMatchObject({ playerId: 'm1' })
    expect(data.serien[0].players[0].steps[0]).toMatchObject({ stepIndex: 0, state: 'done' })
  })
```
(Adjust expected ids to the seed: `serieResults` se1 is `passeIndex:0`, player `m1`, steps done/failed-a; `serieDefs.se1` = Stand 1 / Morgen / [solo A, pair B/D].)

- [ ] **Step 2:** Run → FAIL.

- [ ] **Step 3: Implement.** In `useCompletedResults.js` add and export:
```javascript
  const STEP_DEDUCTION = { 'failed-both': 2, 'failed-a': 1, 'failed-b': 1 }
  const earnedFor = (state, pointValue) =>
    state === 'pending' ? 0 : Math.max(0, pointValue - (STEP_DEDUCTION[state] ?? 0))

  const recomputeSerieTotals = (steps) => steps.reduce(
    (acc, s) => ({
      totalPoints: acc.totalPoints + earnedFor(s.state, s.pointValue ?? 0),
      maxPoints: acc.maxPoints + (s.pointValue ?? 0),
    }), { totalPoints: 0, maxPoints: 0 })

  // Per-Serie, per-player editable view for one Passe (PRE_COMPLETE correction).
  const getCorrectionData = (passeIndex) => {
    const serieResults = (entry.value?.serieResults ?? []).filter(sr => (sr.passeIndex ?? 0) === passeIndex)
    const defs = entry.value?.serieDefs ?? {}
    const serien = serieResults
      .map(sr => {
        const def = defs[sr.serieId] ?? null
        const players = (sr.results ?? []).map(r => ({
          playerId: r.playerId,
          displayName: r.displayName ?? null,
          steps: (r.stepStates ?? []).map(s => ({
            stepIndex: s.stepIndex ?? 0, state: s.state, pointValue: s.pointValue ?? 0, pointsEarned: s.pointsEarned ?? 0,
          })),
        }))
        return {
          groupId: sr.groupId,
          serieId: sr.serieId,
          rangeName: def?.rangeName ?? null,
          serieName: def?.serieName ?? 'Serie',
          sortIndex: def?.sortIndex ?? 0,
          steps: (def?.steps ?? []).map((d, i) => ({ stepIndex: i, type: d.type, letter: d.letter, letter1: d.letter1, letter2: d.letter2 })),
          players,
        }
      })
      .sort((a, b) => a.sortIndex - b.sortIndex)
    return { passeIndex, serien }
  }
```
Add `recomputeSerieTotals, getCorrectionData` to the returned object.

- [ ] **Step 4:** Run → PASS.

- [ ] **Step 5:** Commit.
```bash
git add src/composables/useCompletedResults.js src/composables/__tests__/useCompletedResults.test.js
git commit -m "[ui] competition: correction selector + serie totals recompute"
```

---

### Task 7: StepScorecard editable mode + StepStatePicker

**Files:** Modify `src/components/competition/StepScorecard.vue`; Create `src/components/competition/StepStatePicker.vue`; Tests `src/components/competition/__tests__/StepScorecard.test.js`, `…/StepStatePicker.test.js`

- [ ] **Step 1: StepScorecard editable test.** Append to `StepScorecard.test.js`:
```javascript
  it('editable mode emits correct-step with the step identity on chip click', async () => {
    const wrapper = mount(StepScorecard, { props: { serien, editable: true } })
    await wrapper.findAll('.step-chip')[0].trigger('click')
    expect(wrapper.emitted('correct-step')).toBeTruthy()
    expect(wrapper.emitted('correct-step')[0][0]).toMatchObject({ stepIndex: 0, currentState: 'done' })
  })

  it('non-editable chips do not emit on click', async () => {
    const wrapper = mount(StepScorecard, { props: { serien } })
    await wrapper.findAll('.step-chip')[0].trigger('click')
    expect(wrapper.emitted('correct-step')).toBeFalsy()
  })
```

- [ ] **Step 2:** Run StepScorecard test → FAIL.

- [ ] **Step 3: StepScorecard editable.** Add prop `editable: { type: Boolean, default: false }` and `defineEmits(['correct-step'])`. Wrap each chip so that when `editable`, it is a `<button type="button">` with `@click="emit('correct-step', { serieKey: serie.key, serieId: serie.serieId, groupId: serie.groupId, stepIndex: step.stepIndex, playerId: serie.playerId, type: step.type, currentState: step.state })"` and class `step-chip--editable` (cursor pointer). Non-editable renders the existing `<span>`. (Each `serie` group passed to `StepScorecard` in correction mode carries `serieId/groupId/playerId`; the existing Task-C callers don't set `editable`, so their behavior is unchanged.)

- [ ] **Step 4:** Run StepScorecard test → PASS.

- [ ] **Step 5: StepStatePicker test (new).** `StepStatePicker.test.js`:
```javascript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StepStatePicker from '../StepStatePicker.vue'

describe('StepStatePicker', () => {
  it('single-target step offers Treffer + one Fehler', () => {
    const w = mount(StepStatePicker, { props: { type: 'solo' } })
    const labels = w.findAll('.picker-btn').map(b => b.text())
    expect(labels).toContain('Treffer')
    expect(labels.filter(l => l.includes('Fehler'))).toHaveLength(1)
  })
  it('double-target step offers Treffer + A/B/Beide and emits the chosen state', async () => {
    const w = mount(StepStatePicker, { props: { type: 'pair' } })
    expect(w.findAll('.picker-btn').length).toBe(4)
    await w.findAll('.picker-btn').find(b => b.text() === 'Treffer').trigger('click')
    expect(w.emitted('pick')[0][0]).toBe('done')
  })
})
```

- [ ] **Step 6:** Run → FAIL.

- [ ] **Step 7: StepStatePicker.vue.** A small overlay/menu emitting `pick` with a `StepState` value:
```vue
<template>
  <div class="picker">
    <button type="button" class="picker-btn picker-btn--hit" @click="emit('pick', StepState.DONE)">Treffer</button>
    <template v-if="isDouble">
      <button type="button" class="picker-btn" @click="emit('pick', StepState.FAILED_A)">Fehler A</button>
      <button type="button" class="picker-btn" @click="emit('pick', StepState.FAILED_B)">Fehler B</button>
      <button type="button" class="picker-btn" @click="emit('pick', StepState.FAILED_BOTH)">Beide Fehler</button>
    </template>
    <button v-else type="button" class="picker-btn" @click="emit('pick', StepState.FAILED_BOTH)">Fehler</button>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { StepState, StepType } from '@/constants/playEnums.js'
const props = defineProps({ type: { type: String, default: null } })
const emit = defineEmits(['pick'])
const isDouble = computed(() => [StepType.PAIR, StepType.A_SCHUSS].includes(props.type))
</script>
<style scoped>
.picker { display: flex; flex-wrap: wrap; gap: 8px; }
.picker-btn { padding: 8px 14px; border-radius: 10px; border: 1px solid var(--sg-border); background: var(--sg-bg-card); color: var(--sg-brand); font: inherit; font-weight: 600; cursor: pointer; }
.picker-btn--hit { background: var(--sg-color-success-bg); color: var(--sg-color-success-text); border-color: color-mix(in srgb, var(--sg-color-success) 40%, transparent); }
</style>
```
(For `raffale` — single `letter`, two shots — `isDouble` is false → Treffer/Fehler; `FAILED_BOTH` is the single-fail value, consistent with the kiosk's single-target handling.)

- [ ] **Step 8:** Run both component tests → PASS.

- [ ] **Step 9:** Commit.
```bash
git add src/components/competition/StepScorecard.vue src/components/competition/StepStatePicker.vue src/components/competition/__tests__/StepScorecard.test.js src/components/competition/__tests__/StepStatePicker.test.js
git commit -m "[ui] competition: StepScorecard editable mode + StepStatePicker"
```

---

### Task 8: CompetitionCorrectionPanel + wire into Fortschritt tab

**Files:** Create `src/components/competition/CompetitionCorrectionPanel.vue`; Modify `src/components/competition/ActiveCompetitionPanel.vue`; Tests `…/__tests__/CompetitionCorrectionPanel.test.js`, update `ActiveCompetitionPanel.test.js`

- [ ] **Step 1: CompetitionCorrectionPanel test (new).** Mount with a seeded store (PRE_COMPLETE serie results via `loadCompletedResults` cache) and assert: a Passe switcher renders one button per Passe; switching changes the rendered Serien; clicking a step chip shows the picker; picking a state calls `store.correctSerieResult` with the corrected results for that Serie. Use the same store-seeding approach as `useCompletedResults.test.js` (set `store.completedResultsBySession[sessionId]` directly), and `vi.spyOn(store, 'correctSerieResult').mockResolvedValue()`. Assert the call args: `(sessionId, groupId, serieId, passeIndex, results)` where `results` contains the toggled step's new state and recomputed totals.

- [ ] **Step 2:** Run → FAIL.

- [ ] **Step 3: CompetitionCorrectionPanel.vue.** Props: `sessionId`, `passen` (from event). Setup:
  - `const { getCorrectionData, recomputeSerieTotals, load } = useCompletedResults(toRef(props,'sessionId'))`; `onMounted(load)`.
  - `activePasse = ref(0)`; switcher buttons set it.
  - `const data = computed(() => getCorrectionData(activePasse.value))`.
  - Render, per Serie: header "RangeName – SerieName"; per player: shooter name + `<StepScorecard editable :serien="[oneSerieForPlayer]" @correct-step="onCorrect" />` where `oneSerieForPlayer` is a single-group object `{ key, serieId, groupId, playerId, rangeName, serieName, steps: <defs joined with this player's states> }` (join `serie.steps` defs with the player's `steps` states by `stepIndex`).
  - `correctionTarget = ref(null)`; `onCorrect(payload)` sets it; render `<StepStatePicker :type="correctionTarget.type" @pick="applyPick" />` in an overlay.
  - `applyPick(newState)`: find the target Serie + player in `data`, set that step's `state` and `pointsEarned = earned(newState, pointValue)`, recompute the player's serie totals, build `results` = that Serie's **all players'** `stepStates` (StepStateRecord shape: `playerId, serieIndex, stepIndex, state, pointValue, pointsEarned`) + `totalPoints/maxPoints`, call `store.correctSerieResult(props.sessionId, groupId, serieId, activePasse.value, results)`, clear `correctionTarget`.
  - `serieIndex` for each StepStateRecord = the Serie's `sortIndex`-derived index within the Passe (use the Serie's position in `data.serien`).
  Use `var(--sg-*)` tokens; German labels.

- [ ] **Step 4: Wire into `ActiveCompetitionPanel`.** In the Fortschritt tab panel, when `props.event.status?.toUpperCase() === 'PRE_COMPLETE'`, render `<CompetitionCorrectionPanel :session-id="event.id" :passen="event.passen ?? []" />` **above** (or instead of) the existing Serie-completion cards; otherwise render the existing cards as today. Import the component.

- [ ] **Step 5: Update `ActiveCompetitionPanel.test.js`.** Add a test: with `event.status === 'PRE_COMPLETE'`, the Fortschritt tab contains the correction panel (e.g. `.competition-correction` root or a Passe switcher), and with `ACTIVE` it shows the serie-completion cards as before. Stub `CompetitionCorrectionPanel` to keep the test focused: `global.stubs: { CompetitionCorrectionPanel: { template: '<div class=\"correction-stub\" />' } }`.

- [ ] **Step 6:** Run the two component test files → PASS.

- [ ] **Step 7: Full verification.**
  - `npm run test` → whole suite passes.
  - `npm run lint:check` → no new errors in touched files.

- [ ] **Step 8:** Commit.
```bash
git add src/components/competition/CompetitionCorrectionPanel.vue src/components/competition/ActiveCompetitionPanel.vue src/components/competition/__tests__/CompetitionCorrectionPanel.test.js src/components/__tests__/ActiveCompetitionPanel.test.js
git commit -m "[ui] competition: PRE_COMPLETE score correction panel in Fortschritt tab"
```

---

## Self-Review Notes

- **Spec coverage:** overwrite endpoint → Tasks 1–4; api+store → 5; selector+recompute → 6; editable chips+picker → 7; Passe switcher + per-shooter correction in the Fortschritt tab → 8. Switch-between-Passen (2.5.1) → Task 8 switcher; per-shooter overview + correct (2.5.2) → Tasks 6–8.
- **Contract consistency:** correction reuses `CompleteSerieRequest` (`passeIndex` + `results: PlayerResult[]` with `StepStateRecord` stepStates). Frontend sends `{ passeIndex, results }`; backend overwrites CSR + replaces the programResults entry; leaderboard re-sums on poll. `recomputeSerieTotals` uses the same deduction rule as the kiosk `ScoreTable`.
- **No-regression:** `completeSerie` keeps append semantics (`replaceExisting=false`); the kiosk `ShooterPlayPage`/`playSessionStore` and Task-C non-editable `StepScorecard` callers are untouched.
- **409 path:** `ConflictException` rethrown by the controller (not re-wrapped), matching `releaseNextPasse`.

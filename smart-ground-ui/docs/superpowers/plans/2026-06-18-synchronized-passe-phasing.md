# Synchronized Passe Phasing (Task D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax. **Cross-stack:** Tasks 1–4 are in `smart-ground-backend` (branch `feat/synchronized-passe-phasing`); Tasks 5–8 are in `smart-ground-ui` (branch `feat/synchronized-passe-phasing`). Do backend first — the frontend depends on the contract.

**Goal:** Gate competition play to an admin-released Passe so all Rotten advance together; a finished Rotte waits ("Warte auf andere Rotten") until the admin clicks "Nächste Passe freigeben".

**Architecture:** A persisted `releasedPasseIndex` on the session, exposed via the progress payload, gates which Passe's Serien are playable. The admin advances it through a guarded endpoint; all clients read it via the existing progress poll.

**Tech Stack:** Java 25 / Spring Boot 4 (contract-first OpenAPI), JPA; Vue 3 `<script setup>`, Pinia, Vitest.

**Spec:** `docs/superpowers/specs/2026-06-18-synchronized-passe-phasing-design.md`

---

## BACKEND (repo: smart-ground-backend)

### Task 1: OpenAPI contract — releasedPasseIndex + release endpoint

**Files:** Modify `src/main/resources/static/openapi.yaml`

- [ ] **Step 1: Add `releasedPasseIndex` to `SessionProgressResponse`.** Under `SessionProgressResponse.properties` (after `groups`), add:

```yaml
        releasedPasseIndex:
          type: integer
          description: Highest 0-based Passe index released for play (admin-gated).
```

- [ ] **Step 2: Add the release path.** Immediately after the `/api/sessions/{sessionId}/progress:` path block, add:

```yaml
  /api/sessions/{sessionId}/passen/release:
    post:
      summary: Release the next Passe for play (admin gate)
      description: >
        Advances the session's releasedPasseIndex by one. Allowed only when every
        Rotte has completed every Serie of the current released Passe and a next
        Passe exists. Returns the updated progress.
      operationId: releaseNextPasse
      tags: [Session]
      parameters:
        - name: sessionId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Progress after advancing
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SessionProgressResponse'
        '404':
          description: Session not found
        '409':
          description: Current Passe not complete, or already at the last Passe
```

- [ ] **Step 3: Regenerate sources.**

Run: `./mvnw -q generate-sources`
Expected: success. The generated `ch.jp.smartground.api.SessionApi` now declares `releaseNextPasse(UUID sessionId)`, and `ch.jp.smartground.model.SessionProgressResponse` has `getReleasedPasseIndex()/setReleasedPasseIndex(Integer)`.

- [ ] **Step 4: Verify the generated artifacts.**

Run: `grep -rn "releaseNextPasse" target/generated-sources | head` and `grep -rn "ReleasedPasseIndex" target/generated-sources | head`
Expected: both found.

- [ ] **Step 5: Commit.**

```bash
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] api: releasedPasseIndex in progress + release-next-passe endpoint"
```

---

### Task 2: LiveSession.releasedPasseIndex

**Files:** Modify `src/main/java/ch/jp/shooting/model/LiveSession.java`

- [ ] **Step 1: Add the field.** After the `status` field block, add:

```java
    /** Höchster (0-basierter) Passe-Index, der zum Spielen freigegeben ist (Admin-Gate). */
    @Column(name = "released_passe_index", nullable = false)
    private int releasedPasseIndex = 0;
```

- [ ] **Step 2: Add getter/setter** (near the other accessors):

```java
    public int getReleasedPasseIndex() { return releasedPasseIndex; }
    public void setReleasedPasseIndex(int releasedPasseIndex) { this.releasedPasseIndex = releasedPasseIndex; }
```

- [ ] **Step 3: Compile.**

Run: `./mvnw -q compile`
Expected: success.

- [ ] **Step 4: Commit.**

```bash
git add src/main/java/ch/jp/shooting/model/LiveSession.java
git commit -m "[backend] competition: persist releasedPasseIndex on LiveSession"
```

---

### Task 3: CompetitionProgressService — expose + advance

**Files:** Modify `src/main/java/ch/jp/shooting/service/CompetitionProgressService.java`, `src/main/java/ch/jp/shooting/exception/` (reuse `ConflictException`); Test `src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java`

- [ ] **Step 1: Write failing service tests.** Append to `CompetitionProgressServiceTest` (mirrors the existing Mockito setup — `session` is `new LiveSession(SessionType.COMPETITION, SessionStatus.ACTIVE)` with a `passe-1` snapshot; add a helper to build a 2-Passe snapshot and stub CSR rows). Add:

```java
    @Test
    void getProgressIncludesReleasedPasseIndex() throws Exception {
        session.setReleasedPasseIndex(1);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(sessionId)).thenReturn(List.of());
        SessionProgressResponse resp = progressService.getProgress(sessionId);
        assertEquals(1, resp.getReleasedPasseIndex());
    }

    @Test
    void releaseNextPasseAdvancesWhenCurrentPasseCompleteByAllGroups() throws Exception {
        // session has 2 passen, each with serie "s0p0"/"s0p1"; one group completed all of passe 0.
        twoPasseSession();
        UUID gid = group.getId();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(sessionId)).thenReturn(List.of(group));
        when(csrRepository.findBySessionId(sessionId)).thenReturn(List.of(
            csr(gid, 0, UUID.fromString("00000000-0000-0000-0000-0000000000a0"))));
        // (build the CSR/serie ids to match twoPasseSession's passe 0 serieIds)

        SessionProgressResponse resp = progressService.releaseNextPasse(sessionId);
        assertEquals(1, session.getReleasedPasseIndex());
        assertEquals(1, resp.getReleasedPasseIndex());
        verify(sessionRepository).save(session);
    }

    @Test
    void releaseNextPasseRejectsWhenCurrentPasseIncomplete() {
        twoPasseSession();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(sessionId)).thenReturn(List.of(group));
        when(csrRepository.findBySessionId(sessionId)).thenReturn(List.of()); // nothing done
        assertThrows(ch.jp.shooting.exception.ConflictException.class,
            () -> progressService.releaseNextPasse(sessionId));
        assertEquals(0, session.getReleasedPasseIndex());
    }

    @Test
    void releaseNextPasseRejectsAtLastPasse() {
        twoPasseSession();
        session.setReleasedPasseIndex(1); // already last (index 1 of 2)
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        assertThrows(ch.jp.shooting.exception.ConflictException.class,
            () -> progressService.releaseNextPasse(sessionId));
    }
```

Add helpers in the test class (adapt ids to your `PasseSnapshot.serieIds` strings — the key is that `twoPasseSession()` builds a `programSnapshots` JSON of two passen each with one serie, and `csr(gid, passeIndex, serieId)` builds a `CompetitionSerieResult` whose `getGroup().getId()`, `getPasseIndex()`, `getSerieId()` match):

```java
    private void twoPasseSession() {
        try {
            PasseSnapshot p0 = new PasseSnapshot(); p0.id = "p0"; p0.name = "Passe 1";
            p0.serieIds = List.of("00000000-0000-0000-0000-0000000000a0");
            PasseSnapshot p1 = new PasseSnapshot(); p1.id = "p1"; p1.name = "Passe 2";
            p1.serieIds = List.of("00000000-0000-0000-0000-0000000000b0");
            session.setProgramSnapshots(objectMapper.writeValueAsString(new PasseSnapshot[]{p0, p1}));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private CompetitionSerieResult csr(UUID groupId, int passeIndex, UUID serieId) {
        ShooterGroup g = new ShooterGroup(); setId(g, groupId);
        CompetitionSerieResult c = new CompetitionSerieResult(session, g, passeIndex, serieId);
        return c;
    }
    // setId: reflectively set ShooterGroup.id if no setter (mirror existing test helpers).
```

(If the existing test already has an id-setter helper for `ShooterGroup`/`group`, reuse it; ensure `group.getId()` equals the `groupId` used in the `csr(...)` row so the completion check matches.)

- [ ] **Step 2: Run tests, verify failure.**

Run: `./mvnw -q -Dtest=CompetitionProgressServiceTest test`
Expected: FAIL — `releaseNextPasse` / `getReleasedPasseIndex` not present.

- [ ] **Step 3: Implement.** In `CompetitionProgressService`:

In `buildSessionProgressResponse`, after `response.setSessionId(sessionId);` add:

```java
        response.setReleasedPasseIndex(session.getReleasedPasseIndex());
```

Add a single-Passe completion check and the action:

```java
    /** True when every group has completed every Serie of the given Passe index. */
    private boolean isPasseDoneForAllGroups(LiveSession session, int passeIndex) throws Exception {
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());
        if (passeIndex < 0 || passeIndex >= passen.length) return false;
        List<ShooterGroup> groups = groupRepository.findBySessionId(session.getId());
        if (groups.isEmpty()) return false;
        Set<String> done = csrRepository.findBySessionId(session.getId()).stream()
            .map(c -> c.getGroup().getId() + ":" + c.getPasseIndex() + ":" + c.getSerieId())
            .collect(java.util.stream.Collectors.toSet());
        for (ShooterGroup g : groups) {
            for (String serieIdStr : passen[passeIndex].serieIds) {
                if (!done.contains(g.getId() + ":" + passeIndex + ":" + serieIdStr)) return false;
            }
        }
        return true;
    }

    /** Admin-Gate: gibt die nächste Passe frei, wenn die aktuelle vollständig ist. */
    public SessionProgressResponse releaseNextPasse(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ConflictException("Passe-Freigabe nur im Status ACTIVE möglich");
        }
        PasseSnapshot[] passen = parsePassen(session.getProgramSnapshots());
        int idx = session.getReleasedPasseIndex();
        if (idx + 1 >= passen.length) {
            throw new ConflictException("Keine weitere Passe vorhanden");
        }
        if (!isPasseDoneForAllGroups(session, idx)) {
            throw new ConflictException("Aktuelle Passe ist noch nicht von allen Rotten abgeschlossen");
        }
        session.setReleasedPasseIndex(idx + 1);
        sessionRepository.save(session);
        return buildSessionProgressResponse(sessionId);
    }
```

Add the import: `import ch.jp.shooting.exception.ConflictException;`

- [ ] **Step 4: Run tests, verify pass.**

Run: `./mvnw -q -Dtest=CompetitionProgressServiceTest test`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/ch/jp/shooting/service/CompetitionProgressService.java src/test/java/ch/jp/shooting/service/CompetitionProgressServiceTest.java
git commit -m "[backend] competition: releaseNextPasse with completion guard + progress field"
```

---

### Task 4: SessionController.releaseNextPasse

**Files:** Modify `src/main/java/ch/jp/shooting/api/SessionController.java`

- [ ] **Step 1: Implement the generated method.** Add (near `getSessionProgress`):

```java
    @Override
    public ResponseEntity<SessionProgressResponse> releaseNextPasse(UUID sessionId) {
        try {
            return ResponseEntity.ok(progressService.releaseNextPasse(sessionId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
```

(Match the surrounding methods' exception style — if `getSessionProgress` wraps checked exceptions the same way, mirror it exactly so `ConflictException` propagates to the handler.)

- [ ] **Step 2: Compile + full backend test.**

Run: `./mvnw -q test`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 3: Commit.**

```bash
git add src/main/java/ch/jp/shooting/api/SessionController.java
git commit -m "[backend] api: wire releaseNextPasse endpoint"
```

---

## FRONTEND (repo: smart-ground-ui)

### Task 5: Store gating + release action

**Files:** Modify `src/services/wettkampfApi.js`, `src/stores/competitionEventStore.js`; Test `src/stores/__tests__/competitionEventStore.test.js`

- [ ] **Step 1: Write failing store tests.** Append to `competitionEventStore.test.js`. Add `releaseNextPasse: vi.fn()` to the `vi.mock('@/services/wettkampfApi.js', …)` list. Then:

```javascript
  describe('synchronized passe phasing', () => {
    const buildInstanceWith = (store, releasedPasseIndex = 0) => {
      const ev = {
        id: 'c1', name: 'WK', status: 'ACTIVE',
        groups: [
          { id: 'r1', name: 'Rotte A', members: [{ id: 'p1', displayName: 'A' }] },
          { id: 'r2', name: 'Rotte B', members: [{ id: 'p2', displayName: 'B' }] },
        ],
        passen: [
          { id: 'pa0', name: 'Passe 1', serien: [{ id: 's0', alias: 'S0', rangeId: 'rg1', steps: [] }] },
          { id: 'pa1', name: 'Passe 2', serien: [{ id: 's1', alias: 'S1', rangeId: 'rg1', steps: [] }] },
        ],
      }
      const inst = store.initCompetitionInstance(ev)
      inst.releasedPasseIndex = releasedPasseIndex
      for (const r of inst.rotten) { r.assignedRangeId = 'rg1'; r.status = 'active' }
      return inst
    }

    it('only surfaces blocks from the released passe', () => {
      const store = useCompetitionEventStore()
      buildInstanceWith(store, 0)
      const blocks = store.getBlocksForRange('rg1')
      expect(blocks.map(b => b.serieId)).toEqual(['s0', 's0']) // both rotten, passe 0 only
    })

    it('a rotte that finished the released passe surfaces no blocks (waiting)', () => {
      const store = useCompetitionEventStore()
      const inst = buildInstanceWith(store, 0)
      inst.rotten[0].phases[0].blocks[0].status = 'done'
      const blocks = store.getBlocksForRange('rg1')
      expect(blocks.map(b => b.rotteId)).toEqual(['r2']) // r1 done with passe 0 → waiting
    })

    it('_completeCompetitionBlock does not advance a rotte past releasedPasseIndex', async () => {
      const store = useCompetitionEventStore()
      const inst = buildInstanceWith(store, 0)
      await store.markBlockDone('c1', 's0', [], 'r1')
      // r1 finished passe 0's only block, but passe 1 is not released → no passe-1 blocks surface
      const blocks = store.getActiveCompetitionRotten('rg1').flatMap(r => r.blocks.map(b => b.serieId))
      expect(blocks).not.toContain('s1')
    })

    it('releaseNextPasse posts and bumps the instance index', async () => {
      api.releaseNextPasse.mockResolvedValue({ releasedPasseIndex: 1 })
      const store = useCompetitionEventStore()
      const inst = buildInstanceWith(store, 0)
      await store.releaseNextPasse('c1')
      expect(api.releaseNextPasse).toHaveBeenCalledWith('c1')
      expect(inst.releasedPasseIndex).toBe(1)
    })

    it('isReleasedPasseComplete is true only when all rotten finished the released passe', () => {
      const store = useCompetitionEventStore()
      const inst = buildInstanceWith(store, 0)
      expect(store.isReleasedPasseComplete('c1')).toBe(false)
      for (const r of inst.rotten) r.phases[0].blocks[0].status = 'done'
      expect(store.isReleasedPasseComplete('c1')).toBe(true)
    })
  })
```

- [ ] **Step 2: Run, verify failure.**

Run: `npm run test -- src/stores/__tests__/competitionEventStore.test.js`
Expected: FAIL.

- [ ] **Step 3: Implement.** In `src/services/wettkampfApi.js` add:

```javascript
export const releaseNextPasse = (sessionId) => apiClient.post(`/sessions/${sessionId}/passen/release`)
```

In `competitionEventStore.js`:

(a) In `_buildInstance`, add `releasedPasseIndex: 0` to the `instance` object literal.

(b) In `_hydrateProgress`, after `if (!inst || !progress?.groups) return`, add:

```javascript
    inst.releasedPasseIndex = progress.releasedPasseIndex ?? 0
```

and, at the end of the per-group loop where it sets `rotte.currentPhaseIndex`, force it to the released index for display:

```javascript
      rotte.currentPhaseIndex = inst.releasedPasseIndex
      rotte.status = firstOpen === -1 ? 'done' : rotte.status
```

(Keep the existing per-block done-marking from `completedSerien`; only the active-phase pointer changes to the released index.)

(c) Replace the phase lookup in `getActiveCompetitionRotten` and `getBlocksForRange`: use `const phaseIdx = inst.releasedPasseIndex ?? 0; const phase = rotte.phases[phaseIdx]` instead of `rotte.phases[rotte.currentPhaseIndex]`. Everything else (filtering `b.status !== 'done'`, rangeId match) stays.

(d) In `_completeCompetitionBlock`, after marking the block done and updating `phase.status`, REMOVE the per-rotte `currentPhaseIndex` advancement. Keep only: when `phase.blocks.every(done)` → `phase.status = 'done'`; and the existing "all rotten all phases done → push to completedCompetitionInstances" check (compute via every rotte every phase done, not via currentPhaseIndex). Concretely:

```javascript
  const _completeCompetitionBlock = (inst, blockId, playerResults, rotteId) => {
    const rotte = inst.rotten?.find(r => r.rotteId === rotteId)
    if (!rotte) return
    const phaseIdx = inst.releasedPasseIndex ?? 0
    const phase = rotte.phases[phaseIdx]
    if (!phase) return
    const block = phase.blocks.find(b => b.blockId === blockId)
    if (!block) return
    block.status = 'done'
    block.completedAt = Date.now()
    block.result = { playerResults }
    if (phase.blocks.every(b => b.status === 'done')) phase.status = 'done'
    const allDone = inst.rotten.every(r => r.phases.every(p => p.blocks.every(b => b.status === 'done')))
    if (allDone) {
      rotte.status = 'done'
      completedCompetitionInstances.value.push({ ...inst, completedAt: Date.now() })
      competitionInstances.value = competitionInstances.value.filter(i => i.instanceId !== inst.instanceId)
    }
  }
```

(e) Add the action + helpers:

```javascript
  const releaseNextPasse = async (instanceId) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    const sessionId = inst?.sessionId ?? instanceId
    const res = await wettkampfApi.releaseNextPasse(sessionId)
    if (inst) {
      inst.releasedPasseIndex = res?.releasedPasseIndex ?? (inst.releasedPasseIndex + 1)
      const phase = inst.rotten[0]?.phases[inst.releasedPasseIndex]
      if (phase) for (const r of inst.rotten) {
        const p = r.phases[inst.releasedPasseIndex]
        if (p && p.status !== 'done') p.status = 'active'
      }
    }
    return res
  }

  const isReleasedPasseComplete = (instanceId) => {
    const inst = competitionInstances.value.find(i => i.instanceId === instanceId)
    if (!inst) return false
    const idx = inst.releasedPasseIndex ?? 0
    return inst.rotten.length > 0 &&
      inst.rotten.every(r => (r.phases[idx]?.blocks ?? []).every(b => b.status === 'done') && (r.phases[idx]?.blocks?.length ?? 0) > 0)
  }

  const isRotteWaitingForPasse = (inst, rotte) => {
    const idx = inst?.releasedPasseIndex ?? 0
    const blocks = rotte?.phases?.[idx]?.blocks ?? []
    return blocks.length > 0 && blocks.every(b => b.status === 'done') && rotte.status !== 'done'
  }
```

Export `releaseNextPasse`, `isReleasedPasseComplete`, `isRotteWaitingForPasse` in the returned object.

- [ ] **Step 4: Run, verify pass.**

Run: `npm run test -- src/stores/__tests__/competitionEventStore.test.js`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add src/services/wettkampfApi.js src/stores/competitionEventStore.js src/stores/__tests__/competitionEventStore.test.js
git commit -m "[ui] competition: gate play on releasedPasseIndex + release action"
```

---

### Task 6: useCompetitionProgress follows releasedPasseIndex

**Files:** Modify `src/composables/useCompetitionProgress.js`; Test `src/composables/__tests__/useCompetitionProgress.test.js`

- [ ] **Step 1: Write failing test.** Append:

```javascript
  it('uses the instance releasedPasseIndex as the active phase', () => {
    const instance = ref({
      releasedPasseIndex: 0,
      rotten: [
        { rotteId: 'r1', name: 'A', currentPhaseIndex: 0, phases: [
          { passeName: 'Passe 1', status: 'done', blocks: [{ serieAlias: 'S0', status: 'done' }] },
          { passeName: 'Passe 2', status: 'pending', blocks: [{ serieAlias: 'S1', status: 'pending' }] },
        ] },
      ],
    })
    const { passenProgress, activePhaseIndex } = useCompetitionProgress(instance)
    // passe 0 done by all, but not released to 1 yet → active phase stays 0
    expect(activePhaseIndex.value).toBe(0)
    expect(passenProgress.value[0].status).toBe('aktiv')
  })
```

(Use the file's existing import style for `ref`/`useCompetitionProgress`.)

- [ ] **Step 2: Run, verify failure.**

Run: `npm run test -- src/composables/__tests__/useCompetitionProgress.test.js`
Expected: FAIL (derived barrier would advance to 1).

- [ ] **Step 3: Implement.** In `useCompetitionProgress.js`:

```javascript
  const activePhaseIndex = computed(() => {
    if (_rotten.value.length === 0) return 0
    const released = instance.value?.releasedPasseIndex
    if (Number.isInteger(released)) return released
    const phaseCount = _rotten.value[0].phases?.length ?? 0
    for (let i = 0; i < phaseCount; i++) {
      if (!_rotten.value.every(r => r.phases[i]?.status === 'done')) return i
    }
    return Math.max(0, phaseCount - 1)
  })
```

In `passenProgress`, change the `aktiv` test to key off `activePhaseIndex`:

```javascript
  const passenProgress = computed(() => {
    if (_rotten.value.length === 0) return []
    const activeIdx = activePhaseIndex.value
    return (_rotten.value[0].phases ?? []).map((phase, i) => {
      const allDone = _rotten.value.every(r => r.phases[i]?.status === 'done')
      const status = allDone ? 'fertig' : i === activeIdx ? 'aktiv' : 'offen'
      return { phaseIndex: i, passeName: phase.passeName, status }
    })
  })
```

(Move `activePhaseIndex` above `passenProgress` if needed for declaration order. `serieCards` already reads `activePhaseIndex.value` — unchanged.)

- [ ] **Step 4: Run, verify pass.**

Run: `npm run test -- src/composables/__tests__/useCompetitionProgress.test.js`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add src/composables/useCompetitionProgress.js src/composables/__tests__/useCompetitionProgress.test.js
git commit -m "[ui] competition: progress composable follows releasedPasseIndex"
```

---

### Task 7: Admin "Nächste Passe freigeben" button

**Files:** Modify `src/components/competition/ActiveCompetitionPanel.vue`; Test `src/components/__tests__/ActiveCompetitionPanel.test.js`

- [ ] **Step 1: Write failing tests.** Append inside the `describe`:

```javascript
  it('disables "Nächste Passe freigeben" until the released passe is complete', async () => {
    wettkampfApi.getProgress.mockResolvedValue({
      releasedPasseIndex: 0,
      groups: [
        { groupId: 'g1', completions: [{ passeIndex: 0, completed: false }, { passeIndex: 1, completed: false }] },
      ],
    })
    const event = { ...makeEvent(), passen: [{ id: 'pa0', name: 'Passe 1' }, { id: 'pa1', name: 'Passe 2' }] }
    const { wrapper } = await mountPanel(event)
    await flushPromises()
    const btn = wrapper.find('.release-passe-btn')
    expect(btn.exists()).toBe(true)
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('enables and calls releaseNextPasse when the released passe is complete', async () => {
    wettkampfApi.getProgress.mockResolvedValue({
      releasedPasseIndex: 0,
      groups: [
        { groupId: 'g1', completions: [{ passeIndex: 0, completed: true }, { passeIndex: 1, completed: false }] },
      ],
    })
    const store = useCompetitionEventStore()
    const spy = vi.spyOn(store, 'releaseNextPasse').mockResolvedValue({ releasedPasseIndex: 1 })
    const event = { ...makeEvent(), id: 'c1', passen: [{ id: 'pa0', name: 'Passe 1' }, { id: 'pa1', name: 'Passe 2' }] }
    const { wrapper } = await mountPanel(event)
    await flushPromises()
    const btn = wrapper.find('.release-passe-btn')
    expect(btn.attributes('disabled')).toBeUndefined()
    await btn.trigger('click')
    expect(spy).toHaveBeenCalledWith('c1')
  })
```

(`ActiveCompetitionPanel.test.js` already imports `useCompetitionEventStore`? If not, add `import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'`. The mock helper `mountPanel` installs pinia via the router-plugin global; ensure `setActivePinia` runs in `beforeEach` — it does.)

- [ ] **Step 2: Run, verify failure.**

Run: `npm run test -- src/components/__tests__/ActiveCompetitionPanel.test.js`
Expected: FAIL — no `.release-passe-btn`.

- [ ] **Step 3: Implement.** In `ActiveCompetitionPanel.vue`:

Add to `<script setup>`:

```javascript
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'
const competitionStore = useCompetitionEventStore()

const releasedPasseIndex = computed(() => progressData.value?.releasedPasseIndex ?? 0)

const releasedPasseComplete = computed(() => {
  const groups = progressData.value?.groups ?? []
  if (groups.length === 0) return false
  return groups.every(g => g.completions?.[releasedPasseIndex.value]?.completed === true)
})

const canReleaseNextPasse = computed(() =>
  releasedPasseComplete.value && releasedPasseIndex.value + 1 < (props.event.passen?.length ?? 0)
)

const releasing = ref(false)
const releaseNext = async () => {
  if (!canReleaseNextPasse.value || releasing.value) return
  releasing.value = true
  try {
    await competitionStore.releaseNextPasse(props.event.id)
    await refresh()
  } catch (e) {
    console.error('[ActiveCompetitionPanel] release passe failed:', e)
  } finally {
    releasing.value = false
  }
}
```

In the template, below the Passen progress bar (before the tab bar), add the button — only render when there is more than one Passe:

```html
    <button
      v-if="(event.passen?.length ?? 0) > 1"
      class="release-passe-btn"
      :disabled="!canReleaseNextPasse || releasing"
      @click="releaseNext"
    >
      Nächste Passe freigeben
    </button>
```

Add a minimal scoped style (reuse the existing button token look):

```css
.release-passe-btn {
  align-self: flex-start;
  padding: 8px 16px; border-radius: 10px;
  background: var(--sg-accent-hover); border: none; color: #fff;
  font-size: 13px; font-weight: 700; font-family: inherit; cursor: pointer;
}
.release-passe-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.release-passe-btn:hover:not(:disabled) { background: var(--sg-accent); }
```

- [ ] **Step 4: Run, verify pass.**

Run: `npm run test -- src/components/__tests__/ActiveCompetitionPanel.test.js`
Expected: PASS.

- [ ] **Step 5: Commit.**

```bash
git add src/components/competition/ActiveCompetitionPanel.vue src/components/__tests__/ActiveCompetitionPanel.test.js
git commit -m "[ui] competition: admin Nächste Passe freigeben button"
```

---

### Task 8: Kiosk "Warte auf andere Rotten" state

**Files:** Modify `src/components/competition/RangePanelCard.vue`; Test `src/components/competition/__tests__/RangePanelCard.test.js` (new)

- [ ] **Step 1: Write the test (new file).**

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import RangePanelCard from '../RangePanelCard.vue'
import { useCompetitionEventStore } from '@/stores/competitionEventStore.js'

vi.mock('@/components/Icons.vue', () => ({ default: { template: '<span />' } }))

const seedInstance = (store, { releasedDone }) => {
  const ev = {
    id: 'c1', name: 'WK', status: 'ACTIVE',
    groups: [{ id: 'r1', name: 'Rotte A', members: [{ id: 'p1', displayName: 'A' }] }],
    passen: [
      { id: 'pa0', name: 'Passe 1', serien: [{ id: 's0', alias: 'S0', rangeId: 'rg1', steps: [] }] },
      { id: 'pa1', name: 'Passe 2', serien: [{ id: 's1', alias: 'S1', rangeId: 'rg1', steps: [] }] },
    ],
  }
  const inst = store.initCompetitionInstance(ev)
  inst.releasedPasseIndex = 0
  const r = inst.rotten[0]
  r.assignedRangeId = 'rg1'; r.status = 'active'
  if (releasedDone) r.phases[0].blocks[0].status = 'done'
  return inst
}

const mountCard = () => mount(RangePanelCard, {
  props: { range: { id: 'rg1', name: 'Stand 1' }, instanceId: 'c1' },
  global: { stubs: { Icons: true, RotteProgressRow: true } },
})

describe('RangePanelCard waiting state', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows "Warte auf andere Rotten" when the assigned rotte finished the released passe', () => {
    const store = useCompetitionEventStore()
    seedInstance(store, { releasedDone: true })
    const wrapper = mountCard()
    expect(wrapper.text()).toContain('Warte auf andere Rotten')
  })

  it('does not show the waiting state while the rotte still has blocks', () => {
    const store = useCompetitionEventStore()
    seedInstance(store, { releasedDone: false })
    const wrapper = mountCard()
    expect(wrapper.text()).not.toContain('Warte auf andere Rotten')
  })
})
```

- [ ] **Step 2: Run, verify failure.**

Run: `npm run test -- src/components/competition/__tests__/RangePanelCard.test.js`
Expected: FAIL.

- [ ] **Step 3: Implement.** In `RangePanelCard.vue` `<script setup>` add:

```javascript
const waitingForOthers = computed(() =>
  activeRotte.value
    ? competitionEventStore.isRotteWaitingForPasse(inst.value, activeRotte.value)
    : false,
)
```

In the template, inside the `<template v-if="activeRotte">` block, wrap the play actions so that when `waitingForOthers` is true the CTA is replaced by a waiting notice. Change:

```html
      <template v-if="activeRotte">
        <RotteProgressRow :rotte="activeRotte" />
        <div class="players-row">…</div>
        <div class="action-row">…</div>
      </template>
```

to:

```html
      <template v-if="activeRotte">
        <RotteProgressRow :rotte="activeRotte" />
        <div class="players-row">{{ activeRotte.players.map(p => p.displayName ?? p.name).join(' · ') }}</div>
        <div v-if="waitingForOthers" class="waiting-notice">Warte auf andere Rotten</div>
        <div v-else class="action-row">
          <button class="btn btn--ghost" @click="handlePause"><Icons icon="stop" :size="13" color="rgba(255,255,255,0.7)" /> Pause</button>
          <button class="btn btn--primary" @click="handleVerwalten"><Icons icon="arrowR" :size="13" color="#1a1a2e" /> Verwalten</button>
        </div>
      </template>
```

Add scoped style:

```css
.waiting-notice {
  font-size: 13px; font-weight: 600; color: #f6ad55;
  background: rgba(246, 173, 85, 0.1); border: 1px solid rgba(246, 173, 85, 0.25);
  border-radius: 10px; padding: 10px 12px; text-align: center;
}
```

- [ ] **Step 4: Run, verify pass.**

Run: `npm run test -- src/components/competition/__tests__/RangePanelCard.test.js`
Expected: PASS.

- [ ] **Step 5: Full frontend verification.**

Run: `npm run test`
Expected: whole suite passes.

Run: `npm run lint:check`
Expected: no new errors in touched files.

- [ ] **Step 6: Commit.**

```bash
git add src/components/competition/RangePanelCard.vue src/components/competition/__tests__/RangePanelCard.test.js
git commit -m "[ui] competition: kiosk Warte auf andere Rotten state"
```

---

## Self-Review Notes

- **Spec coverage:** persisted field + endpoint → Tasks 1–4; progress exposure → Tasks 1,3; gating + waiting + release action → Task 5; display follows gate → Task 6; admin release button → Task 7; kiosk waiting state → Task 8. All covered.
- **Contract consistency:** `releasedPasseIndex` (int) on `SessionProgressResponse`; frontend reads `progress.releasedPasseIndex`; release endpoint returns the updated progress; store action reads `res.releasedPasseIndex`. Names match across the stack.
- **409 path:** guard failures throw `ConflictException` (existing handler → 409); `SessionController` must propagate it (don't swallow) — mirror `getSessionProgress`'s exception style.
- **YAGNI / dead code:** per-Rotte `currentPhaseIndex` advancement removed from `_completeCompetitionBlock`; `currentPhaseIndex` retained only as a display pointer synced to `releasedPasseIndex`.
- **Backend test ids:** the `csr(...)` rows in Task 3's tests must use serieIds equal to the `PasseSnapshot.serieIds` strings, and `group.getId()` must equal the row's groupId, or the completion check won't match — verify against the existing test's id helpers when implementing.

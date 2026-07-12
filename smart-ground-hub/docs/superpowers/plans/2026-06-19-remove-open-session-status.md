# Remove the vestigial `OPEN` session status — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete the `OPEN` value from the competition `SessionStatus` so the state machine becomes `SETUP → ACTIVE → PRE_COMPLETE → COMPLETED` (+ `ABANDONED` from any non-terminal), and collapse the UI's two-step go-live into a single `startEvent`.

**Architecture:** `OPEN` is vestigial — it grants no capability `SETUP` lacks, and nothing branches on it. The backend enum, its `validateStatusTransition` arm, seven `SETUP || OPEN` edit-guards, and the OpenAPI contract drop `OPEN`. The UI's `goLive`/`openEvent` store actions (which only existed to hide the `SETUP → OPEN → ACTIVE` two-step) are deleted; the view calls `startEvent` directly, which now advances `SETUP` straight to `ACTIVE`.

**Tech Stack:** Java 25 / Spring Boot 4 / JUnit 5 + Mockito (backend); Vue 3 / Pinia / Vitest (frontend). Two separate git repos: `smart-ground-backend` and `smart-ground-ui`.

**Spec:** `smart-ground-backend/docs/superpowers/specs/2026-06-19-remove-open-session-status-design.md`

---

## File Structure

**Backend (`smart-ground-backend`)**
- Modify: `src/main/java/ch/jp/shooting/model/SessionStatus.java` — drop `OPEN`
- Modify: `src/main/java/ch/jp/shooting/service/SessionService.java` — transition arm + 7 guards + javadoc
- Modify: `src/main/java/ch/jp/shooting/model/LiveSession.java:43` — status comment
- Modify: `src/main/resources/static/openapi.yaml` — enum ×2, transition prose ×2, delete summary
- Modify: `src/test/java/ch/jp/shooting/service/SessionServiceStatusTest.java`
- Create: `src/test/java/ch/jp/shooting/service/SessionServiceTransitionTest.java`

**Frontend (`smart-ground-ui`)**
- Modify: `src/stores/competitionEventStore.js` — remove `openEvent`/`goLive`, fix `planningEvents`, export list
- Modify: `src/views/admin/WettkampfDetailView.vue` — render branch, status label, start call, dead CSS, comments
- Modify: `src/stores/__tests__/competitionEventStore.test.js`
- Modify: `src/views/admin/__tests__/WettkampfDetailView.test.js`
- Modify: `src/services/__tests__/wettkampfApi.test.js`

**Run commands** (Windows; use the Bash tool / git-bash):
- Backend: `cd smart-ground-backend && ./mvnw clean test` (regenerates OpenAPI sources via the `generate-sources` phase)
- Frontend: `cd smart-ground-ui && npm run test` and `npm run lint:check`

---

## Task 1: Backend — failing tests for the collapsed state machine (RED)

**Files:**
- Modify: `smart-ground-backend/src/test/java/ch/jp/shooting/service/SessionServiceStatusTest.java`
- Create: `smart-ground-backend/src/test/java/ch/jp/shooting/service/SessionServiceTransitionTest.java`

- [ ] **Step 1: Rewrite `SessionServiceStatusTest` to expect 5 values and no `OPEN`**

Replace the entire file body of `SessionServiceStatusTest.java` with:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.SessionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionServiceStatusTest {

    @Test
    void sessionStatus_hasPreCompleteButNoOpen() {
        assertNotNull(SessionStatus.valueOf("PRE_COMPLETE"));
        assertThrows(IllegalArgumentException.class, () -> SessionStatus.valueOf("OPEN"));
    }

    @Test
    void sessionStatus_hasFiveValues() {
        assertEquals(5, SessionStatus.values().length);
    }
}
```

- [ ] **Step 2: Create `SessionServiceTransitionTest` asserting `SETUP → ACTIVE` is valid and `SETUP → COMPLETED` is not**

Create `SessionServiceTransitionTest.java` with:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.SessionStatus;
import ch.jp.shooting.model.SessionType;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.PlayerResultRepository;
import ch.jp.shooting.repository.SessionPlayerRepository;
import ch.jp.shooting.repository.SessionTemplateRepository;
import ch.jp.shooting.repository.ShooterGroupRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTransitionTest {

    @Mock LiveSessionRepository sessionRepository;
    @Mock ShooterGroupRepository groupRepository;
    @Mock SessionPlayerRepository playerRepository;
    @Mock PlayerResultRepository resultRepository;
    @Mock SessionTemplateRepository templateRepository;
    @Mock UserRepository userRepository;
    @Mock PasseRepository passeRepository;
    @Mock TiebreakerService tiebreakerService;

    SessionService service;
    UUID sessionId;
    LiveSession session;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                sessionRepository, groupRepository, playerRepository, resultRepository,
                templateRepository, userRepository, passeRepository,
                new ObjectMapper(), tiebreakerService);
        sessionId = UUID.randomUUID();
        session = new LiveSession();
        session.setName("Wettkampf");
        session.setType(SessionType.COMPETITION);
        session.setStatus(SessionStatus.SETUP);
        lenient().when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.save(any(LiveSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void updateSessionStatus_setupToActive_isAllowed() {
        service.updateSessionStatus(sessionId, "active");
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
    }

    @Test
    void updateSessionStatus_setupToCompleted_isRejected() {
        assertThrows(IllegalStateException.class,
                () -> service.updateSessionStatus(sessionId, "completed"));
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail (RED)**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=SessionServiceStatusTest,SessionServiceTransitionTest`
Expected: FAIL —
- `sessionStatus_hasFiveValues` fails (enum still has 6 values)
- `sessionStatus_hasPreCompleteButNoOpen` fails (`valueOf("OPEN")` does not throw — `OPEN` still exists)
- `updateSessionStatus_setupToActive_isAllowed` fails (current rule only permits `SETUP → OPEN`, so this throws `IllegalStateException`)
- `updateSessionStatus_setupToCompleted_isRejected` passes (already rejected)

---

## Task 2: Backend — remove `OPEN` from enum, state machine, guards, contract (GREEN)

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/model/SessionStatus.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/SessionService.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/model/LiveSession.java:43`
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml`

- [ ] **Step 1: Drop `OPEN` from the enum**

In `SessionStatus.java`, change line 4 from:

```java
    SETUP, OPEN, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED
```

to:

```java
    SETUP, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED
```

- [ ] **Step 2: Collapse the transition arm in `SessionService.validateStatusTransition`**

In `SessionService.java`, replace these two lines:

```java
            case SETUP -> to == SessionStatus.OPEN;
            case OPEN  -> to == SessionStatus.ACTIVE;
```

with the single line:

```java
            case SETUP -> to == SessionStatus.ACTIVE;
```

- [ ] **Step 3: Simplify the seven edit-guards**

In `SessionService.java`, every occurrence of this guard condition:

```java
session.getStatus() != SessionStatus.SETUP && session.getStatus() != SessionStatus.OPEN
```

becomes:

```java
session.getStatus() != SessionStatus.SETUP
```

and every occurrence of this variant (uses the local `st`):

```java
st != SessionStatus.SETUP && st != SessionStatus.OPEN
```

becomes:

```java
st != SessionStatus.SETUP
```

These appear in `createGroup`, `updateGroup`, `deleteGroup`, `addMember`, `removeMember`, `updateGroupApi`, and `addMemberApi`. Use a find-and-replace of the two patterns above (the first matches `createGroup`; the rest use `st`).

- [ ] **Step 4: Update the German javadoc that mentions `OPEN`**

In `SessionService.java`, the method-doc lines that read `(SETUP oder OPEN)` — on `createGroup`, `updateGroup`, `deleteGroup`, `addMember`, `removeMember` — change `(SETUP oder OPEN)` to `(SETUP)`.

- [ ] **Step 5: Fix the `LiveSession` status comment**

In `LiveSession.java` line 43, change:

```java
    private SessionStatus status; // SETUP, OPEN, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED
```

to:

```java
    private SessionStatus status; // SETUP, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED
```

- [ ] **Step 6: Remove `OPEN` from both OpenAPI enums**

In `openapi.yaml`, both of these lines (the `status` query-param enum near line 1251, and the `SessionStatus` schema near line 3672):

```yaml
            enum: [SETUP, OPEN, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED]
```
and
```yaml
      enum: [SETUP, OPEN, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED]
```

become (preserving each line's existing indentation):

```yaml
            enum: [SETUP, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED]
```
and
```yaml
      enum: [SETUP, ACTIVE, PRE_COMPLETE, COMPLETED, ABANDONED]
```

- [ ] **Step 7: Update the two transition descriptions**

In `openapi.yaml`, the `patchSessionStatus` path description (near line 1342):

```yaml
        Allowed transitions: SETUP → OPEN, OPEN → ACTIVE, ACTIVE → ABANDONED,
        ACTIVE → PRE_COMPLETE, PRE_COMPLETE → COMPLETED.
        Pass the target status as a case-insensitive string (e.g. "open", "active").
```

becomes:

```yaml
        Allowed transitions: SETUP → ACTIVE, ACTIVE → ABANDONED,
        ACTIVE → PRE_COMPLETE, PRE_COMPLETE → COMPLETED.
        Pass the target status as a case-insensitive string (e.g. "active", "completed").
```

And the `UpdateSessionStatusRequest.status` description (near line 3769):

```yaml
            Target status (case-insensitive).
            Allowed transitions: SETUP → OPEN, OPEN → ACTIVE,
            ACTIVE → PRE_COMPLETE, ACTIVE → ABANDONED, PRE_COMPLETE → COMPLETED.
```

becomes:

```yaml
            Target status (case-insensitive).
            Allowed transitions: SETUP → ACTIVE,
            ACTIVE → PRE_COMPLETE, ACTIVE → ABANDONED, PRE_COMPLETE → COMPLETED.
```

- [ ] **Step 8: Fix the `deleteSession` summary (corrects existing drift)**

In `openapi.yaml` near line 1320, change:

```yaml
      summary: Delete a session (SETUP or OPEN status only)
```

to:

```yaml
      summary: Delete a session (SETUP status only)
```

- [ ] **Step 9: Run the full backend test suite (regenerates OpenAPI sources)**

Run: `cd smart-ground-backend && ./mvnw clean test`
Expected: PASS — all tests green, including the four from Task 1. The `generate-sources` phase rebuilds `ch.jp.smartground.model.SessionStatus` without `OPEN`; no handwritten code references the generated `OPEN` constant, so compilation succeeds.

- [ ] **Step 10: Verify no stray `OPEN` references remain in backend source**

Run: `cd smart-ground-backend && grep -rn "SessionStatus.OPEN\|SETUP oder OPEN\|SETUP, OPEN" src/main src/test`
Expected: no output.

- [ ] **Step 11: Commit (backend)**

```bash
cd smart-ground-backend
git add src/main/java/ch/jp/shooting/model/SessionStatus.java \
        src/main/java/ch/jp/shooting/service/SessionService.java \
        src/main/java/ch/jp/shooting/model/LiveSession.java \
        src/main/resources/static/openapi.yaml \
        src/test/java/ch/jp/shooting/service/SessionServiceStatusTest.java \
        src/test/java/ch/jp/shooting/service/SessionServiceTransitionTest.java
git commit -m "[backend] remove vestigial OPEN session status (Task G)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Frontend — collapse the store's go-live path

**Files:**
- Modify: `smart-ground-ui/src/stores/competitionEventStore.js`
- Modify: `smart-ground-ui/src/stores/__tests__/competitionEventStore.test.js`

- [ ] **Step 1: Update the store tests to the collapsed behavior (RED)**

In `competitionEventStore.test.js`:

(a) Delete the `openEvent patches status to open …` test (the `it('openEvent …')` block).

(b) Delete both `goLive …` tests (`it('goLive from SETUP …')` and `it('goLive from OPEN …')`).

(c) In the `startEvent patches status to active` test, change the starting state from `OPEN` to `SETUP`:

```javascript
    store.events = [mkSession({ status: 'SETUP' })]
```

(d) Update the `planningEvents returns SETUP and OPEN` test to reflect that only `SETUP` is now a planning state. Replace that `it(...)` block with:

```javascript
  it('planningEvents returns only SETUP events', () => {
    const store = useCompetitionEventStore()
    store.events = [mkSession({ id: 's1', status: 'SETUP' }), mkSession({ id: 's2', status: 'ACTIVE' })]
    expect(store.planningEvents).toHaveLength(1)
    expect(store.planningEvents[0].id).toBe('s1')
  })
```

- [ ] **Step 2: Run the store tests to verify the suite fails (RED)**

Run: `cd smart-ground-ui && npm run test -- src/stores/__tests__/competitionEventStore.test.js`
Expected: FAIL — the `planningEvents returns only SETUP events` test fails because `planningEvents` still includes `OPEN` (an `OPEN`-status event is absent here, but the assertion on length/id is the new contract and the computed still references `OPEN`). The deleted `goLive`/`openEvent` tests no longer run.

Note: if the suite happens to pass at this step (because the removed event made the count coincidentally correct), proceed — Step 3/4 are still required to delete the dead actions.

- [ ] **Step 3: Edit the store — remove `openEvent` and `goLive`, fix `planningEvents`**

In `competitionEventStore.js`:

(a) Change `planningEvents` (line ~28) from:

```javascript
  const planningEvents  = computed(() => events.value.filter(e => ['SETUP', 'OPEN'].includes(e.status?.toUpperCase())))
```

to:

```javascript
  const planningEvents  = computed(() => events.value.filter(e => e.status?.toUpperCase() === 'SETUP'))
```

(b) Delete the `openEvent` action (lines ~73-76):

```javascript
  const openEvent = async (id) => {
    await wettkampfApi.patchStatus(id, 'open')
    _replaceEvent(await wettkampfApi.getSession(id))
  }
```

(c) Delete the `goLive` action and its comment (lines ~85-92):

```javascript
  // Advance a planned competition all the way to ACTIVE in one call. The backend
  // requires SETUP → OPEN → ACTIVE; this hides the transient OPEN step from callers.
  // (Once OPEN is removed server-side — Task G — this collapses to startEvent.)
  const goLive = async (id) => {
    const status = getEvent(id)?.status?.toUpperCase()
    if (status === 'SETUP') await openEvent(id)
    await startEvent(id)
  }
```

(d) In the store's returned object (line ~549), remove `openEvent` and `goLive`:

```javascript
    createEvent, openEvent, startEvent, goLive, stopEvent, stopCompetition, deleteEvent,
```

becomes:

```javascript
    createEvent, startEvent, stopEvent, stopCompetition, deleteEvent,
```

- [ ] **Step 4: Run the store tests to verify they pass (GREEN)**

Run: `cd smart-ground-ui && npm run test -- src/stores/__tests__/competitionEventStore.test.js`
Expected: PASS.

- [ ] **Step 5: Commit (frontend, part 1)**

```bash
cd smart-ground-ui
git add src/stores/competitionEventStore.js src/stores/__tests__/competitionEventStore.test.js
git commit -m "[ui] collapse goLive into startEvent; drop OPEN planning state (Task G)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Frontend — view + remaining tests

**Files:**
- Modify: `smart-ground-ui/src/views/admin/WettkampfDetailView.vue`
- Modify: `smart-ground-ui/src/views/admin/__tests__/WettkampfDetailView.test.js`
- Modify: `smart-ground-ui/src/services/__tests__/wettkampfApi.test.js`

- [ ] **Step 1: Update the view test to use `startEvent` and drop the OPEN case (RED)**

In `WettkampfDetailView.test.js`:

(a) In `setup()`, change the spy (line ~45) from:

```javascript
  vi.spyOn(store, 'goLive').mockResolvedValue()
```

to:

```javascript
  vi.spyOn(store, 'startEvent').mockResolvedValue()
```

(b) In the test `starts the competition in one click when everyone has paid`, change the assertion (line ~75):

```javascript
    expect(store.startEvent).toHaveBeenCalledWith('ev-1')
```

(c) In the test `shows the payment warning before starting when a player is unpaid`, change both `goLive` assertions (lines ~84, ~87) to `startEvent`:

```javascript
    expect(store.startEvent).not.toHaveBeenCalled()
```
and
```javascript
    expect(store.startEvent).toHaveBeenCalledWith('ev-1')
```

(d) Delete the test `renders the planning screen (not a tab-less screen) for an OPEN event` (the `it(...)` block at lines ~90-93) — `OPEN` is no longer a valid status and the `SETUP` planning screen is already covered.

- [ ] **Step 2: Run the view test to verify it fails (RED)**

Run: `cd smart-ground-ui && npm run test -- src/views/admin/__tests__/WettkampfDetailView.test.js`
Expected: FAIL — `vi.spyOn(store, 'startEvent')` would succeed, but the view still calls `store.goLive`, so `expect(store.startEvent).toHaveBeenCalledWith('ev-1')` fails (startEvent never called).

- [ ] **Step 3: Edit the view — call `startEvent`, drop `OPEN` from render branch, label, comments, CSS**

In `WettkampfDetailView.vue`:

(a) Change the planning render branch (line ~26) from:

```vue
    <template v-else-if="['SETUP', 'OPEN'].includes(event.status?.toUpperCase())">
```

to:

```vue
    <template v-else-if="event.status?.toUpperCase() === 'SETUP'">
```

(b) Change the comment above it (line ~25) from `<!-- ══ SETUP / OPEN (Planung) ══ -->` to `<!-- ══ SETUP (Planung) ══ -->`.

(c) In the `statusLabel` map (line ~216), remove the `OPEN: 'Offen',` entry:

```javascript
    SETUP: 'Planung', OPEN: 'Offen', ACTIVE: 'Aktiv',
```

becomes:

```javascript
    SETUP: 'Planung', ACTIVE: 'Aktiv',
```

(d) Change the start-section comment (line ~300) from `// ── Start (SETUP/OPEN → ACTIVE) ──…` to `// ── Start (SETUP → ACTIVE) ──…`.

(e) In `confirmStart()` (line ~313), change:

```javascript
  store.goLive(eventId.value)
```

to:

```javascript
  store.startEvent(eventId.value)
```

(f) Delete the now-dead `.badge-open` style rule (line ~416):

```css
.badge-open { background: var(--sg-color-info-bg); color: var(--sg-color-info-text); border: 1px solid var(--sg-accent); }
```

- [ ] **Step 4: Run the view test to verify it passes (GREEN)**

Run: `cd smart-ground-ui && npm run test -- src/views/admin/__tests__/WettkampfDetailView.test.js`
Expected: PASS.

- [ ] **Step 5: Update the `wettkampfApi` test to not reference a removed status**

In `wettkampfApi.test.js`, the `patchStatus patches /sessions/:id/status` test — change line ~32 from `await api.patchStatus('s1', 'open')` to `await api.patchStatus('s1', 'active')`, and line ~37 from `expect(body.status).toBe('open')` to `expect(body.status).toBe('active')`.

- [ ] **Step 6: Run the full frontend suite + lint**

Run: `cd smart-ground-ui && npm run test`
Expected: PASS (all files).

Run: `cd smart-ground-ui && npm run lint:check`
Expected: no errors.

- [ ] **Step 7: Verify no stray `OPEN`/`'open'` competition-status references remain**

Run: `cd smart-ground-ui && grep -rn "'open'\|'OPEN'\|badge-open\|OPEN:" src`
Expected: no output (matches in unrelated drawer `props: ['open', …]` are fine; there should be none referencing session status).

- [ ] **Step 8: Commit (frontend, part 2)**

```bash
cd smart-ground-ui
git add src/views/admin/WettkampfDetailView.vue \
        src/views/admin/__tests__/WettkampfDetailView.test.js \
        src/services/__tests__/wettkampfApi.test.js
git commit -m "[ui] WettkampfDetailView starts via startEvent; remove OPEN status UI (Task G)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Backend — package check + CLAUDE.md confirmation

**Files:**
- Possibly modify: `smart-ground-backend/CLAUDE.md`

- [ ] **Step 1: Confirm a warning-free package build**

Run: `cd smart-ground-backend && ./mvnw clean package`
Expected: BUILD SUCCESS, no warnings.

- [ ] **Step 2: Confirm `CLAUDE.md` needs no edit**

The Competition/Session domain table already lists the `LiveSession` status as `SETUP → ACTIVE → PAUSED → COMPLETED/ABANDONED` (no `OPEN`). Confirm with:

Run: `cd smart-ground-backend && grep -n "OPEN" CLAUDE.md`
Expected: no output. If any line references an `OPEN` session status, remove it; otherwise no change and no commit for this task.

---

## Self-Review

- **Spec coverage:** SessionStatus enum → T2.S1; transition arm → T2.S2; 7 guards → T2.S3; javadoc → T2.S4; LiveSession comment → T2.S5; openapi enums/prose/delete-summary → T2.S6-8; regenerate → T2.S9; backend tests → T1; UI store (openEvent/goLive removal) → T3; UI view (startEvent, render branch, label) → T4; UI tests → T3/T4; CLAUDE.md confirmation → T5. All spec items mapped. Two items beyond the spec found during planning and added: store `planningEvents` (T3.S3a) + its test (T3.S1d), and the dead `.badge-open` CSS (T4.S3f).
- **Placeholder scan:** none — every code step shows the exact before/after.
- **Type/name consistency:** `startEvent` (already exported, unchanged) is the single go-live entry after `goLive`/`openEvent` deletion; `planningEvents` keeps its name; new test class is `SessionServiceTransitionTest`; method under test is `updateSessionStatus(UUID, String)`.

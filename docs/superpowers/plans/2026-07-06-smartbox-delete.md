# SmartBox Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an admin permanently remove a broken SmartBox from the system, cascading-deleting its devices, while preserving competition history and allowing MQTT re-discovery to reactivate the same MAC.

**Architecture:** Soft-delete the SmartBox (a new nullable `deletedAt` column, mirroring `User.geloeschtAm`). The list and get-by-id API paths exclude soft-deleted boxes; a new `DELETE /api/smart-boxes/{id}` hard-deletes the box's `Device` rows (nulling any range positions first) and stamps `deletedAt`. `SmartBoxDiscoveryHandler` clears `deletedAt` on the next discovery for that MAC, so a box that comes back online reappears with zero devices. The Vue admin UI gains an ADMIN-only delete action behind a two-step "Are you sure" confirmation.

**Tech Stack:** Java 25, Spring Boot 4, JPA/Hibernate (`ddl-auto=update`, pre-v1.0), OpenAPI contract-first codegen, JUnit 5 + Mockito + AssertJ; Vue 3 (`<script setup>`), Pinia 3, Vitest 4 + @vue/test-utils.

## Global Constraints

- Backend package split: handwritten code in `ch.jp.shooting.*`; generated interfaces/DTOs in `ch.jp.smartground.*` — never edit generated files.
- Every controller HTTP method must have an `openapi.yaml` entry; controllers `implement` the generated interface with **no** `@RequestMapping`/`@GetMapping`/`@DeleteMapping` annotations.
- Regenerate after any spec change: `./mvnw generate-sources` (from `smart-ground-backend/`).
- `@NullMarked` on all new backend classes; `@Nullable` where a value can be null. UUID PK via `GenerationType.UUID`. JSON columns are `TEXT`.
- German inline comments for backend/firmware domain logic; English for frontend and all tests.
- Schema changes are JPA entity edits only (pre-v1.0, no Liquibase changeset).
- Frontend: `<script setup>` only; no direct API calls in components (store → service chain); design tokens via `--sg-*` (no hardcoded colors); German display labels, English identifiers/comments/tests; the admin permission constant is `ADMIN_PERMISSION = 'MANAGE_USERS'` in `constants/deviceTypes.js`.
- Backend test profile is H2 (`./mvnw test`); no Docker/Mosquitto required for unit tests.
- Commit messages: `[backend] ...` / `[ui] ...`.

---

## File Structure

**Backend**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/model/SmartBox.java` — add `deletedAt` field + accessors.
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/repository/SmartBoxRepository.java` — add `findByDeletedAtIsNull(Pageable)` and `findByIdAndDeletedAtIsNull(UUID)`.
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml` — add `delete` under `/api/smart-boxes/{id}`.
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/SmartBoxController.java` — filter deleted boxes in list/get; implement `deleteSmartBox`.
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java` — clear `deletedAt` on discovery.
- Create: `smart-ground-backend/src/test/java/ch/jp/shooting/api/SmartBoxControllerTest.java` — unit tests for delete + filtering.
- Modify: `smart-ground-backend/src/test/java/ch/jp/shooting/config/SmartBoxDiscoveryHandlerTest.java` — reactivation test.

**Frontend**
- Modify: `smart-ground-ui/src/services/smartBoxApi.js` — add `deleteSmartBox(id)`.
- Modify: `smart-ground-ui/src/stores/smartBoxStore.js` — add `deleteSmartBox(boxId)` action.
- Modify: `smart-ground-ui/src/components/SmartBoxCard.vue` — ADMIN-only delete button + confirm step; emit `delete-box`.
- Modify: `smart-ground-ui/src/views/admin/SmartBoxesView.vue` — wire `@delete-box` to the store action.
- Create: `smart-ground-ui/src/stores/__tests__/smartBoxStore.test.js` — store action test.

**Docs**
- Modify: `smart-ground-backend/CLAUDE.md` — record the SmartBox soft-delete decision.

---

## Task 1: Add `deletedAt` to the SmartBox entity

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/model/SmartBox.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `SmartBox.getDeletedAt(): @Nullable Instant`, `SmartBox.setDeletedAt(@Nullable Instant)`. Column `deleted_at`.

This is a pure entity field addition. Hibernate applies the column on next startup. There is no dedicated unit test for a plain getter/setter; it is verified by the controller and discovery tests in later tasks. Fold verification into a compile check.

- [ ] **Step 1: Add the field**

In `SmartBox.java`, after the `configSynced` field block (around line 77), add:

```java
    // Soft-Delete: gesetzt wenn eine Box (z.B. defekt) aus dem System entfernt wurde.
    // Die Zeile bleibt für Historie erhalten; die API blendet sie aus.
    @Column(name = "deleted_at")
    @Nullable
    private Instant deletedAt;
```

- [ ] **Step 2: Add the accessors**

In `SmartBox.java`, next to the other accessors (after `setConfigSynced`, around line 112), add:

```java
    public @Nullable Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(@Nullable Instant deletedAt) { this.deletedAt = deletedAt; }
```

(`Instant` and `@Nullable` are already imported in this file.)

- [ ] **Step 3: Compile**

Run: `cd smart-ground-backend && ./mvnw -q compile`
Expected: BUILD SUCCESS (no errors).

- [ ] **Step 4: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/model/SmartBox.java
git commit -m "[backend] add deletedAt soft-delete field to SmartBox"
```

---

## Task 2: Repository queries that exclude soft-deleted boxes

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/repository/SmartBoxRepository.java`

**Interfaces:**
- Consumes: `SmartBox.deletedAt` (Task 1).
- Produces:
  - `Page<SmartBox> findByDeletedAtIsNull(Pageable pageable)`
  - `Optional<SmartBox> findByIdAndDeletedAtIsNull(UUID id)`

Spring Data derives both queries from the method names — no `@Query` needed. These are exercised by Task 4's controller tests; no standalone repository test is added (deriving-query methods are Spring-provided).

- [ ] **Step 1: Add the query methods**

In `SmartBoxRepository.java`, add these imports at the top (alongside the existing imports):

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```

Then add inside the interface body, after `findByMacAddress`:

```java
    Page<SmartBox> findByDeletedAtIsNull(Pageable pageable);

    Optional<SmartBox> findByIdAndDeletedAtIsNull(UUID id);
```

- [ ] **Step 2: Compile**

Run: `cd smart-ground-backend && ./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/repository/SmartBoxRepository.java
git commit -m "[backend] add non-deleted SmartBox repository queries"
```

---

## Task 3: Add the DELETE endpoint to the OpenAPI spec

**Files:**
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml`

**Interfaces:**
- Consumes: nothing.
- Produces: generated `SmartBoxApi.deleteSmartBox(UUID id): ResponseEntity<Void>` after codegen. Path `DELETE /api/smart-boxes/{id}`, responses `204` / `404`.

- [ ] **Step 1: Add the `delete` operation**

In `openapi.yaml`, the `/api/smart-boxes/{id}` path item currently has only a `get`. Add a `delete` sibling to the existing `get:` (keep the `get:` intact). The block to add, indented as a sibling of `get:` under `/api/smart-boxes/{id}:`:

```yaml
    delete:
      summary: Delete a smart box
      description: >
        Soft-deletes the SmartBox (marks it removed and hides it from all
        listings) and hard-deletes every device wired to it. Intended for
        permanently retiring broken hardware. If the same MAC address later
        sends an MQTT discovery message the box is automatically reactivated,
        starting again with zero devices. Requires ADMIN role.
      operationId: deleteSmartBox
      tags: [SmartBox]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Smart box deleted
        '403':
          description: User does not have ADMIN role
        '404':
          description: Smart box not found
```

- [ ] **Step 2: Regenerate sources**

Run: `cd smart-ground-backend && ./mvnw -q generate-sources`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Verify the generated interface has the method**

Run: `grep -rn "deleteSmartBox" smart-ground-backend/target/generated-sources/openapi/ch/jp/smartground/api/SmartBoxApi.java`
Expected: at least one line referencing `deleteSmartBox` (the generated default method / mapping).

- [ ] **Step 4: Commit**

```bash
git add smart-ground-backend/src/main/resources/static/openapi.yaml
git commit -m "[backend] add DELETE /api/smart-boxes/{id} to OpenAPI spec"
```

---

## Task 4: Implement delete + filter deleted boxes in SmartBoxController

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/SmartBoxController.java`
- Create: `smart-ground-backend/src/test/java/ch/jp/shooting/api/SmartBoxControllerTest.java`

**Interfaces:**
- Consumes: `SmartBoxRepository.findByDeletedAtIsNull`, `findByIdAndDeletedAtIsNull` (Task 2); `SmartBox.setDeletedAt` (Task 1); `DeviceRepository.findBySmartBoxId(UUID): List<Device>`; `RangePositionRepository` (save); `Device.getRangePosition()`, `RangePosition.setDevice(null)`; generated `SmartBoxApi.deleteSmartBox` (Task 3).
- Produces: `SmartBoxController.deleteSmartBox(UUID): ResponseEntity<Void>` (204 / 404 / 403). List and get now exclude soft-deleted boxes.

The controller gains two new injected dependencies: `DeviceRepository` and `RangePositionRepository`. Devices are non-nullable on `smartBox`, so they must be hard-deleted; any range position pointing at a device must be unassigned first (mirroring `RangePositionService.removeDevice`) to avoid the `range_positions.device_id` FK constraint.

- [ ] **Step 1: Write the failing test**

Create `smart-ground-backend/src/test/java/ch/jp/shooting/api/SmartBoxControllerTest.java`:

```java
package ch.jp.shooting.api;

import ch.jp.shooting.config.SmartBoxConfigPushService;
import ch.jp.shooting.exception.SmartBoxNotFoundException;
import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.RangePositionRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxControllerTest {

    @Mock SmartBoxRepository smartBoxRepository;
    @Mock SmartBoxConfigPushService configPushService;
    @Mock DeviceRepository deviceRepository;
    @Mock RangePositionRepository rangePositionRepository;

    private SmartBoxController controller() {
        return new SmartBoxController(
            smartBoxRepository, configPushService, deviceRepository, rangePositionRepository);
    }

    @Test
    void deleteSoftDeletesBoxAndHardDeletesItsDevices() {
        UUID boxId = UUID.randomUUID();
        SmartBox box = new SmartBox();
        box.setId(boxId);
        Device d1 = new Device();
        Device d2 = new Device();
        when(smartBoxRepository.findByIdAndDeletedAtIsNull(boxId)).thenReturn(Optional.of(box));
        when(deviceRepository.findBySmartBoxId(boxId)).thenReturn(List.of(d1, d2));
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));

        var response = controller().deleteSmartBox(boxId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deviceRepository).deleteAll(List.of(d1, d2));
        ArgumentCaptor<SmartBox> cap = ArgumentCaptor.forClass(SmartBox.class);
        verify(smartBoxRepository).save(cap.capture());
        assertThat(cap.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void deleteUnassignsRangePositionBeforeDeletingDevice() {
        UUID boxId = UUID.randomUUID();
        SmartBox box = new SmartBox();
        box.setId(boxId);
        Device device = new Device();
        RangePosition position = new RangePosition();
        position.setDevice(device);
        device.setRangePosition(position);
        when(smartBoxRepository.findByIdAndDeletedAtIsNull(boxId)).thenReturn(Optional.of(box));
        when(deviceRepository.findBySmartBoxId(boxId)).thenReturn(List.of(device));
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));

        controller().deleteSmartBox(boxId);

        ArgumentCaptor<RangePosition> cap = ArgumentCaptor.forClass(RangePosition.class);
        verify(rangePositionRepository).save(cap.capture());
        assertThat(cap.getValue().getDevice()).isNull();
        verify(deviceRepository).deleteAll(List.of(device));
    }

    @Test
    void deleteThrowsWhenBoxMissingOrAlreadyDeleted() {
        UUID boxId = UUID.randomUUID();
        when(smartBoxRepository.findByIdAndDeletedAtIsNull(boxId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().deleteSmartBox(boxId))
            .isInstanceOf(SmartBoxNotFoundException.class);
        verify(deviceRepository, never()).deleteAll(any());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd smart-ground-backend && ./mvnw -q test -Dtest=SmartBoxControllerTest`
Expected: FAIL — the `SmartBoxController` constructor does not yet take `DeviceRepository`/`RangePositionRepository` and `deleteSmartBox` is not implemented (compilation failure counts as the RED state).

- [ ] **Step 3: Update the controller constructor and dependencies**

In `SmartBoxController.java`, add imports:

```java
import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.RangePositionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.util.List;
```

Replace the fields + constructor (lines ~26–33) with:

```java
    private final SmartBoxRepository smartBoxRepository;
    private final SmartBoxConfigPushService configPushService;
    private final DeviceRepository deviceRepository;
    private final RangePositionRepository rangePositionRepository;

    public SmartBoxController(SmartBoxRepository smartBoxRepository,
                               SmartBoxConfigPushService configPushService,
                               DeviceRepository deviceRepository,
                               RangePositionRepository rangePositionRepository) {
        this.smartBoxRepository = smartBoxRepository;
        this.configPushService = configPushService;
        this.deviceRepository = deviceRepository;
        this.rangePositionRepository = rangePositionRepository;
    }
```

- [ ] **Step 4: Filter deleted boxes in list and get**

In `listSmartBoxes`, change the repository call from:

```java
        Page<SmartBox> boxPage = smartBoxRepository.findAll(PageRequest.of(page, size));
```

to:

```java
        Page<SmartBox> boxPage = smartBoxRepository.findByDeletedAtIsNull(PageRequest.of(page, size));
```

In the private `findBox` helper, change:

```java
    private SmartBox findBox(UUID id) {
        return smartBoxRepository.findById(id)
            .orElseThrow(() -> new SmartBoxNotFoundException(id));
    }
```

to:

```java
    private SmartBox findBox(UUID id) {
        return smartBoxRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SmartBoxNotFoundException(id));
    }
```

- [ ] **Step 5: Implement `deleteSmartBox`**

Add this method to `SmartBoxController` (e.g. after `pushSmartBoxConfig`):

```java
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSmartBox(@PathVariable("id") UUID id) {
        SmartBox box = findBox(id);

        // Geräte hängen physisch an den GPIO-Pins dieser Box – sie werden mitgelöscht.
        // Zuvor jede Range-Position freigeben, sonst verletzt der FK range_positions.device_id.
        List<Device> devices = deviceRepository.findBySmartBoxId(id);
        for (Device device : devices) {
            RangePosition position = device.getRangePosition();
            if (position != null) {
                position.setDevice(null);
                rangePositionRepository.save(position);
            }
        }
        deviceRepository.deleteAll(devices);

        // Soft-Delete der Box selbst: Zeile bleibt für Historie, wird aber ausgeblendet.
        box.setDeletedAt(Instant.now());
        smartBoxRepository.save(box);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd smart-ground-backend && ./mvnw -q test -Dtest=SmartBoxControllerTest`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/api/SmartBoxController.java \
        smart-ground-backend/src/test/java/ch/jp/shooting/api/SmartBoxControllerTest.java
git commit -m "[backend] implement SmartBox delete with device cascade and soft-delete filtering"
```

---

## Task 5: Reactivate a soft-deleted box on MQTT discovery

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java`
- Modify: `smart-ground-backend/src/test/java/ch/jp/shooting/config/SmartBoxDiscoveryHandlerTest.java`

**Interfaces:**
- Consumes: `SmartBox.setDeletedAt` / `getDeletedAt` (Task 1).
- Produces: discovery clears `deletedAt` so a re-discovered MAC reappears in listings.

- [ ] **Step 1: Write the failing test**

Add this test method to `SmartBoxDiscoveryHandlerTest.java` (imports `Instant` — add `import java.time.Instant;` at the top if absent):

```java
    @Test
    void reactivatesSoftDeletedBoxOnRediscovery() {
        var handler = new SmartBoxDiscoveryHandler(
            smartBoxRepository, firmwareConfigRepository, new ObjectMapper(), configPushService);
        SmartBox deleted = new SmartBox();
        deleted.setMacAddress("aabbccddeeff");
        deleted.setDeletedAt(java.time.Instant.now());
        when(smartBoxRepository.findByMacAddress("aabbccddeeff")).thenReturn(Optional.of(deleted));
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));
        when(firmwareConfigRepository.findByVersionAndBoxType(any(), any()))
            .thenReturn(Optional.empty());
        when(firmwareConfigRepository.save(any(FirmwareConfig.class)))
            .thenAnswer(i -> i.getArgument(0));

        String json = "{\"mac\":\"aabbccddeeff\",\"appVersion\":\"0.7\","
                    + "\"firmwareVersion\":\"micropython-1.23.0\",\"boxType\":\"xiao-esp32s3\",\"ip\":\"1.2.3.4\"}";
        handler.handleMessage(MessageBuilder.withPayload(json.getBytes()).build());

        assertThat(deleted.getDeletedAt()).isNull();
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd smart-ground-backend && ./mvnw -q test -Dtest=SmartBoxDiscoveryHandlerTest#reactivatesSoftDeletedBoxOnRediscovery`
Expected: FAIL — `deletedAt` is still non-null (handler does not clear it yet).

- [ ] **Step 3: Clear `deletedAt` in `upsertSmartBox`**

In `SmartBoxDiscoveryHandler.upsertSmartBox`, right after the `box.setStatus(...)` / `box.setLastSeen(...)` lines (around line 85), add:

```java
        // Meldet sich eine zuvor (soft-)gelöschte Box erneut, wird sie automatisch reaktiviert.
        box.setDeletedAt(null);
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd smart-ground-backend && ./mvnw -q test -Dtest=SmartBoxDiscoveryHandlerTest`
Expected: PASS (all tests in the class, including the new one).

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java \
        smart-ground-backend/src/test/java/ch/jp/shooting/config/SmartBoxDiscoveryHandlerTest.java
git commit -m "[backend] reactivate soft-deleted SmartBox on MQTT discovery"
```

---

## Task 6: Full backend test run

**Files:** none (verification only).

- [ ] **Step 1: Run the whole backend suite**

Run: `cd smart-ground-backend && ./mvnw -q test`
Expected: BUILD SUCCESS, all tests green. If anything fails, fix it before continuing (a compile break elsewhere from the new constructor argument would surface here — the only production caller of `new SmartBoxController(...)` is Spring, which auto-wires the new beans, so no other call sites need updating).

---

## Task 7: Frontend API + store action for delete

**Files:**
- Modify: `smart-ground-ui/src/services/smartBoxApi.js`
- Modify: `smart-ground-ui/src/stores/smartBoxStore.js`
- Create: `smart-ground-ui/src/stores/__tests__/smartBoxStore.test.js`

**Interfaces:**
- Consumes: `apiFetch` (existing); backend `DELETE /api/smart-boxes/{id}` (Task 3/4).
- Produces:
  - `deleteSmartBox(id)` in `smartBoxApi.js` → `DELETE /smart-boxes/{id}`.
  - `smartBoxStore.deleteSmartBox(boxId)` action → calls the API, then removes the box from `smartboxes` local state. Exposed in the store's returned object.

- [ ] **Step 1: Write the failing store test**

Create `smart-ground-ui/src/stores/__tests__/smartBoxStore.test.js`:

```javascript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useSmartBoxStore } from '../smartBoxStore.js';
import * as smartBoxApi from '../../services/smartBoxApi.js';

vi.mock('../../services/smartBoxApi.js');

describe('smartBoxStore.deleteSmartBox', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('calls the API and removes the box from local state', async () => {
    const store = useSmartBoxStore();
    store.smartboxes = [
      { id: 'box-1', alias: 'A' },
      { id: 'box-2', alias: 'B' },
    ];
    smartBoxApi.deleteSmartBox.mockResolvedValue(null);

    await store.deleteSmartBox('box-1');

    expect(smartBoxApi.deleteSmartBox).toHaveBeenCalledWith('box-1');
    expect(store.smartboxes.map((b) => b.id)).toEqual(['box-2']);
  });

  it('keeps the box in state and rethrows if the API fails', async () => {
    const store = useSmartBoxStore();
    store.smartboxes = [{ id: 'box-1', alias: 'A' }];
    smartBoxApi.deleteSmartBox.mockRejectedValue(new Error('boom'));

    await expect(store.deleteSmartBox('box-1')).rejects.toThrow('boom');
    expect(store.smartboxes.map((b) => b.id)).toEqual(['box-1']);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd smart-ground-ui && npm run test -- src/stores/__tests__/smartBoxStore.test.js`
Expected: FAIL — `smartBoxApi.deleteSmartBox` is not a function and `store.deleteSmartBox` is undefined.

- [ ] **Step 3: Add the API function**

In `smart-ground-ui/src/services/smartBoxApi.js`, add:

```javascript
export async function deleteSmartBox(id) {
  return apiFetch(`/smart-boxes/${id}`, { method: 'DELETE' });
}
```

- [ ] **Step 4: Add the store action**

In `smart-ground-ui/src/stores/smartBoxStore.js`, add this action inside the store factory (e.g. after `saveSmartBox`):

```javascript
  const deleteSmartBox = async (boxId) => {
    await smartBoxApi.deleteSmartBox(boxId);
    const index = smartboxes.value.findIndex((b) => b.id === boxId);
    if (index > -1) {
      smartboxes.value.splice(index, 1);
    }
  };
```

Then add `deleteSmartBox` to the returned object (the `return { ... }` block), alongside `saveSmartBox`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd smart-ground-ui && npm run test -- src/stores/__tests__/smartBoxStore.test.js`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add smart-ground-ui/src/services/smartBoxApi.js \
        smart-ground-ui/src/stores/smartBoxStore.js \
        smart-ground-ui/src/stores/__tests__/smartBoxStore.test.js
git commit -m "[ui] add deleteSmartBox API + store action"
```

---

## Task 8: Delete button + "Are you sure" confirm in SmartBoxCard

**Files:**
- Modify: `smart-ground-ui/src/components/SmartBoxCard.vue`

**Interfaces:**
- Consumes: `authStore.hasPermission(ADMIN_PERMISSION)` (already wired as `isAdmin`); existing `confirmingDelete` pattern is device-scoped, so a separate `confirmingDeleteBox` ref is used for the box.
- Produces: emits `delete-box` with the box id when the admin confirms. New emit name `delete-box` added to `defineEmits`.

The card header (`.box-status-info`, lines ~35–51) holds the box-level actions (status, firmware, device count, OTA toggle). The delete control belongs here, ADMIN-only, mirroring the existing device `confirm-delete` two-step pattern (lines 150–154).

- [ ] **Step 1: Add the box-delete control to the header template**

In `SmartBoxCard.vue`, inside `.box-status-info`, after the `.ota-toggle-btn` button (line 50, before the closing `</div>` at line 51), add:

```html
        <template v-if="isAdmin">
          <div v-if="confirmingDeleteBox" class="confirm-delete-box">
            <span class="confirm-text">Box löschen?</span>
            <button class="delete-confirm-btn" @click="deleteBox">Ja</button>
            <button class="cancel-btn-sm" @click="confirmingDeleteBox = false">Nein</button>
          </div>
          <button
            v-else
            class="box-delete-btn"
            type="button"
            aria-label="SmartBox löschen"
            @click="confirmingDeleteBox = true"
          >
            <Icons icon="trash" :size="13" />
          </button>
        </template>
```

- [ ] **Step 2: Add the emit, state, and handler in the script**

In `SmartBoxCard.vue` script, add `'delete-box'` to the `defineEmits([...])` array (after `'rename-box'`).

Add a ref next to the other refs (near `confirmingDelete`, line 378):

```javascript
const confirmingDeleteBox = ref(false);
```

Add the handler next to `deleteDevice` (near line 481):

```javascript
const deleteBox = () => {
  emit('delete-box', props.box.id);
  confirmingDeleteBox.value = false;
};
```

- [ ] **Step 3: Add styles for the box-delete control**

In the `<style scoped>` block of `SmartBoxCard.vue`, add (the `.confirm-text`, `.delete-confirm-btn`, `.cancel-btn-sm` classes already exist and are reused):

```css
.box-delete-btn {
  background: var(--sg-bg-panel);
  border: none;
  border-radius: 5px;
  cursor: pointer;
  color: var(--sg-color-danger);
  padding: 4px 6px;
  display: flex;
  align-items: center;
  transition: background 0.15s;
}

.box-delete-btn:hover {
  background: var(--sg-color-danger-bg);
}

.confirm-delete-box {
  display: flex;
  align-items: center;
  gap: 6px;
}
```

- [ ] **Step 4: Verify build + lint**

Run: `cd smart-ground-ui && npm run lint:check && npm run build`
Expected: no ESLint warnings; build succeeds with no warnings.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-ui/src/components/SmartBoxCard.vue
git commit -m "[ui] add admin-only delete button with confirm step to SmartBoxCard"
```

---

## Task 9: Wire `@delete-box` in SmartBoxesView

**Files:**
- Modify: `smart-ground-ui/src/views/admin/SmartBoxesView.vue`

**Interfaces:**
- Consumes: `SmartBoxCard`'s `delete-box` emit (Task 8); `smartBoxStore.deleteSmartBox` (Task 7).
- Produces: nothing downstream.

- [ ] **Step 1: Bind the handler on the component**

In `SmartBoxesView.vue`, on the `<SmartBoxCard ... />` element (around lines 72–87), add a listener next to `@rename-box="renameBox"`:

```html
          @delete-box="deleteBox"
```

- [ ] **Step 2: Add the handler in the script**

In `SmartBoxesView.vue` script, add next to `renameBox` (around line 214):

```javascript
const deleteBox = async (boxId) => {
  try {
    await smartBoxStore.deleteSmartBox(boxId);
  } catch (e) {
    console.error('Failed to delete SmartBox:', e);
  }
};
```

- [ ] **Step 3: Verify build + lint**

Run: `cd smart-ground-ui && npm run lint:check && npm run build`
Expected: no ESLint warnings; build succeeds.

- [ ] **Step 4: Full frontend test run**

Run: `cd smart-ground-ui && npm run test`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-ui/src/views/admin/SmartBoxesView.vue
git commit -m "[ui] wire SmartBox delete action in SmartBoxesView"
```

---

## Task 10: Document the decision in backend CLAUDE.md

**Files:**
- Modify: `smart-ground-backend/CLAUDE.md`

**Interfaces:** none.

- [ ] **Step 1: Update the schema table**

In `smart-ground-backend/CLAUDE.md`, in the `### IoT / Device tables` block, append `, deleted_at` to the `smart_boxes` column list (end of the `mqtt_username, config_synced, firmware_config_id→firmware_configs` line).

- [ ] **Step 2: Record the decision under Implemented**

In the `### ✅ Implemented` list, add a bullet:

```markdown
- **SmartBox soft-delete** (implemented 2026-07-06) — `DELETE /api/smart-boxes/{id}` (ADMIN-only) sets `SmartBox.deletedAt` and hard-deletes the box's devices (range positions unassigned first to satisfy the `range_positions.device_id` FK). List/get filter on `deletedAt IS NULL` (`SmartBoxRepository.findByDeletedAtIsNull` / `findByIdAndDeletedAtIsNull`). Competition/session history is untouched (it never references `SmartBox`/`Device` directly). A re-discovered MAC is auto-reactivated: `SmartBoxDiscoveryHandler` clears `deletedAt`, and the box returns with zero devices. Decision: soft-delete (not hard-delete) the box so a broken unit stays out of listings without destroying its identity, while devices are hard-deleted because they are meaningless without their physical GPIO wiring.
```

- [ ] **Step 3: Update the SmartBoxController row in the Controllers Overview table**

Change the `SmartBoxController` row's Key Endpoints cell from:

```
GET /api/smart-boxes, PATCH alias, POST push-config
```

to:

```
GET /api/smart-boxes, PATCH alias, POST push-config, DELETE /api/smart-boxes/{id}
```

- [ ] **Step 4: Commit**

```bash
git add smart-ground-backend/CLAUDE.md
git commit -m "[backend] document SmartBox soft-delete decision"
```

---

## Self-Review Notes

- **Spec coverage:** soft-delete field (Task 1), API filtering (Tasks 2, 4), DELETE endpoint ADMIN-gated (Tasks 3, 4), silent device cascade with range-position safety (Task 4), history preserved (no session/competition FKs touched — verified against schema), MQTT auto-reactivation (Task 5), UI delete + "Are you sure" confirm ADMIN-only (Tasks 7–9). All spec sections map to a task.
- **Placeholder scan:** none — every code and test block is concrete.
- **Type consistency:** `deleteSmartBox` used identically in service, store, spec, and controller; `findByDeletedAtIsNull` / `findByIdAndDeletedAtIsNull` names match between Task 2 (definition) and Task 4 (use); the `SmartBoxController` 4-arg constructor matches between the test (Task 4 Step 1) and the implementation (Task 4 Step 3); `delete-box` emit name matches between Task 8 (emit) and Task 9 (listener).

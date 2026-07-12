# ReservationController OpenAPI Conformance — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `ReservationController` conform to the project's contract-first convention by declaring all endpoints in `openapi.yaml`, generating the `ReservationApi` interface, and rewriting the controller to implement it — with no Spring mapping annotations on the class or methods.

**Architecture:** `openapi.yaml` is the single source of truth. The generator produces `ch.jp.smartground.api.ReservationApi`; `ReservationController implements ReservationApi` delegates to the existing `ReservationService`. A bug in `ReservationService.release()` (throws `ConflictException` instead of `ForbiddenException` when the caller is not the reservation owner) is fixed in the same PR.

**Tech Stack:** Spring Boot 4, openapi-generator-maven-plugin, Jackson 2.x (via `JacksonConfig`), JUnit 5 + Mockito, H2 (tests).

---

## Files Changed

| File | Action | Reason |
|---|---|---|
| `src/main/resources/static/openapi.yaml` | Modify | Add `/api/ranges/{id}/reservation` paths + `ReservationResponse` schema |
| `src/main/java/ch/jp/shooting/service/ReservationService.java` | Modify | Fix `ConflictException` → `ForbiddenException` for "not owner" case |
| `src/main/java/ch/jp/shooting/api/ReservationController.java` | Rewrite | Implement generated `ReservationApi`; remove all Spring mapping annotations |
| `src/test/java/ch/jp/shooting/service/ReservationServiceTest.java` | Create | Unit tests for service — especially the fixed ForbiddenException path |

---

### Task 1: Add reservation endpoints to `openapi.yaml`

**Files:**
- Modify: `src/main/resources/static/openapi.yaml`

This inserts three operations under `/api/ranges/{id}/reservation` and a `ReservationResponse` schema. Insert the path block just after the last Range path (line 1160, before the Sessions comment block). Insert the schema at the bottom of the `schemas:` section, just before `securitySchemes:`.

- [ ] **Step 1: Insert the path block into `openapi.yaml`**

Find the exact text:

```yaml
  # ─────────────────────────────────────────────────────────────────────────────
  # Sessions (Competition Management)
  # ─────────────────────────────────────────────────────────────────────────────
  /api/sessions:
```

Replace it with:

```yaml
  # ─────────────────────────────────────────────────────────────────────────────
  # Reservations
  # ─────────────────────────────────────────────────────────────────────────────
  /api/ranges/{id}/reservation:
    get:
      summary: Get the active reservation for a range
      operationId: getActiveRangeReservation
      tags: [Reservation]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Active reservation found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ReservationResponse'
        '204':
          description: No active reservation for this range
        '404':
          description: Range not found

    post:
      summary: Reserve a range for the current user
      operationId: reserveRange
      tags: [Reservation]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '201':
          description: Reservation created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ReservationResponse'
        '404':
          description: Range not found
        '409':
          description: Range already reserved, or current user already holds a reservation

    delete:
      summary: Release the active reservation for a range
      operationId: releaseRangeReservation
      tags: [Reservation]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Reservation released
        '403':
          description: Caller is not the reservation owner and does not have admin rights
        '404':
          description: No active reservation found for this range

  # ─────────────────────────────────────────────────────────────────────────────
  # Sessions (Competition Management)
  # ─────────────────────────────────────────────────────────────────────────────
  /api/sessions:
```

- [ ] **Step 2: Insert the `ReservationResponse` schema into `openapi.yaml`**

Find the exact text (near end of file):

```yaml
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

Replace it with:

```yaml
    ReservationResponse:
      type: object
      required: [id, rangeId, username, startedAt, lastActivityAt, status]
      properties:
        id:
          type: string
          format: uuid
        rangeId:
          type: string
          format: uuid
        username:
          type: string
        startedAt:
          type: string
          format: date-time
        lastActivityAt:
          type: string
          format: date-time
        status:
          type: string
          enum: [ACTIVE, RELEASED]

  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

- [ ] **Step 3: Regenerate sources**

```bash
./mvnw generate-sources -q
```

Expected: exits 0. Verify the generated interface exists:

```bash
ls target/generated-sources/openapi/ch/jp/smartground/api/ReservationApi.java
```

Expected: file is present with `getActiveRangeReservation`, `reserveRange`, and `releaseRangeReservation` methods.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] Add /api/ranges/{id}/reservation endpoints to openapi.yaml"
```

---

### Task 2: Fix `ReservationService.release()` — wrong exception type

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/ReservationService.java`
- Create: `src/test/java/ch/jp/shooting/service/ReservationServiceTest.java`

The method currently throws `ConflictException` (→ HTTP 409) when the caller is not the owner of the reservation. This should be `ForbiddenException` (→ HTTP 403) — conflicting ownership is an authorization failure, not a data conflict.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/ch/jp/shooting/service/ReservationServiceTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.exception.ForbiddenException;
import ch.jp.shooting.exception.NotFoundException;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.Reservation;
import ch.jp.shooting.model.Reservation.ReservationStatus;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock RangeRepository rangeRepository;

    @InjectMocks ReservationService reservationService;

    @BeforeEach
    void setUpAuth() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("alice", null, java.util.List.of())
        );
    }

    // ── release() ──

    @Test
    void release_noActiveReservation_throwsNotFoundException() {
        when(reservationRepository.findByRangeIdAndStatus(any(), any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> reservationService.release(UUID.randomUUID()));
    }

    @Test
    void release_callerIsNotOwner_throwsForbiddenException() {
        Range range = new Range();
        Reservation reservation = new Reservation();
        reservation.setRange(range);
        reservation.setUsername("bob"); // current user is "alice"
        reservation.setStatus(ReservationStatus.ACTIVE);

        when(reservationRepository.findByRangeIdAndStatus(any(), any())).thenReturn(Optional.of(reservation));

        assertThrows(ForbiddenException.class, () -> reservationService.release(UUID.randomUUID()));
    }

    // ── reserve() ──

    @Test
    void reserve_rangeNotFound_throwsNotFoundException() {
        when(rangeRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> reservationService.reserve(UUID.randomUUID()));
    }

    @Test
    void reserve_rangeAlreadyReserved_throwsConflictException() {
        Range range = new Range();
        Reservation existing = new Reservation();
        existing.setRange(range);
        existing.setUsername("bob");
        existing.setStatus(ReservationStatus.ACTIVE);

        when(rangeRepository.findById(any())).thenReturn(Optional.of(range));
        when(reservationRepository.findByRangeIdAndStatus(any(), any())).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> reservationService.reserve(UUID.randomUUID()));
    }
}
```

- [ ] **Step 2: Run the tests — expect `release_callerIsNotOwner_throwsForbiddenException` to fail**

```bash
./mvnw test -Dtest=ReservationServiceTest -q
```

Expected: `release_callerIsNotOwner_throwsForbiddenException` fails with `ConflictException` rather than `ForbiddenException`.

- [ ] **Step 3: Fix the service**

In `src/main/java/ch/jp/shooting/service/ReservationService.java`, change the import and the throw:

Add import (alongside existing `ConflictException` import):
```java
import ch.jp.shooting.exception.ForbiddenException;
```

Replace:
```java
        // Prüfe: Reservierung gehört dem Benutzer
        if (!reservation.getUsername().equals(username)) {
            throw new ConflictException("You do not have a reservation on this range");
        }
```

With:
```java
        // Prüfe: Reservierung gehört dem Benutzer
        if (!reservation.getUsername().equals(username)) {
            throw new ForbiddenException("You do not have a reservation on this range");
        }
```

- [ ] **Step 4: Run all tests — expect green**

```bash
./mvnw test -Dtest=ReservationServiceTest -q
```

Expected: all 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/ReservationService.java \
        src/test/java/ch/jp/shooting/service/ReservationServiceTest.java
git commit -m "[backend] Fix ReservationService.release() to throw ForbiddenException when caller is not owner"
```

---

### Task 3: Rewrite `ReservationController` to implement `ReservationApi`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/api/ReservationController.java`

The controller is rewritten to implement the generated `ch.jp.smartground.api.ReservationApi`. All `@RequestMapping`, `@PostMapping`, `@GetMapping`, and `@DeleteMapping` annotations are removed. The `DELETE` endpoint checks `ROLE_ADMIN` authority and dispatches to `forceRelease()` for admins and `release()` for regular users.

- [ ] **Step 1: Overwrite `ReservationController.java` with the conformant implementation**

Replace the entire contents of `src/main/java/ch/jp/shooting/api/ReservationController.java` with:

```java
package ch.jp.shooting.api;

import ch.jp.shooting.dto.ReservationDTO;
import ch.jp.shooting.exception.ForbiddenException;
import ch.jp.shooting.service.ReservationService;
import ch.jp.smartground.api.ReservationApi;
import ch.jp.smartground.model.ReservationResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@NullMarked
public class ReservationController implements ReservationApi {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Override
    public ResponseEntity<ReservationResponse> getActiveRangeReservation(UUID id) {
        ReservationDTO dto = reservationService.getActiveReservation(id);
        if (dto == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(toResponse(dto));
    }

    @Override
    public ResponseEntity<ReservationResponse> reserveRange(UUID id) {
        ReservationDTO dto = reservationService.reserve(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(dto));
    }

    @Override
    public ResponseEntity<Void> releaseRangeReservation(UUID id) {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));

        if (isAdmin) {
            reservationService.forceRelease(id);
        } else {
            reservationService.release(id);
        }
        return ResponseEntity.noContent().build();
    }

    private ReservationResponse toResponse(ReservationDTO dto) {
        return new ReservationResponse()
                .id(dto.getId())
                .rangeId(dto.getRangeId())
                .username(dto.getUsername())
                .startedAt(dto.getStartedAt())
                .lastActivityAt(dto.getLastActivityAt())
                .status(ReservationResponse.StatusEnum.valueOf(dto.getStatus()));
    }
}
```

- [ ] **Step 2: Compile and run all tests**

```bash
./mvnw test -q
```

Expected: BUILD SUCCESS with all tests green. If you see a compilation error referencing `ReservationResponse.StatusEnum`, verify step 3 from Task 1 produced a generated model with an inner `StatusEnum` enum — the enum values `ACTIVE` and `RELEASED` in the schema drive this. If the generator produced a plain `String` field instead, change `.status(ReservationResponse.StatusEnum.valueOf(dto.getStatus()))` to `.status(dto.getStatus())` and adjust the field type accordingly.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ch/jp/shooting/api/ReservationController.java
git commit -m "[backend] Rewrite ReservationController to implement generated ReservationApi"
```

---

## Self-Review

**Spec coverage:**
- ✅ `GET /api/ranges/{id}/reservation` → `getActiveRangeReservation` (Task 1 + Task 3)
- ✅ `POST /api/ranges/{id}/reservation` → `reserveRange` (Task 1 + Task 3)
- ✅ `DELETE /api/ranges/{id}/reservation` → `releaseRangeReservation`, admin-aware (Task 1 + Task 3)
- ✅ `ReservationResponse` schema in openapi.yaml (Task 1)
- ✅ Fix `ConflictException` → `ForbiddenException` (Task 2)
- ✅ No `@RequestMapping`/`@GetMapping`/`@PostMapping`/`@DeleteMapping` on controller (Task 3)
- ✅ Unit tests covering the fixed path and main service error cases (Task 2)

**Placeholder scan:** None found.

**Type consistency:**
- `ReservationService.getActiveReservation()` returns `ReservationDTO` (nullable) — controller handles null → 204.
- `ReservationService.reserve()` returns `ReservationDTO` — controller maps → `ReservationResponse`.
- `ReservationService.release()` / `forceRelease()` return void — controller returns 204.
- `toResponse()` maps each field by name — field names match between `ReservationDTO` getters and `ReservationResponse` setters generated from the schema.

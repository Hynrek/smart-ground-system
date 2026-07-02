# Admin "Testing" Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an admin-only "Testing" panel that creates test data on demand — a user (single credential), the four standard ranges, and a mock SmartBox with N devices — via three new `ROLE_ADMIN`-gated REST endpoints.

**Architecture:** Contract-first backend: three endpoints under `/api/testing` declared in `openapi.yaml`, implemented by `TestingController implements TestingApi` delegating to a new `TestDataService`. Frontend adds a `TestingView.vue` admin page driven by a `testingApi.js` service, wired into the router and sidebar, gated on the `MANAGE_USERS` permission.

**Tech Stack:** Java 25, Spring Boot 4, JPA/Hibernate, openapi-generator, JUnit 5 + Mockito (H2 tests); Vue 3, Vite, Vitest.

---

## File Structure

**Backend:**
- Modify: `src/main/resources/static/openapi.yaml` — three paths + six schemas under a `Testing` tag
- Modify: `src/main/java/ch/jp/shooting/repository/RangeRepository.java` — add `findByName`
- Modify: `src/main/java/ch/jp/shooting/repository/DeviceTypeRepository.java` — add `findByName`
- Create: `src/main/java/ch/jp/shooting/service/TestDataService.java` — business logic
- Create: `src/main/java/ch/jp/shooting/api/TestingController.java` — implements generated `TestingApi`
- Create: `src/test/java/ch/jp/shooting/service/TestDataServiceTest.java` — unit tests

**Frontend:**
- Create: `smart-ground-ui/src/services/testingApi.js` — REST client
- Create: `smart-ground-ui/src/views/admin/TestingView.vue` — the panel
- Modify: `smart-ground-ui/src/router/index.js` — `/testing` route
- Modify: `smart-ground-ui/src/components/Sidebar.vue` — nav item
- Modify: `smart-ground-ui/src/App.vue` — `navMap` entry for active-nav highlight
- Create: `smart-ground-ui/src/services/__tests__/testingApi.test.js` — service test

**Docs:**
- Modify: `CLAUDE.md` — record the Testing panel + endpoints

---

## Task 1: OpenAPI contract for the Testing endpoints

**Files:**
- Modify: `src/main/resources/static/openapi.yaml`

- [ ] **Step 1: Add the three paths.** Insert this block into the `paths:` section of `src/main/resources/static/openapi.yaml` (place it after the last `/api/users...` path block, keeping 2-space indentation consistent with the file):

```yaml
  /api/testing/users:
    post:
      summary: Create a test user from a single credential (dev tooling, admin only)
      operationId: createTestUser
      tags: [Testing]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateTestUserRequest'
      responses:
        '201':
          description: Test user created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TestUserResponse'
        '400':
          description: Blank credential or credential contains '@'
        '403':
          description: Not an admin
        '409':
          description: Username or email already in use

  /api/testing/ranges/seed:
    post:
      summary: Create the four standard test ranges if missing (idempotent, admin only)
      operationId: seedTestRanges
      tags: [Testing]
      responses:
        '200':
          description: Ranges present (created or already existing)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SeedRangesResponse'
        '403':
          description: Not an admin

  /api/testing/mock-smartbox:
    post:
      summary: Create a mock SmartBox with N devices (dev tooling, admin only)
      operationId: createMockSmartBox
      tags: [Testing]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateMockSmartBoxRequest'
      responses:
        '201':
          description: Mock SmartBox created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/MockSmartBoxResponse'
        '400':
          description: deviceCount out of range (1-50)
        '403':
          description: Not an admin
```

- [ ] **Step 2: Add the schemas.** Insert these into the `components: schemas:` section (2-space indent under `schemas:`, matching neighbours):

```yaml
    CreateTestUserRequest:
      type: object
      required: [credential]
      properties:
        credential:
          type: string
          description: Used as username AND password; email becomes {credential}@test.local
          example: bob
    TestUserResponse:
      type: object
      required: [id, username, email]
      properties:
        id:
          type: string
          format: uuid
        username:
          type: string
        email:
          type: string
    SeededRange:
      type: object
      required: [id, name, created]
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        created:
          type: boolean
          description: true if inserted by this call, false if it already existed
    SeedRangesResponse:
      type: object
      required: [ranges]
      properties:
        ranges:
          type: array
          items:
            $ref: '#/components/schemas/SeededRange'
    CreateMockSmartBoxRequest:
      type: object
      required: [deviceCount]
      properties:
        deviceCount:
          type: integer
          format: int32
          minimum: 1
          maximum: 50
          example: 4
        alias:
          type: string
          nullable: true
    MockSmartBoxResponse:
      type: object
      required: [id, macAddress, deviceCount]
      properties:
        id:
          type: string
          format: uuid
        macAddress:
          type: string
        alias:
          type: string
          nullable: true
        deviceCount:
          type: integer
          format: int32
```

- [ ] **Step 3: Regenerate and verify the interface exists.**

Run: `./mvnw generate-sources -q`
Then verify: `ls target/generated-sources/openapi/ch/jp/smartground/api/TestingApi.java`
Expected: the file exists (interface with `createTestUser`, `seedTestRanges`, `createMockSmartBox`).

- [ ] **Step 4: Commit.**

```bash
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] openapi: add /api/testing endpoints for admin test-data panel"
```

---

## Task 2: Repository lookup methods

**Files:**
- Modify: `src/main/java/ch/jp/shooting/repository/RangeRepository.java`
- Modify: `src/main/java/ch/jp/shooting/repository/DeviceTypeRepository.java`

- [ ] **Step 1: Add `findByName` to `RangeRepository`.** Add this method inside the interface (next to `existsByName`):

```java
    java.util.Optional<Range> findByName(String name);
```

- [ ] **Step 2: Add `findByName` to `DeviceTypeRepository`.** Add this method inside the interface:

```java
    Optional<DeviceType> findByName(String name);
```

(`Optional` and `DeviceType` are already imported in that file.)

- [ ] **Step 3: Verify it compiles.**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit.**

```bash
git add src/main/java/ch/jp/shooting/repository/RangeRepository.java src/main/java/ch/jp/shooting/repository/DeviceTypeRepository.java
git commit -m "[backend] add findByName lookups for range + device-type test seeding"
```

---

## Task 3: TestDataService — createTestUser (TDD)

**Files:**
- Create: `src/main/java/ch/jp/shooting/service/TestDataService.java`
- Test: `src/test/java/ch/jp/shooting/service/TestDataServiceTest.java`

- [ ] **Step 1: Write the failing test.** Create `src/test/java/ch/jp/shooting/service/TestDataServiceTest.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.*;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.repository.auth.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestDataServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RangeRepository rangeRepository;
    @Mock SmartBoxRepository smartBoxRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock DeviceTypeRepository deviceTypeRepository;
    @Mock FirmwareConfigRepository firmwareConfigRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks TestDataService service;

    @Test
    void createTestUser_createsShooterWithDerivedFields() {
        when(userRepository.findByUsernameLower("bob")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("bob@test.local")).thenReturn(Optional.empty());
        when(roleRepository.findByName("SHOOTER")).thenReturn(Optional.of(new Role("SHOOTER", "x")));
        when(passwordEncoder.encode("bob")).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.createTestUser("bob");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getEmail()).isEqualTo("bob@test.local");
        assertThat(saved.getPasswordHash()).isEqualTo("ENC");
        assertThat(saved.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        verify(userRoleRepository).save(any());
        assertThat(result.getUsername()).isEqualTo("bob");
    }

    @Test
    void createTestUser_duplicateUsername_throwsConflict() {
        when(userRepository.findByUsernameLower("bob")).thenReturn(Optional.of(new User()));
        assertThatThrownBy(() -> service.createTestUser("bob"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createTestUser_blankCredential_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createTestUser("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createTestUser_credentialWithAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createTestUser("a@b"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./mvnw test -Dtest=TestDataServiceTest -q`
Expected: FAIL / compile error — `TestDataService` does not exist yet.

- [ ] **Step 3: Create `TestDataService` with `createTestUser`.** Create `src/main/java/ch/jp/shooting/service/TestDataService.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.model.auth.UserRoleEntity;
import ch.jp.shooting.repository.*;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.repository.auth.UserRoleRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NullMarked
public class TestDataService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RangeRepository rangeRepository;
    private final SmartBoxRepository smartBoxRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final FirmwareConfigRepository firmwareConfigRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            RangeRepository rangeRepository,
            SmartBoxRepository smartBoxRepository,
            DeviceRepository deviceRepository,
            DeviceTypeRepository deviceTypeRepository,
            FirmwareConfigRepository firmwareConfigRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.rangeRepository = rangeRepository;
        this.smartBoxRepository = smartBoxRepository;
        this.deviceRepository = deviceRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.firmwareConfigRepository = firmwareConfigRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Legt einen Test-Benutzer an: Benutzername = Passwort = credential, E-Mail abgeleitet.
    @Transactional
    public User createTestUser(String credential) {
        String cred = credential == null ? "" : credential.trim();
        if (cred.isEmpty() || cred.contains("@")) {
            throw new IllegalArgumentException("credential must be non-blank and must not contain '@'");
        }
        String email = cred.toLowerCase() + "@test.local";
        if (userRepository.findByUsernameLower(cred.toLowerCase()).isPresent()) {
            throw new ConflictException("Username already in use: " + cred);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email already in use: " + email);
        }
        Role shooter = roleRepository.findByName("SHOOTER")
                .orElseThrow(() -> new IllegalStateException("SHOOTER role missing – seed did not run"));

        User user = new User(email, cred, "Test");
        user.setUsername(cred);
        user.setPasswordHash(passwordEncoder.encode(cred));
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailBestaetigt(true);
        user.setSprache("DE");
        User saved = userRepository.save(user);
        userRoleRepository.save(new UserRoleEntity(saved, shooter, null));
        return saved;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes.**

Run: `./mvnw test -Dtest=TestDataServiceTest -q`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/ch/jp/shooting/service/TestDataService.java src/test/java/ch/jp/shooting/service/TestDataServiceTest.java
git commit -m "[backend] TestDataService: createTestUser from single credential"
```

---

## Task 4: TestDataService — seedRanges (TDD)

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/TestDataService.java`
- Modify: `src/test/java/ch/jp/shooting/service/TestDataServiceTest.java`

- [ ] **Step 1: Add the failing test.** Add these methods to `TestDataServiceTest` (and add `import ch.jp.shooting.model.Range;`, `import java.util.List;`, `import java.util.Map;`):

```java
    @Test
    void seedRanges_createsMissingLeavesExisting() {
        Range existing = new Range();
        existing.setName("Trapstand");
        when(rangeRepository.findByName("Vorderlader")).thenReturn(Optional.empty());
        when(rangeRepository.findByName("Trapstand")).thenReturn(Optional.of(existing));
        when(rangeRepository.findByName("Rollhase")).thenReturn(Optional.empty());
        when(rangeRepository.findByName("Kippreh")).thenReturn(Optional.empty());
        when(rangeRepository.save(any(Range.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TestDataService.SeededRange> result = service.seedRanges();

        Map<String, Boolean> createdByName = new java.util.HashMap<>();
        result.forEach(r -> createdByName.put(r.range().getName(), r.created()));
        assertThat(createdByName.get("Vorderlader")).isTrue();
        assertThat(createdByName.get("Trapstand")).isFalse();
        assertThat(createdByName.get("Rollhase")).isTrue();
        assertThat(createdByName.get("Kippreh")).isTrue();
        // 3 new ranges saved, the existing one not re-saved
        verify(rangeRepository, times(3)).save(any(Range.class));
    }
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./mvnw test -Dtest=TestDataServiceTest -q`
Expected: FAIL / compile error — `seedRanges` and `SeededRange` do not exist.

- [ ] **Step 3: Implement `seedRanges` + `SeededRange`.** Add to `TestDataService` (add `import ch.jp.shooting.model.Range;`, `import java.util.ArrayList;`, `import java.util.List;`):

```java
    // Standard-Testplätze; idempotent nach Name.
    private static final List<String> TEST_RANGE_NAMES =
            List.of("Vorderlader", "Trapstand", "Rollhase", "Kippreh");

    public record SeededRange(Range range, boolean created) {}

    @Transactional
    public List<SeededRange> seedRanges() {
        List<SeededRange> result = new ArrayList<>();
        for (String name : TEST_RANGE_NAMES) {
            Range existing = rangeRepository.findByName(name).orElse(null);
            if (existing != null) {
                result.add(new SeededRange(existing, false));
            } else {
                Range range = new Range();
                range.setName(name);
                result.add(new SeededRange(rangeRepository.save(range), true));
            }
        }
        return result;
    }
```

- [ ] **Step 4: Run the test to verify it passes.**

Run: `./mvnw test -Dtest=TestDataServiceTest -q`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/ch/jp/shooting/service/TestDataService.java src/test/java/ch/jp/shooting/service/TestDataServiceTest.java
git commit -m "[backend] TestDataService: idempotent seedRanges for 4 standard ranges"
```

---

## Task 5: TestDataService — createMockSmartBox (TDD)

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/TestDataService.java`
- Modify: `src/test/java/ch/jp/shooting/service/TestDataServiceTest.java`

- [ ] **Step 1: Add the failing test.** Add to `TestDataServiceTest` (add `import ch.jp.shooting.model.*;`):

```java
    @Test
    void createMockSmartBox_createsBoxAndDevices() {
        DeviceTypeGroup group = new DeviceTypeGroup("Wurfmaschine");
        DeviceType werfer = new DeviceType();
        werfer.setName("Werfer");
        werfer.setGroup(group);
        FirmwareConfig fw = new FirmwareConfig("0.6", "xiao-esp32s3");

        when(deviceTypeRepository.findByName("Werfer")).thenReturn(Optional.of(werfer));
        when(firmwareConfigRepository.findByVersionAndBoxType("0.6", "xiao-esp32s3"))
                .thenReturn(Optional.of(fw));
        when(smartBoxRepository.findByMacAddress(anyString())).thenReturn(Optional.empty());
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        SmartBox box = service.createMockSmartBox(3, "Mock-1");

        assertThat(box.getAlias()).isEqualTo("Mock-1");
        assertThat(box.getStatus()).isEqualTo(SmartBoxStates.OFFLINE);
        assertThat(box.getMacAddress()).isNotBlank();
        verify(deviceRepository, times(3)).save(any(Device.class));
    }

    @Test
    void createMockSmartBox_deviceCountZero_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createMockSmartBox(0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createMockSmartBox_deviceCountTooHigh_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createMockSmartBox(51, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `./mvnw test -Dtest=TestDataServiceTest -q`
Expected: FAIL / compile error — `createMockSmartBox` does not exist.

- [ ] **Step 3: Implement `createMockSmartBox`.** Add to `TestDataService` (add `import ch.jp.shooting.model.*;`, `import org.jspecify.annotations.Nullable;`, `import java.util.Locale;`, `import java.util.concurrent.ThreadLocalRandom;`):

```java
    // Legt eine Mock-SmartBox mit N Geräten (Typ "Werfer") an, keiner Range zugeordnet.
    @Transactional
    public SmartBox createMockSmartBox(int deviceCount, @Nullable String alias) {
        if (deviceCount < 1 || deviceCount > 50) {
            throw new IllegalArgumentException("deviceCount must be between 1 and 50");
        }
        DeviceType werfer = deviceTypeRepository.findByName("Werfer")
                .orElseThrow(() -> new IllegalStateException("DeviceType 'Werfer' missing – seed did not run"));
        FirmwareConfig firmware = firmwareConfigRepository
                .findByVersionAndBoxType("0.6", "xiao-esp32s3")
                .orElseThrow(() -> new IllegalStateException("FirmwareConfig 0.6/xiao-esp32s3 missing – seed did not run"));

        SmartBox box = new SmartBox();
        box.setMacAddress(generateUniqueMac());
        box.setStatus(SmartBoxStates.OFFLINE);
        box.setFirmwareConfig(firmware);
        box.setAppVersion("0.6");
        if (alias != null && !alias.isBlank()) {
            box.setAlias(alias.trim());
        }
        SmartBox savedBox = smartBoxRepository.save(box);

        for (int i = 1; i <= deviceCount; i++) {
            Device device = new Device();
            device.setSmartBox(savedBox);
            device.setDeviceTypeGroup(werfer.getGroup());
            device.setDeviceType(werfer);
            device.setAlias("Werfer " + i);
            deviceRepository.save(device);
        }
        return savedBox;
    }

    private String generateUniqueMac() {
        for (int attempt = 0; attempt < 100; attempt++) {
            byte[] mac = new byte[6];
            ThreadLocalRandom.current().nextBytes(mac);
            mac[0] = 0x02; // lokal verwaltete Unicast-Adresse
            String candidate = String.format(Locale.ROOT, "%02x:%02x:%02x:%02x:%02x:%02x",
                    mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
            if (smartBoxRepository.findByMacAddress(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique MAC after 100 attempts");
    }
```

Note: `DeviceType.getGroup()` is the accessor used in `DataInitializer` — confirm the name matches (`werfer.getGroup()`); if the entity exposes `getDeviceTypeGroup()` instead, use that. Check `src/main/java/ch/jp/shooting/model/DeviceType.java` before implementing.

- [ ] **Step 4: Run the test to verify it passes.**

Run: `./mvnw test -Dtest=TestDataServiceTest -q`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit.**

```bash
git add src/main/java/ch/jp/shooting/service/TestDataService.java src/test/java/ch/jp/shooting/service/TestDataServiceTest.java
git commit -m "[backend] TestDataService: createMockSmartBox with N Werfer devices"
```

---

## Task 6: TestingController implementing TestingApi

**Files:**
- Create: `src/main/java/ch/jp/shooting/api/TestingController.java`

- [ ] **Step 1: Implement the controller.** Create `src/main/java/ch/jp/shooting/api/TestingController.java`. Import the generated request/response types from `ch.jp.smartground.model` and the interface from `ch.jp.smartground.api.TestingApi`:

```java
package ch.jp.shooting.api;

import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.service.TestDataService;
import ch.jp.smartground.api.TestingApi;
import ch.jp.smartground.model.CreateMockSmartBoxRequest;
import ch.jp.smartground.model.CreateTestUserRequest;
import ch.jp.smartground.model.MockSmartBoxResponse;
import ch.jp.smartground.model.SeedRangesResponse;
import ch.jp.smartground.model.SeededRange;
import ch.jp.smartground.model.TestUserResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@NullMarked
public class TestingController implements TestingApi {

    private final TestDataService testDataService;

    public TestingController(TestDataService testDataService) {
        this.testDataService = testDataService;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestUserResponse> createTestUser(CreateTestUserRequest request) {
        User user = testDataService.createTestUser(request.getCredential());
        TestUserResponse body = new TestUserResponse()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SeedRangesResponse> seedTestRanges() {
        List<SeededRange> ranges = testDataService.seedRanges().stream()
                .map(sr -> {
                    Range r = sr.range();
                    return new SeededRange().id(r.getId()).name(r.getName()).created(sr.created());
                })
                .toList();
        return ResponseEntity.ok(new SeedRangesResponse().ranges(ranges));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MockSmartBoxResponse> createMockSmartBox(CreateMockSmartBoxRequest request) {
        SmartBox box = testDataService.createMockSmartBox(request.getDeviceCount(), request.getAlias());
        MockSmartBoxResponse body = new MockSmartBoxResponse()
                .id(box.getId())
                .macAddress(box.getMacAddress())
                .alias(box.getAlias())
                .deviceCount(request.getDeviceCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
```

Note: the generated model builder method names (e.g. `.id(...)`, `.username(...)`, `.getCredential()`, `.getDeviceCount()`) follow the schema property names from Task 1. If openapi-generator produced different setter styles in this project (e.g. `setId` only), open one generated model under `target/generated-sources/openapi/ch/jp/smartground/model/` and match its actual API. Adjust the fluent calls accordingly.

- [ ] **Step 2: Build to verify controller + interface line up.**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS. If it fails on model method names, inspect the generated model and fix as noted.

- [ ] **Step 3: Run the full test suite.**

Run: `./mvnw test -q`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4: Commit.**

```bash
git add src/main/java/ch/jp/shooting/api/TestingController.java
git commit -m "[backend] TestingController implementing generated TestingApi (ADMIN-gated)"
```

---

## Task 7: Frontend service — testingApi.js (TDD)

**Files:**
- Create: `smart-ground-ui/src/services/testingApi.js`
- Test: `smart-ground-ui/src/services/__tests__/testingApi.test.js`

- [ ] **Step 1: Write the failing test.** Create `smart-ground-ui/src/services/__tests__/testingApi.test.js`:

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as apiClient from '../apiClient.js';
import { createTestUser, seedRanges, createMockSmartBox } from '../testingApi.js';

describe('testingApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('createTestUser posts the credential', async () => {
    const spy = vi.spyOn(apiClient, 'apiFetch').mockResolvedValue({ username: 'bob' });
    await createTestUser('bob');
    expect(spy).toHaveBeenCalledWith('/testing/users', {
      method: 'POST',
      body: JSON.stringify({ credential: 'bob' }),
    });
  });

  it('seedRanges posts to the seed endpoint', async () => {
    const spy = vi.spyOn(apiClient, 'apiFetch').mockResolvedValue({ ranges: [] });
    await seedRanges();
    expect(spy).toHaveBeenCalledWith('/testing/ranges/seed', { method: 'POST' });
  });

  it('createMockSmartBox posts device count and alias', async () => {
    const spy = vi.spyOn(apiClient, 'apiFetch').mockResolvedValue({ macAddress: 'x' });
    await createMockSmartBox({ deviceCount: 4, alias: 'Mock-1' });
    expect(spy).toHaveBeenCalledWith('/testing/mock-smartbox', {
      method: 'POST',
      body: JSON.stringify({ deviceCount: 4, alias: 'Mock-1' }),
    });
  });
});
```

- [ ] **Step 2: Run the test to verify it fails.**

Run (from `smart-ground-ui/`): `npm run test -- testingApi`
Expected: FAIL — `../testingApi.js` cannot be resolved.

- [ ] **Step 3: Implement the service.** Create `smart-ground-ui/src/services/testingApi.js`:

```javascript
import { apiFetch } from './apiClient.js';

export async function createTestUser(credential) {
  return apiFetch('/testing/users', {
    method: 'POST',
    body: JSON.stringify({ credential }),
  });
}

export async function seedRanges() {
  return apiFetch('/testing/ranges/seed', { method: 'POST' });
}

export async function createMockSmartBox({ deviceCount, alias }) {
  return apiFetch('/testing/mock-smartbox', {
    method: 'POST',
    body: JSON.stringify({ deviceCount, alias }),
  });
}
```

- [ ] **Step 4: Run the test to verify it passes.**

Run (from `smart-ground-ui/`): `npm run test -- testingApi`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit.**

```bash
git add smart-ground-ui/src/services/testingApi.js smart-ground-ui/src/services/__tests__/testingApi.test.js
git commit -m "[ui] testingApi service for admin test-data endpoints"
```

---

## Task 8: Frontend view — TestingView.vue

**Files:**
- Create: `smart-ground-ui/src/views/admin/TestingView.vue`

- [ ] **Step 1: Create the view.** Create `smart-ground-ui/src/views/admin/TestingView.vue`:

```vue
<template>
  <div class="testing-view">
    <header class="page-header">
      <h1>Testing</h1>
      <p class="subtitle">Testdaten schnell erzeugen (nur Admin).</p>
    </header>

    <div class="cards">
      <!-- Create User -->
      <section class="card">
        <h2>Benutzer erstellen</h2>
        <p class="hint">Wird als Benutzername UND Passwort verwendet. E-Mail: {credential}@test.local, Rolle SHOOTER.</p>
        <FormField label="Credential">
          <input v-model="credential" class="input" placeholder="z.B. bob" @keyup.enter="onCreateUser" />
        </FormField>
        <Button :disabled="userBusy || !credential.trim()" @click="onCreateUser">Erstellen</Button>
        <p v-if="userMsg" :class="['result', userError ? 'err' : 'ok']">{{ userMsg }}</p>
      </section>

      <!-- Schiessplatz Setup -->
      <section class="card">
        <h2>Schiessplatz Setup</h2>
        <p class="hint">Erstellt Vorderlader, Trapstand, Rollhase, Kippreh (falls noch nicht vorhanden).</p>
        <Button :disabled="rangeBusy" @click="onSeedRanges">4 Plätze erstellen</Button>
        <ul v-if="rangeResult.length" class="result-list">
          <li v-for="r in rangeResult" :key="r.id">
            {{ r.name }} — <span :class="r.created ? 'ok' : 'muted'">{{ r.created ? 'erstellt' : 'bereits vorhanden' }}</span>
          </li>
        </ul>
        <p v-if="rangeError" class="result err">{{ rangeError }}</p>
      </section>

      <!-- Mock SmartBox -->
      <section class="card">
        <h2>Mock SmartBox erstellen</h2>
        <p class="hint">Erstellt eine SmartBox mit N Werfer-Geräten (keiner Range zugeordnet).</p>
        <FormField label="Anzahl Geräte">
          <input v-model.number="deviceCount" type="number" min="1" max="50" class="input" />
        </FormField>
        <FormField label="Alias (optional)">
          <input v-model="boxAlias" class="input" placeholder="z.B. Mock-1" />
        </FormField>
        <Button :disabled="boxBusy || deviceCount < 1 || deviceCount > 50" @click="onCreateBox">Erstellen</Button>
        <p v-if="boxMsg" :class="['result', boxError ? 'err' : 'ok']">{{ boxMsg }}</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import Button from '@/components/Button.vue';
import FormField from '@/components/FormField.vue';
import { createTestUser, seedRanges, createMockSmartBox } from '@/services/testingApi.js';

const credential = ref('');
const userBusy = ref(false);
const userMsg = ref('');
const userError = ref(false);

const onCreateUser = async () => {
  if (!credential.value.trim()) return;
  userBusy.value = true;
  userMsg.value = '';
  userError.value = false;
  try {
    const u = await createTestUser(credential.value.trim());
    userMsg.value = `Benutzer "${u.username}" erstellt (${u.email}).`;
    credential.value = '';
  } catch (e) {
    userError.value = true;
    userMsg.value = e.message ?? 'Fehler beim Erstellen.';
  } finally {
    userBusy.value = false;
  }
};

const rangeBusy = ref(false);
const rangeResult = ref([]);
const rangeError = ref('');

const onSeedRanges = async () => {
  rangeBusy.value = true;
  rangeError.value = '';
  rangeResult.value = [];
  try {
    const res = await seedRanges();
    rangeResult.value = res.ranges ?? [];
  } catch (e) {
    rangeError.value = e.message ?? 'Fehler beim Erstellen.';
  } finally {
    rangeBusy.value = false;
  }
};

const deviceCount = ref(4);
const boxAlias = ref('');
const boxBusy = ref(false);
const boxMsg = ref('');
const boxError = ref(false);

const onCreateBox = async () => {
  boxBusy.value = true;
  boxMsg.value = '';
  boxError.value = false;
  try {
    const box = await createMockSmartBox({ deviceCount: deviceCount.value, alias: boxAlias.value.trim() || null });
    boxMsg.value = `SmartBox ${box.macAddress} mit ${box.deviceCount} Geräten erstellt.`;
    boxAlias.value = '';
  } catch (e) {
    boxError.value = true;
    boxMsg.value = e.message ?? 'Fehler beim Erstellen.';
  } finally {
    boxBusy.value = false;
  }
};
</script>

<style scoped>
.testing-view { padding: 24px; }
.page-header { margin-bottom: 20px; }
.page-header h1 { font-size: 20px; font-weight: 700; }
.subtitle { color: var(--sg-text-faint); font-size: 13px; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.card {
  background: var(--sg-bg-card);
  border: 1px solid var(--sg-border);
  border-radius: 8px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card h2 { font-size: 15px; font-weight: 600; }
.hint { font-size: 12px; color: var(--sg-text-faint); }
.input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--sg-border);
  border-radius: 6px;
  font: inherit;
}
.result { font-size: 13px; margin: 0; }
.result-list { font-size: 13px; list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; }
.ok { color: var(--sg-color-success, #2e7d32); }
.err { color: var(--sg-color-danger); }
.muted { color: var(--sg-text-faint); }
</style>
```

- [ ] **Step 2: Verify the app builds.**

Run (from `smart-ground-ui/`): `npm run build`
Expected: build succeeds (no import/parse errors). The route isn't wired yet — that's Task 9.

- [ ] **Step 3: Commit.**

```bash
git add smart-ground-ui/src/views/admin/TestingView.vue
git commit -m "[ui] TestingView: create user / seed ranges / mock smartbox cards"
```

---

## Task 9: Wire router, sidebar, and active-nav

**Files:**
- Modify: `smart-ground-ui/src/router/index.js`
- Modify: `smart-ground-ui/src/components/Sidebar.vue`
- Modify: `smart-ground-ui/src/App.vue`

- [ ] **Step 1: Add the route.** In `smart-ground-ui/src/router/index.js`, add the import near the other admin view imports:

```javascript
import TestingView from '@/views/admin/TestingView.vue';
```

Then add this route inside the `// ── Admin routes ──` group (e.g. after the `/passen` line):

```javascript
  { path: '/testing',              component: TestingView,                  meta: { layout: 'admin', permission: 'MANAGE_USERS' } },
```

- [ ] **Step 2: Add the sidebar nav item.** In `smart-ground-ui/src/components/Sidebar.vue`, add this as the last entry of the `allNavItems` array:

```javascript
  { id: 'testing', label: 'Testing', icon: 'cpu', requiredPermission: 'MANAGE_USERS' },
```

- [ ] **Step 3: Add the active-nav mapping.** In `smart-ground-ui/src/App.vue`, add this entry to the `navMap` object:

```javascript
  '/testing': 'testing',
```

- [ ] **Step 4: Verify build + existing router tests.**

Run (from `smart-ground-ui/`): `npm run build`
Expected: build succeeds.
Run: `npm run test -- router`
Expected: existing router tests still pass.

- [ ] **Step 5: Commit.**

```bash
git add smart-ground-ui/src/router/index.js smart-ground-ui/src/components/Sidebar.vue smart-ground-ui/src/App.vue
git commit -m "[ui] wire Testing admin route + sidebar nav (MANAGE_USERS gated)"
```

---

## Task 10: Update CLAUDE.md

**Files:**
- Modify: `smart-ground-backend/CLAUDE.md`

- [ ] **Step 1: Add the controller row.** In the "Controllers Overview" table, add:

```
| `TestingController` | POST /api/testing/users, /api/testing/ranges/seed, /api/testing/mock-smartbox | Admin-only dev tooling: create test user / seed 4 ranges / create mock SmartBox with N devices (implements `TestingApi`) |
```

- [ ] **Step 2: Add an Implementation-Status note.** Under "### ✅ Implemented", add:

```
- **Admin Testing panel** (dev tooling) — `/api/testing/*` endpoints (`ROLE_ADMIN`-gated) create a test user (single credential = username+password, SHOOTER role), seed the 4 standard ranges (Vorderlader, Trapstand, Rollhase, Kippreh, idempotent), and create a mock SmartBox with N `Werfer` devices (unassigned, status OFFLINE). Frontend: `/testing` admin view, gated on `MANAGE_USERS`. `DataInitializer` is unchanged — actions are on-demand only.
```

- [ ] **Step 3: Commit.**

```bash
git add CLAUDE.md
git commit -m "[backend] docs: record admin Testing panel + /api/testing endpoints"
```

---

## Task 11: Full verification

- [ ] **Step 1: Backend full build + tests.**

Run (from `smart-ground-backend/`): `./mvnw clean package -q`
Expected: BUILD SUCCESS, no warnings/failures.

- [ ] **Step 2: Frontend tests + build.**

Run (from `smart-ground-ui/`): `npm run test` then `npm run build`
Expected: all tests pass, build succeeds.

- [ ] **Step 3 (optional manual smoke test):** Start backend (`./mvnw spring-boot:run`) + frontend (`npm run dev`), log in as `admin@smartground.local` / `admin123`, open the **Testing** nav, and exercise all three cards. Confirm a user, 4 ranges, and a mock SmartBox with devices appear in their respective admin views.

---

## Notes for the implementer

- **Verify accessor names before implementing Task 5/6:** check `DeviceType.getGroup()` vs `getDeviceTypeGroup()`, and the generated model builder methods under `target/generated-sources/openapi/ch/jp/smartground/model/`. The plan uses the names seen in `DataInitializer` and the schema property names; adjust if the codegen differs.
- **`User` constructor** is `new User(email, vorname, nachname)`; `setUsername` is expected to derive `usernameLower` (as `DataInitializer` relies on). If it does not, set it explicitly.
- Do not add `@RequestMapping`/`@PostMapping` to the controller — routing is owned by the generated `TestingApi` interface.

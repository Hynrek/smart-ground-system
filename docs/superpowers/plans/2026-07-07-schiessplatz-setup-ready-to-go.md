# Schiessplatz Setup Ready-to-Go Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the admin Testing panel's "Schiessplatz Setup" action so one click produces 4 fully wired ranges — 8 positions each, 2 SmartBoxes × 4 devices per range (32 devices total), all devices assigned 1:1 to positions.

**Architecture:** No new endpoint. `TestDataService.seedRanges()` grows to build positions/boxes/devices per range (skipping ranges that already have positions); `SeedRangesResponse`/`SeededRange` (OpenAPI) grow three integer fields to report what was built; `TestingView.vue` renders the extended counts.

**Tech Stack:** Java 25 / Spring Boot 4 / JPA (backend), Vue 3 `<script setup>` (frontend), JUnit 5 + Mockito + AssertJ (backend tests), openapi-generator (contract-first).

## Global Constraints

- `openapi.yaml` is the single source of truth for the REST contract — update it before/with the implementation, then run `./mvnw generate-sources`.
- Controllers implement generated interfaces only — no `@GetMapping`/`@PostMapping` etc. added anywhere.
- `@NullMarked` on new/touched classes; `@Nullable` only where a value can actually be null.
- JSON columns use `TEXT`, never `JSONB` (not touched by this plan, but no regressions).
- German inline comments for backend domain logic; English identifiers.
- German display labels in UI text; English identifiers/comments.
- No unused code left behind — delete, don't comment out.
- Coverage target ≥80% for new/changed code.
- Commit message format: `[backend] short description` / `[ui] short description`.
- `DataInitializer` is not touched — this remains on-demand admin tooling only.

---

### Task 1: Extend the OpenAPI contract for the richer `SeededRange`

**Files:**
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml:5086-5097` (the `SeededRange` schema)

**Interfaces:**
- Produces: generated `ch.jp.smartground.model.SeededRange` gains `positionsCreated`, `boxesCreated`, `devicesAssigned` (all `int32`, required) — Task 3 depends on these generated setters.

- [ ] **Step 1: Edit the `SeededRange` schema**

Replace the schema at `openapi.yaml:5086-5097`:

```yaml
    SeededRange:
      type: object
      required: [id, name, created, positionsCreated, boxesCreated, devicesAssigned]
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        created:
          type: boolean
          description: true if inserted by this call, false if it already existed
        positionsCreated:
          type: integer
          format: int32
          description: Number of RangePositions created for this range by this call (0 if it already had positions)
        boxesCreated:
          type: integer
          format: int32
          description: Number of mock SmartBoxes created for this range by this call
        devicesAssigned:
          type: integer
          format: int32
          description: Number of devices created and assigned to this range's positions by this call
```

Also update the `seedTestRanges` operation summary at `openapi.yaml:557` to reflect the new behavior:

```yaml
      summary: Create the four standard test ranges with 8 positions, 2 mock SmartBoxes (4 devices each) and full device-to-position assignment if missing (idempotent per range, admin only)
```

- [ ] **Step 2: Regenerate the OpenAPI interfaces**

Run: `cd smart-ground-backend && ./mvnw generate-sources`
Expected: `BUILD SUCCESS`. Inspect `target/generated-sources/openapi/ch/jp/smartground/model/SeededRange.java` and confirm it now has `positionsCreated(Integer)`, `boxesCreated(Integer)`, `devicesAssigned(Integer)` fluent setters.

- [ ] **Step 3: Commit**

```bash
cd "smart-ground-backend"
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] extend SeededRange schema with position/box/device counts"
```

---

### Task 2: `TestDataService` — build positions, boxes and devices per range

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/service/TestDataService.java`
- Test: `smart-ground-backend/src/test/java/ch/jp/shooting/service/TestDataServiceTest.java`

**Interfaces:**
- Consumes: `RangePositionRepository` (existing, `ch.jp.shooting.repository`) — `countByRangeId(UUID)`, `save(RangePosition)`.
- Produces: `TestDataService.SeededRange` record becomes `SeededRange(Range range, boolean created, int positionsCreated, int boxesCreated, int devicesAssigned)` — Task 3 (`TestingController`) consumes these three new accessors. `TestDataService.seedRanges()` keeps its signature `List<SeededRange> seedRanges()`. `TestDataService.createMockSmartBox(int, String)` keeps its exact signature and behavior (internal refactor only).

- [ ] **Step 1: Write the failing tests**

Replace the full contents of `smart-ground-backend/src/test/java/ch/jp/shooting/service/TestDataServiceTest.java` with:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.model.*;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestDataServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RangeRepository rangeRepository;
    @Mock RangePositionRepository positionRepository;
    @Mock SmartBoxRepository smartBoxRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock DeviceTypeRepository deviceTypeRepository;
    @Mock DeviceTypeGroupRepository deviceTypeGroupRepository;
    @Mock FirmwareConfigRepository firmwareConfigRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks TestDataService service;

    private static final List<String> RANGE_NAMES =
            List.of("Vorderlader", "Trapstand", "Rollhase", "Kippreh");

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

    @Test
    void seedRanges_createsMissingLeavesExisting() {
        Range existing = new Range();
        existing.setId(UUID.randomUUID());
        existing.setName("Trapstand");
        when(rangeRepository.findByName("Vorderlader")).thenReturn(Optional.empty());
        when(rangeRepository.findByName("Trapstand")).thenReturn(Optional.of(existing));
        when(rangeRepository.findByName("Rollhase")).thenReturn(Optional.empty());
        when(rangeRepository.findByName("Kippreh")).thenReturn(Optional.empty());
        when(rangeRepository.save(any(Range.class))).thenAnswer(inv -> {
            Range r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        // Isolate range-creation behavior: treat every range as already fully wired
        // so this test doesn't need to stub firmware/box/device resolution.
        when(positionRepository.countByRangeId(any())).thenReturn(8);

        List<TestDataService.SeededRange> result = service.seedRanges();

        Map<String, Boolean> createdByName = new HashMap<>();
        result.forEach(r -> createdByName.put(r.range().getName(), r.created()));
        assertThat(createdByName.get("Vorderlader")).isTrue();
        assertThat(createdByName.get("Trapstand")).isFalse();
        assertThat(createdByName.get("Rollhase")).isTrue();
        assertThat(createdByName.get("Kippreh")).isTrue();
        // 3 new ranges saved, the existing one not re-saved
        verify(rangeRepository, times(3)).save(any(Range.class));
        result.forEach(r -> {
            assertThat(r.positionsCreated()).isZero();
            assertThat(r.boxesCreated()).isZero();
            assertThat(r.devicesAssigned()).isZero();
        });
        verifyNoInteractions(firmwareConfigRepository, deviceTypeGroupRepository, deviceTypeRepository);
    }

    @Test
    void seedRanges_freshDatabase_createsFullyWiredSetupPerRange() {
        mockFreshRanges();
        mockPositionRepositorySaveIdentity();
        mockWerferDeviceTypeResolution();
        when(smartBoxRepository.findByMacAddress(anyString())).thenReturn(Optional.empty());
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TestDataService.SeededRange> result = service.seedRanges();

        assertThat(result).hasSize(4);
        result.forEach(r -> {
            assertThat(r.created()).isTrue();
            assertThat(r.positionsCreated()).isEqualTo(8);
            assertThat(r.boxesCreated()).isEqualTo(2);
            assertThat(r.devicesAssigned()).isEqualTo(8);
        });
        // 4 ranges x 2 boxes
        verify(smartBoxRepository, times(8)).save(any(SmartBox.class));
        // 4 ranges x 8 devices
        verify(deviceRepository, times(32)).save(any(Device.class));
    }

    @Test
    void seedRanges_freshDatabase_wiresPositionsToDevicesInOrder() {
        mockFreshRanges();
        mockPositionRepositorySaveIdentity();
        mockWerferDeviceTypeResolution();
        when(smartBoxRepository.findByMacAddress(anyString())).thenReturn(Optional.empty());
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        service.seedRanges();

        ArgumentCaptor<RangePosition> captor = ArgumentCaptor.forClass(RangePosition.class);
        verify(positionRepository, atLeastOnce()).save(captor.capture());

        Map<String, RangePosition> vorderladerByLabel = new HashMap<>();
        captor.getAllValues().stream()
                .filter(p -> p.getRange().getName().equals("Vorderlader"))
                .filter(p -> p.getDevice() != null)
                .forEach(p -> vorderladerByLabel.put(p.getLabel(), p));

        assertThat(vorderladerByLabel).hasSize(8);
        assertThat(vorderladerByLabel.get("A").getDevice().getAlias()).isEqualTo("Werfer A");
        assertThat(vorderladerByLabel.get("A").getDevice().getSmartBox().getAlias())
                .isEqualTo("Vorderlader Box 1");
        assertThat(vorderladerByLabel.get("D").getDevice().getSmartBox().getAlias())
                .isEqualTo("Vorderlader Box 1");
        assertThat(vorderladerByLabel.get("E").getDevice().getSmartBox().getAlias())
                .isEqualTo("Vorderlader Box 2");
        assertThat(vorderladerByLabel.get("H").getDevice().getSmartBox().getAlias())
                .isEqualTo("Vorderlader Box 2");
        assertThat(vorderladerByLabel.get("E").getDevice().getAlias()).isEqualTo("Werfer E");
        assertThat(vorderladerByLabel.get("A").getDevice().getRange().getName()).isEqualTo("Vorderlader");
    }

    @Test
    void seedRanges_secondCall_isNoOpWhenAlreadyFullyWired() {
        for (String name : RANGE_NAMES) {
            Range existing = new Range();
            existing.setId(UUID.randomUUID());
            existing.setName(name);
            when(rangeRepository.findByName(name)).thenReturn(Optional.of(existing));
        }
        when(positionRepository.countByRangeId(any())).thenReturn(8);

        List<TestDataService.SeededRange> result = service.seedRanges();

        result.forEach(r -> {
            assertThat(r.created()).isFalse();
            assertThat(r.positionsCreated()).isZero();
            assertThat(r.boxesCreated()).isZero();
            assertThat(r.devicesAssigned()).isZero();
        });
        verify(rangeRepository, never()).save(any());
        verify(positionRepository, never()).save(any());
        verify(smartBoxRepository, never()).save(any());
        verify(deviceRepository, never()).save(any());
        verifyNoInteractions(firmwareConfigRepository, deviceTypeGroupRepository, deviceTypeRepository);
    }

    @Test
    void seedRanges_partiallyWiredRange_isLeftUntouched() {
        Map<String, UUID> ids = new HashMap<>();
        for (String name : RANGE_NAMES) {
            Range existing = new Range();
            UUID id = UUID.randomUUID();
            existing.setId(id);
            existing.setName(name);
            ids.put(name, id);
            when(rangeRepository.findByName(name)).thenReturn(Optional.of(existing));
        }
        // Trapstand was manually half-configured; everything else is already fully wired.
        when(positionRepository.countByRangeId(ids.get("Trapstand"))).thenReturn(3);
        when(positionRepository.countByRangeId(ids.get("Vorderlader"))).thenReturn(8);
        when(positionRepository.countByRangeId(ids.get("Rollhase"))).thenReturn(8);
        when(positionRepository.countByRangeId(ids.get("Kippreh"))).thenReturn(8);

        List<TestDataService.SeededRange> result = service.seedRanges();

        TestDataService.SeededRange trapstand = result.stream()
                .filter(r -> r.range().getName().equals("Trapstand"))
                .findFirst().orElseThrow();
        assertThat(trapstand.created()).isFalse();
        assertThat(trapstand.positionsCreated()).isZero();
        assertThat(trapstand.boxesCreated()).isZero();
        assertThat(trapstand.devicesAssigned()).isZero();
        verify(positionRepository, never()).save(any());
        verify(smartBoxRepository, never()).save(any());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void seedRanges_missingFirmwareConfig_throwsIllegalState() {
        mockFreshRanges();
        mockPositionRepositorySaveIdentity();
        when(firmwareConfigRepository.findByVersionAndBoxType("0.6", "xiao-esp32s3"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.seedRanges())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createMockSmartBox_createsBoxAndDevices() {
        DeviceTypeGroup group = new DeviceTypeGroup("Wurfmaschine");
        DeviceType werfer = new DeviceType();
        werfer.setName("Werfer");
        werfer.setGroup(group);
        FirmwareConfig fw = new FirmwareConfig("0.6", "xiao-esp32s3");

        when(firmwareConfigRepository.findByVersionAndBoxType("0.6", "xiao-esp32s3"))
                .thenReturn(Optional.of(fw));
        when(deviceTypeGroupRepository.findByName("Wurfmaschine")).thenReturn(Optional.of(group));
        when(deviceTypeRepository.findByGroupIdAndSignalType_FirmwareConfigId(any(), any()))
                .thenReturn(Optional.of(werfer));
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

    // ── Shared test setup helpers ────────────────────────────────────────────

    private void mockFreshRanges() {
        for (String name : RANGE_NAMES) {
            when(rangeRepository.findByName(name)).thenReturn(Optional.empty());
        }
        when(rangeRepository.save(any(Range.class))).thenAnswer(inv -> {
            Range r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    private void mockPositionRepositorySaveIdentity() {
        when(positionRepository.countByRangeId(any())).thenReturn(0);
        when(positionRepository.save(any(RangePosition.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void mockWerferDeviceTypeResolution() {
        DeviceTypeGroup group = new DeviceTypeGroup("Wurfmaschine");
        DeviceType werfer = new DeviceType();
        werfer.setName("Werfer");
        werfer.setGroup(group);
        FirmwareConfig fw = new FirmwareConfig("0.6", "xiao-esp32s3");
        when(firmwareConfigRepository.findByVersionAndBoxType("0.6", "xiao-esp32s3"))
                .thenReturn(Optional.of(fw));
        when(deviceTypeGroupRepository.findByName("Wurfmaschine")).thenReturn(Optional.of(group));
        when(deviceTypeRepository.findByGroupIdAndSignalType_FirmwareConfigId(any(), any()))
                .thenReturn(Optional.of(werfer));
    }
}
```

- [ ] **Step 2: Run the tests to verify the new ones fail**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=TestDataServiceTest`
Expected: compile error or failures on `seedRanges_freshDatabase_*`, `seedRanges_partiallyWiredRange_isLeftUntouched`, `seedRanges_missingFirmwareConfig_throwsIllegalState` (constructor/record shape mismatch — `RangePositionRepository` mock has no matching constructor argument yet, `SeededRange` has no `positionsCreated()` etc.). `seedRanges_createsMissingLeavesExisting` fails too until Step 3 lands (currently expects `positionsCreated()` etc. that don't exist).

- [ ] **Step 3: Implement `TestDataService`**

Replace the full contents of `smart-ground-backend/src/main/java/ch/jp/shooting/service/TestDataService.java` with:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.model.*;
import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.model.auth.UserRoleEntity;
import ch.jp.shooting.repository.*;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.repository.auth.UserRoleRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

// Admin-only Dev-Tooling zum Anlegen von Testdaten (Benutzer, Ranges, SmartBoxes).
@Service
@NullMarked
public class TestDataService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RangeRepository rangeRepository;
    private final RangePositionRepository positionRepository;
    private final SmartBoxRepository smartBoxRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final DeviceTypeGroupRepository deviceTypeGroupRepository;
    private final FirmwareConfigRepository firmwareConfigRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            RangeRepository rangeRepository,
            RangePositionRepository positionRepository,
            SmartBoxRepository smartBoxRepository,
            DeviceRepository deviceRepository,
            DeviceTypeRepository deviceTypeRepository,
            DeviceTypeGroupRepository deviceTypeGroupRepository,
            FirmwareConfigRepository firmwareConfigRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.rangeRepository = rangeRepository;
        this.positionRepository = positionRepository;
        this.smartBoxRepository = smartBoxRepository;
        this.deviceRepository = deviceRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.deviceTypeGroupRepository = deviceTypeGroupRepository;
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

    // Standard-Testplätze; idempotent nach Name.
    private static final List<String> TEST_RANGE_NAMES =
            List.of("Vorderlader", "Trapstand", "Rollhase", "Kippreh");

    // Positions-Labels für ein "ready to go"-Setup: 8 Positionen A–H je Platz.
    private static final List<String> POSITION_LABELS =
            List.of("A", "B", "C", "D", "E", "F", "G", "H");

    private static final int DEVICES_PER_BOX = 4;

    public record SeededRange(
            Range range,
            boolean created,
            int positionsCreated,
            int boxesCreated,
            int devicesAssigned) {}

    // Legt die 4 Standard-Ranges an (idempotent nach Name) und baut für jede neue Range
    // (ohne bestehende Positionen) 8 Positionen + 2 Mock-SmartBoxes à 4 Geräte und weist
    // alle 8 Geräte den 8 Positionen 1:1 zu. Ranges mit bereits vorhandenen Positionen
    // werden nicht angetastet (auch nicht bei einer nur teilweise befüllten Range).
    @Transactional
    public List<SeededRange> seedRanges() {
        List<SeededRange> result = new ArrayList<>();
        for (String name : TEST_RANGE_NAMES) {
            Range existing = rangeRepository.findByName(name).orElse(null);
            boolean created;
            Range range;
            if (existing != null) {
                range = existing;
                created = false;
            } else {
                Range fresh = new Range();
                fresh.setName(name);
                range = rangeRepository.save(fresh);
                created = true;
            }

            if (positionRepository.countByRangeId(range.getId()) > 0) {
                result.add(new SeededRange(range, created, 0, 0, 0));
                continue;
            }

            List<RangePosition> positions = createPositions(range);
            WerferDeviceTypeContext ctx = resolveWerferDeviceType();
            int boxesCreated = buildBoxesAndAssignDevices(range, positions, ctx);
            result.add(new SeededRange(range, created, positions.size(), boxesCreated, positions.size()));
        }
        return result;
    }

    private List<RangePosition> createPositions(Range range) {
        List<RangePosition> positions = new ArrayList<>();
        for (int i = 0; i < POSITION_LABELS.size(); i++) {
            RangePosition position = new RangePosition();
            position.setRange(range);
            position.setLabel(POSITION_LABELS.get(i));
            position.setSortOrder(i);
            positions.add(positionRepository.save(position));
        }
        return positions;
    }

    // Baut 2 Mock-SmartBoxes à 4 Geräte und weist Box 1 → Positionen A–D, Box 2 → Positionen E–H zu.
    private int buildBoxesAndAssignDevices(Range range, List<RangePosition> positions, WerferDeviceTypeContext ctx) {
        int boxCount = positions.size() / DEVICES_PER_BOX;
        for (int b = 0; b < boxCount; b++) {
            SmartBox box = new SmartBox();
            box.setMacAddress(generateUniqueMac());
            box.setStatus(SmartBoxStates.OFFLINE);
            box.setFirmwareConfig(ctx.firmware());
            box.setAppVersion("0.6");
            box.setAlias(range.getName() + " Box " + (b + 1));
            SmartBox savedBox = smartBoxRepository.save(box);

            for (int d = 0; d < DEVICES_PER_BOX; d++) {
                RangePosition position = positions.get(b * DEVICES_PER_BOX + d);
                Device device = new Device();
                device.setSmartBox(savedBox);
                device.setDeviceTypeGroup(ctx.group());
                device.setDeviceType(ctx.deviceType());
                device.setAlias("Werfer " + position.getLabel());
                device.setRange(range);
                device.setRangePosition(position);
                Device savedDevice = deviceRepository.save(device);

                position.setDevice(savedDevice);
                positionRepository.save(position);
            }
        }
        return boxCount;
    }

    private record WerferDeviceTypeContext(FirmwareConfig firmware, DeviceTypeGroup group, DeviceType deviceType) {}

    // Löst FirmwareConfig/DeviceTypeGroup/DeviceType für den generischen "Werfer"-Mock auf.
    private WerferDeviceTypeContext resolveWerferDeviceType() {
        FirmwareConfig firmware = firmwareConfigRepository
                .findByVersionAndBoxType("0.6", "xiao-esp32s3")
                .orElseThrow(() -> new IllegalStateException("FirmwareConfig 0.6/xiao-esp32s3 missing – seed did not run"));
        DeviceTypeGroup werferGroup = deviceTypeGroupRepository.findByName("Wurfmaschine")
                .orElseThrow(() -> new IllegalStateException("DeviceTypeGroup 'Wurfmaschine' missing – seed did not run"));
        // DeviceType eindeutig über (Gruppe, FirmwareConfig) auflösen – der Name allein ist nicht eindeutig
        // (pro FirmwareConfig existiert ein eigener "Werfer"-Typ, z.B. pico2w vs. xiao-esp32s3).
        DeviceType werfer = deviceTypeRepository
                .findByGroupIdAndSignalType_FirmwareConfigId(werferGroup.getId(), firmware.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "DeviceType für Gruppe 'Wurfmaschine' und FirmwareConfig 0.6/xiao-esp32s3 fehlt – seed did not run"));
        return new WerferDeviceTypeContext(firmware, werferGroup, werfer);
    }

    // Legt eine Mock-SmartBox mit N Geräten (Typ "Werfer") an, keiner Range zugeordnet.
    @Transactional
    public SmartBox createMockSmartBox(int deviceCount, @Nullable String alias) {
        if (deviceCount < 1 || deviceCount > 50) {
            throw new IllegalArgumentException("deviceCount must be between 1 and 50");
        }
        WerferDeviceTypeContext ctx = resolveWerferDeviceType();

        SmartBox box = new SmartBox();
        box.setMacAddress(generateUniqueMac());
        box.setStatus(SmartBoxStates.OFFLINE);
        box.setFirmwareConfig(ctx.firmware());
        box.setAppVersion("0.6");
        if (alias != null && !alias.isBlank()) {
            box.setAlias(alias.trim());
        }
        SmartBox savedBox = smartBoxRepository.save(box);

        for (int i = 1; i <= deviceCount; i++) {
            Device device = new Device();
            device.setSmartBox(savedBox);
            device.setDeviceTypeGroup(ctx.group());
            device.setDeviceType(ctx.deviceType());
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
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd smart-ground-backend && ./mvnw test -Dtest=TestDataServiceTest`
Expected: `Tests run: 13, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
cd "smart-ground-backend"
git add src/main/java/ch/jp/shooting/service/TestDataService.java src/test/java/ch/jp/shooting/service/TestDataServiceTest.java
git commit -m "[backend] build positions/boxes/devices in Schiessplatz Setup seed"
```

---

### Task 3: `TestingController` — pass the new counts through

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/TestingController.java:46-54`

**Interfaces:**
- Consumes: `TestDataService.SeededRange.positionsCreated()/boxesCreated()/devicesAssigned()` (Task 2), generated `ch.jp.smartground.model.SeededRange.positionsCreated(Integer)/boxesCreated(Integer)/devicesAssigned(Integer)` (Task 1).

- [ ] **Step 1: Update the mapping in `seedTestRanges`**

Replace the method body at `TestingController.java:44-54`:

```java
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SeedRangesResponse> seedTestRanges() {
        List<SeededRange> ranges = testDataService.seedRanges().stream()
                .map(sr -> {
                    Range r = sr.range();
                    return new SeededRange()
                            .id(r.getId())
                            .name(r.getName())
                            .created(sr.created())
                            .positionsCreated(sr.positionsCreated())
                            .boxesCreated(sr.boxesCreated())
                            .devicesAssigned(sr.devicesAssigned());
                })
                .toList();
        return ResponseEntity.ok(new SeedRangesResponse().ranges(ranges));
    }
```

- [ ] **Step 2: Compile and run the full backend test suite**

Run: `cd smart-ground-backend && ./mvnw clean test`
Expected: `BUILD SUCCESS`, all tests pass (no test file exists for `TestingController` today, so this step is a compile + full-suite regression check, not a new test).

- [ ] **Step 3: Commit**

```bash
cd "smart-ground-backend"
git add src/main/java/ch/jp/shooting/api/TestingController.java
git commit -m "[backend] expose position/box/device counts from seedRanges endpoint"
```

---

### Task 4: Frontend — show the ready-to-go result in the Testing view

**Files:**
- Modify: `smart-ground-ui/src/views/admin/TestingView.vue:20-31`

**Interfaces:**
- Consumes: `seedRanges()` from `src/services/testingApi.js` (unchanged signature) resolving to `{ ranges: [{ id, name, created, positionsCreated, boxesCreated, devicesAssigned }] }`.

- [ ] **Step 1: Update the "Schiessplatz Setup" card**

In `smart-ground-ui/src/views/admin/TestingView.vue`, replace lines 20-31:

```html
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
```

with:

```html
      <!-- Schiessplatz Setup -->
      <section class="card">
        <h2>Schiessplatz Setup</h2>
        <p class="hint">Erstellt Vorderlader, Trapstand, Rollhase, Kippreh mit je 8 Positionen, 2 SmartBoxes (4 Geräte je Box) und weist alle 32 Geräte den Positionen zu (falls noch nicht vorhanden).</p>
        <Button :disabled="rangeBusy" @click="onSeedRanges">4 Plätze erstellen</Button>
        <ul v-if="rangeResult.length" class="result-list">
          <li v-for="r in rangeResult" :key="r.id">
            {{ r.name }} —
            <span v-if="r.positionsCreated > 0" class="ok">
              erstellt · {{ r.positionsCreated }} Positionen · {{ r.boxesCreated }} Boxen · {{ r.devicesAssigned }} Geräte zugeordnet
            </span>
            <span v-else :class="r.created ? 'ok' : 'muted'">
              {{ r.created ? 'erstellt' : 'bereits vorhanden' }}
            </span>
          </li>
        </ul>
        <p v-if="rangeError" class="result err">{{ rangeError }}</p>
      </section>
```

No changes needed in `<script setup>` — `onSeedRanges` already assigns the whole `res.ranges` array to `rangeResult`, so the new fields flow through automatically.

- [ ] **Step 2: Manual verification in the browser**

Start the backend (`cd smart-ground-backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres`, requires `docker compose up` for Postgres/Mosquitto first) and the frontend dev server (`cd smart-ground-ui && npm run dev`). Using the preview tooling:
1. Log in as `admin@smartground.local` / `admin123`.
2. Navigate to `/testing`.
3. Click "4 Plätze erstellen" on a fresh/wiped DB. Confirm all 4 ranges show `erstellt · 8 Positionen · 2 Boxen · 8 Geräte zugeordnet`.
4. Click the button again. Confirm all 4 ranges now show `bereits vorhanden`.
5. Navigate to `/ranges`, open one of the 4 ranges, and confirm it has 8 positions (A–H) each with a device assigned, and `/smartboxes` shows 8 new boxes aliased `"<Range> Box 1"` / `"<Range> Box 2"`.

- [ ] **Step 3: Run the frontend test suite**

Run: `cd smart-ground-ui && npm run test`
Expected: all existing tests still pass (no test file targets `TestingView.vue` today, so this is a regression check — `testingApi.test.js` is unaffected since the service call itself didn't change).

- [ ] **Step 4: Commit**

```bash
cd "smart-ground-ui"
git add src/views/admin/TestingView.vue
git commit -m "[ui] show ready-to-go position/box/device counts in Schiessplatz Setup"
```

---

### Task 5: Update `CLAUDE.md` with the extended behavior

**Files:**
- Modify: `smart-ground-backend/CLAUDE.md` (the "Admin Testing panel" bullet under "✅ Implemented")

**Interfaces:** None (documentation only).

- [ ] **Step 1: Update the implementation-status bullet**

In `smart-ground-backend/CLAUDE.md`, find the bullet starting `**Admin Testing panel** (dev tooling, implemented 2026-07-02)`. Append a sentence:

```
 Extended 2026-07-07: seeding a fresh range now also creates 8 `RangePosition`s (A–H), 2 mock SmartBoxes (aliased `"<Range> Box 1/2"`), and 4 `Werfer` devices per box, assigning all 8 devices 1:1 to that range's 8 positions — a "ready to go" setup in one click. A range is left untouched if it already has any positions (idempotent per range, not just per name).
```

- [ ] **Step 2: Commit**

```bash
git add "smart-ground-backend/CLAUDE.md"
git commit -m "docs: record Schiessplatz Setup ready-to-go extension in backend CLAUDE.md"
```

---

## Post-plan verification

- [ ] `cd smart-ground-backend && ./mvnw clean test` — full backend suite green.
- [ ] `cd smart-ground-ui && npm run test && npm run lint:check` — full frontend suite green, no lint warnings.
- [ ] Manual click-through from Task 4 Step 2 confirms the end-to-end wiring in a running stack.

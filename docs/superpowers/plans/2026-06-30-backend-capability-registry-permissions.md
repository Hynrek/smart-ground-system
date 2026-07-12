# Backend: Discovery-Driven Capability Registry & Permission Model

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the backend learn SmartBox capabilities from the discovery payload (no manual `firmware_configs` seeding required), track admin-sticky device blocks separately from user blocks, and enforce the correct permission level on each device operation.

**Architecture:** `SmartBoxDiscoveryHandler` upserts `FirmwareConfig` from the discovery payload — creating a new row when the `(appVersion, boxType)` pair is unknown, never throwing `FirmwareNotResolvedException` on first boot. `Device` gains an `adminBlocked` boolean; the effective block state (`blocked || adminBlocked`) is what gets pushed to the SmartBox. Permission guards are tightened: GPIO pin assignment requires `ADMIN`; signal duration requires `MANAGE_RANGES`; firing a command and toggling block/unblock are open to all authenticated users, with `ADMIN` required to lift an admin-set block.

**Tech Stack:** Java 25, Spring Boot 4, Spring Security, JPA/Hibernate, Mockito, AssertJ

---

## File Map

| File | Change |
|---|---|
| `smart-ground-backend/src/main/java/ch/jp/shooting/model/FirmwareConfig.java` | Add `capabilitiesJson TEXT`, `configSchemaVersion` |
| `smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java` | Extend `DiscoveryPayload`; upsert `FirmwareConfig` instead of `ifPresent` |
| `smart-ground-backend/src/main/java/ch/jp/shooting/model/Device.java` | Add `adminBlocked boolean` |
| `smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxConfigPushService.java` | Effective block = `blocked \|\| adminBlocked`; expose `adminBlocked` in response |
| `smart-ground-backend/src/main/resources/static/openapi.yaml` | Add `adminBlocked` to device schema |
| `smart-ground-backend/src/main/java/ch/jp/shooting/api/DeviceController.java` | Permission guard: ADMIN for pin change, MANAGE_RANGES for duration, authenticated for fire/block |
| `smart-ground-backend/src/test/java/ch/jp/shooting/config/SmartBoxDiscoveryHandlerTest.java` | Add upsert tests |

---

### Task 1: Add `capabilitiesJson` and `configSchemaVersion` to `FirmwareConfig`

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/model/FirmwareConfig.java`

No new tests — the field is persistence-only; it is exercised by Task 2 tests.

- [ ] **Step 1: Add fields, getters, and setters to `FirmwareConfig.java`**

Add after the `boxType` field (line 27):

```java
@Column(name = "capabilities_json", columnDefinition = "TEXT")
@Nullable
private String capabilitiesJson;

@Column(name = "config_schema_version")
@Nullable
private String configSchemaVersion;
```

Add getters/setters before the closing brace:

```java
public @Nullable String getCapabilitiesJson() { return capabilitiesJson; }
public void setCapabilitiesJson(@Nullable String capabilitiesJson) { this.capabilitiesJson = capabilitiesJson; }

public @Nullable String getConfigSchemaVersion() { return configSchemaVersion; }
public void setConfigSchemaVersion(@Nullable String configSchemaVersion) { this.configSchemaVersion = configSchemaVersion; }
```

- [ ] **Step 2: Run tests to confirm nothing broke**

```bash
cd smart-ground-backend
./mvnw test -Dtest=SmartBoxDiscoveryHandlerTest -q
```

Expected: existing 2 tests pass (new columns are nullable, no migration needed pre-v1.0).

- [ ] **Step 3: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/model/FirmwareConfig.java
git commit -m "[backend] Add capabilitiesJson and configSchemaVersion to FirmwareConfig"
```

---

### Task 2: Upsert `FirmwareConfig` from discovery payload

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java`
- Modify: `smart-ground-backend/src/test/java/ch/jp/shooting/config/SmartBoxDiscoveryHandlerTest.java`

The handler currently uses `ifPresent` — if no `FirmwareConfig` row exists for `(appVersion, boxType)`, the box connects but `firmwareConfig` stays null, causing `FirmwareNotResolvedException` on the first config push. After this task the handler upserts: creates a new row if none exists, updates capability JSON and schema version on every discovery.

- [ ] **Step 1: Write the failing tests**

Add to `SmartBoxDiscoveryHandlerTest.java`:

```java
@Test
void createsNewFirmwareConfigWhenVersionUnknown() {
    var handler = new SmartBoxDiscoveryHandler(
        smartBoxRepository, firmwareConfigRepository, new ObjectMapper(), configPushService);
    when(smartBoxRepository.findByMacAddress("aabbccddeeff")).thenReturn(Optional.empty());
    when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));
    when(firmwareConfigRepository.findByVersionAndBoxType("1.0", "xiao-esp32s3"))
        .thenReturn(Optional.empty());
    when(firmwareConfigRepository.save(any(FirmwareConfig.class)))
        .thenAnswer(i -> i.getArgument(0));

    String json = """
        {"mac":"aabbccddeeff","appVersion":"1.0","boxType":"xiao-esp32s3",
         "firmwareVersion":"micropython-1.23.0","ip":"1.2.3.4",
         "configSchemaVersion":"1",
         "capabilities":{"GPIO":{"commands":["ON","OFF"]},"LED":{"commands":["ON"]}}}
        """;
    handler.handleMessage(MessageBuilder.withPayload(json.getBytes()).build());

    ArgumentCaptor<FirmwareConfig> cap = ArgumentCaptor.forClass(FirmwareConfig.class);
    verify(firmwareConfigRepository).save(cap.capture());
    assertThat(cap.getValue().getVersion()).isEqualTo("1.0");
    assertThat(cap.getValue().getBoxType()).isEqualTo("xiao-esp32s3");
    assertThat(cap.getValue().getCapabilitiesJson()).contains("GPIO");
    assertThat(cap.getValue().getConfigSchemaVersion()).isEqualTo("1");
}

@Test
void updatesExistingFirmwareConfigCapabilitiesOnRediscovery() {
    var handler = new SmartBoxDiscoveryHandler(
        smartBoxRepository, firmwareConfigRepository, new ObjectMapper(), configPushService);
    FirmwareConfig existing = new FirmwareConfig("0.6", "xiao-esp32s3");
    when(smartBoxRepository.findByMacAddress("aabbccddeeff")).thenReturn(Optional.empty());
    when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));
    when(firmwareConfigRepository.findByVersionAndBoxType("0.6", "xiao-esp32s3"))
        .thenReturn(Optional.of(existing));
    when(firmwareConfigRepository.save(any(FirmwareConfig.class)))
        .thenAnswer(i -> i.getArgument(0));

    String json = """
        {"mac":"aabbccddeeff","appVersion":"0.6","boxType":"xiao-esp32s3",
         "firmwareVersion":"micropython-1.23.0","ip":"1.2.3.4",
         "configSchemaVersion":"1",
         "capabilities":{"GPIO":{"commands":["ON","OFF"]}}}
        """;
    handler.handleMessage(MessageBuilder.withPayload(json.getBytes()).build());

    verify(firmwareConfigRepository).save(existing);
    assertThat(existing.getCapabilitiesJson()).contains("GPIO");
}

@Test
void firmwareConfigAlwaysSetOnBoxAfterDiscovery() {
    var handler = new SmartBoxDiscoveryHandler(
        smartBoxRepository, firmwareConfigRepository, new ObjectMapper(), configPushService);
    when(smartBoxRepository.findByMacAddress("aabbccddeeff")).thenReturn(Optional.empty());
    when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));
    when(firmwareConfigRepository.findByVersionAndBoxType(any(), any()))
        .thenReturn(Optional.empty());
    when(firmwareConfigRepository.save(any(FirmwareConfig.class)))
        .thenAnswer(i -> i.getArgument(0));

    String json = """
        {"mac":"aabbccddeeff","appVersion":"2.0","boxType":"xiao-esp32s3",
         "firmwareVersion":"micropython-1.24.0","ip":"1.2.3.4"}
        """;
    handler.handleMessage(MessageBuilder.withPayload(json.getBytes()).build());

    ArgumentCaptor<SmartBox> boxCap = ArgumentCaptor.forClass(SmartBox.class);
    verify(smartBoxRepository).save(boxCap.capture());
    assertThat(boxCap.getValue().getFirmwareConfig()).isNotNull();
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./mvnw test -Dtest=SmartBoxDiscoveryHandlerTest -q
```

Expected: 3 new tests FAIL. Existing 2 tests pass.

- [ ] **Step 3: Update `DiscoveryPayload` record and `upsertSmartBox` in `SmartBoxDiscoveryHandler.java`**

Replace the `DiscoveryPayload` record (lines 27–33):

```java
record DiscoveryPayload(
    String mac,
    @Nullable String ip,
    @Nullable String appVersion,
    @Nullable String firmwareVersion,
    @Nullable String boxType,
    @Nullable String configSchemaVersion,
    @Nullable com.fasterxml.jackson.databind.JsonNode capabilities
) {}
```

Replace the `upsertSmartBox` method (lines 71–104):

```java
private SmartBox upsertSmartBox(DiscoveryPayload payload) {
    SmartBox box = smartBoxRepository.findByMacAddress(payload.mac())
        .orElseGet(() -> {
            SmartBox newBox = new SmartBox();
            newBox.setMacAddress(payload.mac());
            log.info("Neue SmartBox entdeckt: {}", payload.mac());
            return newBox;
        });

    box.setStatus(SmartBoxStates.ONLINE);
    box.setLastSeen(Instant.now());

    if (payload.appVersion() != null) {
        box.setAppVersion(payload.appVersion());
    }
    if (payload.firmwareVersion() != null) {
        box.setFirmwareVersion(payload.firmwareVersion());
    }

    // Capability-Registry immer aus dem Discovery-Payload upserten.
    // Neue Versionen werden automatisch angelegt – kein manuelles Seeden erforderlich.
    String capabilityVersion = payload.appVersion() != null
        ? payload.appVersion()
        : payload.firmwareVersion();
    if (capabilityVersion != null) {
        String boxType = payload.boxType() != null ? payload.boxType() : "UNKNOWN";
        FirmwareConfig fc = firmwareConfigRepository
            .findByVersionAndBoxType(capabilityVersion, boxType)
            .orElseGet(() -> {
                log.info("Unbekannte AppVersion {} – neue FirmwareConfig wird erstellt.", capabilityVersion);
                return new FirmwareConfig(capabilityVersion, boxType);
            });

        if (payload.capabilities() != null) {
            try {
                fc.setCapabilitiesJson(objectMapper.writeValueAsString(payload.capabilities()));
            } catch (Exception e) {
                log.warn("Capabilities konnten nicht serialisiert werden: {}", e.getMessage());
            }
        }
        if (payload.configSchemaVersion() != null) {
            fc.setConfigSchemaVersion(payload.configSchemaVersion());
        }

        FirmwareConfig savedFc = firmwareConfigRepository.save(fc);
        box.setFirmwareConfig(savedFc);
    }

    return smartBoxRepository.save(box);
}
```

- [ ] **Step 4: Run all tests**

```bash
./mvnw test -Dtest=SmartBoxDiscoveryHandlerTest -q
```

Expected: all 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java
git add smart-ground-backend/src/test/java/ch/jp/shooting/config/SmartBoxDiscoveryHandlerTest.java
git commit -m "[backend] Upsert FirmwareConfig from discovery payload – no manual seeding required"
```

---

### Task 3: Add `adminBlocked` to `Device` and update effective block state

**Files:**
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/model/Device.java`
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxConfigPushService.java`

`blocked` = toggled by any authenticated user at runtime. `adminBlocked` = set/cleared only by ADMIN; survives a user unblock. The firmware receives the effective block (`blocked || adminBlocked`) in the config push. The permission enforcement for who can set/clear each field is in Task 4.

- [ ] **Step 1: Add `adminBlocked` to `Device.java`**

Add after the `blocked` field:

```java
@Column(name = "admin_blocked", nullable = false)
private boolean adminBlocked = false;
```

Add getter/setter:

```java
public boolean isAdminBlocked() { return adminBlocked; }
public void setAdminBlocked(boolean adminBlocked) { this.adminBlocked = adminBlocked; }
```

- [ ] **Step 2: Write a failing test for effective block in `SmartBoxConfigPushService`**

Create `smart-ground-backend/src/test/java/ch/jp/shooting/config/SmartBoxConfigPushServiceTest.java`:

```java
package ch.jp.shooting.config;

import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.DeviceTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxConfigPushServiceTest {

    @Mock MessageChannel mqttOutboundChannel;
    @Mock DeviceRepository deviceRepository;
    @Mock DeviceTypeRepository deviceTypeRepository;

    private SmartBox boxWithFirmware() {
        FirmwareConfig fc = new FirmwareConfig("1.0", "xiao-esp32s3");
        // ID muss gesetzt sein, damit findBySmartBoxId und findByGroupId... aufgerufen werden
        try {
            var idField = FirmwareConfig.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(fc, UUID.randomUUID());
        } catch (Exception e) { throw new RuntimeException(e); }
        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        try {
            var idField = SmartBox.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(box, UUID.randomUUID());
        } catch (Exception e) { throw new RuntimeException(e); }
        box.setFirmwareConfig(fc);
        return box;
    }

    @Test
    void effectiveBlockIsTrueWhenAdminBlockedEvenIfUserBlockedIsFalse() {
        SmartBox box = boxWithFirmware();

        Device device = new Device();
        device.setAlias("Werfer 1");
        device.setBlocked(false);
        device.setAdminBlocked(true);

        DeviceTypeGroup group = new DeviceTypeGroup();
        device.setDeviceTypeGroup(group);

        SignalType signal = new SignalType();
        signal.setCommunicationDirection(CommunicationDirection.OUTPUT);
        signal.setDevice(DeviceKind.GPIO);
        signal.setCommand("15");

        DeviceType dt = new DeviceType();
        dt.setSignalType(signal);
        dt.setSignalDurationMs(500);

        when(deviceRepository.findBySmartBoxId(any())).thenReturn(List.of(device));
        when(deviceTypeRepository.findByGroupIdAndSignalType_FirmwareConfigId(any(), any()))
            .thenReturn(Optional.of(dt));
        when(mqttOutboundChannel.send(any())).thenReturn(true);

        var service = new SmartBoxConfigPushService(
            mqttOutboundChannel, deviceRepository, deviceTypeRepository, new ObjectMapper());
        service.push(box);

        ArgumentCaptor<org.springframework.messaging.Message<?>> cap =
            ArgumentCaptor.forClass(org.springframework.messaging.Message.class);
        verify(mqttOutboundChannel).send(cap.capture());
        String json = (String) cap.getValue().getPayload();
        assertThat(json).contains("\"blocked\":true");
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./mvnw test -Dtest=SmartBoxConfigPushServiceTest -q
```

Expected: FAIL — `SmartBoxConfigPushService` uses only `device.isBlocked()`, ignoring `adminBlocked`.

- [ ] **Step 4: Update `SmartBoxConfigPushService.buildPayload` to use effective block**

In `SmartBoxConfigPushService.java`, change the `DeviceConfigEntry` construction inside `buildPayload` — the last argument to the record:

```java
entries.add(new DeviceConfigEntry(
    device.getId(),
    device.getAlias(),
    signal.getCommunicationDirection().name(),
    signal.getDevice().name(),
    signal.getCommand(),
    deviceType.getSignalDurationMs(),
    device.isBlocked() || device.isAdminBlocked()   // effektive Blockierung
));
```

- [ ] **Step 5: Run all tests**

```bash
./mvnw test -Dtest=SmartBoxConfigPushServiceTest -q
```

Expected: passes.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-backend/src/main/java/ch/jp/shooting/model/Device.java
git add smart-ground-backend/src/main/java/ch/jp/shooting/config/SmartBoxConfigPushService.java
git add smart-ground-backend/src/test/java/ch/jp/shooting/config/SmartBoxConfigPushServiceTest.java
git commit -m "[backend] Add adminBlocked to Device; effective block state in config push"
```

---

### Task 4: Permission guards on device endpoints

**Files:**
- Modify: `smart-ground-backend/src/main/resources/static/openapi.yaml` (device schemas)
- Modify: `smart-ground-backend/src/main/java/ch/jp/shooting/api/DeviceController.java`

Permission model:

| Action | Required |
|---|---|
| Create device / assign GPIO pin | `ADMIN` |
| Change GPIO pin on existing device | `ADMIN` |
| Change `signalDurationMs` | `MANAGE_RANGES` |
| Fire a command (ON / OFF) | Authenticated (any role) |
| Send BLOCK command | Authenticated (any role) |
| Send UNBLOCK command when `adminBlocked = false` | Authenticated (any role) |
| Send UNBLOCK command when `adminBlocked = true` | `ADMIN` |

- [ ] **Step 1: Add `adminBlocked` to the device response schema in `openapi.yaml`**

Find the `Device` schema definition in `openapi.yaml` and add the field alongside `blocked`:

```yaml
adminBlocked:
  type: boolean
  description: Set by ADMIN only. Cannot be cleared by regular users.
  readOnly: true
```

Regenerate sources:

```bash
./mvnw generate-sources -q
```

- [ ] **Step 2: Update device response mapping to include `adminBlocked`**

Find where the `Device` entity is mapped to the OpenAPI-generated `Device` response model (in `EntityMappers` or the controller itself). Add `adminBlocked` to the mapped response. Example (adjust to match your mapper pattern):

```java
response.setAdminBlocked(device.isAdminBlocked());
```

- [ ] **Step 3: Add `ADMIN` guard to device creation and pin-assignment update**

In `DeviceController.java`, find the method implementing `POST /api/devices` (device creation). Add at the top of the method body:

```java
authorizationService.requirePermission(Permission.MANAGE_USERS); // placeholder — replace with ADMIN check
```

The correct pattern used elsewhere in this codebase is `@PreAuthorize`. Check how other ADMIN-only endpoints are guarded (e.g., `UserController`) and apply the same annotation. Example:

```java
@PreAuthorize("hasAuthority('MANAGE_USERS')")  // replace MANAGE_USERS with the correct ADMIN authority check
```

Look at `SecurityConfig.java` and `AuthorizationService.java` to find the correct authority string for admin-only access, then apply it to:
- The device creation method
- The device patch/update method — but **only** reject if `pinConfig` or `command` (GPIO pin assignment fields) are being changed. If only `signalDurationMs` is changing, `MANAGE_RANGES` suffices.

- [ ] **Step 4: Guard `UNBLOCK` when `adminBlocked = true`**

In `DeviceController.java`, find the method implementing `POST /api/devices/{id}/command`. Locate where the BLOCK/UNBLOCK commands are handled. Add the admin guard for admin-blocked unblock:

```java
if ("UNBLOCK".equals(command)) {
    Device device = deviceRepository.findById(id)
        .orElseThrow(() -> new DeviceNotFoundException(id));
    if (device.isAdminBlocked()) {
        authorizationService.requirePermission(Permission.MANAGE_USERS); // ADMIN check
    }
}
```

After the MQTT command is sent and the ACK is received (or optimistically), update the DB:
```java
if ("BLOCK".equals(command)) {
    device.setBlocked(true);
    // If caller is ADMIN, also set adminBlocked
    if (authorizationService.hasPermission(Permission.MANAGE_USERS)) {
        device.setAdminBlocked(true);
    }
    deviceRepository.save(device);
}
if ("UNBLOCK".equals(command)) {
    device.setBlocked(false);
    if (authorizationService.hasPermission(Permission.MANAGE_USERS)) {
        device.setAdminBlocked(false);
    }
    deviceRepository.save(device);
}
```

- [ ] **Step 5: Run all tests**

```bash
./mvnw test -q
```

Expected: all tests pass. If any test breaks because it no longer has the right permission, add the appropriate security context to that test (see how existing controller tests set up `@WithMockUser`).

- [ ] **Step 6: Commit**

```bash
git add smart-ground-backend/src/main/resources/static/openapi.yaml
git add smart-ground-backend/src/main/java/ch/jp/shooting/api/DeviceController.java
git commit -m "[backend] Permission guards: ADMIN for GPIO pin, MANAGE_RANGES for duration, auth for fire/block"
```

---

### Task 5: Full test run and integration smoke test

- [ ] **Step 1: Run full backend test suite**

```bash
./mvnw clean test -q
```

Expected: all tests pass, no warnings in compilation.

- [ ] **Step 2: Start the stack and verify end-to-end discovery flow**

```bash
# Terminal 1
docker compose up
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres

# Terminal 2 — simulate a SmartBox discovery with the new payload
mosquitto_pub -t smartboxes/discovery -m '{
  "mac":"aabbccddeeff",
  "appVersion":"1.0",
  "boxType":"xiao-esp32s3",
  "firmwareVersion":"micropython-1.23.0",
  "ip":"192.168.1.42",
  "configSchemaVersion":"1",
  "capabilities":{
    "GPIO":{"directions":["INPUT","OUTPUT"],"commands":["ON","OFF"],"config_fields":{"signal_duration_ms":{"type":"int","default":0}}},
    "LED":{"directions":["OUTPUT"],"commands":["ON"],"config_fields":{"signal_duration_ms":{"type":"int","default":150}}}
  }
}'
```

Verify in the backend logs:
- `Neue SmartBox entdeckt: aabbccddeeff` (or existing box recognised)
- `Unbekannte AppVersion 1.0 – neue FirmwareConfig wird erstellt.` (first time) OR no such message (if row existed)
- `Config-Push an SmartBox aabbccddeeff` in the logs

Verify in the DB:
```sql
SELECT version, box_type, config_schema_version, LEFT(capabilities_json, 80)
FROM firmware_configs WHERE version = '1.0';
```

Expected: row exists with `capabilities_json` containing `GPIO` and `LED`.

- [ ] **Step 3: Commit**

No code changes in this task — commit only if any test fixes were needed.

---

## Self-Review

**Spec coverage:**
- ✅ Backend learns capabilities from discovery, not from manual seeding
- ✅ `FirmwareConfig` row auto-created for unknown `(appVersion, boxType)` — no `FirmwareNotResolvedException` on first boot of a new version
- ✅ `adminBlocked` tracked separately from `blocked`
- ✅ Effective block (`blocked || adminBlocked`) sent in config push to SmartBox
- ✅ ADMIN required to clear an admin-set block
- ✅ All authenticated users can fire commands and set/clear user-level blocks
- ✅ ADMIN required for GPIO pin assignment
- ✅ `MANAGE_RANGES` required for signal duration changes
- ✅ `adminBlocked` exposed in device response via `openapi.yaml`

**Placeholder scan:** Task 4 Step 3 references "replace MANAGE_USERS with the correct ADMIN authority check" — this is intentional guidance to look at the actual codebase pattern rather than guess the string. The developer must read `SecurityConfig.java` and `AuthorizationService.java` to fill in the exact authority name.

**Type consistency:** `isAdminBlocked()` / `setAdminBlocked()` used consistently in `Device.java`, `SmartBoxConfigPushService.java`, and `DeviceController.java`.

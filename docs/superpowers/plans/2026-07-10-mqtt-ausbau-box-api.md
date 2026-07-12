# MQTT-Ausbau und box-api Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove MQTT/Mosquitto entirely from `smart-ground-hub`; add a `box-api` HTTPS surface on `smart-ground-node` that lets an unprovisioned SmartBox obtain its `K_Box` and pull OTA artifacts without any broker running anywhere.

**Architecture:** `smart-ground-hub` loses its MQTT client, all `smartboxes/#` topic handlers, and its dynsec/credential machinery. Device-command dispatch (`MqttCommandPublisher`) and OTA triggering (`OtaPublishService`) are stubbed to a typed `501` — their real replacement is `node-channel` (sub-project #4), which this plan does not build. `smart-ground-node` gains its own embedded H2-backed box registry, a `KBoxGenerator`, and three new controllers under `/box-api/v1/*`: discovery/provisioning, status/heartbeat, and an OTA proxy that streams manifest/artifact bytes through from the Hub's existing (now box-facing-only) `OtaDownloadController`. The Node serves `box-api` over HTTPS with a self-signed certificate (no `hostapd`/AP needed yet — that's #9); `smart-box` firmware gets a small CA-pinned HTTPS client, replacing `mqttutils.py` wholesale.

**Tech Stack:** Java 25 / Spring Boot 4.0.5 (Hub, Node), Spring Data JPA + H2 (Node, new), MicroPython (`smart-box`), `ussl`/`urequests` (firmware HTTPS), JUnit 5 + `MockMvc`/`RestClient` test utilities, MicroPython `unittest`.

## Global Constraints

- **Command dispatch is stubbed, not rebuilt.** `DeviceController.sendDeviceCommand` and `RangePositionService.sendPositionCommand` must return `501 Not Implemented` instead of publishing MQTT. This is an accepted, temporary regression — no production release exists yet — resolved by sub-project #4 (`node-channel`), not this plan. *(User decision, confirmed before planning.)*
- **`box-api` is software-complete without a physical AP.** The Node serves `box-api` over HTTPS with a self-signed certificate on its normal STA/LAN address; `hostapd`/`dnsmasq` (sub-project #9) is infrastructure that arrives later and changes only how the box reaches the Node's IP, not the protocol. *(User decision, confirmed before planning.)*
- **Mosquitto, MQTT, and dynsec are removed completely** from `smart-ground-hub`, `smart-ground-deploy`, and `smart-box` — per the roadmap: *"Mosquitto, `SmartBoxMqttRouter`, alle MQTT-Handler, `SmartBoxConfigPushService` und `mqttutils.py` entfernen."*
- **Deliverable (roadmap, item 7):** *"Eine unprovisionierte Box holt sich `K_Box` über `box-api`, ohne dass irgendwo ein Broker läuft."*
- **`K_Box` generation:** 32 random bytes (`SecureRandom`), per ADR-003. Provisioning is idempotent — a box that calls discovery twice gets back the *same* `K_Box`, not a freshly minted one.
- **Config-push (GPIO/device wiring) and automatic OTA triggering stay out of scope.** Both require Anlagenkonfiguration to reach the Node, which is sync-fundament (#2) — not built yet. `box-api` in this plan only ever *serves reads* (discovery/provisioning, status intake, OTA artifact bytes) that the box itself initiates.
- **Do not touch the `domain` module** (`SmartBox.mqttUsername` field, `smart-ground-contracts` repo) — leaving one dead column is a smaller blast radius than a cross-repo schema edit for a field with no remaining reader or writer after this plan.
- **Node module boundary still applies:** `smart-ground-node` may depend on `contracts` only, never on Hub internals (enforced by the existing `ModuleBoundaryTest`, unaffected by this plan).
- Commit prefixes: `[backend]` for `smart-ground-hub` changes, `[node]` for `smart-ground-node`, `[firmware]` for `smart-box`, `[deploy]` for `smart-ground-deploy`, `[docs]` for the root/CLAUDE.md-only task.

---

### Task 1: Hub — stub device command dispatch

**Files:**
- Modify: `smart-ground-hub/src/main/java/ch/jp/shooting/api/DeviceController.java` (method `sendDeviceCommand`, lines ~677-719)
- Modify: `smart-ground-hub/src/main/java/ch/jp/shooting/service/RangePositionService.java` (method `sendPositionCommand`, lines ~852-882)
- Test: find existing test classes covering these two methods (`grep -rl "sendDeviceCommand\|sendPositionCommand" smart-ground-hub/src/test`) and add cases there. If none exist, create `smart-ground-hub/src/test/java/ch/jp/shooting/api/DeviceControllerCommandStubTest.java` using the project's existing `@WebMvcTest`/`MockMvc` or full `@SpringBootTest` pattern (match whatever the sibling tests in that directory use).

**Interfaces:**
- Produces: `sendDeviceCommand` and `sendPositionCommand` now throw `ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Command-Dispatch nicht verfügbar — wartet auf node-channel (#4)")` instead of calling `MqttCommandPublisher`.

- [ ] **Step 1: Write the failing test for `DeviceController.sendDeviceCommand`**

```java
@Test
void sendDeviceCommand_returns501_untilNodeChannelExists() throws Exception {
    // arrange a Device + SmartBox exactly as the existing happy-path test does
    // (reuse that test's fixture setup), then:
    mockMvc.perform(post("/api/devices/{id}/command", deviceId)
                .with(user("admin").roles("ADMIN")))
            .andExpect(status().isNotImplemented());
}
```

Adapt the fixture/arrange block to match whatever helper the existing `sendDeviceCommand` happy-path test already uses (device/range/reservation setup) — do not re-derive it from scratch if a builder or `@BeforeEach` already exists in the same test class.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -o test -Dspring.profiles.active=h2 -Dtest=<TestClassName>#sendDeviceCommand_returns501_untilNodeChannelExists`
Expected: FAIL — current code returns 200 (or fails earlier for an unrelated reason if `MqttCommandPublisher` can't be constructed in the test context; either way, not `501`).

- [ ] **Step 3: Replace the MQTT publish with a stub in `DeviceController`**

Replace this block (current lines ~705-711):
```java
        String command = device.getDeviceType().getSignalType().getCommand();
        String mac = device.getSmartBox().getMacAddress();
        String topic = "smartboxes/" + mac + "/command";

        int signalDurationMs = device.getDeviceType().getSignalDurationMs();

        mqttCommandPublisher.publishToTopic(topic, command, id.toString(), signalDurationMs);
```
with:
```java
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
            "Command-Dispatch nicht verfügbar — wartet auf node-channel (#4)");
```

Remove the now-unused `mqttCommandPublisher` field, constructor parameter, and the `import ch.jp.shooting.config.MqttCommandPublisher;` line from `DeviceController.java`. Leave `configPushService` untouched (Task 2 handles it).

- [ ] **Step 4: Write the failing test for `RangePositionService.sendPositionCommand`**

```java
@Test
void sendPositionCommand_returns501_untilNodeChannelExists() {
    // arrange a RangePosition with an assigned Device exactly as the existing
    // happy-path test for sendPositionCommand does
    assertThatThrownBy(() -> rangePositionService.sendPositionCommand(rangeId, positionId, "admin", true))
        .isInstanceOf(ResponseStatusException.class)
        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_IMPLEMENTED);
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./mvnw -o test -Dspring.profiles.active=h2 -Dtest=RangePositionServiceTest#sendPositionCommand_returns501_untilNodeChannelExists`
Expected: FAIL (current behavior returns `accepted`, no exception).

- [ ] **Step 6: Replace the MQTT publish with a stub in `RangePositionService`**

Replace (current lines ~874-879):
```java
        String command = device.getDeviceType().getSignalType().getCommand();
        String mac = device.getSmartBox().getMacAddress();
        String topic = "smartboxes/" + mac + "/command";
        int signalDurationMs = device.getDeviceType().getSignalDurationMs();

        mqttCommandPublisher.publishToTopic(topic, command, device.getId().toString(), signalDurationMs);

        return new CommandResponse().status("accepted");
```
with:
```java
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
            "Command-Dispatch nicht verfügbar — wartet auf node-channel (#4)");
```

Remove the `mqttCommandPublisher` field, constructor parameter, and `import ch.jp.shooting.config.MqttCommandPublisher;` from `RangePositionService.java`.

- [ ] **Step 7: Run both tests to verify they pass**

Run: `./mvnw -o test -Dspring.profiles.active=h2 -Dtest=<TestClassName>,RangePositionServiceTest`
Expected: PASS (both new tests green).

- [ ] **Step 8: Run the full Hub suite to confirm no other test depended on the old command behavior**

Run: `./mvnw -o test -Dspring.profiles.active=h2`
Expected: BUILD SUCCESS. If any other test asserted `status("accepted")` for these two methods, update it to expect `501` (same rationale, not a new requirement).

- [ ] **Step 9: Commit**

```bash
git add smart-ground-hub/src/main/java/ch/jp/shooting/api/DeviceController.java smart-ground-hub/src/main/java/ch/jp/shooting/service/RangePositionService.java smart-ground-hub/src/test
git commit -m "[backend] stub device command dispatch pending node-channel (#4)"
```

---

### Task 2: Hub — remove MQTT infrastructure

**Files:**
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/MqttConfig.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxMqttRouter.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxStatusHandler.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxConfigAckHandler.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxDeviceExecutedHandler.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxOtaStatusHandler.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxConfigPushService.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/MqttCommandPublisher.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/MqttDynsecClient.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxCredentialService.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/config/OtaPublishService.java`
- Delete: `smart-ground-hub/src/main/java/ch/jp/shooting/exception/MqttDynsecException.java`
- Delete any test classes under `smart-ground-hub/src/test/**` for the classes above (`grep -rl "MqttConfig\|SmartBoxMqttRouter\|SmartBoxDiscoveryHandler\|SmartBoxStatusHandler\|SmartBoxConfigAckHandler\|SmartBoxDeviceExecutedHandler\|SmartBoxOtaStatusHandler\|SmartBoxConfigPushService\|MqttCommandPublisher\|MqttDynsecClient\|SmartBoxCredentialService\|OtaPublishService\|MqttDynsecException" smart-ground-hub/src/test`)
- Modify: `smart-ground-hub/pom.xml`
- Modify: `smart-ground-hub/src/main/resources/application.properties`, `application-h2.properties`, `application-docker.properties`, `application-postgres.properties`
- Modify: `smart-ground-hub/src/main/java/ch/jp/shooting/api/SmartBoxController.java`
- Modify (if it references any deleted class): `smart-ground-hub/src/main/java/ch/jp/shooting/service/OtaService.java`

**Interfaces:**
- Consumes: Task 1's already-stubbed `DeviceController`/`RangePositionService` (no more `MqttCommandPublisher` references anywhere after this task).
- Produces: a Hub that compiles and boots with zero MQTT dependencies. `SmartBoxController`'s config-push and credential-provisioning code paths now throw the same `501` pattern as Task 1.

- [ ] **Step 1: Delete the twelve Java classes listed above**

```bash
git rm smart-ground-hub/src/main/java/ch/jp/shooting/config/MqttConfig.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxMqttRouter.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxStatusHandler.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxConfigAckHandler.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxDeviceExecutedHandler.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxOtaStatusHandler.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxConfigPushService.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/MqttCommandPublisher.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/MqttDynsecClient.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/SmartBoxCredentialService.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/config/OtaPublishService.java \
       smart-ground-hub/src/main/java/ch/jp/shooting/exception/MqttDynsecException.java
```
Also `git rm` any test files found by the grep in the Files section above.

- [ ] **Step 2: Remove Paho/Spring-Integration-MQTT from `smart-ground-hub/pom.xml`**

Remove the repository block:
```xml
    <repositories>
        <repository>
            <id>Eclipse Paho Repo</id>
            <url>https://repo.eclipse.org/content/repositories/paho-releases/</url>
        </repository>
    </repositories>
```
Remove the dependencies:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-integration</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.integration</groupId>
            <artifactId>spring-integration-mqtt</artifactId>
        </dependency>
```
```xml
        <dependency>
            <groupId>org.eclipse.paho</groupId>
            <artifactId>org.eclipse.paho.mqttv5.client</artifactId>
            <version>1.2.5</version>
        </dependency>
```
Before removing `spring-boot-starter-integration`, run `grep -rl "org.springframework.integration" smart-ground-hub/src/main` — if nothing outside the deleted MQTT classes used Spring Integration, remove it too; otherwise leave it and note why in the commit message.

- [ ] **Step 3: Remove `mqtt.*` properties from all four properties files**

`application.properties` — remove:
```properties
# MQTT configuration
mqtt.broker.url=${MQTT_BROKER_URL:ssl://mosquitto:8883}
mqtt.clientId=${MQTT_CLIENT_ID:smartrange-backend}
mqtt.health-check.offline-threshold-seconds=${MQTT_HEALTH_OFFLINE_THRESHOLD:30}
# Dynsec login for the backend's own client (see smart-ground-deploy/dynsec-init.sh) —
# never hardcode the real password here, only via env var.
mqtt.username=${MQTT_USERNAME:backend}
mqtt.password=${MQTT_PASSWORD:}
# Filesystem path to the Dev-CA's ca.crt trusted for the ssl:// broker URL above.
mqtt.tls.ca-cert-path=${MQTT_TLS_CA_CERT_PATH:}
```
`application-h2.properties` — remove:
```properties
# External Mosquitto broker config
mqtt.broker.url=tcp://localhost:1883
mqtt.clientId=smartground-local
mqtt.topic.discovery=devices/discovery
```
`application-docker.properties` — remove:
```properties
mqtt.broker.url=${MQTT_BROKER_URL:ssl://mosquitto:8883}
mqtt.clientId=smartground-docker
# Dynsec login — set MQTT_PASSWORD to the same value as MQTT_BACKEND_PASSWORD in
# smart-ground-deploy's .env (see docker-compose.yml's app service + its README).
mqtt.username=${MQTT_USERNAME:backend}
mqtt.password=${MQTT_PASSWORD:}
mqtt.tls.ca-cert-path=${MQTT_TLS_CA_CERT_PATH:}
```
`application-postgres.properties` — remove:
```properties
# Host-run backend against `docker compose up`'s broker. Task A only host-publishes
# the TLS listener (8883) — plaintext 1883 is internal-network-only — so this must
# use ssl:// too. Unlike application-docker.properties, there is no automatic way
# for a host process to read the Dev-CA's ca.crt out of the mosquitto_certs Docker
# volume: extract it once (e.g. `docker compose cp mosquitto:/mosquitto/config/certs/ca.crt ./ca.crt`)
# and point MQTT_TLS_CA_CERT_PATH at the extracted file before running this profile.
mqtt.broker.url=${MQTT_BROKER_URL:ssl://localhost:8883}
mqtt.clientId=smartground-dev
mqtt.username=${MQTT_USERNAME:backend}
mqtt.password=${MQTT_PASSWORD:}
mqtt.tls.ca-cert-path=${MQTT_TLS_CA_CERT_PATH:}
```

- [ ] **Step 4: Compile and fix every resulting error**

Run: `./mvnw -o compile`
For each compile error naming a deleted class as a dependency, apply this pattern at the call site:

```java
throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
    "MQTT entfernt (#7) — Ersatz folgt über Sync-Fundament (#2) / node-channel (#4)");
```

Specifically for `SmartBoxController.java`: remove the `configPushService` (`SmartBoxConfigPushService`) and `credentialService` (`SmartBoxCredentialService`) fields, their constructor parameters, and their imports. In the method that implements `pushSmartBoxConfig` (from `SmartBoxApi`), replace its body with the `501` throw above. If any method calls `credentialService.provisionCredentials(...)` or similar, replace that call site the same way — do not delete the method itself, since it implements the `SmartBoxApi` contract interface and the endpoint shape must stay intact.

Repeat `./mvnw -o compile` until it succeeds, fixing one error at a time with the same pattern.

- [ ] **Step 5: Run the full test suite**

Run: `./mvnw -o test -Dspring.profiles.active=h2`
Expected: BUILD SUCCESS. Fix any test that references a deleted class (delete the test) or asserted old MQTT-driven behavior on `SmartBoxController` (update the expectation to `501`, same rationale as Task 1).

- [ ] **Step 6: Commit**

```bash
git add -A smart-ground-hub
git commit -m "[backend] remove MQTT infrastructure (Mosquitto client, handlers, dynsec, config-push)"
```

---

### Task 3: Hub + smart-ground-deploy — remove Mosquitto containers and config

**Files:**
- Modify: `smart-ground-hub/docker-compose.yml`
- Delete: `smart-ground-hub/mosquitto.conf`, `smart-ground-hub/mosquitto/` (entire directory)
- Modify: `smart-ground-deploy/docker-compose.yml`
- Delete: `smart-ground-deploy/mosquitto.conf`, `smart-ground-deploy/mosquitto/` (entire directory), `smart-ground-deploy/dynsec-init.sh`

**Interfaces:**
- Consumes: nothing from earlier tasks (pure infra cleanup, independent of Task 1/2's Java changes).
- Produces: both compose stacks start `db` (+ `app`/`web` in the deploy stack) with no `mosquitto` service, no MQTT env vars, no mosquitto-related volumes.

- [ ] **Step 1: Edit `smart-ground-hub/docker-compose.yml`**

Remove the entire `mosquitto:` service block (image, ports `1883`/`9001`, volumes, healthcheck). Leave `db` and the commented-out `app` block as-is, but if the commented `app` block references `MQTT_BROKER_URL` or `mosquitto:` in its `depends_on`, strip those two lines from the comment too (dead reference, no functional effect either way but keep the file honest). The `smartground` network and `postgres_data` volume stay unchanged.

- [ ] **Step 2: Delete the Hub's Mosquitto config files**

```bash
git rm smart-ground-hub/mosquitto.conf
git rm -r smart-ground-hub/mosquitto
```

- [ ] **Step 3: Edit `smart-ground-deploy/docker-compose.yml`**

Remove the entire `mosquitto:` service block. In the `app:` service, remove:
```yaml
      MQTT_BROKER_URL: ssl://mosquitto:8883
      MQTT_USERNAME: backend
      MQTT_PASSWORD: ${MQTT_BACKEND_PASSWORD}
      MQTT_TLS_CA_CERT_PATH: /certs/ca.crt
```
and its volume mount:
```yaml
    volumes:
      # Read-only: trust the broker's Dev-CA cert for the ssl:// connection
      # above. See smart-ground-deploy README's "Cert volume/path convention".
      - mosquitto_certs:/certs:ro
```
and the `mosquitto` entry from `depends_on`:
```yaml
      mosquitto:
        condition: service_started
```
(keep the `db: condition: service_healthy` entry). Remove `mosquitto_certs` and `mosquitto_dynsec` from the top-level `volumes:` block. Leave `db` and `web` untouched.

- [ ] **Step 4: Delete the deploy stack's Mosquitto files**

```bash
git rm smart-ground-deploy/mosquitto.conf
git rm -r smart-ground-deploy/mosquitto
git rm smart-ground-deploy/dynsec-init.sh
```

- [ ] **Step 5: Verify both compose files parse**

Run: `docker compose -f smart-ground-hub/docker-compose.yml config --quiet`
Run: `docker compose -f smart-ground-deploy/docker-compose.yml config --quiet`
Expected: both exit `0` with no error (missing `.env` values for `${REGISTRY}`/`${TAG}`/etc. in the deploy file are pre-existing and fine — `config --quiet` only checks YAML/schema validity, not that every variable resolves).

- [ ] **Step 6: Commit**

```bash
git add -A smart-ground-hub/docker-compose.yml smart-ground-deploy
git commit -m "[deploy] remove Mosquitto containers and dynsec bootstrap"
```

---

### Task 4: Node — embedded box registry persistence

**Files:**
- Modify: `smart-ground-node/pom.xml`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxRecord.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxRecordRepository.java`
- Modify: `smart-ground-node/src/main/resources/application.properties`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxRecordRepositoryTest.java`

**Interfaces:**
- Produces: `BoxRecord` entity (fields: `id` (UUID PK), `macAddress` (unique), `kBox` (byte[], 32 bytes), `boxType`, `appVersion`, `firmwareVersion`, `capabilitiesJson`, `provisionedAt` (Instant), `lastSeenAt` (Instant, nullable), `lastStatus` (String, nullable)). `BoxRecordRepository extends JpaRepository<BoxRecord, UUID>` with `Optional<BoxRecord> findByMacAddress(String macAddress)`.
- Consumed by: Task 5 (`BoxProvisioningService`), Task 6 (discovery controller), Task 7 (status controller).

- [ ] **Step 1: Add JPA + H2 to `smart-ground-node/pom.xml`**

Add inside `<dependencies>`:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: Configure a file-based H2 datasource in `application.properties`**

Append to `smart-ground-node/src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:h2:file:${user.home}/.smartground-node/box-registry;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
```
File-based (not `mem:`) because the box registry is the Node's own operative truth and must survive a restart, per the spec's "Node besitzt die operative Wahrheit seines eigenen Schiessplatzes."

- [ ] **Step 3: Write the failing repository test**

```java
package ch.jp.shooting.node.box;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class BoxRecordRepositoryTest {

    @Autowired
    private BoxRecordRepository repository;

    @Test
    void findByMacAddress_returnsSavedRecord() {
        BoxRecord record = new BoxRecord();
        record.setMacAddress("AA:BB:CC:DD:EE:FF");
        record.setKBox(new byte[32]);
        record.setBoxType("thrower");
        record.setAppVersion("1.0.0");
        record.setFirmwareVersion("micropython-1.23");
        record.setCapabilitiesJson("{}");
        record.setProvisionedAt(Instant.now());
        repository.save(record);

        var found = repository.findByMacAddress("AA:BB:CC:DD:EE:FF");

        assertThat(found).isPresent();
        assertThat(found.get().getKBox()).hasSize(32);
    }

    @Test
    void findByMacAddress_unknownMac_returnsEmpty() {
        assertThat(repository.findByMacAddress("00:00:00:00:00:00")).isEmpty();
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./mvnw -o test -Dtest=BoxRecordRepositoryTest`
Expected: FAIL — compile error, `BoxRecord`/`BoxRecordRepository` don't exist yet.

- [ ] **Step 5: Create `BoxRecord`**

```java
package ch.jp.shooting.node.box;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "box_records")
public class BoxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "mac_address", nullable = false, unique = true)
    private String macAddress;

    @Column(name = "k_box", nullable = false)
    private byte[] kBox;

    @Column(name = "box_type")
    private String boxType;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "capabilities_json", length = 4000)
    private String capabilitiesJson;

    @Column(name = "provisioned_at", nullable = false)
    private Instant provisionedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_status")
    private String lastStatus;

    public UUID getId() { return id; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public byte[] getKBox() { return kBox; }
    public void setKBox(byte[] kBox) { this.kBox = kBox; }
    public String getBoxType() { return boxType; }
    public void setBoxType(String boxType) { this.boxType = boxType; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public void setCapabilitiesJson(String capabilitiesJson) { this.capabilitiesJson = capabilitiesJson; }
    public Instant getProvisionedAt() { return provisionedAt; }
    public void setProvisionedAt(Instant provisionedAt) { this.provisionedAt = provisionedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
}
```

- [ ] **Step 6: Create `BoxRecordRepository`**

```java
package ch.jp.shooting.node.box;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BoxRecordRepository extends JpaRepository<BoxRecord, UUID> {
    Optional<BoxRecord> findByMacAddress(String macAddress);
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./mvnw -o test -Dtest=BoxRecordRepositoryTest`
Expected: PASS (2/2).

- [ ] **Step 8: Commit**

```bash
git add smart-ground-node/pom.xml smart-ground-node/src/main/resources/application.properties smart-ground-node/src/main/java/ch/jp/shooting/node/box smart-ground-node/src/test/java/ch/jp/shooting/node/box
git commit -m "[node] add embedded box registry persistence"
```

---

### Task 5: Node — K_Box generation and provisioning service

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/KBoxGenerator.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxProvisioningService.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxProvisioningServiceTest.java`

**Interfaces:**
- Consumes: `BoxRecordRepository`, `BoxRecord` (Task 4).
- Produces: `BoxProvisioningService.provision(String macAddress, String appVersion, String firmwareVersion, String boxType, String capabilitiesJson) -> BoxRecord` — idempotent per `macAddress`, used by Task 6's controller.

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.box;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class BoxProvisioningServiceTest {

    @Autowired
    private BoxRecordRepository repository;

    private BoxProvisioningService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new BoxProvisioningService(repository, new KBoxGenerator());
    }

    @Test
    void provision_newMac_generatesFreshKBox() {
        BoxRecord record = service.provision("AA:BB:CC:DD:EE:01", "1.0.0", "micropython-1.23", "thrower", "{}");

        assertThat(record.getKBox()).hasSize(32);
        assertThat(repository.findByMacAddress("AA:BB:CC:DD:EE:01")).isPresent();
    }

    @Test
    void provision_sameMacTwice_returnsSameKBox() {
        BoxRecord first = service.provision("AA:BB:CC:DD:EE:02", "1.0.0", "micropython-1.23", "thrower", "{}");
        BoxRecord second = service.provision("AA:BB:CC:DD:EE:02", "1.0.1", "micropython-1.24", "thrower", "{}");

        assertThat(second.getKBox()).isEqualTo(first.getKBox());
        assertThat(second.getAppVersion()).isEqualTo("1.0.1");
        assertThat(second.getFirmwareVersion()).isEqualTo("micropython-1.24");
    }

    @Test
    void provision_differentMacs_generateDifferentKBoxes() {
        BoxRecord a = service.provision("AA:BB:CC:DD:EE:03", "1.0.0", "micropython-1.23", "thrower", "{}");
        BoxRecord b = service.provision("AA:BB:CC:DD:EE:04", "1.0.0", "micropython-1.23", "thrower", "{}");

        assertThat(a.getKBox()).isNotEqualTo(b.getKBox());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -o test -Dtest=BoxProvisioningServiceTest`
Expected: FAIL — `KBoxGenerator`/`BoxProvisioningService` don't exist.

- [ ] **Step 3: Create `KBoxGenerator`**

```java
package ch.jp.shooting.node.box;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Erzeugt zufällige 32-Byte K_Box-Schlüssel (ADR-003). */
@Component
public class KBoxGenerator {

    private final SecureRandom random = new SecureRandom();

    public byte[] generate() {
        byte[] kBox = new byte[32];
        random.nextBytes(kBox);
        return kBox;
    }
}
```

- [ ] **Step 4: Create `BoxProvisioningService`**

```java
package ch.jp.shooting.node.box;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BoxProvisioningService {

    private final BoxRecordRepository repository;
    private final KBoxGenerator kBoxGenerator;

    public BoxProvisioningService(BoxRecordRepository repository, KBoxGenerator kBoxGenerator) {
        this.repository = repository;
        this.kBoxGenerator = kBoxGenerator;
    }

    @Transactional
    public BoxRecord provision(String macAddress, String appVersion, String firmwareVersion,
                                String boxType, String capabilitiesJson) {
        BoxRecord record = repository.findByMacAddress(macAddress).orElseGet(() -> {
            BoxRecord fresh = new BoxRecord();
            fresh.setMacAddress(macAddress);
            fresh.setKBox(kBoxGenerator.generate());
            fresh.setProvisionedAt(Instant.now());
            return fresh;
        });
        record.setAppVersion(appVersion);
        record.setFirmwareVersion(firmwareVersion);
        record.setBoxType(boxType);
        record.setCapabilitiesJson(capabilitiesJson);
        return repository.save(record);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -o test -Dtest=BoxProvisioningServiceTest`
Expected: PASS (3/3).

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/box smart-ground-node/src/test/java/ch/jp/shooting/node/box
git commit -m "[node] add K_Box generation and idempotent box provisioning"
```

---

### Task 6: Node — box-api discovery/provisioning endpoint

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxDiscoveryRequest.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxDiscoveryResponse.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxDiscoveryController.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxDiscoveryControllerTest.java`

**Interfaces:**
- Consumes: `BoxProvisioningService.provision(...)` (Task 5).
- Produces: `POST /box-api/v1/discovery` — hand-specced JSON (not OpenAPI-generated: the box is a MicroPython client, not a Java consumer of `contracts`, matching the precedent set by `OtaDownloadController`'s documented contract-first exception). Request: `{macAddress, appVersion, firmwareVersion, boxType, capabilitiesJson}`. Response: `{kBoxBase64, provisioned}` where `provisioned` is `true` on first-ever contact for that MAC, `false` on a repeat call.

- [ ] **Step 1: Write the failing controller test**

```java
package ch.jp.shooting.node.box;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BoxDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void discovery_newBox_returnsProvisionedTrueWithKBox() throws Exception {
        mockMvc.perform(post("/box-api/v1/discovery")
                .contentType("application/json")
                .content("""
                    {"macAddress":"AA:BB:CC:DD:EE:10","appVersion":"1.0.0",
                     "firmwareVersion":"micropython-1.23","boxType":"thrower","capabilitiesJson":"{}"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provisioned").value(true))
            .andExpect(jsonPath("$.kBoxBase64").isNotEmpty());
    }

    @Test
    void discovery_sameBoxTwice_secondCallReportsProvisionedFalse() throws Exception {
        String body = """
            {"macAddress":"AA:BB:CC:DD:EE:11","appVersion":"1.0.0",
             "firmwareVersion":"micropython-1.23","boxType":"thrower","capabilitiesJson":"{}"}
            """;
        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body))
            .andExpect(status().isOk());

        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provisioned").value(false));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -o test -Dtest=BoxDiscoveryControllerTest`
Expected: FAIL — `404`, no such endpoint yet.

- [ ] **Step 3: Create the request/response records**

```java
package ch.jp.shooting.node.box;

public record BoxDiscoveryRequest(
        String macAddress,
        String appVersion,
        String firmwareVersion,
        String boxType,
        String capabilitiesJson) {
}
```

```java
package ch.jp.shooting.node.box;

public record BoxDiscoveryResponse(String kBoxBase64, boolean provisioned) {
}
```

- [ ] **Step 4: Create `BoxDiscoveryController`**

```java
package ch.jp.shooting.node.box;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Box-zugewandter Discovery-/Provisionierungs-Endpunkt. Hand-spezifiziert statt über
 * openapi.yaml generiert: die SmartBox ist ein MicroPython-Client, nicht ein Java-
 * Konsument von contracts — gleiche bewusste Ausnahme wie OtaDownloadController im Hub.
 */
@RestController
public class BoxDiscoveryController {

    private final BoxProvisioningService provisioningService;
    private final BoxRecordRepository repository;

    public BoxDiscoveryController(BoxProvisioningService provisioningService, BoxRecordRepository repository) {
        this.provisioningService = provisioningService;
        this.repository = repository;
    }

    @PostMapping("/box-api/v1/discovery")
    public BoxDiscoveryResponse discover(@RequestBody BoxDiscoveryRequest request) {
        boolean wasKnown = repository.findByMacAddress(request.macAddress()).isPresent();
        BoxRecord record = provisioningService.provision(
                request.macAddress(), request.appVersion(), request.firmwareVersion(),
                request.boxType(), request.capabilitiesJson());
        return new BoxDiscoveryResponse(Base64.getEncoder().encodeToString(record.getKBox()), !wasKnown);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -o test -Dtest=BoxDiscoveryControllerTest`
Expected: PASS (2/2).

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/box smart-ground-node/src/test/java/ch/jp/shooting/node/box
git commit -m "[node] add box-api discovery/provisioning endpoint"
```

---

### Task 7: Node — box-api status/heartbeat endpoint

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxStatusRequest.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxStatusController.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxStatusControllerTest.java`

**Interfaces:**
- Consumes: `BoxRecordRepository` (Task 4).
- Produces: `POST /box-api/v1/boxes/{macAddress}/status` — body `{status}`. `200` and updates `lastSeenAt`/`lastStatus` for a known MAC; `404` (`ProblemDetail`, type `/errors/box-unknown`) if the box never completed discovery.

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.box;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BoxStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoxRecordRepository repository;

    @BeforeEach
    void seedKnownBox() {
        BoxRecord record = new BoxRecord();
        record.setMacAddress("AA:BB:CC:DD:EE:20");
        record.setKBox(new byte[32]);
        record.setProvisionedAt(Instant.now());
        repository.save(record);
    }

    @Test
    void status_knownBox_returns200AndUpdatesLastSeen() throws Exception {
        mockMvc.perform(post("/box-api/v1/boxes/{mac}/status", "AA:BB:CC:DD:EE:20")
                .contentType("application/json")
                .content("""{"status":"idle"}"""))
            .andExpect(status().isOk());

        var updated = repository.findByMacAddress("AA:BB:CC:DD:EE:20").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getLastStatus()).isEqualTo("idle");
        org.assertj.core.api.Assertions.assertThat(updated.getLastSeenAt()).isNotNull();
    }

    @Test
    void status_unknownBox_returns404() throws Exception {
        mockMvc.perform(post("/box-api/v1/boxes/{mac}/status", "00:00:00:00:00:99")
                .contentType("application/json")
                .content("""{"status":"idle"}"""))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -o test -Dtest=BoxStatusControllerTest`
Expected: FAIL — endpoint doesn't exist.

- [ ] **Step 3: Create `BoxStatusRequest`**

```java
package ch.jp.shooting.node.box;

public record BoxStatusRequest(String status) {
}
```

- [ ] **Step 4: Create `BoxStatusController`**

```java
package ch.jp.shooting.node.box;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;

@RestController
public class BoxStatusController {

    private final BoxRecordRepository repository;

    public BoxStatusController(BoxRecordRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/box-api/v1/boxes/{macAddress}/status")
    public void status(@PathVariable String macAddress, @RequestBody BoxStatusRequest request) {
        BoxRecord record = repository.findByMacAddress(macAddress).orElseThrow(() -> {
            ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "Box " + macAddress + " ist nicht provisioniert.");
            detail.setType(URI.create("/errors/box-unknown"));
            return new ResponseStatusException(HttpStatus.NOT_FOUND, detail.getDetail());
        });
        record.setLastStatus(request.status());
        record.setLastSeenAt(Instant.now());
        repository.save(record);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -o test -Dtest=BoxStatusControllerTest`
Expected: PASS (2/2).

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/box smart-ground-node/src/test/java/ch/jp/shooting/node/box
git commit -m "[node] add box-api status/heartbeat endpoint"
```

---

### Task 8: Node — HTTPS with self-signed certificate

**Files:**
- Create: `smart-ground-node/src/main/resources/node-dev-keystore.p12` (generated, not hand-written — see Step 1)
- Modify: `smart-ground-node/src/main/resources/application.properties`
- Modify: `smart-ground-node/pom.xml` (only if the keystore needs the `resources` plugin's default copy behavior — normally not required)
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxApiHttpsTest.java`

**Interfaces:**
- Produces: `smart-ground-node` serves on `https://<host>:8443` with a self-signed cert (Subject `CN=smartground-node`), keystore password read from `${NODE_KEYSTORE_PASSWORD:changeit}`.

- [ ] **Step 1: Generate the dev keystore**

Run from `smart-ground-node/src/main/resources/`:
```bash
keytool -genkeypair -alias smartground-node -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=smartground-node,OU=SmartGround,O=SmartGround,L=Dev,ST=Dev,C=CH" \
  -keystore node-dev-keystore.p12 -storetype PKCS12 -storepass changeit -keypass changeit
```
Confirm the file `node-dev-keystore.p12` now exists in `smart-ground-node/src/main/resources/`.

- [ ] **Step 2: Configure HTTPS in `application.properties`**

Append:
```properties
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:node-dev-keystore.p12
server.ssl.key-store-type=PKCS12
server.ssl.key-store-password=${NODE_KEYSTORE_PASSWORD:changeit}
server.ssl.key-alias=smartground-node
```
(Port `8443`, not `8080`: the Hub already claims `8080` and, per the architecture, Hub and Node run as two processes on the same machine at N=1.)

- [ ] **Step 3: Write a smoke test confirming HTTPS is actually active**

```java
package ch.jp.shooting.node.box;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BoxApiHttpsTest {

    @LocalServerPort
    private int port;

    @Test
    void discoveryEndpoint_isReachableOverHttps() {
        TestRestTemplate restTemplate = new TestRestTemplate(TestRestTemplate.HttpClientOption.SSL);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://localhost:" + port + "/box-api/v1/discovery",
                "{\"macAddress\":\"AA:BB:CC:DD:EE:30\",\"appVersion\":\"1.0.0\","
                        + "\"firmwareVersion\":\"micropython-1.23\",\"boxType\":\"thrower\",\"capabilitiesJson\":\"{}\"}",
                String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
```
Note: `@SpringBootTest(webEnvironment = RANDOM_PORT)` overrides `server.port` for the test, so this exercises the SSL config without colliding with `8443` on a dev machine that already has the app running.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -o test -Dtest=BoxApiHttpsTest`
Expected: PASS. If it fails with a keystore-not-found error, confirm Step 1's file landed under `src/main/resources/` (not `target/`) and re-run `./mvnw -o test`.

- [ ] **Step 5: Run the full Node suite to confirm nothing else broke**

Run: `./mvnw -o test`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add -f smart-ground-node/src/main/resources/node-dev-keystore.p12 smart-ground-node/src/main/resources/application.properties smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxApiHttpsTest.java
git commit -m "[node] serve box-api over HTTPS with a self-signed dev certificate"
```
(`-f` because a `.p12` binary may otherwise be caught by a repo-wide binary-file gitignore rule — check `smart-ground-node/.gitignore` first; if it already allow-lists resources, drop `-f`.)

---

### Task 9: Node — box-api OTA proxy

**Files:**
- Modify: `smart-ground-node/src/main/java/ch/jp/shooting/node/hub/HubClient.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxOtaController.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxOtaControllerTest.java`

**Interfaces:**
- Consumes: `HubClient` (existing, Node → Hub only).
- Produces: `GET /box-api/v1/ota/app/{version}/manifest.json`, `GET /box-api/v1/ota/app/{version}/files/{*path}`, `GET /box-api/v1/ota/firmware/{version}` on the Node — same three artifact shapes as the Hub's `OtaDownloadController`, proxied byte-for-byte from `${hub.base-url}/api/ota/...` via `HubClient`.

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.box;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BoxOtaControllerTest {

    private static MockWebServer hub;

    @BeforeEach
    void startHub() throws IOException {
        hub = new MockWebServer();
        hub.start();
    }

    @AfterEach
    void stopHub() throws IOException {
        hub.shutdown();
    }

    @DynamicPropertySource
    static void hubUrl(DynamicPropertyRegistry registry) {
        registry.add("hub.base-url", () -> "http://localhost:" + hub.getPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void manifest_proxiesHubResponseBytes() throws Exception {
        hub.enqueue(new MockResponse()
                .setBody("{\"files\":[]}")
                .addHeader("Content-Type", "application/json"));

        mockMvc.perform(get("/box-api/v1/ota/app/{version}/manifest.json", "1.0.0"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"files\":[]}"));

        var recorded = hub.takeRequest();
        org.assertj.core.api.Assertions.assertThat(recorded.getPath())
            .isEqualTo("/api/ota/app/1.0.0/manifest.json");
    }
}
```
Add the `MockWebServer` test dependency to `smart-ground-node/pom.xml` if not already present:
```xml
        <dependency>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>mockwebserver</artifactId>
            <version>4.12.0</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -o test -Dtest=BoxOtaControllerTest`
Expected: FAIL — `404`, no such endpoint.

- [ ] **Step 3: Extend `HubClient` with byte-proxying methods**

Add to `smart-ground-node/src/main/java/ch/jp/shooting/node/hub/HubClient.java`:
```java
    public byte[] fetchOtaAppManifest(String version) {
        return restClient.get().uri("/api/ota/app/{version}/manifest.json", version)
                .retrieve().body(byte[].class);
    }

    public byte[] fetchOtaAppFile(String version, String path) {
        return restClient.get().uri("/api/ota/app/{version}/files{path}", version, path)
                .retrieve().body(byte[].class);
    }

    public byte[] fetchOtaFirmware(String version) {
        return restClient.get().uri("/api/ota/firmware/{version}", version)
                .retrieve().body(byte[].class);
    }
```

- [ ] **Step 4: Create `BoxOtaController`**

```java
package ch.jp.shooting.node.box;

import ch.jp.shooting.node.hub.HubClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Box-zugewandter OTA-Proxy: reicht Artefakt-Bytes unverändert vom Hub weiter, damit die
 * Box nur den Node (box-api, HTTPS) kontaktieren muss und nie eine Hub-Adresse kennt.
 * Auslösung/Trigger bleibt vorerst manuell (#7 baut nur den Lesepfad) — automatisches
 * Anstossen folgt mit node-channel (#4).
 */
@RestController
public class BoxOtaController {

    private final HubClient hubClient;

    public BoxOtaController(HubClient hubClient) {
        this.hubClient = hubClient;
    }

    @GetMapping("/box-api/v1/ota/app/{version}/manifest.json")
    public ResponseEntity<byte[]> manifest(@PathVariable String version) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(hubClient.fetchOtaAppManifest(version));
    }

    @GetMapping("/box-api/v1/ota/app/{version}/files/{*path}")
    public ResponseEntity<byte[]> appFile(@PathVariable String version, @PathVariable String path) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(hubClient.fetchOtaAppFile(version, path));
    }

    @GetMapping("/box-api/v1/ota/firmware/{version}")
    public ResponseEntity<byte[]> firmware(@PathVariable String version) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(hubClient.fetchOtaFirmware(version));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw -o test -Dtest=BoxOtaControllerTest`
Expected: PASS.

- [ ] **Step 6: Run the full Node suite**

Run: `./mvnw -o test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add smart-ground-node/pom.xml smart-ground-node/src/main/java/ch/jp/shooting/node/hub/HubClient.java smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxOtaController.java smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxOtaControllerTest.java
git commit -m "[node] add box-api OTA artifact proxy"
```

---

### Task 10: smart-box — CA-pinned HTTPS client for box-api

**Repo:** `smart-box` (separate git repo, own `main`/`master` branch — commit there directly, matching the precedent set by prior firmware tasks in this ledger).

**Files:**
- Create: `smart-box/box_api_client.py`
- Test: `smart-box/tests/test_box_api_client.py`

**Interfaces:**
- Produces: `box_api_client.post_json(path, payload_dict) -> dict`, `box_api_client.get_bytes(path) -> bytes`, both HTTPS with a pinned CA loaded from `systemconfig/node_ca.crt` (same pattern as today's `mqttutils.load_ca_cert()`, repointed from the Mosquitto Dev-CA to the Node's self-signed cert generated in Task 8). Configuration: `userconfig/client_config.json` gains a `box_api_base_url` field (e.g. `"https://192.168.4.1:8443"`), read the same way `mqttutils` reads `broker_ip` today.

- [ ] **Step 1: Write the failing test**

```python
# smart-box/tests/test_box_api_client.py
import unittest
from unittest.mock import patch, MagicMock
import box_api_client


class TestBoxApiClient(unittest.TestCase):
    def test_post_json_sends_https_request_with_pinned_ca(self):
        fake_response = MagicMock()
        fake_response.text = '{"provisioned": true, "kBoxBase64": "AAAA"}'
        fake_response.status_code = 200

        with patch('box_api_client._load_ca_cert', return_value=b'fake-ca-bytes'), \
             patch('urequests.post', return_value=fake_response) as mock_post:
            result = box_api_client.post_json(
                'https://node.local:8443/box-api/v1/discovery',
                {'macAddress': 'AA:BB:CC:DD:EE:01'})

        self.assertEqual(result['provisioned'], True)
        self.assertEqual(result['kBoxBase64'], 'AAAA')
        args, kwargs = mock_post.call_args
        self.assertEqual(args[0], 'https://node.local:8443/box-api/v1/discovery')
        self.assertIn('cadata', kwargs.get('ssl_params', {}))

    def test_get_bytes_returns_raw_content(self):
        fake_response = MagicMock()
        fake_response.content = b'\x01\x02\x03'
        fake_response.status_code = 200

        with patch('box_api_client._load_ca_cert', return_value=b'fake-ca-bytes'), \
             patch('urequests.get', return_value=fake_response):
            result = box_api_client.get_bytes('https://node.local:8443/box-api/v1/ota/firmware/1.0.0')

        self.assertEqual(result, b'\x01\x02\x03')


if __name__ == '__main__':
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `smart-box/`): `python -m unittest tests.test_box_api_client -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'box_api_client'`.

- [ ] **Step 3: Create `box_api_client.py`**

```python
"""
HTTPS-Client für box-api (Node), mit gepinntem CA-Root. Ersatz für mqttutils' MQTT-TLS-
Verbindung: gleiches Pinning-Prinzip (CA aus systemconfig/, nie zur Laufzeit geschrieben),
anderer Transport.
"""
import json
import urequests

CA_CERT_PATH = "systemconfig/node_ca.crt"

_ca_cert_data = None


def _load_ca_cert():
    global _ca_cert_data
    if _ca_cert_data is not None:
        return _ca_cert_data
    try:
        with open(CA_CERT_PATH, 'rb') as f:
            data = f.read()
            _ca_cert_data = data
            return data
    except OSError as e:
        print("Node-CA-Root nicht gefunden:", e)
        return None


def post_json(url, payload_dict):
    ca = _load_ca_cert()
    response = urequests.post(
        url,
        data=json.dumps(payload_dict),
        headers={"Content-Type": "application/json"},
        ssl_params={"cadata": ca} if ca else {})
    try:
        return json.loads(response.text)
    finally:
        response.close()


def get_bytes(url):
    ca = _load_ca_cert()
    response = urequests.get(url, ssl_params={"cadata": ca} if ca else {})
    try:
        return response.content
    finally:
        response.close()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests.test_box_api_client -v`
Expected: PASS (2/2).

- [ ] **Step 5: Run the full firmware test suite**

Run (from `smart-box/`): `python -m unittest discover -s tests -v`
Expected: all existing tests still pass (this task added a new module, touched nothing existing yet).

- [ ] **Step 6: Commit**

```bash
git add box_api_client.py tests/test_box_api_client.py
git commit -m "[firmware] add CA-pinned HTTPS client for box-api"
```

---

### Task 11: smart-box — replace discovery/provisioning and heartbeat with box-api calls

**Repo:** `smart-box`.

**Files:**
- Modify: `smart-box/main.py` (import block, boot sequence)
- Create: `smart-box/box_provisioning.py`
- Test: `smart-box/tests/test_box_provisioning.py`

**Interfaces:**
- Consumes: `box_api_client.post_json` (Task 10).
- Produces: `box_provisioning.discover_and_provision(mac, box_api_base_url) -> dict` (persists `k_box_base64` into `userconfig/client_config.json`); `box_provisioning.send_heartbeat(mac, box_api_base_url, status)`.

- [ ] **Step 1: Write the failing test**

```python
# smart-box/tests/test_box_provisioning.py
import unittest
from unittest.mock import patch
import box_provisioning


class TestBoxProvisioning(unittest.TestCase):
    def test_discover_and_provision_persists_k_box(self):
        fake_response = {"kBoxBase64": "ZmFrZS1rLWJveA==", "provisioned": True}
        written = {}

        def fake_persist(data):
            written.update(data)

        with patch('box_provisioning.box_api_client.post_json', return_value=fake_response), \
             patch('box_provisioning._read_config', return_value={}), \
             patch('box_provisioning._write_config', side_effect=fake_persist):
            result = box_provisioning.discover_and_provision(
                'AA:BB:CC:DD:EE:01', 'https://node.local:8443')

        self.assertEqual(result['kBoxBase64'], 'ZmFrZS1rLWJveA==')
        self.assertEqual(written['k_box_base64'], 'ZmFrZS1rLWJveA==')

    def test_send_heartbeat_posts_status(self):
        with patch('box_provisioning.box_api_client.post_json', return_value={}) as mock_post:
            box_provisioning.send_heartbeat('AA:BB:CC:DD:EE:01', 'https://node.local:8443', 'idle')

        args, _ = mock_post.call_args
        self.assertEqual(args[0], 'https://node.local:8443/box-api/v1/boxes/AA:BB:CC:DD:EE:01/status')
        self.assertEqual(args[1], {'status': 'idle'})


if __name__ == '__main__':
    unittest.main()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m unittest tests.test_box_provisioning -v`
Expected: FAIL — module doesn't exist.

- [ ] **Step 3: Create `box_provisioning.py`**

```python
"""
Ersetzt mqttutils.publish_discovery / publish_heartbeat / _persist_mqtt_credentials für
den box-api-Transport. Read-modify-write auf client_config.json bleibt (gleiche Begründung
wie zuvor: bestehende WiFi-Felder dürfen nicht verloren gehen).
"""
import json
import box_api_client

CLIENT_CONFIG_PATH = "userconfig/client_config.json"


def _read_config():
    try:
        with open(CLIENT_CONFIG_PATH, 'r') as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def _write_config(data):
    with open(CLIENT_CONFIG_PATH, 'w') as f:
        json.dump(data, f)


def discover_and_provision(mac_address, box_api_base_url, app_version="unknown",
                            firmware_version="unknown", box_type="unknown", capabilities_json="{}"):
    result = box_api_client.post_json(
        box_api_base_url + "/box-api/v1/discovery",
        {
            "macAddress": mac_address,
            "appVersion": app_version,
            "firmwareVersion": firmware_version,
            "boxType": box_type,
            "capabilitiesJson": capabilities_json,
        })
    data = _read_config()
    data['k_box_base64'] = result['kBoxBase64']
    _write_config(data)
    return result


def send_heartbeat(mac_address, box_api_base_url, status):
    box_api_client.post_json(
        box_api_base_url + "/box-api/v1/boxes/" + mac_address + "/status",
        {"status": status})
```

- [ ] **Step 4: Run test to verify it passes**

Run: `python -m unittest tests.test_box_provisioning -v`
Expected: PASS (2/2).

- [ ] **Step 5: Wire it into `main.py`'s boot sequence**

In `smart-box/main.py`, replace:
```python
from mqttutils import publish_discovery, publish_heartbeat, connect_mqtt, reconnect_mqtt, load_device_config, load_firmware_config, update_device_pulses
from mqttutils import _update_known_devices, set_watchdog, publish_ota_status
```
with:
```python
from box_provisioning import discover_and_provision, send_heartbeat
import box_api_client
from mqttutils import load_device_config, load_firmware_config, update_device_pulses
from mqttutils import _update_known_devices, set_watchdog
```
(`load_device_config`, `load_firmware_config`, `update_device_pulses`, `_update_known_devices`, `set_watchdog` stay in `mqttutils.py` for this task — Task 12 finishes the removal once OTA status reporting also moves over, then deletes the file. Deleting it mid-task here would leave `main.py` unable to import functions Task 12 still needs.)

Locate every call site in `main.py` that currently calls `publish_discovery(...)`, `publish_heartbeat(...)`, `connect_mqtt(...)`, `reconnect_mqtt(...)` and replace with calls to `discover_and_provision(mac, box_api_base_url, ...)` / `send_heartbeat(mac, box_api_base_url, status)`, reading `box_api_base_url` from the config loaded via `_read_config()`/existing config-loading helpers already in `main.py`. Since the exact call sites and surrounding control flow in `main.py` are not reproduced in this plan, grep for them first (`grep -n "publish_discovery\|publish_heartbeat\|connect_mqtt\|reconnect_mqtt" smart-box/main.py`) and adapt each one to the pattern above, preserving existing error handling/retry structure.

- [ ] **Step 6: Run the full firmware suite**

Run: `python -m unittest discover -s tests -v`
Expected: all tests pass. If `main.py` has no dedicated test coverage (likely, since it's the boot entrypoint), this step is a static check only — confirm the module imports without `SyntaxError` via `python -m py_compile main.py`.

- [ ] **Step 7: Commit**

```bash
git add box_provisioning.py tests/test_box_provisioning.py main.py
git commit -m "[firmware] switch discovery/provisioning and heartbeat to box-api"
```

---

### Task 12: smart-box — move OTA download to box-api, delete mqttutils.py

**Repo:** `smart-box`.

**Files:**
- Modify: `smart-box/ota.py` (HTTP download call sites)
- Modify: `smart-box/main.py` (remaining `mqttutils` imports)
- Delete: `smart-box/mqttutils.py`
- Modify/Delete: `smart-box/tests/test_mqtt_tls.py`, `smart-box/tests/test_publish.py`, `smart-box/tests/test_security.py` (delete — MQTT-specific), `smart-box/tests/test_config.py`, `smart-box/tests/test_ota_routing.py` (modify — keep non-MQTT assertions, drop `import mqttutils`)

**Interfaces:**
- Consumes: `box_api_client.get_bytes` (Task 10).
- Produces: `ota.py`'s artifact download goes through `box_api_client.get_bytes(box_api_base_url + "/box-api/v1/ota/...")` instead of plain-HTTP `urequests` directly against the Hub.

- [ ] **Step 1: Find `ota.py`'s current download call site**

Run: `grep -n "urequests\|_default_http_stream" smart-box/ota.py`
Read the surrounding function (`_default_http_stream` per the earlier inventory) to see its exact signature and how the base URL is constructed today.

- [ ] **Step 2: Write a failing test asserting the new URL shape**

Add to (or create, if `ota.py` has no existing test file) `smart-box/tests/test_ota.py`:
```python
import unittest
from unittest.mock import patch
import ota


class TestOtaBoxApiDownload(unittest.TestCase):
    def test_download_uses_box_api_path(self):
        with patch('ota.box_api_client.get_bytes', return_value=b'manifest-bytes') as mock_get:
            ota._default_http_stream('https://node.local:8443', '1.0.0', 'manifest.json')

        args, _ = mock_get.call_args
        self.assertEqual(args[0], 'https://node.local:8443/box-api/v1/ota/app/1.0.0/manifest.json')


if __name__ == '__main__':
    unittest.main()
```
Adjust the call signature in this test to match whatever `_default_http_stream`'s actual parameters turn out to be from Step 1 — the assertion on the resulting URL path is the requirement; the exact function signature is discovered, not invented.

- [ ] **Step 3: Run test to verify it fails**

Run: `python -m unittest tests.test_ota -v`
Expected: FAIL (still calls `urequests` directly against the old Hub-based URL).

- [ ] **Step 4: Update `ota.py` to route through `box_api_client`**

Add `import box_api_client` near the top of `ota.py`. In the download function found in Step 1, replace the direct `urequests.get(...)` call with `box_api_client.get_bytes(url)`, and change URL construction from the Hub's `/api/ota/...` prefix to the Node's `/box-api/v1/ota/...` prefix (manifest: `/box-api/v1/ota/app/{version}/manifest.json`; app files: `/box-api/v1/ota/app/{version}/files/{path}`; firmware: `/box-api/v1/ota/firmware/{version}`), matching `BoxOtaController`'s routes from Task 9. Keep every other part of the download/verify/stage/swap pipeline in `ota.py` untouched.

- [ ] **Step 5: Run test to verify it passes**

Run: `python -m unittest tests.test_ota -v`
Expected: PASS.

- [ ] **Step 6: Replace remaining `mqttutils` imports in `main.py` and remove OTA-status-over-MQTT**

Replace the second remaining import line:
```python
from mqttutils import _update_known_devices, set_watchdog, publish_ota_status
```
with:
```python
from mqttutils import _update_known_devices, set_watchdog
```
Find every call to `publish_ota_status(...)` in `main.py`/`ota.py` (`grep -rn "publish_ota_status" smart-box/`) and remove the call (OTA status reporting back to the Hub is deferred — it required the now-removed MQTT topic and has no box-api replacement in this plan; box-api's status endpoint from Task 7 reports generic box status, not OTA-specific progress). Leave a one-line comment at each removed call site: `# OTA-Statusmeldung entfällt mit MQTT — kein Ersatz in #7, siehe node-channel (#4)`.

Move `load_device_config`, `save_device_config`, `_update_known_devices`, `_is_device_blocked`, `_admin_block_device`, `_admin_unblock_device`, `set_watchdog` out of `mqttutils.py` into a new `smart-box/device_state.py` (these are device-blocking/config-cache functions with no MQTT dependency — they were only ever colocated with the MQTT code, not coupled to it). Update `main.py`'s import to `from device_state import load_device_config, _update_known_devices, set_watchdog`.

- [ ] **Step 7: Delete `mqttutils.py` and its dedicated test files**

```bash
git rm mqttutils.py
git rm tests/test_mqtt_tls.py tests/test_publish.py tests/test_security.py
```
Edit `tests/test_config.py` and `tests/test_ota_routing.py`: remove `import mqttutils` and any test cases that exercised MQTT-specific behavior (topic routing, dynsec, credential persistence); keep and adapt any cases that were really testing `device_state.py`'s logic (block/unblock, config load/save) by changing their import to `import device_state`.

- [ ] **Step 8: Run the full firmware suite**

Run: `python -m unittest discover -s tests -v`
Expected: BUILD SUCCESS (all remaining tests pass — MQTT-specific tests are gone, not failing).

- [ ] **Step 9: Commit**

```bash
git add ota.py main.py device_state.py tests
git commit -m "[firmware] move OTA download to box-api, remove mqttutils.py"
```

---

### Task 13: Documentation — reflect MQTT removal and box-api addition

**Files:**
- Modify: `CLAUDE.md` (root, monorepo)
- Modify: `smart-ground-hub/CLAUDE.md`
- Modify: `smart-ground-node/CLAUDE.md`
- Modify: `smart-box/CLAUDE.md`

**Interfaces:**
- Consumes: nothing (docs-only task, run last so it can describe the actual landed state).

- [ ] **Step 1: Update root `CLAUDE.md`**

In the "Architecture" section's ASCII diagram and prose (`Client App ──REST──▶ Backend ──MQTT──▶ SmartBox`), replace the MQTT line with a note that MQTT has been removed for the Hub/Node architecture (link to the spec, as the existing banner above the diagram already does) — since the diagram documents the *old* architecture and is already flagged as superseded, add one sentence noting sub-project #7 is now `erledigt` and MQTT no longer exists anywhere in the stack. In "What is Not Yet Implemented", remove any line implying MQTT is the transport (if present) — grep first: `grep -n -i mqtt CLAUDE.md`.

- [ ] **Step 2: Update `smart-ground-hub/CLAUDE.md`**

Grep for every MQTT/Mosquitto/dynsec/`SmartBoxMqttRouter`/`SmartBoxConfigPushService` reference (`grep -n -i "mqtt\|mosquitto\|dynsec" smart-ground-hub/CLAUDE.md`) and remove or rewrite each: config-push, credential provisioning, and command dispatch are gone from the Hub; note that command dispatch returns `501` pending `node-channel` (#4), and that discovery/provisioning/OTA-artifact-serving now live on `smart-ground-node`'s `box-api` (this plan). Keep `OtaController`'s admin upload/list/trigger documentation (unchanged by this plan) but update the "download" half to say artifacts are fetched by the Node, not the box directly.

- [ ] **Step 3: Update `smart-ground-node/CLAUDE.md`**

Add a "box-api" section documenting the three endpoint groups added in Tasks 6/7/9 (discovery/provisioning, status/heartbeat, OTA proxy), the embedded H2 box registry (Task 4), `K_Box` generation semantics (idempotent per MAC, Task 5), and HTTPS setup (self-signed dev keystore, Task 8, `hostapd`/AP still pending #9).

- [ ] **Step 4: Update `smart-box/CLAUDE.md`**

Remove/replace the "MQTT TLS + Credentials" and "Bootstrap credential — not yet implemented" sections (the gap they described is closed by this plan — box-api provisioning has no bootstrap problem, since discovery/provisioning is the box-api's very first call, not requiring pre-existing credentials). Add a section documenting `box_api_client.py` (Task 10), `box_provisioning.py` (Task 11), and the CA-pinning repointing from the Mosquitto Dev-CA to the Node's cert (`systemconfig/node_ca.crt`). Remove the "OTA download path — CA-pinning investigation" open item (resolved by Task 10/12) and the "Not a trivial one-liner" paragraph about `urequests` HTTPS support (now demonstrated working).

- [ ] **Step 5: Update the roadmap status table**

In `docs/superpowers/plans/2026-07-10-hub-node-roadmap.md`, change row `| 7 | MQTT-Ausbau und `box-api` | 1 | offen | — |` to:
```
| 7 | MQTT-Ausbau und `box-api` | 1 | erledigt | [2026-07-10-mqtt-ausbau-box-api.md](2026-07-10-mqtt-ausbau-box-api.md) |
```

- [ ] **Step 6: Commit (root repo)**

```bash
git add CLAUDE.md smart-ground-hub/CLAUDE.md docs/superpowers/plans/2026-07-10-hub-node-roadmap.md
git commit -m "[docs] document MQTT removal and box-api (sub-project 7)"
```
(`smart-ground-node/CLAUDE.md` and `smart-box/CLAUDE.md` are gitlinked submodule-style siblings — commit those changes in their own repos with the same message, `[node]`/`[firmware]` prefix respectively, matching the pattern already used by prior sub-projects in this ledger.)

---

## Final Verification (after all 13 tasks)

Run, in order:
1. `smart-ground-contracts`: `mvn -o clean install` — unaffected by this plan, confirms nothing upstream broke.
2. `smart-ground-hub`: `./mvnw -o test -Dspring.profiles.active=h2` — expect BUILD SUCCESS, zero MQTT references (`grep -rli mqtt smart-ground-hub/src` returns nothing outside comments explaining the removal, if any).
3. `smart-ground-node`: `./mvnw -o test` — expect BUILD SUCCESS.
4. `smart-box`: `python -m unittest discover -s tests -v` — expect all green, `mqttutils.py` gone (`test -f smart-box/mqttutils.py` exits non-zero).
5. `docker compose -f smart-ground-hub/docker-compose.yml config --quiet` and `docker compose -f smart-ground-deploy/docker-compose.yml config --quiet` — both exit `0`.
6. Manual smoke test (no hardware): start `smart-ground-node` locally (`./mvnw -o spring-boot:run`), then from a shell: `curl -k -X POST https://localhost:8443/box-api/v1/discovery -H "Content-Type: application/json" -d '{"macAddress":"AA:BB:CC:DD:EE:99","appVersion":"1.0.0","firmwareVersion":"micropython-1.23","boxType":"thrower","capabilitiesJson":"{}"}'` — expect a `200` with a `kBoxBase64` field. This is the roadmap's literal deliverable, exercised without any broker running anywhere.

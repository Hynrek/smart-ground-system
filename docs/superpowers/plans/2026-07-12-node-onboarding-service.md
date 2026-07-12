# Node Onboarding Service + node-api Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the node-side half of the SmartBox coupling flow — a pending-box registry fed by `HELLO`, a one-time provisioning-token lifecycle, a `couple` action that emits `ONBOARD_OFFER` through a `RadioSender` seam, a token-gated `box-api` discovery that mints `K_Box` and queues a device registration to an outbox, and a `node-api` surface (`GET /onboarding/pending`, `POST /onboarding/{mac}/couple`) guarded by a node-local JWT signature check.

**Architecture:** All work is in `smart-ground-node` (root repo, `[node]` commits). The `node-api` endpoints are hand-written `@RestController` classes (the `box-api` precedent), tested with `MockMvcBuilders.standaloneSetup(...)`. The onboarding service reuses Plan 1's `OnboardingCodec` (already on `main`) to build the `ONBOARD_OFFER` wire bytes. `RadioSender` is a send-only seam (logging impl now, serial impl deferred); `HELLO` ingestion is a method seam (`onHello`) with no serial receive wiring. Auth is a plain servlet filter doing JDK-only HMAC-SHA256 JWT verification against a shared `jwt.secret` — no `spring-security`, no `io.jsonwebtoken`.

**Tech Stack:** Java 25, Spring Boot 4.0.5, Spring Web MVC, Spring Data JPA + file-based H2, JUnit 5 + AssertJ, `javax.crypto` (JDK), Jackson (already on classpath).

## Global Constraints

- **Maven runs offline:** every build/test command is `mvn -o ...` from `smart-ground-node/`. No Maven Central route in this sandbox.
- **No Spring test slices:** `@AutoConfigureMockMvc` / `@DataJpaTest` are broken here (trimmed cached `spring-boot-test-autoconfigure` jar). Controller tests use `MockMvcBuilders.standaloneSetup(new Controller(...realBeans...))` under `@SpringBootTest @Transactional` with real `@Autowired` beans — mirror `smart-ground-node/.../box/BoxDiscoveryControllerTest.java`. Repository/service tests use `@SpringBootTest @Transactional` (the H2 datasource is **file-based**, not in-memory — defensively delete fixture rows in `@BeforeEach`, mirroring `BoxDiscoveryControllerTest`).
- **No new Maven dependencies.** Everything needed (spring-web, spring-boot-starter-web, data-jpa, h2, jackson-databind) is already in `smart-ground-node/pom.xml`. Do **not** add `spring-security` or `io.jsonwebtoken`.
- **Comments German for domain logic, identifiers/tests English** (node convention).
- **Frame header is fixed 16 bytes** (`FrameHeader`, `ch.jp.shooting.node.frame`); do not change it. `ONBOARD_OFFER` bytes are built only through `OnboardingCodec.buildOnboardOffer(...)` (Plan 1) — never re-implement the layout.
- **Typed rejections, never 500:** expected failures throw `ErrorResponseException(status, ProblemDetail, null)` with a `/errors/<slug>` `type` (RFC 9457; `spring.mvc.problemdetails.enabled=true` is already set). Mirror `BoxStatusController`.
- **`node-api` base path is `/node-api/v1/...`.** `box-api` stays `/box-api/v1/...`. The JWT filter guards `/node-api/*` only — `box-api` remains unauthenticated (boxes are anonymous clients).
- **Shared dev JWT secret** (matches the Hub verbatim): `bWVpbi1zZWNyZXQta2V5LWZ1ZXItc21hcnQtZ3JvdW5kLWlzdC1sYW5n` (Base64 of the raw HMAC key; the Hub signs HS256 with `Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))`).
- **Scope boundaries (from the design spec):** minimal durable outbox seam + single best-effort push (no retry/drain worker); `HELLO` ingestion seam only (no serial receive); no `node-api` provenance envelope / degradation / offline-login; fine-grained admin permission gate deferred.

---

## File Structure

**Created (main):**
- `.../node/onboarding/PendingBox.java` — record: one pending box (mac, rssi, firstSeen, lastSeen, boxNonce).
- `.../node/onboarding/PendingBoxRegistry.java` — in-RAM registry; `onHello` ingestion seam.
- `.../node/onboarding/ProvisioningTokenRecord.java` — JPA entity (token, mac, expiresAt, used).
- `.../node/onboarding/ProvisioningTokenRepository.java` — Spring Data repo.
- `.../node/onboarding/ProvisioningTokenService.java` — mint / validate-and-consume with TTL + MAC binding + single-use.
- `.../node/onboarding/Macs.java` — MAC string ("AA:BB:.." or plain hex) ↔ 6 bytes.
- `.../node/onboarding/NodeCertFingerprint.java` — SHA-256 of the node's TLS cert.
- `.../node/onboarding/RadioSender.java` — send-only seam interface.
- `.../node/onboarding/LoggingRadioSender.java` — default logging impl.
- `.../node/onboarding/OnboardingService.java` — `couple()` orchestration.
- `.../node/onboarding/CoupleResult.java` — record returned by `couple()`.
- `.../node/onboarding/OnboardingController.java` — `node-api` endpoints.
- `.../node/onboarding/PendingBoxResponse.java` — `node-api` DTO for a pending row.
- `.../node/onboarding/outbox/RegistrationOutboxRecord.java` — JPA entity (queued device registration).
- `.../node/onboarding/outbox/RegistrationOutboxRepository.java` — Spring Data repo.
- `.../node/onboarding/outbox/HubRegistrationClient.java` — push seam interface.
- `.../node/onboarding/outbox/LoggingHubRegistrationClient.java` — default impl (logs, reports not-sent; real Hub endpoint = #2).
- `.../node/onboarding/outbox/RegistrationOutboxService.java` — enqueue + one best-effort push.
- `.../node/security/NodeJwtVerifier.java` — JDK-only HMAC-SHA256 verify + exp check.
- `.../node/security/NodeApiAuthFilter.java` — `OncePerRequestFilter` for `/node-api/*`.
- `.../node/security/NodeApiSecurityConfig.java` — registers the filter for `/node-api/*`.

**Modified (main):**
- `.../node/box/BoxDiscoveryRequest.java` — add `token`.
- `.../node/box/BoxDiscoveryController.java` — validate token, enqueue outbox.
- `smart-ground-node/src/main/resources/application.properties` — `jwt.secret`, `onboarding.*` config.
- `smart-ground-node/CLAUDE.md` — document `node-api` + onboarding.

**Created (test):** one test class per created/modified production class listed in each task.

**Package base:** `ch.jp.shooting.node`. Path base: `smart-ground-node/src/main/java/ch/jp/shooting/node/`, tests under `.../src/test/java/ch/jp/shooting/node/`.

---

### Task 1: Pending-box registry + `onHello` ingestion seam

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/PendingBox.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/PendingBoxRegistry.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/PendingBoxRegistryTest.java`

**Interfaces:**
- Produces: `PendingBox(String mac, int rssi, Instant firstSeen, Instant lastSeen, byte[] boxNonce)`; `PendingBoxRegistry.onHello(String mac, int rssi, byte[] boxNonce)`, `Collection<PendingBox> list()`, `Optional<PendingBox> find(String mac)`, `void remove(String mac)`. A repeated `onHello` for the same MAC keeps the original `firstSeen`, refreshes `lastSeen`/`rssi`/`boxNonce`.

- [ ] **Step 1: Write the failing test**

Create `PendingBoxRegistryTest.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PendingBoxRegistryTest {

    private final PendingBoxRegistry registry = new PendingBoxRegistry();

    @Test
    void onHello_registersBox_listAndFindReturnIt() {
        registry.onHello("AA:BB:CC:DD:EE:01", -40, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        assertThat(registry.list()).hasSize(1);
        PendingBox box = registry.find("AA:BB:CC:DD:EE:01").orElseThrow();
        assertThat(box.mac()).isEqualTo("AA:BB:CC:DD:EE:01");
        assertThat(box.rssi()).isEqualTo(-40);
        assertThat(box.boxNonce()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(box.firstSeen()).isNotNull();
        assertThat(box.lastSeen()).isEqualTo(box.firstSeen());
    }

    @Test
    void onHello_sameMacTwice_keepsFirstSeenRefreshesLastSeen() throws InterruptedException {
        registry.onHello("AA:BB:CC:DD:EE:02", -50, new byte[8]);
        PendingBox first = registry.find("AA:BB:CC:DD:EE:02").orElseThrow();
        Thread.sleep(5);
        registry.onHello("AA:BB:CC:DD:EE:02", -30, new byte[]{9, 9, 9, 9, 9, 9, 9, 9});

        assertThat(registry.list()).hasSize(1);
        PendingBox second = registry.find("AA:BB:CC:DD:EE:02").orElseThrow();
        assertThat(second.firstSeen()).isEqualTo(first.firstSeen());
        assertThat(second.lastSeen()).isAfterOrEqualTo(first.lastSeen());
        assertThat(second.rssi()).isEqualTo(-30);
        assertThat(second.boxNonce()).containsExactly(9, 9, 9, 9, 9, 9, 9, 9);
    }

    @Test
    void remove_dropsBox() {
        registry.onHello("AA:BB:CC:DD:EE:03", -60, new byte[8]);
        registry.remove("AA:BB:CC:DD:EE:03");
        assertThat(registry.find("AA:BB:CC:DD:EE:03")).isEmpty();
        assertThat(registry.list()).isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=PendingBoxRegistryTest`
Expected: compile failure — `cannot find symbol: class PendingBox` / `PendingBoxRegistry`.

- [ ] **Step 3: Create the `PendingBox` record**

Create `PendingBox.java`:

```java
package ch.jp.shooting.node.onboarding;

import java.time.Instant;

/** Ein noch nicht gekoppeltes Gerät, das per HELLO-Broadcast aufgetaucht ist. */
public record PendingBox(String mac, int rssi, Instant firstSeen, Instant lastSeen, byte[] boxNonce) {
}
```

- [ ] **Step 4: Create the registry**

Create `PendingBoxRegistry.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-RAM-Register der pending Boxen, gefüllt über die Ingest-Seam {@link #onHello}.
 * Bewusst flüchtig: eine Box, die aufhört zu senden, veraltet; nach Node-Neustart
 * ist die Liste leer, bis wieder HELLO eintrifft. Keine Serial-Anbindung in Plan 2 —
 * {@code onHello} wird von Tests (und später der Radio-Receive-Schleife) aufgerufen.
 */
@Component
public class PendingBoxRegistry {

    private final ConcurrentHashMap<String, PendingBox> byMac = new ConcurrentHashMap<>();

    public void onHello(String mac, int rssi, byte[] boxNonce) {
        Instant now = Instant.now();
        byte[] nonceCopy = boxNonce.clone();
        byMac.compute(mac, (key, existing) -> {
            Instant firstSeen = existing == null ? now : existing.firstSeen();
            return new PendingBox(mac, rssi, firstSeen, now, nonceCopy);
        });
    }

    public Collection<PendingBox> list() {
        return List.copyOf(byMac.values());
    }

    public Optional<PendingBox> find(String mac) {
        return Optional.ofNullable(byMac.get(mac));
    }

    public void remove(String mac) {
        byMac.remove(mac);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=PendingBoxRegistryTest`
Expected: PASS — 3 tests green.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/PendingBox.java \
        smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/PendingBoxRegistry.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/PendingBoxRegistryTest.java
git commit -m "[node] add pending-box registry with onHello ingestion seam"
```

---

### Task 2: Provisioning-token lifecycle (entity, repo, service)

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/ProvisioningTokenRecord.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/ProvisioningTokenRepository.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/ProvisioningTokenService.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/ProvisioningTokenServiceTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks.
- Produces:
  - `ProvisioningTokenService.MintedToken(String hex, byte[] raw, java.time.Instant expiresAt)` — `raw` is 16 bytes, `hex` is its lowercase hex.
  - `MintedToken ProvisioningTokenService.mint(String mac)` — 16 `SecureRandom` bytes, TTL from `onboarding.token-ttl-seconds`, `used=false`, persisted.
  - `void ProvisioningTokenService.validateAndConsume(String tokenHex, String mac)` — throws `ErrorResponseException` (400, `/errors/invalid-provisioning-token`) if the token is unknown / already used / expired / bound to a different MAC; otherwise marks it `used` and saves.

- [ ] **Step 1: Write the failing test**

Create `ProvisioningTokenServiceTest.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProvisioningTokenServiceTest {

    @Autowired
    private ProvisioningTokenService service;

    @Test
    void mint_thenValidateAndConsume_succeedsOnce() {
        ProvisioningTokenService.MintedToken token = service.mint("AA:BB:CC:DD:EE:20");
        assertThat(token.raw()).hasSize(16);
        assertThat(token.hex()).hasSize(32);
        assertThat(token.expiresAt()).isAfter(java.time.Instant.now());

        // first consume succeeds
        service.validateAndConsume(token.hex(), "AA:BB:CC:DD:EE:20");

        // second consume fails: single-use
        assertThatThrownBy(() -> service.validateAndConsume(token.hex(), "AA:BB:CC:DD:EE:20"))
                .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void validateAndConsume_wrongMac_isRejected() {
        ProvisioningTokenService.MintedToken token = service.mint("AA:BB:CC:DD:EE:21");
        assertThatThrownBy(() -> service.validateAndConsume(token.hex(), "AA:BB:CC:DD:EE:99"))
                .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void validateAndConsume_unknownToken_isRejected() {
        assertThatThrownBy(() -> service.validateAndConsume("deadbeef", "AA:BB:CC:DD:EE:22"))
                .isInstanceOf(ErrorResponseException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=ProvisioningTokenServiceTest`
Expected: compile failure — `cannot find symbol: class ProvisioningTokenService`.

- [ ] **Step 3: Create the entity**

Create `ProvisioningTokenRecord.java`:

```java
package ch.jp.shooting.node.onboarding;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Einmaliges Provisioning-Token (TTL, an eine MAC gebunden), node-seitig persistiert. */
@Entity
@Table(name = "provisioning_tokens")
public class ProvisioningTokenRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Column(name = "mac_address", nullable = false)
    private String macAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used;

    public UUID getId() { return id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
}
```

- [ ] **Step 4: Create the repository**

Create `ProvisioningTokenRepository.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProvisioningTokenRepository extends JpaRepository<ProvisioningTokenRecord, UUID> {
    Optional<ProvisioningTokenRecord> findByToken(String token);
}
```

- [ ] **Step 5: Create the service**

Create `ProvisioningTokenService.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Vergibt und prüft einmalige Provisioning-Token. Das Token wandert im Klartext-
 * ONBOARD_OFFER zur Box und kommt bei der box-api-Discovery zurück; hier wird es
 * gegen unbenutzt / nicht-abgelaufen / MAC-gebunden geprüft und dabei verbraucht.
 */
@Service
public class ProvisioningTokenService {

    /** hex = Kleinbuchstaben-Hex von raw (16 Byte); raw wandert roh in den ONBOARD_OFFER-Frame. */
    public record MintedToken(String hex, byte[] raw, Instant expiresAt) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProvisioningTokenRepository repository;
    private final Duration ttl;

    public ProvisioningTokenService(ProvisioningTokenRepository repository,
                                    @Value("${onboarding.token-ttl-seconds:300}") long ttlSeconds) {
        this.repository = repository;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Transactional
    public MintedToken mint(String mac) {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        String hex = toHex(raw);
        Instant expiresAt = Instant.now().plus(ttl);

        ProvisioningTokenRecord record = new ProvisioningTokenRecord();
        record.setToken(hex);
        record.setMacAddress(mac);
        record.setExpiresAt(expiresAt);
        record.setUsed(false);
        repository.save(record);

        return new MintedToken(hex, raw, expiresAt);
    }

    @Transactional
    public void validateAndConsume(String tokenHex, String mac) {
        ProvisioningTokenRecord record = repository.findByToken(tokenHex).orElseThrow(() -> reject("unbekannt"));
        if (record.isUsed()) {
            throw reject("bereits benutzt");
        }
        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw reject("abgelaufen");
        }
        if (!record.getMacAddress().equals(mac)) {
            throw reject("MAC passt nicht");
        }
        record.setUsed(true);
        repository.save(record);
    }

    private static ErrorResponseException reject(String reason) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Provisioning-Token ungültig: " + reason + ".");
        detail.setType(URI.create("/errors/invalid-provisioning-token"));
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, detail, null);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=ProvisioningTokenServiceTest`
Expected: PASS — 3 tests green.

- [ ] **Step 7: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/ProvisioningToken*.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/ProvisioningTokenServiceTest.java
git commit -m "[node] add one-time provisioning-token lifecycle"
```

---

### Task 3: MAC parsing helper

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/Macs.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/MacsTest.java`

**Interfaces:**
- Produces: `byte[] Macs.parse(String mac)` → 6 bytes, accepting colon-form (`AA:BB:CC:DD:EE:01`) or plain 12-hex (`aabbccddee01`), case-insensitive; throws `IllegalArgumentException` if the result is not 6 bytes.

- [ ] **Step 1: Write the failing test**

Create `MacsTest.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MacsTest {

    @Test
    void parse_colonForm() {
        assertThat(Macs.parse("AA:BB:CC:DD:EE:01"))
                .containsExactly(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0x01);
    }

    @Test
    void parse_plainHex_caseInsensitive() {
        assertThat(Macs.parse("aabbccddee01"))
                .containsExactly(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0x01);
    }

    @Test
    void parse_wrongLength_throws() {
        assertThatThrownBy(() -> Macs.parse("AA:BB:CC")).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=MacsTest`
Expected: compile failure — `cannot find symbol: class Macs`.

- [ ] **Step 3: Create the helper**

Create `Macs.java`:

```java
package ch.jp.shooting.node.onboarding;

/** Wandelt MAC-Strings (Doppelpunkt- oder reine Hex-Form) in 6 rohe Bytes für den Frame-Header. */
public final class Macs {

    private Macs() {
    }

    public static byte[] parse(String mac) {
        String hex = mac.replace(":", "").replace("-", "");
        if (hex.length() != 12) {
            throw new IllegalArgumentException("MAC muss 6 Byte sein: " + mac);
        }
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=MacsTest`
Expected: PASS — 3 tests green.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/Macs.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/MacsTest.java
git commit -m "[node] add MAC string-to-bytes helper"
```

---

### Task 4: Node cert fingerprint (SHA-256 of the TLS cert)

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/NodeCertFingerprint.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/NodeCertFingerprintTest.java`

**Interfaces:**
- Produces: `byte[] NodeCertFingerprint.sha256()` → 32 bytes, the SHA-256 of the node's server certificate (`server.ssl.key-store` / `key-alias`). Constructed as a Spring `@Component` reading the keystore once at startup.

**Notes:** the dev keystore is already on the classpath at `classpath:node-dev-keystore.p12`, alias `smartground-node`, password default `changeit` (see `application.properties` / node CLAUDE.md).

- [ ] **Step 1: Write the failing test**

Create `NodeCertFingerprintTest.java` — it independently recomputes the digest from the same keystore and asserts equality + 32-byte length:

```java
package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NodeCertFingerprintTest {

    @Autowired
    private NodeCertFingerprint fingerprint;

    @Test
    void sha256_is32Bytes_andMatchesIndependentComputation() throws Exception {
        byte[] actual = fingerprint.sha256();
        assertThat(actual).hasSize(32);

        ResourceLoader loader = new DefaultResourceLoader();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = loader.getResource("classpath:node-dev-keystore.p12").getInputStream()) {
            ks.load(in, "changeit".toCharArray());
        }
        Certificate cert = ks.getCertificate("smartground-node");
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void sha256_isStableAcrossCalls() {
        assertThat(fingerprint.sha256()).isEqualTo(fingerprint.sha256());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=NodeCertFingerprintTest`
Expected: compile failure — `cannot find symbol: class NodeCertFingerprint`.

- [ ] **Step 3: Create the component**

Create `NodeCertFingerprint.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;

/**
 * SHA-256-Fingerprint des Node-Server-Zertifikats. Wandert im ONBOARD_OFFER zur Box, die
 * genau diesen Fingerprint für die Provisioning-TLS-Sitzung pinnt (Vertrauen über den
 * ohnehin vertrauten ESP-NOW-Kanal gebootstrappt — keine PKI). Einmal beim Start berechnet.
 */
@Component
public class NodeCertFingerprint {

    private final byte[] fingerprint;

    public NodeCertFingerprint(
            ResourceLoader resourceLoader,
            @Value("${server.ssl.key-store}") String keyStoreLocation,
            @Value("${server.ssl.key-store-password}") String password,
            @Value("${server.ssl.key-store-type:PKCS12}") String type,
            @Value("${server.ssl.key-alias}") String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            try (InputStream in = resourceLoader.getResource(keyStoreLocation).getInputStream()) {
                keyStore.load(in, password.toCharArray());
            }
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) {
                throw new IllegalStateException("Kein Zertifikat für Alias " + alias + " im Keystore.");
            }
            this.fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Zert-Fingerprint konnte nicht berechnet werden", e);
        }
    }

    public byte[] sha256() {
        return fingerprint.clone();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=NodeCertFingerprintTest`
Expected: PASS — 2 tests green.

- [ ] **Step 5: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/NodeCertFingerprint.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/NodeCertFingerprintTest.java
git commit -m "[node] add node TLS cert SHA-256 fingerprint for ONBOARD_OFFER"
```

---

### Task 5: RadioSender seam + `OnboardingService.couple()`

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/RadioSender.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/LoggingRadioSender.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/CoupleResult.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/OnboardingService.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/OnboardingServiceTest.java`

**Interfaces:**
- Consumes: `PendingBoxRegistry` (Task 1), `ProvisioningTokenService` + `MintedToken` (Task 2), `Macs.parse` (Task 3), `NodeCertFingerprint.sha256()` (Task 4), `OnboardingCodec.buildOnboardOffer(FrameHeader, byte[] echoNonce, byte[] token, byte[] fingerprint, byte[] ssid, byte[] psk, byte[] url)` + accessors `echoNonceOf/tokenOf/fingerprintOf/ssidOf/pskOf/urlOf` (Plan 1, on `main`), `FrameHeader`, `FrameType.ONBOARD_OFFER`.
- Produces:
  - `interface RadioSender { void send(byte[] destMac, byte[] frame); }`
  - `CoupleResult(String mac, String status, java.time.Instant tokenExpiresAt)`
  - `CoupleResult OnboardingService.couple(String mac)` — looks up the pending box (throws `ErrorResponseException` 404 `/errors/box-not-pending` if absent), mints a token, builds and sends `ONBOARD_OFFER`, returns `status="offered"`.

- [ ] **Step 1: Write the failing test**

Create `OnboardingServiceTest.java` — uses a capturing fake `RadioSender` and decodes the emitted frame with the Plan 1 codec:

```java
package ch.jp.shooting.node.onboarding;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class OnboardingServiceTest {

    @Autowired
    private PendingBoxRegistry registry;
    @Autowired
    private ProvisioningTokenService tokenService;
    @Autowired
    private NodeCertFingerprint certFingerprint;

    @Test
    void couple_emitsOnboardOfferWithTokenFingerprintAndEchoNonce() {
        byte[] boxNonce = {10, 20, 30, 40, 50, 60, 70, 80};
        registry.onHello("AA:BB:CC:DD:EE:30", -42, boxNonce);

        AtomicReference<byte[]> sentDest = new AtomicReference<>();
        AtomicReference<byte[]> sentFrame = new AtomicReference<>();
        RadioSender capturing = (dest, frame) -> { sentDest.set(dest); sentFrame.set(frame); };

        OnboardingService service = new OnboardingService(registry, tokenService, certFingerprint, capturing,
                "30:ae:a4:1f:2b:3c", "SmartGround-Node-1", "provision-pw-123", "https://192.168.4.1:8443");

        CoupleResult result = service.couple("AA:BB:CC:DD:EE:30");

        assertThat(result.mac()).isEqualTo("AA:BB:CC:DD:EE:30");
        assertThat(result.status()).isEqualTo("offered");
        assertThat(sentDest.get()).containsExactly(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0x30);

        byte[] frame = sentFrame.get();
        FrameHeader header = FrameHeader.decode(frame);
        assertThat(header.type()).isEqualTo(FrameType.ONBOARD_OFFER);
        assertThat(OnboardingCodec.echoNonceOf(frame)).containsExactly(10, 20, 30, 40, 50, 60, 70, 80);
        assertThat(OnboardingCodec.fingerprintOf(frame)).isEqualTo(certFingerprint.sha256());
        assertThat(OnboardingCodec.tokenOf(frame)).hasSize(16);
        assertThat(new String(OnboardingCodec.ssidOf(frame), StandardCharsets.UTF_8)).isEqualTo("SmartGround-Node-1");
        assertThat(new String(OnboardingCodec.pskOf(frame), StandardCharsets.UTF_8)).isEqualTo("provision-pw-123");
        assertThat(new String(OnboardingCodec.urlOf(frame), StandardCharsets.UTF_8)).isEqualTo("https://192.168.4.1:8443");
    }

    @Test
    void couple_unknownMac_throwsTypedRejection() {
        OnboardingService service = new OnboardingService(registry, tokenService, certFingerprint,
                (dest, frame) -> { }, "30:ae:a4:1f:2b:3c", "S", "P", "https://x");

        assertThatThrownBy(() -> service.couple("AA:BB:CC:DD:EE:FF"))
                .isInstanceOf(ErrorResponseException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=OnboardingServiceTest`
Expected: compile failure — `cannot find symbol: class OnboardingService` / `RadioSender` / `CoupleResult`.

- [ ] **Step 3: Create the seam + result record**

Create `RadioSender.java`:

```java
package ch.jp.shooting.node.onboarding;

/**
 * Sende-Seam für Onboarding-Frames über den Funk. In Plan 2 nur Interface + Logging-Impl;
 * die echte Serial/UART-Anbindung folgt in einem späteren Plan (Baustein D liefert den UART-Codec).
 */
public interface RadioSender {
    void send(byte[] destMac, byte[] frame);
}
```

Create `LoggingRadioSender.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HexFormat;

/** Default-Impl der {@link RadioSender}-Seam: protokolliert den Frame, bis die Serial-Anbindung existiert. */
@Component
public class LoggingRadioSender implements RadioSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingRadioSender.class);

    @Override
    public void send(byte[] destMac, byte[] frame) {
        log.info("ONBOARD_OFFER an {} ({} B): {}",
                HexFormat.ofDelimiter(":").formatHex(destMac), frame.length, HexFormat.of().formatHex(frame));
    }
}
```

Create `CoupleResult.java`:

```java
package ch.jp.shooting.node.onboarding;

import java.time.Instant;

/** Ergebnis eines couple()-Aufrufs für die node-api-Antwort. */
public record CoupleResult(String mac, String status, Instant tokenExpiresAt) {
}
```

- [ ] **Step 4: Create the service**

Create `OnboardingService.java`:

```java
package ch.jp.shooting.node.onboarding;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Orchestriert die Kopplung: schlägt die pending Box nach, mintet ein Einmal-Token,
 * baut den ONBOARD_OFFER (Plan-1-Codec) und emittiert ihn über die RadioSender-Seam.
 */
@Service
public class OnboardingService {

    private final PendingBoxRegistry registry;
    private final ProvisioningTokenService tokenService;
    private final NodeCertFingerprint certFingerprint;
    private final RadioSender radioSender;
    private final byte[] nodeMac;
    private final byte[] apSsid;
    private final byte[] apPsk;
    private final byte[] boxApiUrl;

    public OnboardingService(PendingBoxRegistry registry, ProvisioningTokenService tokenService,
                             NodeCertFingerprint certFingerprint, RadioSender radioSender,
                             @Value("${onboarding.node-mac}") String nodeMacHex,
                             @Value("${onboarding.ap-ssid}") String apSsid,
                             @Value("${onboarding.ap-psk}") String apPsk,
                             @Value("${onboarding.box-api-url}") String boxApiUrl) {
        this.registry = registry;
        this.tokenService = tokenService;
        this.certFingerprint = certFingerprint;
        this.radioSender = radioSender;
        this.nodeMac = Macs.parse(nodeMacHex);
        this.apSsid = apSsid.getBytes(StandardCharsets.UTF_8);
        this.apPsk = apPsk.getBytes(StandardCharsets.UTF_8);
        this.boxApiUrl = boxApiUrl.getBytes(StandardCharsets.UTF_8);
    }

    public CoupleResult couple(String mac) {
        PendingBox box = registry.find(mac).orElseThrow(() -> {
            ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND, "Gerät " + mac + " ist nicht mehr erreichbar.");
            detail.setType(URI.create("/errors/box-not-pending"));
            return new ErrorResponseException(HttpStatus.NOT_FOUND, detail, null);
        });

        ProvisioningTokenService.MintedToken token = tokenService.mint(mac);

        FrameHeader header = new FrameHeader(Macs.parse(mac), nodeMac, 1, 1, FrameType.ONBOARD_OFFER);
        byte[] frame = OnboardingCodec.buildOnboardOffer(header, box.boxNonce(), token.raw(),
                certFingerprint.sha256(), apSsid, apPsk, boxApiUrl);

        radioSender.send(Macs.parse(mac), frame);

        return new CoupleResult(mac, "offered", token.expiresAt());
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=OnboardingServiceTest`
Expected: PASS — 2 tests green.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/RadioSender.java \
        smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/LoggingRadioSender.java \
        smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/CoupleResult.java \
        smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/OnboardingService.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/OnboardingServiceTest.java
git commit -m "[node] add OnboardingService.couple emitting ONBOARD_OFFER via RadioSender seam"
```

---

### Task 6: Registration outbox (entity, repo, push seam, service)

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/outbox/RegistrationOutboxRecord.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/outbox/RegistrationOutboxRepository.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/outbox/HubRegistrationClient.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/outbox/LoggingHubRegistrationClient.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/outbox/RegistrationOutboxService.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/outbox/RegistrationOutboxServiceTest.java`

**Interfaces:**
- Consumes: `ch.jp.shooting.node.box.BoxRecord` (getters `getId`, `getMacAddress`, `getBoxType`, `getAppVersion`, `getFirmwareVersion`, `getCapabilitiesJson`).
- Produces:
  - `RegistrationOutboxRecord` entity, statuses `"PENDING"` / `"SENT"`. Note: **does not carry `K_Box`** — the Hub only needs identity (box UUID + MAC + descriptors); `K_Box` stays node-local per the design's security model.
  - `interface HubRegistrationClient { boolean register(RegistrationOutboxRecord row); }` — returns `true` if the Hub accepted it.
  - `RegistrationOutboxRecord RegistrationOutboxService.enqueueAndAttempt(BoxRecord box)` — persists a `PENDING` row, makes **one** best-effort `register` call; on `true` marks `SENT`, on `false`/exception leaves it `PENDING` (increment `attempts`, record `lastError`). Returns the saved row.

- [ ] **Step 1: Write the failing test**

Create `RegistrationOutboxServiceTest.java`:

```java
package ch.jp.shooting.node.onboarding.outbox;

import ch.jp.shooting.node.box.BoxProvisioningService;
import ch.jp.shooting.node.box.BoxRecord;
import ch.jp.shooting.node.box.BoxRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RegistrationOutboxServiceTest {

    @Autowired
    private RegistrationOutboxRepository repository;
    @Autowired
    private BoxProvisioningService provisioningService;
    @Autowired
    private BoxRecordRepository boxRepository;

    private BoxRecord box;

    @BeforeEach
    void setUp() {
        boxRepository.findByMacAddress("AA:BB:CC:DD:EE:40").ifPresent(boxRepository::delete);
        box = provisioningService.provision("AA:BB:CC:DD:EE:40", "1.0.0", "fw-1", "thrower", "{}");
    }

    @Test
    void enqueueAndAttempt_pushSucceeds_marksSent() {
        RegistrationOutboxService service = new RegistrationOutboxService(repository, row -> true);
        RegistrationOutboxRecord saved = service.enqueueAndAttempt(box);

        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getMacAddress()).isEqualTo("AA:BB:CC:DD:EE:40");
        assertThat(saved.getBoxId()).isEqualTo(box.getId());
    }

    @Test
    void enqueueAndAttempt_pushFails_staysPending() {
        RegistrationOutboxService service = new RegistrationOutboxService(repository, row -> false);
        RegistrationOutboxRecord saved = service.enqueueAndAttempt(box);

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttempts()).isEqualTo(1);
    }

    @Test
    void enqueueAndAttempt_pushThrows_staysPendingWithError() {
        RegistrationOutboxService service = new RegistrationOutboxService(repository, row -> {
            throw new RuntimeException("hub down");
        });
        RegistrationOutboxRecord saved = service.enqueueAndAttempt(box);

        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getLastError()).contains("hub down");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=RegistrationOutboxServiceTest`
Expected: compile failure — `cannot find symbol: class RegistrationOutboxService`.

- [ ] **Step 3: Create the entity**

Create `RegistrationOutboxRecord.java`:

```java
package ch.jp.shooting.node.onboarding.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Ausgehende Geräte-Registrierung, die zum Hub steigen soll. Trägt bewusst KEIN K_Box —
 * der Hub braucht nur Identität (UUID + MAC + Deskriptoren); K_Box bleibt node-lokal.
 * Minimale durable Seam: der Retry/Drain-Worker gehört dem Sync-Fundament (#2).
 */
@Entity
@Table(name = "registration_outbox")
public class RegistrationOutboxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "box_id", nullable = false)
    private UUID boxId;

    @Column(name = "mac_address", nullable = false)
    private String macAddress;

    @Column(name = "box_type")
    private String boxType;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "capabilities_json", length = 4000)
    private String capabilitiesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    public UUID getId() { return id; }
    public UUID getBoxId() { return boxId; }
    public void setBoxId(UUID boxId) { this.boxId = boxId; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public String getBoxType() { return boxType; }
    public void setBoxType(String boxType) { this.boxType = boxType; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public void setCapabilitiesJson(String capabilitiesJson) { this.capabilitiesJson = capabilitiesJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
```

- [ ] **Step 4: Create the repository**

Create `RegistrationOutboxRepository.java`:

```java
package ch.jp.shooting.node.onboarding.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistrationOutboxRepository extends JpaRepository<RegistrationOutboxRecord, UUID> {
    List<RegistrationOutboxRecord> findByStatus(String status);
}
```

- [ ] **Step 5: Create the push seam + default impl**

Create `HubRegistrationClient.java`:

```java
package ch.jp.shooting.node.onboarding.outbox;

/**
 * Push-Seam für die Geräte-Registrierung zum Hub. Das echte Hub-Registrierungs-Endpoint
 * (idempotente Annahme der node-vergebenen Box-UUID) gehört dem Sync-Fundament (#2);
 * bis dahin meldet die Default-Impl "nicht gesendet", die Zeile bleibt in der Outbox.
 */
public interface HubRegistrationClient {
    boolean register(RegistrationOutboxRecord row);
}
```

Create `LoggingHubRegistrationClient.java`:

```java
package ch.jp.shooting.node.onboarding.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default-Impl: kein Hub-Endpoint (#2) — protokolliert und meldet "nicht gesendet". */
@Component
public class LoggingHubRegistrationClient implements HubRegistrationClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingHubRegistrationClient.class);

    @Override
    public boolean register(RegistrationOutboxRecord row) {
        log.info("Geräte-Registrierung {} (MAC {}) in Outbox — Hub-Push folgt mit Sync-Fundament (#2)",
                row.getBoxId(), row.getMacAddress());
        return false;
    }
}
```

- [ ] **Step 6: Create the service**

Create `RegistrationOutboxService.java`:

```java
package ch.jp.shooting.node.onboarding.outbox;

import ch.jp.shooting.node.box.BoxRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Schreibt die Geräte-Registrierung durable in die Outbox und macht EINEN Best-Effort-Push.
 * Kein Retry/Drain-Worker in Plan 2 — bleibt die Zeile PENDING, holt der Sync-Fundament-Worker (#2) sie später ab.
 */
@Service
public class RegistrationOutboxService {

    private final RegistrationOutboxRepository repository;
    private final HubRegistrationClient hubClient;

    public RegistrationOutboxService(RegistrationOutboxRepository repository, HubRegistrationClient hubClient) {
        this.repository = repository;
        this.hubClient = hubClient;
    }

    @Transactional
    public RegistrationOutboxRecord enqueueAndAttempt(BoxRecord box) {
        RegistrationOutboxRecord row = new RegistrationOutboxRecord();
        row.setBoxId(box.getId());
        row.setMacAddress(box.getMacAddress());
        row.setBoxType(box.getBoxType());
        row.setAppVersion(box.getAppVersion());
        row.setFirmwareVersion(box.getFirmwareVersion());
        row.setCapabilitiesJson(box.getCapabilitiesJson());
        row.setCreatedAt(Instant.now());
        row.setStatus("PENDING");
        row.setAttempts(0);
        row = repository.save(row);

        row.setAttempts(row.getAttempts() + 1);
        try {
            if (hubClient.register(row)) {
                row.setStatus("SENT");
            }
        } catch (RuntimeException e) {
            row.setLastError(e.getMessage());
        }
        return repository.save(row);
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=RegistrationOutboxServiceTest`
Expected: PASS — 3 tests green.

- [ ] **Step 8: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/outbox/ \
        smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/outbox/RegistrationOutboxServiceTest.java
git commit -m "[node] add registration outbox with best-effort hub push seam"
```

---

### Task 7: Token-gate `box-api` discovery + enqueue registration

**Files:**
- Modify: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxDiscoveryRequest.java`
- Modify: `smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxDiscoveryController.java`
- Modify: `smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxDiscoveryControllerTest.java`

**Interfaces:**
- Consumes: `ProvisioningTokenService.validateAndConsume` + `mint` (Task 2), `RegistrationOutboxService.enqueueAndAttempt` (Task 6), existing `BoxProvisioningService`, `BoxRecordRepository`.
- Produces: `BoxDiscoveryRequest` gains a `String token` field; `BoxDiscoveryController` constructor becomes `(BoxProvisioningService, BoxRecordRepository, ProvisioningTokenService, RegistrationOutboxService)`. Discovery now **requires** a valid token; on success it provisions and enqueues a registration row.

**Design note (redefinition):** discovery is now the token-gated first-contact provisioning call. A provisioned box never re-fetches `K_Box` (it persists it and moves to the ESP-NOW paired path), so requiring a token every time is correct — a tokenless call is rejected. The old "idempotent every-boot discovery" behavior is redefined out (pre-v1.0, allowed).

- [ ] **Step 1: Update the existing test to the new token-gated contract**

Replace `BoxDiscoveryControllerTest.java` entirely with the version below. It mints a real token per fixture MAC (via the autowired `ProvisioningTokenService`), passes it in the body, and adds rejection + outbox-side-effect cases. The `standaloneSetup` construction gets the two new beans.

```java
package ch.jp.shooting.node.box;

import ch.jp.shooting.node.onboarding.ProvisioningTokenService;
import ch.jp.shooting.node.onboarding.outbox.RegistrationOutboxRepository;
import ch.jp.shooting.node.onboarding.outbox.RegistrationOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Uses {@code MockMvcBuilders.standaloneSetup(...)} with real {@code @Autowired} beans under
 * {@code @SpringBootTest @Transactional} — the offline environment's trimmed
 * {@code spring-boot-test-autoconfigure} jar has no {@code @AutoConfigureMockMvc}; see the
 * Hub's {@code OtaDownloadControllerTest} pattern. Fixture MACs are defensively cleared in
 * {@code @BeforeEach} because this module's H2 datasource is file-based, not in-memory.
 */
@SpringBootTest
@Transactional
class BoxDiscoveryControllerTest {

    @Autowired
    private BoxProvisioningService provisioningService;
    @Autowired
    private BoxRecordRepository repository;
    @Autowired
    private ProvisioningTokenService tokenService;
    @Autowired
    private RegistrationOutboxService outboxService;
    @Autowired
    private RegistrationOutboxRepository outboxRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository.findByMacAddress("AA:BB:CC:DD:EE:10").ifPresent(repository::delete);
        repository.findByMacAddress("AA:BB:CC:DD:EE:11").ifPresent(repository::delete);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BoxDiscoveryController(provisioningService, repository, tokenService, outboxService)).build();
    }

    private String body(String mac, String token) {
        return """
            {"macAddress":"%s","token":"%s","appVersion":"1.0.0",
             "firmwareVersion":"micropython-1.23","boxType":"thrower","capabilitiesJson":"{}"}
            """.formatted(mac, token);
    }

    @Test
    void discovery_withValidToken_provisionsAndEnqueuesRegistration() throws Exception {
        String token = tokenService.mint("AA:BB:CC:DD:EE:10").hex();

        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body("AA:BB:CC:DD:EE:10", token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provisioned").value(true))
            .andExpect(jsonPath("$.kBoxBase64").isNotEmpty());

        assertThat(outboxRepository.findByStatus("PENDING"))
                .anyMatch(r -> r.getMacAddress().equals("AA:BB:CC:DD:EE:10"));
    }

    @Test
    void discovery_withoutToken_isRejected() throws Exception {
        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body("AA:BB:CC:DD:EE:11", "deadbeef")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void discovery_reusedToken_isRejectedSecondTime() throws Exception {
        String token = tokenService.mint("AA:BB:CC:DD:EE:11").hex();

        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body("AA:BB:CC:DD:EE:11", token)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body("AA:BB:CC:DD:EE:11", token)))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=BoxDiscoveryControllerTest`
Expected: compile failure — `BoxDiscoveryController` constructor arity mismatch / `token()` not found on `BoxDiscoveryRequest`.

- [ ] **Step 3: Add the `token` field to the request**

Replace `BoxDiscoveryRequest.java`:

```java
package ch.jp.shooting.node.box;

public record BoxDiscoveryRequest(
        String macAddress,
        String token,
        String appVersion,
        String firmwareVersion,
        String boxType,
        String capabilitiesJson) {
}
```

- [ ] **Step 4: Token-gate the controller + enqueue registration**

Replace `BoxDiscoveryController.java`:

```java
package ch.jp.shooting.node.box;

import ch.jp.shooting.node.onboarding.ProvisioningTokenService;
import ch.jp.shooting.node.onboarding.outbox.RegistrationOutboxService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Box-zugewandter Discovery-/Provisionierungs-Endpunkt. Hand-spezifiziert statt über
 * openapi.yaml generiert: die SmartBox ist ein MicroPython-Client, nicht ein Java-
 * Konsument von contracts — gleiche bewusste Ausnahme wie OtaDownloadController im Hub.
 *
 * Discovery ist der token-gesicherte Erst-Kontakt (Provisionierung). Eine gültige Box
 * holt K_Box genau einmal hier ab und wechselt danach auf den ESP-NOW-Pairing-Pfad;
 * ein Token-loser Aufruf wird abgewiesen (sonst könnte ein MAC-Spoofer K_Box ziehen).
 */
@RestController
public class BoxDiscoveryController {

    private final BoxProvisioningService provisioningService;
    private final BoxRecordRepository repository;
    private final ProvisioningTokenService tokenService;
    private final RegistrationOutboxService outboxService;

    public BoxDiscoveryController(BoxProvisioningService provisioningService, BoxRecordRepository repository,
                                  ProvisioningTokenService tokenService, RegistrationOutboxService outboxService) {
        this.provisioningService = provisioningService;
        this.repository = repository;
        this.tokenService = tokenService;
        this.outboxService = outboxService;
    }

    @PostMapping("/box-api/v1/discovery")
    public BoxDiscoveryResponse discover(@RequestBody BoxDiscoveryRequest request) {
        tokenService.validateAndConsume(request.token(), request.macAddress());

        boolean wasKnown = repository.findByMacAddress(request.macAddress()).isPresent();
        BoxRecord record = provisioningService.provision(
                request.macAddress(), request.appVersion(), request.firmwareVersion(),
                request.boxType(), request.capabilitiesJson());
        outboxService.enqueueAndAttempt(record);
        return new BoxDiscoveryResponse(Base64.getEncoder().encodeToString(record.getKBox()), !wasKnown);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=BoxDiscoveryControllerTest`
Expected: PASS — 3 tests green.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxDiscoveryRequest.java \
        smart-ground-node/src/main/java/ch/jp/shooting/node/box/BoxDiscoveryController.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/box/BoxDiscoveryControllerTest.java
git commit -m "[node] token-gate box-api discovery and enqueue device registration"
```

---

### Task 8: node-api onboarding controller

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/PendingBoxResponse.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/OnboardingController.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/OnboardingControllerTest.java`

**Interfaces:**
- Consumes: `PendingBoxRegistry` (Task 1), `OnboardingService.couple` + `CoupleResult` (Task 5).
- Produces:
  - `PendingBoxResponse(String mac, int rssi, java.time.Instant firstSeen, java.time.Instant lastSeen)` — note: `boxNonce` is **not** exposed to the operator UI.
  - `OnboardingController`: `GET /node-api/v1/onboarding/pending` → `List<PendingBoxResponse>`; `POST /node-api/v1/onboarding/{mac}/couple` → `CoupleResult`.

- [ ] **Step 1: Write the failing test**

Create `OnboardingControllerTest.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * standaloneSetup with real beans under @SpringBootTest @Transactional — same offline
 * constraint and pattern as BoxDiscoveryControllerTest. The pending registry is in-RAM;
 * this test seeds it via onHello and constructs the OnboardingService with a no-op RadioSender.
 */
@SpringBootTest
@Transactional
class OnboardingControllerTest {

    @Autowired
    private PendingBoxRegistry registry;
    @Autowired
    private ProvisioningTokenService tokenService;
    @Autowired
    private NodeCertFingerprint certFingerprint;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry.remove("AA:BB:CC:DD:EE:50");
        OnboardingService service = new OnboardingService(registry, tokenService, certFingerprint,
                (dest, frame) -> { }, "30:ae:a4:1f:2b:3c", "SmartGround-Node-1", "provision-pw-123", "https://192.168.4.1:8443");
        mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(registry, service)).build();
    }

    @Test
    void pending_listsRegisteredBoxes_withoutExposingNonce() throws Exception {
        registry.onHello("AA:BB:CC:DD:EE:50", -44, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        mockMvc.perform(get("/node-api/v1/onboarding/pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.mac=='AA:BB:CC:DD:EE:50')].rssi").value(org.hamcrest.Matchers.hasItem(-44)))
            .andExpect(jsonPath("$[0].boxNonce").doesNotExist());
    }

    @Test
    void couple_returnsOfferedStatus() throws Exception {
        registry.onHello("AA:BB:CC:DD:EE:50", -44, new byte[8]);

        mockMvc.perform(post("/node-api/v1/onboarding/AA:BB:CC:DD:EE:50/couple"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mac").value("AA:BB:CC:DD:EE:50"))
            .andExpect(jsonPath("$.status").value("offered"));
    }

    @Test
    void couple_unknownMac_returns404() throws Exception {
        mockMvc.perform(post("/node-api/v1/onboarding/AA:BB:CC:DD:EE:51/couple"))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=OnboardingControllerTest`
Expected: compile failure — `cannot find symbol: class OnboardingController` / `PendingBoxResponse`.

- [ ] **Step 3: Create the response DTO**

Create `PendingBoxResponse.java`:

```java
package ch.jp.shooting.node.onboarding;

import java.time.Instant;

/** node-api-Sicht auf eine pending Box. Ohne boxNonce — die geht die Bediener-UI nichts an. */
public record PendingBoxResponse(String mac, int rssi, Instant firstSeen, Instant lastSeen) {

    public static PendingBoxResponse from(PendingBox box) {
        return new PendingBoxResponse(box.mac(), box.rssi(), box.firstSeen(), box.lastSeen());
    }
}
```

- [ ] **Step 4: Create the controller**

Create `OnboardingController.java`:

```java
package ch.jp.shooting.node.onboarding;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * node-api-Fläche für die Bediener-Kopplung. Hand-spezifiziert (box-api-Muster), nicht aus
 * openapi.yaml generiert. Nur die Onboarding-Scheibe — keine Provenance-/Degradations-Semantik
 * der vollen node-api-Fassade (#5). Geschützt durch den NodeApiAuthFilter (Task 9).
 */
@RestController
public class OnboardingController {

    private final PendingBoxRegistry registry;
    private final OnboardingService onboardingService;

    public OnboardingController(PendingBoxRegistry registry, OnboardingService onboardingService) {
        this.registry = registry;
        this.onboardingService = onboardingService;
    }

    @GetMapping("/node-api/v1/onboarding/pending")
    public List<PendingBoxResponse> pending() {
        return registry.list().stream().map(PendingBoxResponse::from).toList();
    }

    @PostMapping("/node-api/v1/onboarding/{mac}/couple")
    public CoupleResult couple(@PathVariable String mac) {
        return onboardingService.couple(mac);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=OnboardingControllerTest`
Expected: PASS — 3 tests green.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/PendingBoxResponse.java \
        smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/OnboardingController.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/OnboardingControllerTest.java
git commit -m "[node] add node-api onboarding controller (pending + couple)"
```

---

### Task 9: node-api JWT auth (verifier + filter)

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/security/NodeJwtVerifier.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/security/NodeApiAuthFilter.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/security/NodeApiSecurityConfig.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/security/NodeJwtVerifierTest.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/security/NodeApiAuthFilterTest.java`

**Interfaces:**
- Consumes: `jwt.secret` property; Jackson `ObjectMapper` (new instance — no Spring wiring needed in the verifier).
- Produces:
  - `boolean NodeJwtVerifier.isValid(String token)` — verifies HS256 signature against `Base64.getDecoder().decode(jwt.secret)` and rejects if the `exp` claim is in the past. Malformed input returns `false`, never throws.
  - `NodeApiAuthFilter extends OncePerRequestFilter` — requires `Authorization: Bearer <jwt>`; on failure writes `401` `application/problem+json` and does not continue the chain.
  - `NodeApiSecurityConfig` — `FilterRegistrationBean<NodeApiAuthFilter>` mapped to URL pattern `/node-api/*` (so `/box-api/*` is unaffected).

- [ ] **Step 1: Write the failing verifier test**

Create `NodeJwtVerifierTest.java` — it hand-crafts HS256 tokens with the shared dev secret using only the JDK:

```java
package ch.jp.shooting.node.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class NodeJwtVerifierTest {

    // Same Base64 dev secret the Hub signs with (application.properties jwt.secret default).
    private static final String SECRET = "bWVpbi1zZWNyZXQta2V5LWZ1ZXItc21hcnQtZ3JvdW5kLWlzdC1sYW5n";
    private final NodeJwtVerifier verifier = new NodeJwtVerifier(SECRET);

    private static String jwt(long expEpochSeconds, String secretBase64) throws Exception {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String header = url.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = url.encodeToString(
                ("{\"sub\":\"admin@smartground.local\",\"exp\":" + expEpochSeconds + "}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(secretBase64), "HmacSHA256"));
        String sig = url.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        return signingInput + "." + sig;
    }

    @Test
    void validSignatureAndFutureExp_isValid() throws Exception {
        String token = jwt(Instant.now().getEpochSecond() + 3600, SECRET);
        assertThat(verifier.isValid(token)).isTrue();
    }

    @Test
    void expiredToken_isInvalid() throws Exception {
        String token = jwt(Instant.now().getEpochSecond() - 10, SECRET);
        assertThat(verifier.isValid(token)).isFalse();
    }

    @Test
    void wrongSecret_isInvalid() throws Exception {
        String token = jwt(Instant.now().getEpochSecond() + 3600,
                Base64.getEncoder().encodeToString("some-other-key-that-is-32-bytes!".getBytes(StandardCharsets.UTF_8)));
        assertThat(verifier.isValid(token)).isFalse();
    }

    @Test
    void garbage_isInvalidNotThrown() {
        assertThat(verifier.isValid("not-a-jwt")).isFalse();
        assertThat(verifier.isValid("a.b")).isFalse();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=NodeJwtVerifierTest`
Expected: compile failure — `cannot find symbol: class NodeJwtVerifier`.

- [ ] **Step 3: Create the verifier**

Create `NodeJwtVerifier.java`:

```java
package ch.jp.shooting.node.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Minimale, JDK-only JWT-Verifikation für node-api: prüft nur HS256-Signatur + Ablauf gegen
 * das geteilte {@code jwt.secret} (dieselbe Basis wie der Hub). Bewusst KEINE feingranulare
 * Permission-Auflösung — das Bediener-Gate bleibt der offene Punkt der Coupling-Spec.
 * Keine io.jsonwebtoken-Abhängigkeit (offline nicht verfügbar).
 */
@Component
public class NodeJwtVerifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final byte[] keyBytes;

    public NodeJwtVerifier(@Value("${jwt.secret}") String secretBase64) {
        this.keyBytes = Base64.getDecoder().decode(secretBase64);
    }

    public boolean isValid(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            String signingInput = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] expected = mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return false;
            }
            JsonNode claims = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (claims.has("exp") && Instant.now().getEpochSecond() >= claims.get("exp").asLong()) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run the verifier test to verify it passes**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=NodeJwtVerifierTest`
Expected: PASS — 4 tests green.

- [ ] **Step 5: Write the failing filter test**

Create `NodeApiAuthFilterTest.java` — drives the filter through a standalone MockMvc on the real `OnboardingController`, with a no-op onboarding service:

```java
package ch.jp.shooting.node.security;

import ch.jp.shooting.node.onboarding.OnboardingController;
import ch.jp.shooting.node.onboarding.OnboardingService;
import ch.jp.shooting.node.onboarding.PendingBoxRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NodeApiAuthFilterTest {

    private static final String SECRET = "bWVpbi1zZWNyZXQta2V5LWZ1ZXItc21hcnQtZ3JvdW5kLWlzdC1sYW5n";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PendingBoxRegistry registry = new PendingBoxRegistry();
        OnboardingService service = new OnboardingService(registry, null, null,
                (d, f) -> { }, "30:ae:a4:1f:2b:3c", "S", "P", "https://x");
        NodeApiAuthFilter filter = new NodeApiAuthFilter(new NodeJwtVerifier(SECRET));
        mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(registry, service))
                .addFilters(filter).build();
    }

    private static String validJwt() throws Exception {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String header = url.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = url.encodeToString(
                ("{\"sub\":\"admin\",\"exp\":" + (Instant.now().getEpochSecond() + 3600) + "}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"));
        String sig = url.encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII)));
        return header + "." + payload + "." + sig;
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/node-api/v1/onboarding/pending")).andExpect(status().isUnauthorized());
    }

    @Test
    void validToken_passesThrough() throws Exception {
        mockMvc.perform(get("/node-api/v1/onboarding/pending").header("Authorization", "Bearer " + validJwt()))
            .andExpect(status().isOk());
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=NodeApiAuthFilterTest`
Expected: compile failure — `cannot find symbol: class NodeApiAuthFilter`.

- [ ] **Step 7: Create the filter + registration config**

Create `NodeApiAuthFilter.java`:

```java
package ch.jp.shooting.node.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Schützt /node-api/* mit einem minimalen Bearer-JWT-Check (Signatur + Ablauf, {@link NodeJwtVerifier}).
 * box-api bleibt unberührt (Boxen sind anonyme Clients). Feingranulares Permission-Gate: offener Punkt.
 */
public class NodeApiAuthFilter extends OncePerRequestFilter {

    private static final String PROBLEM_JSON =
            "{\"type\":\"/errors/unauthenticated\",\"title\":\"Unauthorized\",\"status\":401}";

    private final NodeJwtVerifier verifier;

    public NodeApiAuthFilter(NodeJwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ") && verifier.isValid(auth.substring(7))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.getWriter().write(PROBLEM_JSON);
    }
}
```

Create `NodeApiSecurityConfig.java`:

```java
package ch.jp.shooting.node.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registriert den {@link NodeApiAuthFilter} nur für /node-api/* — box-api bleibt offen. */
@Configuration
class NodeApiSecurityConfig {

    @Bean
    FilterRegistrationBean<NodeApiAuthFilter> nodeApiAuthFilter(NodeJwtVerifier verifier) {
        FilterRegistrationBean<NodeApiAuthFilter> registration = new FilterRegistrationBean<>(new NodeApiAuthFilter(verifier));
        registration.addUrlPatterns("/node-api/*");
        return registration;
    }
}
```

- [ ] **Step 8: Run both security tests to verify they pass**

Run: `cd smart-ground-node && mvn -o -q test -Dtest=NodeJwtVerifierTest,NodeApiAuthFilterTest`
Expected: PASS — 6 tests green.

- [ ] **Step 9: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/security/ \
        smart-ground-node/src/test/java/ch/jp/shooting/node/security/
git commit -m "[node] guard node-api with JDK-only HMAC JWT signature filter"
```

---

### Task 10: Wire config, full-suite verification, and docs

**Files:**
- Modify: `smart-ground-node/src/main/resources/application.properties`
- Modify: `smart-ground-node/CLAUDE.md`

**Interfaces:** none new — this task provides the `@Value` properties every earlier bean reads (`jwt.secret`, `onboarding.*`) and proves the whole module boots + tests green.

- [ ] **Step 1: Add the onboarding + JWT config**

Append to `smart-ground-node/src/main/resources/application.properties`:

```properties
# --- node-api onboarding ---
# Shared HMAC secret: MUST equal the Hub's jwt.secret (same Base64 raw key). The node only
# verifies the signature + expiry; it never issues tokens. Dev default matches the Hub's default.
jwt.secret=${JWT_SECRET:bWVpbi1zZWNyZXQta2V5LWZ1ZXItc21hcnQtZ3JvdW5kLWlzdC1sYW5n}
# Radio MAC placed as ONBOARD_OFFER source; dev placeholder until the serial radio layer sets it.
onboarding.node-mac=${ONBOARDING_NODE_MAC:30:ae:a4:1f:2b:3c}
# AP credentials + box-api base URL handed to the box in the (cleartext) ONBOARD_OFFER.
onboarding.ap-ssid=${ONBOARDING_AP_SSID:SmartGround-Node-1}
onboarding.ap-psk=${ONBOARDING_AP_PSK:provision-pw-123}
onboarding.box-api-url=${ONBOARDING_BOX_API_URL:https://192.168.4.1:8443}
# One-time provisioning-token lifetime.
onboarding.token-ttl-seconds=${ONBOARDING_TOKEN_TTL_SECONDS:300}
```

- [ ] **Step 2: Run the whole node suite (no regressions, config resolves)**

Run: `cd smart-ground-node && mvn -o -q test`
Expected: `BUILD SUCCESS` — all prior tests plus the new onboarding/security tests pass; the Spring context boots with the new `@Value` properties resolved (a missing property would fail context startup).

- [ ] **Step 3: Document node-api + onboarding in the node CLAUDE.md**

In `smart-ground-node/CLAUDE.md`, after the `### HTTPS setup (Task 8)` section at the end of the `box-api` section, add a new top-level section:

````markdown
## node-api (onboarding slice)

`smart-ground-node` also serves a **client-facing** `node-api` — distinct from the box-facing `box-api`. Plan 2 stands up only the **onboarding slice**; the full node-api facade (provenance envelope, Hub-degradation, offline login) is sub-project #5 and is **not** built here.

### Endpoints (hand-written `@RestController`, not generated — same exception as box-api)

- `GET /node-api/v1/onboarding/pending` → `List<PendingBoxResponse>` — boxes that announced themselves via `HELLO` (mac, rssi, firstSeen, lastSeen; `boxNonce` is not exposed).
- `POST /node-api/v1/onboarding/{mac}/couple` → `CoupleResult` — mints a one-time provisioning token and emits an `ONBOARD_OFFER` to the box via the `RadioSender` seam; returns `status="offered"`. `404 /errors/box-not-pending` if the MAC is no longer announcing.

### Auth

`/node-api/*` is guarded by `NodeApiAuthFilter` (`ch.jp.shooting.node.security`): a plain servlet filter doing **JDK-only HMAC-SHA256** verification of the bearer JWT's **signature + expiry** against the shared `jwt.secret` (must equal the Hub's). No `spring-security`, no `io.jsonwebtoken`. **Fine-grained admin permission is deliberately deferred** (the Hub JWT carries no permission claims; the operator gate is the coupling spec's open point). `/box-api/*` stays unauthenticated.

### Onboarding flow pieces (`ch.jp.shooting.node.onboarding`)

- `PendingBoxRegistry` — in-RAM, fed by the `onHello(mac, rssi, boxNonce)` **ingestion seam** (no serial receive wiring in Plan 2).
- `ProvisioningTokenService` / `ProvisioningTokenRecord` — one-time token (16 random bytes, TTL, MAC-bound, single-use), persisted in the node's H2.
- `RadioSender` — **send-only seam**; `LoggingRadioSender` is the current impl (real serial impl deferred).
- `NodeCertFingerprint` — SHA-256 of the node's TLS cert, pinned by the box for the provisioning TLS session; travels in the `ONBOARD_OFFER`.
- `OnboardingService.couple(mac)` — orchestrates lookup → mint token → build `ONBOARD_OFFER` (Plan 1 `OnboardingCodec`) → `RadioSender.send`.
- `outbox/RegistrationOutboxService` — on provisioning, persists a device-registration row and makes **one best-effort** push through the `HubRegistrationClient` seam (`LoggingHubRegistrationClient` currently no-ops; the real Hub endpoint + retry/drain worker are Sync-Fundament #2). The outbox row carries identity only — **never `K_Box`**.

### box-api discovery is now token-gated first-contact provisioning

`POST /box-api/v1/discovery` now **requires** a valid `token` (`BoxDiscoveryRequest.token`): `ProvisioningTokenService.validateAndConsume(token, mac)` runs first (typed `400 /errors/invalid-provisioning-token` on unknown/used/expired/MAC-mismatch), then `BoxProvisioningService.provision(...)` mints `K_Box` and `RegistrationOutboxService.enqueueAndAttempt(...)` queues the registration. A provisioned box never re-fetches `K_Box` (it persists it and moves to the ESP-NOW paired path), so the old idempotent every-boot discovery is redefined out (pre-v1.0).
````

- [ ] **Step 4: Commit**

```bash
git add smart-ground-node/src/main/resources/application.properties smart-ground-node/CLAUDE.md
git commit -m "[node] wire onboarding config and document node-api onboarding slice"
```

---

## Self-Review

**1. Spec coverage** — every design-spec building block maps to a task:

| Design-spec item | Task |
|---|---|
| `node-api` surface (`GET pending`, `POST couple`), hand-written | 8 |
| Node JWT filter (signature+expiry, JDK-only, `/node-api/*` only) | 9 |
| `PendingBoxRegistry` + `onHello` ingestion seam | 1 |
| One-time provisioning-token lifecycle (TTL, MAC-bound, single-use) | 2 |
| `RadioSender` seam (interface + logging impl) | 5 |
| `NodeCertFingerprint` (SHA-256 of TLS cert) | 4 |
| `OnboardingService.couple` → `ONBOARD_OFFER` via codec + sender | 5 (+ `Macs` in 3) |
| box-api discovery token gate → mint `K_Box` → outbox | 7 |
| Minimal durable outbox + best-effort push seam | 6 |
| Config + docs + full-suite verification | 10 |

Scope boundaries (no retry/drain worker; ingestion seam only; no provenance/degradation/offline-login; deferred admin gate) are honored — none of those produce a task, by design.

**2. Placeholder scan** — no `TBD`/`TODO`/"handle errors appropriately"/"similar to Task N". Every code step shows complete, compilable code; every run step gives the exact `mvn -o` command and expected result.

**3. Type consistency** — cross-task signatures verified:
- `ProvisioningTokenService.mint(String)→MintedToken(hex,raw,expiresAt)` and `validateAndConsume(String,String)` are produced in Task 2 and consumed identically in Tasks 5 (`token.raw()`, `token.expiresAt()`) and 7 (`.hex()`, `validateAndConsume`).
- `OnboardingCodec.buildOnboardOffer(FrameHeader, byte[]×6)` + accessors match Plan 1's committed signatures (verified against `OnboardingCodec.java` on `main`).
- `BoxDiscoveryController` new constructor `(BoxProvisioningService, BoxRecordRepository, ProvisioningTokenService, RegistrationOutboxService)` is used consistently in Task 7's controller and its test.
- `RegistrationOutboxService.enqueueAndAttempt(BoxRecord)→RegistrationOutboxRecord` (Task 6) is called in Task 7; `BoxRecord` getters used (`getId/getMacAddress/getBoxType/getAppVersion/getFirmwareVersion/getCapabilitiesJson`) exist verbatim in `BoxRecord.java`.
- `OnboardingService` constructor arg order `(registry, tokenService, certFingerprint, radioSender, nodeMac, apSsid, apPsk, boxApiUrl)` is identical in Tasks 5, 8, and 9 test/production call sites.
- `NodeJwtVerifier(String)` / `isValid(String)` and `NodeApiAuthFilter(NodeJwtVerifier)` match across Task 9's verifier test, filter test, and config.
- `PendingBoxRegistry` methods `onHello/list/find/remove` (Task 1) are used identically in Tasks 5, 8, 9.
````

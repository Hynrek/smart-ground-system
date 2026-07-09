# ESP-NOW Krypto-Fundament (Baustein A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the `smart-ground-node` Spring Boot module skeleton and a shared, cross-verified AES-256-GCM + HKDF-SHA256 crypto test vector fixture, with matching implementations in Java (node) and MicroPython (box) that both pass against it — Phase 0 milestone M0 ("Test-Vektoren bestehen auf beiden Seiten") from [plan-espnow-migration.md](../../plan-espnow-migration.md).

**Architecture:** One canonical JSON fixture file at `docs/espnow/crypto-test-vectors.json`, generated and cross-verified against Node.js's native `crypto` module (itself OpenSSL-backed) before being committed — never hand-typed into source, since a single wrong hex nibble silently breaks the whole cross-language guarantee. Java gets a thin `Hkdf`/`AesGcm` wrapper around `javax.crypto` (native AES-GCM support). MicroPython gets a hand-rolled `espnow_crypto.py`: `ucryptolib.aes(key, MODE_ECB)` for the AES primitive (hardware-accelerated, real on-device) plus a pure-Python GHASH/counter-mode assembly on top, because MicroPython's `ucryptolib` has no native GCM mode. Host tests (CPython, no `ucryptolib` available) get a pure-Python AES-256-ECB stub in `tests/_stubs.py`, following this project's existing stub pattern for `machine`/`network`/`umqtt.simple`.

**Tech Stack:** Java 25, Spring Boot 4.0.5, Maven, JUnit 5 + AssertJ (via `spring-boot-starter-test`), Jackson (fixture parsing) — MicroPython 1.23+ compatible Python, CPython 3 host tests via `unittest`.

## Global Constraints

- Java 25, `spring-boot-starter-parent` version `4.0.5` (matches `smart-ground-backend/pom.xml`), Maven groupId `ch.jp.shooting`.
- Use the system `mvn` (already installed, Maven 3.9.14) — `smart-ground-backend`'s `mvnw.cmd` has no committed wrapper jar/`.mvn/wrapper` directory to copy, so don't add a wrapper here either; keep it consistent (no wrapper) rather than half-copying one.
- MicroPython 1.23+ compatible code only in `smart-box/espnow_crypto.py`. This task extends the "Allowed stdlib modules" rule in `smart-box/CLAUDE.md` — `hashlib` becomes usable in `espnow_crypto.py` too (previously "only in `ota.py`") — Task 7 updates that doc.
- Comments: German for domain logic (both the new `smart-ground-node` Java code and `smart-box` MicroPython code, per the top-level `CLAUDE.md` convention and the existing `smart-box/CLAUDE.md` convention), English identifiers everywhere.
- Exactly one canonical crypto fixture file: `docs/espnow/crypto-test-vectors.json`. Both test suites read it via a relative path — never copy or duplicate its content.
- Test commands: Java — `mvn test` from `smart-ground-node/`. MicroPython — `python -m unittest discover -s tests -t . -v` from `smart-box/`.
- No frame-format, pairing, UART, or crypto-vector *generation* logic beyond what's specified here — those are separate follow-on plans ("Baustein B/C/D" from the ESP-NOW protocol contracts design).

---

### Task 1: Canonical crypto test vector fixture

**Files:**
- Create: `docs/espnow/crypto-test-vectors.json`

**Interfaces:**
- Produces: a JSON file with two top-level arrays, `hkdf_sha256_rfc5869` (1 entry: `hash`, `ikm`, `salt`, `info`, `l`, `prk`, `okm`, all hex strings except `hash`/`l`) and `aes256_gcm` (4 entries: `name`, `key`, `iv`, `aad`, `plaintext`, `ciphertext`, `tag`, all hex strings except `name`). All values below were generated via Node.js's native `crypto` module (`aes-256-gcm`, OpenSSL-backed) and round-trip-verified (encrypt→decrypt→compare) before being written here; the HKDF vector is RFC 5869 Appendix A.1, cross-checked via two independent fetches of the RFC text. Do not hand-edit any hex value in this file — regenerate via the equivalent script if a value ever needs to change.

- [ ] **Step 1: Write the fixture file**

```json
{
  "hkdf_sha256_rfc5869": [
    {
      "hash": "SHA-256",
      "ikm": "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b",
      "salt": "000102030405060708090a0b0c",
      "info": "f0f1f2f3f4f5f6f7f8f9",
      "l": 42,
      "prk": "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5",
      "okm": "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
    }
  ],
  "aes256_gcm": [
    {
      "name": "aes256_gcm_populated",
      "key": "76c28bf1ff1dd2affd8949f8f352aaa25a03e93d6509e7e02c5920a13e379f87",
      "iv": "22cb0a3edab72caa40903755",
      "aad": "feedfacedeadbeeffeedfacedeadbeefabaddad2",
      "plaintext": "48656c6c6f2c20536d617274426f782120546869732069732061203235302d62797465206672616d6521",
      "ciphertext": "0615b020d7fe787eb427f0ace1459aed48716d00a84e046808822d8a3b6946cba6ef75eaa5f829bb187a",
      "tag": "d97d397d4ea18787bafc363636ca1aad"
    },
    {
      "name": "aes256_gcm_empty",
      "key": "0000000000000000000000000000000000000000000000000000000000000000",
      "iv": "000000000000000000000000",
      "aad": "",
      "plaintext": "",
      "ciphertext": "",
      "tag": "530f8afbc74536b9a963b4f1c4cb738b"
    },
    {
      "name": "aes256_gcm_single_block_no_aad",
      "key": "7f4cee817aadde86ca41462f0ee4d2df4e7db865def0952f535cac69fcf132e5",
      "iv": "139c0e7af6f6131c45cf0af3",
      "aad": "",
      "plaintext": "00000000000000000000000000000000",
      "ciphertext": "87a74da07b599ee7eef2ab8cdfa9008c",
      "tag": "2ac9c4e1b814b755e92419384bdda7de"
    },
    {
      "name": "aes256_gcm_multiblock_with_aad",
      "key": "4210f8f4dcae37c7698e1cbab994cd7c01fb43ee6fab2073bb6699a1dca7f112",
      "iv": "89ed4adce2faa5642eb6d76e",
      "aad": "000102030405060708090a0b0c0d0e0f",
      "plaintext": "d41d8cd98f00b204e9800998ecf8427e00000000000000000000000000000000112233445566778899aabbccddeeff0011223344",
      "ciphertext": "2a0201ffd9f83e3b6b31613aa2e20ea838b61b184a19ef94dad705ac78dca8fc40d523686e68156cd7102ae5850079ca56439bfe",
      "tag": "00c206e3b1571b9be02d0c2be248beaf"
    }
  ]
}
```

- [ ] **Step 2: Verify the file is valid JSON**

Run: `node -e "JSON.parse(require('fs').readFileSync('docs/espnow/crypto-test-vectors.json', 'utf8')); console.log('valid json')"` (from the repo root)
Expected: `valid json`

- [ ] **Step 3: Commit**

```bash
git add docs/espnow/crypto-test-vectors.json
git commit -m "docs: add canonical ESP-NOW crypto test vector fixture (HKDF + AES-256-GCM)"
```

---

### Task 2: `smart-ground-node` Spring Boot module scaffold

**Files:**
- Create: `smart-ground-node/pom.xml`
- Create: `smart-ground-node/CLAUDE.md`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/SmartGroundNodeApplication.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/SmartGroundNodeApplicationTests.java`
- Create: `smart-ground-node/.gitignore`

**Interfaces:**
- Produces: a standalone Maven module (not part of a reactor — matches how `smart-ground-backend` is standalone) that builds and starts a minimal Spring context. Later tasks in this plan add `ch.jp.shooting.node.crypto.*` under `src/main/java`.

- [ ] **Step 1: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.5</version>
        <relativePath/>
    </parent>

    <groupId>ch.jp.shooting</groupId>
    <artifactId>smart-ground-node</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>smart-ground-node</name>
    <description>SmartGround - ESP-NOW-Bridge (SmartNode)</description>

    <properties>
        <java.version>25</java.version>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Write the application entry point**

```java
package ch.jp.shooting.node;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SmartGroundNodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartGroundNodeApplication.class, args);
    }
}
```

- [ ] **Step 3: Write the context-load smoke test**

```java
package ch.jp.shooting.node;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SmartGroundNodeApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Write `.gitignore`** (matches Maven's standard build output directory)

```
target/
```

- [ ] **Step 5: Run the smoke test**

Run: `cd smart-ground-node && mvn test`
Expected: `BUILD SUCCESS`, with `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` for `SmartGroundNodeApplicationTests`

- [ ] **Step 6: Write `smart-ground-node/CLAUDE.md`**

```markdown
# smart-ground-node — SmartNode Development Guide

## Project Overview

`smart-ground-node` is the future ESP-NOW↔MQTT bridge (SmartNode) described in ADR-001.
It runs today as a separate Spring Boot process on the same machine as the backend
(`plan-espnow-migration.md`, "Vereinte Zwischenstufe") and will later move to its own
Pi-class device without a rewrite — it speaks only the final interfaces (MQTT, HTTPS,
UART) from day one, never the backend's database or in-process APIs directly.

## Stack & Versions

- **Java 25**, Spring Boot 4.0.5, Maven (system `mvn`, no wrapper committed)
- Standalone Maven module (like `smart-ground-backend`), not part of a reactor build

## Project Structure

```
smart-ground-node/
├── pom.xml
├── src/main/java/ch/jp/shooting/node/
│   ├── SmartGroundNodeApplication.java
│   └── crypto/                         # AES-256-GCM + HKDF-SHA256 (ADR-002/ADR-003)
└── src/test/java/ch/jp/shooting/node/
    └── crypto/                         # Cross-verified against docs/espnow/crypto-test-vectors.json
```

## Conventions

Same as `smart-ground-backend`: German comments for domain logic, English identifiers.
Contract-first for anything crossing a process boundary (MQTT payloads, HTTPS sync
endpoints, UART framing) — see `docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md`
for the wire formats this module implements.

## Crypto (`crypto/` package)

`Hkdf` and `AesGcm` are thin wrappers around `javax.crypto` (HMAC-SHA256 / AES/GCM/NoPadding,
both natively supported by the JDK — no hand-rolled crypto needed on this side, unlike
MicroPython's `ucryptolib` which lacks a native GCM mode). Both are tested against the
canonical fixture at `../docs/espnow/crypto-test-vectors.json`, which `smart-box`'s
MicroPython implementation is tested against too — this is what keeps the two sides from
silently diverging.

## Host Tests

```bash
mvn test
```

No hardware dependency yet (Baustein A is crypto primitives + module scaffold only).
Later tasks (frame codec, UART, pairing) will add hardware-adjacent seams (serial port
abstraction) analogous to `smart-box`'s `http_stream`/`esp32.Partition` test seams.
```

- [ ] **Step 7: Commit**

```bash
git add smart-ground-node/
git commit -m "feat(node): scaffold smart-ground-node Spring Boot module"
```

---

### Task 3: Java `Hkdf` utility

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/crypto/Hkdf.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/crypto/CryptoTestVectors.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/crypto/HkdfTest.java`

**Interfaces:**
- Consumes: `docs/espnow/crypto-test-vectors.json` (Task 1), resolved as `../docs/espnow/crypto-test-vectors.json` relative to the Maven working directory.
- Produces: `Hkdf.extract(byte[] salt, byte[] ikm) -> byte[]` and `Hkdf.expand(byte[] prk, byte[] info, int length) -> byte[]` — used later (Baustein B) to derive `K_S = HKDF(K_Box, nonce_b‖nonce_n)` per ADR-003.

- [ ] **Step 1: Write the fixture-loading test helper**

```java
package ch.jp.shooting.node.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

record HkdfVector(String hash, String ikm, String salt, String info, int l, String prk, String okm) {
}

record GcmVector(String name, String key, String iv, String aad, String plaintext, String ciphertext, String tag) {
}

record Fixture(List<HkdfVector> hkdf_sha256_rfc5869, List<GcmVector> aes256_gcm) {
}

final class CryptoTestVectors {

    private static final String FIXTURE_PATH = "../docs/espnow/crypto-test-vectors.json";

    private CryptoTestVectors() {
    }

    static Fixture load() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(new File(FIXTURE_PATH), Fixture.class);
        } catch (IOException e) {
            throw new IllegalStateException("Krypto-Test-Vektoren nicht lesbar: " + FIXTURE_PATH, e);
        }
    }

    static byte[] hex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package ch.jp.shooting.node.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HkdfTest {

    @Test
    void extractAndExpand_matchRfc5869TestCase1() {
        HkdfVector v = CryptoTestVectors.load().hkdf_sha256_rfc5869().get(0);

        byte[] prk = Hkdf.extract(CryptoTestVectors.hex(v.salt()), CryptoTestVectors.hex(v.ikm()));
        assertThat(prk).isEqualTo(CryptoTestVectors.hex(v.prk()));

        byte[] okm = Hkdf.expand(prk, CryptoTestVectors.hex(v.info()), v.l());
        assertThat(okm).isEqualTo(CryptoTestVectors.hex(v.okm()));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=HkdfTest`
Expected: FAIL (compile error) — `Hkdf` does not exist yet

- [ ] **Step 4: Write `Hkdf.java`**

```java
package ch.jp.shooting.node.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * HKDF (RFC 5869) auf Basis von HMAC-SHA256 — leitet K_S aus K_Box ab (ADR-003).
 */
public final class Hkdf {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private Hkdf() {
    }

    public static byte[] extract(byte[] salt, byte[] ikm) {
        return hmac(salt, ikm);
    }

    public static byte[] expand(byte[] prk, byte[] info, int length) {
        int n = (length + HASH_LENGTH - 1) / HASH_LENGTH;
        byte[] okm = new byte[length];
        byte[] t = new byte[0];
        int copied = 0;
        for (int i = 1; i <= n; i++) {
            byte[] input = new byte[t.length + info.length + 1];
            System.arraycopy(t, 0, input, 0, t.length);
            System.arraycopy(info, 0, input, t.length, info.length);
            input[input.length - 1] = (byte) i;
            t = hmac(prk, input);
            int toCopy = Math.min(HASH_LENGTH, length - copied);
            System.arraycopy(t, 0, okm, copied, toCopy);
            copied += toCopy;
        }
        return okm;
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 nicht verfuegbar", e);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=HkdfTest`
Expected: `BUILD SUCCESS`, `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/crypto/Hkdf.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/crypto/CryptoTestVectors.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/crypto/HkdfTest.java
git commit -m "feat(node): add HKDF-SHA256 utility, verified against RFC 5869 fixture"
```

---

### Task 4: Java `AesGcm` utility

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/crypto/AesGcm.java`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/crypto/AesGcmTest.java`

**Interfaces:**
- Consumes: `CryptoTestVectors.load()`, `CryptoTestVectors.hex(String)` (Task 3).
- Produces: `AesGcm.encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext) -> byte[]` and `AesGcm.decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextAndTag) -> byte[]` (throws `AEADBadTagException` on auth failure). Both use `ciphertext ‖ tag` concatenated (tag last 16 bytes) — this matches the frame body layout in `docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md` Section 4 (`counter_nonce ‖ ciphertext ‖ tag`) directly, no reshuffling needed at the call site.

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AesGcmTest {

    @Test
    void encrypt_matchesAllFixtureVectors() {
        List<GcmVector> vectors = CryptoTestVectors.load().aes256_gcm();
        assertThat(vectors).isNotEmpty();

        for (GcmVector v : vectors) {
            byte[] key = CryptoTestVectors.hex(v.key());
            byte[] iv = CryptoTestVectors.hex(v.iv());
            byte[] aad = CryptoTestVectors.hex(v.aad());
            byte[] plaintext = CryptoTestVectors.hex(v.plaintext());
            byte[] expected = concat(CryptoTestVectors.hex(v.ciphertext()), CryptoTestVectors.hex(v.tag()));

            byte[] actual = AesGcm.encrypt(key, iv, aad, plaintext);
            assertThat(actual).as("vector " + v.name()).isEqualTo(expected);
        }
    }

    @Test
    void decrypt_matchesAllFixtureVectors() throws AEADBadTagException {
        List<GcmVector> vectors = CryptoTestVectors.load().aes256_gcm();

        for (GcmVector v : vectors) {
            byte[] key = CryptoTestVectors.hex(v.key());
            byte[] iv = CryptoTestVectors.hex(v.iv());
            byte[] aad = CryptoTestVectors.hex(v.aad());
            byte[] ciphertextAndTag = concat(CryptoTestVectors.hex(v.ciphertext()), CryptoTestVectors.hex(v.tag()));
            byte[] expectedPlaintext = CryptoTestVectors.hex(v.plaintext());

            byte[] actual = AesGcm.decrypt(key, iv, aad, ciphertextAndTag);
            assertThat(actual).as("vector " + v.name()).isEqualTo(expectedPlaintext);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=AesGcmTest`
Expected: FAIL (compile error) — `AesGcm` does not exist yet

- [ ] **Step 3: Write `AesGcm.java`**

```java
package ch.jp.shooting.node.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * AES-256-GCM ueber javax.crypto — verschluesselt Betriebs- und Pairing-Frames unter K_Box/K_S
 * (ADR-002/ADR-003). Ein-/Ausgabe ist ciphertext||tag konkateniert (16-Byte-Tag am Ende),
 * passend zum Frame-Body-Layout aus
 * docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 4.
 */
public final class AesGcm {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BITS = 128;

    private AesGcm() {
    }

    public static byte[] encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM-Verschluesselung fehlgeschlagen", e);
        }
    }

    public static byte[] decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextAndTag) throws AEADBadTagException {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertextAndTag);
        } catch (AEADBadTagException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM-Entschluesselung fehlgeschlagen", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=AesGcmTest`
Expected: `BUILD SUCCESS`, `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full Java test suite**

Run: `cd smart-ground-node && mvn test`
Expected: `BUILD SUCCESS`, `Tests run: 4` (1 context-load + `HkdfTest` + 2 `AesGcmTest`), `Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/crypto/AesGcm.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/crypto/AesGcmTest.java
git commit -m "feat(node): add AES-256-GCM utility, verified against fixture vectors"
```

---

### Task 5: MicroPython `ucryptolib` host-test stub

**Files:**
- Modify: `smart-box/tests/_stubs.py` (append at end of file, after the existing `esp32.Partition` section)

**Interfaces:**
- Produces: a fake `ucryptolib` module in `sys.modules` providing `ucryptolib.MODE_ECB = 1` and `ucryptolib.aes(key, mode).encrypt(data)` for AES-256, ECB, arbitrary multiples of 16 bytes. Implemented as a from-scratch, computed-S-box (no hardcoded lookup table — removes transcription risk) pure-Python AES-256 core; verified against the FIPS-197 Appendix C.3 published test vector before being written here. **This stub exists only for host tests** — real hardware uses the genuine `ucryptolib` module; `espnow_crypto.py` (Task 6/7) calls the same `ucryptolib.aes(key, MODE_ECB).encrypt(...)` API either way, so no test-only branching is needed in production code.
- Consumes: nothing new (self-contained).

- [ ] **Step 1: Append the stub to `smart-box/tests/_stubs.py`**

```python

# --- ucryptolib (AES-256-ECB in reinem Python, nur fuer Host-Tests; echte Hardware nutzt
#     das mitgelieferte ucryptolib-Modul — siehe espnow_crypto.py, das dieselbe API ruft) ---
ucryptolib = types.ModuleType("ucryptolib")


def _gmul(a, b):
    p = 0
    for _ in range(8):
        if b & 1:
            p ^= a
        hi = a & 0x80
        a = (a << 1) & 0xFF
        if hi:
            a ^= 0x1B
        b >>= 1
    return p


def _build_sbox():
    inv = [0] * 256
    for x in range(1, 256):
        for y in range(1, 256):
            if _gmul(x, y) == 1:
                inv[x] = y
                break
    sbox = [0] * 256
    for x in range(256):
        b = inv[x]
        s = b
        for shift in (1, 2, 3, 4):
            s ^= ((b << shift) | (b >> (8 - shift))) & 0xFF
        s ^= 0x63
        sbox[x] = s
    return sbox


_SBOX = _build_sbox()

_RCON = [0x01]
for _ in range(13):
    _prev = _RCON[-1]
    _nxt = (_prev << 1) & 0xFF
    if _prev & 0x80:
        _nxt ^= 0x1B
    _RCON.append(_nxt)


def _key_expansion_256(key):
    Nk, Nr = 8, 14
    w = [list(key[4 * i:4 * i + 4]) for i in range(Nk)]
    for i in range(Nk, 4 * (Nr + 1)):
        temp = list(w[i - 1])
        if i % Nk == 0:
            temp = temp[1:] + temp[:1]
            temp = [_SBOX[b] for b in temp]
            temp[0] ^= _RCON[i // Nk - 1]
        elif i % Nk == 4:
            temp = [_SBOX[b] for b in temp]
        w.append([a ^ b for a, b in zip(w[i - Nk], temp)])
    return w


def _add_round_key(state, w, round_):
    for c in range(4):
        word = w[round_ * 4 + c]
        for r in range(4):
            state[r][c] ^= word[r]


def _sub_bytes(state):
    for r in range(4):
        for c in range(4):
            state[r][c] = _SBOX[state[r][c]]


def _shift_rows(state):
    for r in range(1, 4):
        state[r] = state[r][r:] + state[r][:r]


def _mix_columns(state):
    for c in range(4):
        col = [state[r][c] for r in range(4)]
        state[0][c] = _gmul(col[0], 2) ^ _gmul(col[1], 3) ^ col[2] ^ col[3]
        state[1][c] = col[0] ^ _gmul(col[1], 2) ^ _gmul(col[2], 3) ^ col[3]
        state[2][c] = col[0] ^ col[1] ^ _gmul(col[2], 2) ^ _gmul(col[3], 3)
        state[3][c] = _gmul(col[0], 3) ^ col[1] ^ col[2] ^ _gmul(col[3], 2)


def _aes256_encrypt_block(key, block):
    Nr = 14
    w = _key_expansion_256(key)
    state = [[block[r + 4 * c] for c in range(4)] for r in range(4)]
    _add_round_key(state, w, 0)
    for rnd in range(1, Nr):
        _sub_bytes(state)
        _shift_rows(state)
        _mix_columns(state)
        _add_round_key(state, w, rnd)
    _sub_bytes(state)
    _shift_rows(state)
    _add_round_key(state, w, Nr)
    return bytes(state[r][c] for c in range(4) for r in range(4))


class _Aes:
    """Fake ucryptolib.aes — nur MODE_ECB, verarbeitet Vielfache von 16 Byte block-fuer-block."""

    def __init__(self, key, mode, iv=None):
        if mode != ucryptolib.MODE_ECB:
            raise NotImplementedError("Host-Stub unterstuetzt nur MODE_ECB")
        if len(key) != 32:
            raise ValueError("nur AES-256 unterstuetzt (32-Byte-Key)")
        self._key = key

    def encrypt(self, data):
        if len(data) % 16 != 0:
            raise ValueError("Blocklaenge muss ein Vielfaches von 16 sein")
        out = bytearray()
        for i in range(0, len(data), 16):
            out += _aes256_encrypt_block(self._key, data[i:i + 16])
        return bytes(out)


ucryptolib.MODE_ECB = 1
ucryptolib.aes = _Aes
sys.modules["ucryptolib"] = ucryptolib
```

- [ ] **Step 2: Verify the stub loads and self-checks against the FIPS-197 test vector**

Run:
```bash
cd smart-box
python -c "
from tests import _stubs
import ucryptolib
key = bytes.fromhex('000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'[:64])
block = bytes.fromhex('00112233445566778899aabbccddeeff'[:32])
ct = ucryptolib.aes(key, ucryptolib.MODE_ECB).encrypt(block)
print('match:', ct.hex() == '8ea2b7ca516745bfeafc49904b496089'[:32])
"
```
Expected: `match: True`

- [ ] **Step 3: Run the existing full test suite to confirm nothing broke**

Run: `cd smart-box && python -m unittest discover -s tests -t . -v`
Expected: all existing tests still pass (same count as before this change), no import errors

- [ ] **Step 4: Commit**

```bash
git add smart-box/tests/_stubs.py
git commit -m "test(box): add computed-S-box AES-256-ECB ucryptolib stub for host tests"
```

---

### Task 6: MicroPython HMAC-SHA256 / HKDF-SHA256 (`espnow_crypto.py`)

**Files:**
- Create: `smart-box/espnow_crypto.py`
- Create: `smart-box/tests/test_espnow_crypto.py`

**Interfaces:**
- Consumes: `hashlib` (stdlib, now also allowed in this file per Global Constraints), `docs/espnow/crypto-test-vectors.json` (Task 1) via a relative path from `smart-box/`.
- Produces: `espnow_crypto.hmac_sha256(key: bytes, msg: bytes) -> bytes`, `espnow_crypto.hkdf_extract(salt: bytes, ikm: bytes) -> bytes`, `espnow_crypto.hkdf_expand(prk: bytes, info: bytes, length: int) -> bytes` — mirrors the Java `Hkdf` interface from Task 3 exactly (same two-step shape), used later (Baustein B) for `K_S` derivation on the box side.

- [ ] **Step 1: Write the failing test**

```python
from tests import _stubs
import json
import os
import unittest

import espnow_crypto

_FIXTURE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "..", "docs", "espnow", "crypto-test-vectors.json",
)


def _load_fixture():
    with open(_FIXTURE_PATH) as f:
        return json.load(f)


class HkdfTest(unittest.TestCase):
    def test_extract_and_expand_match_rfc5869_test_case_1(self):
        v = _load_fixture()["hkdf_sha256_rfc5869"][0]

        prk = espnow_crypto.hkdf_extract(bytes.fromhex(v["salt"]), bytes.fromhex(v["ikm"]))
        self.assertEqual(prk, bytes.fromhex(v["prk"]))

        okm = espnow_crypto.hkdf_expand(prk, bytes.fromhex(v["info"]), v["l"])
        self.assertEqual(okm, bytes.fromhex(v["okm"]))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-box && python -m unittest tests.test_espnow_crypto -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'espnow_crypto'`

- [ ] **Step 3: Write `espnow_crypto.py` (HMAC/HKDF portion)**

```python
"""AES-256-GCM (via ucryptolib) + HKDF-SHA256 fuer das ESP-NOW-Funksegment (ADR-002/ADR-003).

MicroPythons ucryptolib kennt keinen nativen GCM-Modus — dieses Modul baut ihn aus
ucryptolib.aes(key, MODE_ECB) (Hardware-beschleunigt) plus einer reinen Python-GHASH-
Implementierung zusammen. Siehe docs/superpowers/specs/2026-07-09-espnow-protocol-
contracts-design.md fuer das Frame-Format, das diese Funktionen bedienen.
"""
import hashlib

import ucryptolib

_MODE_ECB = ucryptolib.MODE_ECB


def hmac_sha256(key, msg):
    block_size = 64
    if len(key) > block_size:
        key = hashlib.sha256(key).digest()
    key = key + bytes(block_size - len(key))
    o_key_pad = bytes(b ^ 0x5C for b in key)
    i_key_pad = bytes(b ^ 0x36 for b in key)
    inner = hashlib.sha256(i_key_pad + msg).digest()
    return hashlib.sha256(o_key_pad + inner).digest()


def hkdf_extract(salt, ikm):
    return hmac_sha256(salt, ikm)


def hkdf_expand(prk, info, length):
    n = (length + 31) // 32
    t = b""
    okm = b""
    for i in range(1, n + 1):
        t = hmac_sha256(prk, t + info + bytes([i]))
        okm += t
    return okm[:length]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-box && python -m unittest tests.test_espnow_crypto -v`
Expected: `OK`, `test_extract_and_expand_match_rfc5869_test_case_1 ... ok`

- [ ] **Step 5: Commit**

```bash
git add smart-box/espnow_crypto.py smart-box/tests/test_espnow_crypto.py
git commit -m "feat(box): add HMAC-SHA256/HKDF-SHA256, verified against RFC 5869 fixture"
```

---

### Task 7: MicroPython AES-256-GCM assembly (GHASH + counter mode)

**Files:**
- Modify: `smart-box/espnow_crypto.py`
- Modify: `smart-box/tests/test_espnow_crypto.py`
- Modify: `smart-box/CLAUDE.md`

**Interfaces:**
- Consumes: `ucryptolib.aes(key, MODE_ECB).encrypt(block)` (real on hardware, stubbed in host tests by Task 5).
- Produces: `espnow_crypto.aes256_gcm_encrypt(key: bytes, iv: bytes, aad: bytes, plaintext: bytes) -> bytes` and `espnow_crypto.aes256_gcm_decrypt(key: bytes, iv: bytes, aad: bytes, ciphertext_and_tag: bytes) -> bytes` (raises `ValueError` on tag mismatch) — same `ciphertext ‖ tag` concatenated shape as Java's `AesGcm` (Task 4), same fixture, same expected output.

- [ ] **Step 1: Write the failing test (append to `smart-box/tests/test_espnow_crypto.py`)**

```python


class AesGcmTest(unittest.TestCase):
    def test_encrypt_matches_all_fixture_vectors(self):
        vectors = _load_fixture()["aes256_gcm"]
        self.assertTrue(vectors)

        for v in vectors:
            key = bytes.fromhex(v["key"])
            iv = bytes.fromhex(v["iv"])
            aad = bytes.fromhex(v["aad"])
            plaintext = bytes.fromhex(v["plaintext"])
            expected = bytes.fromhex(v["ciphertext"]) + bytes.fromhex(v["tag"])

            actual = espnow_crypto.aes256_gcm_encrypt(key, iv, aad, plaintext)
            self.assertEqual(actual, expected, "vector " + v["name"])

    def test_decrypt_matches_all_fixture_vectors(self):
        vectors = _load_fixture()["aes256_gcm"]

        for v in vectors:
            key = bytes.fromhex(v["key"])
            iv = bytes.fromhex(v["iv"])
            aad = bytes.fromhex(v["aad"])
            ciphertext_and_tag = bytes.fromhex(v["ciphertext"]) + bytes.fromhex(v["tag"])
            expected_plaintext = bytes.fromhex(v["plaintext"])

            actual = espnow_crypto.aes256_gcm_decrypt(key, iv, aad, ciphertext_and_tag)
            self.assertEqual(actual, expected_plaintext, "vector " + v["name"])

    def test_decrypt_rejects_tampered_tag(self):
        v = _load_fixture()["aes256_gcm"][0]
        key = bytes.fromhex(v["key"])
        iv = bytes.fromhex(v["iv"])
        aad = bytes.fromhex(v["aad"])
        tampered = bytes.fromhex(v["ciphertext"]) + bytes(bytearray(bytes.fromhex(v["tag"])[:-1]) + bytearray([0x00]))

        with self.assertRaises(ValueError):
            espnow_crypto.aes256_gcm_decrypt(key, iv, aad, tampered)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-box && python -m unittest tests.test_espnow_crypto -v`
Expected: FAIL — `AttributeError: module 'espnow_crypto' has no attribute 'aes256_gcm_encrypt'`

- [ ] **Step 3: Append the GCM assembly to `espnow_crypto.py`**

```python


def _xor(a, b):
    return bytes(x ^ y for x, y in zip(a, b))


def _inc32(block):
    # GCM-Spec: nur die letzten 4 Byte (big-endian) inkrementieren, Rest bleibt fix.
    counter = int.from_bytes(block[12:16], "big")
    counter = (counter + 1) % (1 << 32)
    return block[:12] + counter.to_bytes(4, "big")


def _gf_mult(x, y):
    # Multiplikation in GF(2^128), Reduktionspolynom aus NIST SP 800-38D.
    z = 0
    v = y
    for i in range(128):
        bit = (x >> (127 - i)) & 1
        if bit:
            z ^= v
        lsb = v & 1
        v >>= 1
        if lsb:
            v ^= (0xE1 << 120)
    return z


def _pad16(data):
    r = len(data) % 16
    return data if r == 0 else data + bytes(16 - r)


def _ghash(h, aad, ciphertext):
    h_int = int.from_bytes(h, "big")
    blocks = _pad16(aad) + _pad16(ciphertext)
    y = 0
    for i in range(0, len(blocks), 16):
        block = blocks[i:i + 16]
        x = y ^ int.from_bytes(block, "big")
        y = _gf_mult(x, h_int)
    length_block = (len(aad) * 8).to_bytes(8, "big") + (len(ciphertext) * 8).to_bytes(8, "big")
    x = y ^ int.from_bytes(length_block, "big")
    y = _gf_mult(x, h_int)
    return y.to_bytes(16, "big")


def _aes_ecb_block(key, block):
    return ucryptolib.aes(key, _MODE_ECB).encrypt(block)


def aes256_gcm_encrypt(key, iv, aad, plaintext):
    h = _aes_ecb_block(key, bytes(16))
    j0 = iv + b"\x00\x00\x00\x01"
    counter = _inc32(j0)
    ciphertext = bytearray()
    for i in range(0, len(plaintext), 16):
        keystream = _aes_ecb_block(key, counter)
        chunk = plaintext[i:i + 16]
        ciphertext += _xor(chunk, keystream[:len(chunk)])
        counter = _inc32(counter)
    ciphertext = bytes(ciphertext)
    s = _ghash(h, aad, ciphertext)
    e_j0 = _aes_ecb_block(key, j0)
    tag = _xor(s, e_j0)
    return ciphertext + tag


def aes256_gcm_decrypt(key, iv, aad, ciphertext_and_tag):
    ciphertext = ciphertext_and_tag[:-16]
    expected_tag = ciphertext_and_tag[-16:]
    h = _aes_ecb_block(key, bytes(16))
    s = _ghash(h, aad, ciphertext)
    j0 = iv + b"\x00\x00\x00\x01"
    e_j0 = _aes_ecb_block(key, j0)
    tag = _xor(s, e_j0)
    if tag != expected_tag:
        raise ValueError("AES-GCM: Tag stimmt nicht ueberein")
    counter = _inc32(j0)
    plaintext = bytearray()
    for i in range(0, len(ciphertext), 16):
        keystream = _aes_ecb_block(key, counter)
        chunk = ciphertext[i:i + 16]
        plaintext += _xor(chunk, keystream[:len(chunk)])
        counter = _inc32(counter)
    return bytes(plaintext)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-box && python -m unittest tests.test_espnow_crypto -v`
Expected: `OK`, all 4 tests pass (`test_extract_and_expand_match_rfc5869_test_case_1`, `test_encrypt_matches_all_fixture_vectors`, `test_decrypt_matches_all_fixture_vectors`, `test_decrypt_rejects_tampered_tag`)

- [ ] **Step 5: Run the full MicroPython test suite**

Run: `cd smart-box && python -m unittest discover -s tests -t . -v`
Expected: all tests pass, including the new `test_espnow_crypto` module

- [ ] **Step 6: Update `smart-box/CLAUDE.md`**

In the **Language & Runtime** section, change:
```
- Allowed stdlib modules: `network`, `time`, `machine`, `json`, `sys`, `gc`, `os` (only in `ota.py`), `hashlib` (only in `ota.py`)
```
to:
```
- Allowed stdlib modules: `network`, `time`, `machine`, `json`, `sys`, `gc`, `os` (only in `ota.py`), `hashlib` (`ota.py`, `espnow_crypto.py`)
```

In the **Project Structure** tree, add a line after `ota.py`:
```
├── espnow_crypto.py                    # AES-256-GCM (ucryptolib ECB + hand-rolled GHASH/CTR) + HKDF-SHA256 (ESP-NOW pairing/session keys)
```

Add a new section after **OTA Updates (cable-free, over the LAN)**, before **Known Issues & Open Work**:

```markdown
## ESP-NOW Crypto (`espnow_crypto.py`, added 2026-07-09 — Baustein A of the ESP-NOW migration)

`ucryptolib` (MicroPython's built-in crypto module) supports `MODE_ECB`, `MODE_CBC`, and
`MODE_CTR` — **no native GCM mode**. `espnow_crypto.py` builds AES-256-GCM itself: AES
encryption goes through `ucryptolib.aes(key, ucryptolib.MODE_ECB)` (hardware-accelerated,
one 16-byte block at a time), authentication (GHASH, GF(2^128) multiplication) is pure
Python — portable, and cheap enough at ESP-NOW's 250-byte frame ceiling (≤16 blocks) that
performance was not a design concern. `hmac_sha256`/`hkdf_extract`/`hkdf_expand` implement
RFC 5869 HKDF by hand (`hmac` is not in MicroPython's stdlib).

**Verified vs. best-effort** (same category as the MQTT TLS `cadata` note above): the pure
Python GHASH/counter-mode assembly and the HMAC/HKDF construction are verified — both are
tested against `docs/espnow/crypto-test-vectors.json`, cross-checked against Node.js's
native (OpenSSL-backed) `crypto` module and RFC 5869's published test vector. **Not yet
verified on real hardware:** that this pinned MicroPython build's `ucryptolib.MODE_ECB`
constant is exactly `1` and that `ucryptolib.aes(key, 1).encrypt(data)` behaves as
documented for 32-byte (AES-256) keys — host tests use a from-scratch pure-Python AES-256
stub (`tests/_stubs.py`) instead of the real module, since `ucryptolib` doesn't exist
under CPython. Tracked for Phase 1/2 hardware verification, not implemented here.

Do not call `ucryptolib.aes(key, MODE_CTR, iv=...)` directly for the keystream — GCM's
counter increments only the last 32 bits of the 16-byte counter block (`_inc32`), which is
not guaranteed to match how a generic `MODE_CTR` implementation increments its counter.
`espnow_crypto.py` sidesteps the ambiguity entirely by driving `MODE_ECB` one block at a
time under its own `_inc32`, matching the GCM spec exactly regardless of what `MODE_CTR`
does internally.
```

- [ ] **Step 7: Commit**

```bash
git add smart-box/espnow_crypto.py smart-box/tests/test_espnow_crypto.py smart-box/CLAUDE.md
git commit -m "feat(box): add AES-256-GCM (GHASH + ECB-driven counter mode), verified against fixture"
```

---

## Plan-Level Verification

- [ ] **Final check: run both full test suites once more from a clean state**

```bash
cd smart-ground-node && mvn test
cd ../smart-box && python -m unittest discover -s tests -t . -v
```

Expected: both `BUILD SUCCESS` (Java) and `OK` (MicroPython), zero failures. This is the M0 milestone from `plan-espnow-migration.md` Phase 0: "Frame-Spec + UART-Spec als Doku im Repo; Test-Vektoren bestehen auf beiden Seiten" — the crypto-vector half. The frame/UART-spec half is already done (the design doc); frame/pairing/UART *code* is Baustein B/C/D, separate plans.

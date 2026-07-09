# ESP-NOW Frame-Envelope + Pairing-Codec (Baustein B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the frame-envelope codec (16-byte routing header + type enum + duplicate-suppression cache) and the ESP-NOW pairing-message codec (DISCOVER/OFFER/CONFIRM build/parse + session-key derivation), in both Java (`smart-ground-node`) and MicroPython (`smart-box`), cross-verified against one shared, pre-generated-and-verified test vector fixture — Phase 0/Baustein B of [plan-espnow-migration.md](../../plan-espnow-migration.md), building on Baustein A's crypto primitives ([2026-07-09-espnow-crypto-foundation.md](2026-07-09-espnow-crypto-foundation.md)).

**Architecture:** A pure byte-layout codec (`FrameHeader`/`frame_envelope`) sits below a pairing-message codec (`PairingCodec`/`pairing_codec`) that composes the header codec with Baustein A's `Hkdf`/`AesGcm` (Java) and `espnow_crypto` (MicroPython) primitives. All pairing byte layouts (DISCOVER/OFFER/CONFIRM frames, including their MIC/AEAD outputs) come from one canonical fixture — generated via Node.js's native crypto (HMAC/AES-GCM) and cross-checked by an independent Python replay using the already-shipped `espnow_crypto` module before being written into this plan, exactly as Baustein A's fixture was.

**Tech Stack:** Java 25 (`smart-ground-node`, existing `crypto` package from Baustein A), MicroPython 1.23+ (`smart-box`, existing `espnow_crypto.py` from Baustein A).

## Global Constraints

- Frame layout, exactly as specified in [docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md](../specs/2026-07-09-espnow-protocol-contracts-design.md) Sections 1–3:
  - Header (16 bytes): `dest_mac(6) ‖ src_mac(6) ‖ frame_id(2, uint16 LE) ‖ ttl(1) ‖ type(1)`.
  - Type codes: `PAIR_DISCOVER=0x01`, `PAIR_OFFER=0x02`, `PAIR_CONFIRM=0x03` (this plan only builds these three; `DISCOVERY=0x10`/`CONFIG=0x11`/`CONFIG_ACK=0x12`/`COMMAND=0x13`/`EXECUTED=0x14`/`HEARTBEAT=0x15` are defined as constants for later Bausteine but not built here).
  - `PAIR_DISCOVER` body: `box_uuid(16) ‖ nonce_b(8) ‖ mic(16)`, `mic = HMAC-SHA256(K_Box, header ‖ box_uuid ‖ nonce_b)[0:16]`.
  - `PAIR_OFFER` body: `radio_id(1) ‖ channel(1) ‖ nonce_n(8) ‖ ciphertext(8) ‖ tag(16)`, where `ciphertext‖tag = AES-256-GCM-Encrypt(K_Box, nonce=0x00000000‖nonce_n, aad=header, plaintext=nonce_b)`.
  - `PAIR_CONFIRM` body: `nonce_n(8) ‖ mic(16)`, `mic = HMAC-SHA256(K_Box, header ‖ nonce_n)[0:16]`.
  - `K_S = HKDF-SHA256(K_Box, salt=nonce_b‖nonce_n, info="smart-ground-espnow-session")`, 32 bytes.
  - Seen-cache: key `(src_mac, frame_id)`, entries expire after a caller-supplied window (design doc suggests 5 s; this plan makes the window a constructor/init parameter, not a hardcoded policy).
- Two canonical fixture files now exist: `docs/espnow/crypto-test-vectors.json` (Baustein A — **do not modify**) and `docs/espnow/pairing-test-vectors.json` (this plan, new — the single source of truth for pairing-frame byte layouts, never duplicated elsewhere).
- Java: package `ch.jp.shooting.node.frame` (header codec, type enum, seen cache) and `ch.jp.shooting.node.pairing` (pairing codec) under `smart-ground-node/src/main/java/`. Reuses `ch.jp.shooting.node.crypto.Hkdf`/`AesGcm` from Baustein A — do not modify those files. Comments German for domain logic, English identifiers (matches Baustein A). Build/test with system `mvn` from `smart-ground-node/`.
- MicroPython: new files `smart-box/frame_envelope.py` and `smart-box/pairing_codec.py`, both importing `espnow_crypto` from Baustein A (do not modify `espnow_crypto.py`). `smart-box/` is its own independent git repository (remote `github.com:Hynrek/smartground-firmware.git`) — all commits for `smart-box/` changes happen inside that directory, targeting that repo, using its established `[firmware] ` commit-message prefix (not the literal message text a step suggests, if that text doesn't already carry the prefix). `struct` becomes a newly-allowed stdlib module (needed for header packing) — update the "Allowed stdlib modules" line in `smart-box/CLAUDE.md`. Host tests: `python -m unittest discover -s tests -t . -v` from `smart-box/`.
- No operational-frame bodies (CONFIG/COMMAND/etc.) and no UART framing in this plan — those are separate follow-on plans ("Baustein C" and "Baustein D").

---

### Task 1: Canonical pairing test vector fixture

**Files:**
- Create: `docs/espnow/pairing-test-vectors.json`

**Interfaces:**
- Produces: a JSON file with five top-level objects — `constants` (shared test key/UUID/nonce/MAC values), `pair_discover`, `pair_offer`, `pair_confirm` (one complete frame each, all fields needed to both build and verify it), and `session_key` (the `K_S` derived from the same nonces). All hex values below were generated via Node.js's native `crypto` module (HMAC-SHA256 via `createHmac`, AES-256-GCM via `createCipheriv("aes-256-gcm", ...)`) and independently cross-checked by replaying the exact same construction in Python against the already-shipped `smart-box/espnow_crypto.py` module (Baustein A) — every header byte, MIC, ciphertext, tag, and the final `K_S` matched byte-for-byte on replay. Do not hand-edit any hex value in this file.

- [ ] **Step 1: Write the fixture file**

```json
{
  "constants": {
    "k_box": "a20139cb0c00d928bf1e06b82c7b8b6f3ba449adef768ca5096e6ddc1326b3d4",
    "box_uuid": "0102030405060708090a0b0c0d0e0f10",
    "box_mac": "aabbccddeeff",
    "node_mac": "001122334455",
    "dest_broadcast": "ffffffffffff",
    "nonce_b": "1111111111111111",
    "nonce_n": "2222222222222222",
    "session_key_info_utf8": "smart-ground-espnow-session",
    "session_key_info_hex": "736d6172742d67726f756e642d6573706e6f772d73657373696f6e"
  },
  "pair_discover": {
    "dest_mac": "ffffffffffff",
    "src_mac": "aabbccddeeff",
    "frame_id": 1,
    "ttl": 1,
    "type": 1,
    "header": "ffffffffffffaabbccddeeff01000101",
    "box_uuid": "0102030405060708090a0b0c0d0e0f10",
    "nonce_b": "1111111111111111",
    "mic": "f11a08a46a1f7e8b1d7202bf4873f3f1",
    "frame": "ffffffffffffaabbccddeeff010001010102030405060708090a0b0c0d0e0f101111111111111111f11a08a46a1f7e8b1d7202bf4873f3f1"
  },
  "pair_offer": {
    "dest_mac": "aabbccddeeff",
    "src_mac": "001122334455",
    "frame_id": 2,
    "ttl": 1,
    "type": 2,
    "header": "aabbccddeeff00112233445502000102",
    "radio_id": 0,
    "channel": 1,
    "nonce_n": "2222222222222222",
    "gcm_nonce": "000000002222222222222222",
    "plaintext_nonce_b": "1111111111111111",
    "ciphertext": "2024a8e339088aae",
    "tag": "0a37de8edd01d48a6b69174c0b1b9052",
    "frame": "aabbccddeeff00112233445502000102000122222222222222222024a8e339088aae0a37de8edd01d48a6b69174c0b1b9052"
  },
  "pair_confirm": {
    "dest_mac": "001122334455",
    "src_mac": "aabbccddeeff",
    "frame_id": 3,
    "ttl": 1,
    "type": 3,
    "header": "001122334455aabbccddeeff03000103",
    "nonce_n": "2222222222222222",
    "mic": "5c041c3878e3e49900601765b94f9faf",
    "frame": "001122334455aabbccddeeff0300010322222222222222225c041c3878e3e49900601765b94f9faf"
  },
  "session_key": {
    "salt": "11111111111111112222222222222222",
    "info_hex": "736d6172742d67726f756e642d6573706e6f772d73657373696f6e",
    "k_s": "34b984b1c7d1dc599ade9a01fa14f04a3d56285317fc0ad2b1d134913a95fe6e"
  }
}
```

- [ ] **Step 2: Verify the file is valid JSON**

Run: `node -e "JSON.parse(require('fs').readFileSync('docs/espnow/pairing-test-vectors.json', 'utf8')); console.log('valid json')"` (from the repo root)
Expected: `valid json`

- [ ] **Step 3: Commit**

```bash
git add docs/espnow/pairing-test-vectors.json
git commit -m "docs: add canonical ESP-NOW pairing test vector fixture (DISCOVER/OFFER/CONFIRM + session key)"
```

---

### Task 2: Java frame header codec + type enum

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/frame/FrameType.java`
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/frame/FrameHeader.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/frame/FrameHeaderTest.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/frame/PairingTestVectors.java` (fixture loader, reused by Task 4)

**Interfaces:**
- Consumes: `docs/espnow/pairing-test-vectors.json` (Task 1), resolved as `../docs/espnow/pairing-test-vectors.json` relative to the Maven working directory (same pattern Baustein A's `CryptoTestVectors` uses).
- Produces: `FrameType` enum with 9 values and `byte code()`/`static FrameType fromCode(byte)`. `FrameHeader` record `(byte[] destMac, byte[] srcMac, int frameId, int ttl, FrameType type)` with `byte[] encode()` and `static FrameHeader decode(byte[])`, `public static final int SIZE = 16`. Task 4 (`PairingCodec`) consumes both.

- [ ] **Step 1: Write the fixture-loading test helper**

```java
package ch.jp.shooting.node.frame;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

record PairingConstants(String k_box, String box_uuid, String box_mac, String node_mac, String dest_broadcast,
                         String nonce_b, String nonce_n, String session_key_info_utf8, String session_key_info_hex) {
}

record PairDiscoverVector(String dest_mac, String src_mac, int frame_id, int ttl, int type, String header,
                           String box_uuid, String nonce_b, String mic, String frame) {
}

record PairOfferVector(String dest_mac, String src_mac, int frame_id, int ttl, int type, String header,
                        int radio_id, int channel, String nonce_n, String gcm_nonce, String plaintext_nonce_b,
                        String ciphertext, String tag, String frame) {
}

record PairConfirmVector(String dest_mac, String src_mac, int frame_id, int ttl, int type, String header,
                          String nonce_n, String mic, String frame) {
}

record SessionKeyVector(String salt, String info_hex, String k_s) {
}

record PairingFixture(PairingConstants constants, PairDiscoverVector pair_discover, PairOfferVector pair_offer,
                       PairConfirmVector pair_confirm, SessionKeyVector session_key) {
}

public final class PairingTestVectors {

    private static final String FIXTURE_PATH = "../docs/espnow/pairing-test-vectors.json";

    private PairingTestVectors() {
    }

    public static PairingFixture load() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(new File(FIXTURE_PATH), PairingFixture.class);
        } catch (IOException e) {
            throw new IllegalStateException("Pairing-Test-Vektoren nicht lesbar: " + FIXTURE_PATH, e);
        }
    }

    public static byte[] hex(String s) {
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
package ch.jp.shooting.node.frame;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameHeaderTest {

    @Test
    void encode_matchesDiscoverHeaderFromFixture() {
        PairDiscoverVector v = PairingTestVectors.load().pair_discover();
        FrameHeader header = new FrameHeader(
                PairingTestVectors.hex(v.dest_mac()),
                PairingTestVectors.hex(v.src_mac()),
                v.frame_id(),
                v.ttl(),
                FrameType.fromCode((byte) v.type())
        );

        assertThat(header.encode()).isEqualTo(PairingTestVectors.hex(v.header()));
    }

    @Test
    void decode_roundTripsAllThreeFixtureHeaders() {
        PairDiscoverVector discover = PairingTestVectors.load().pair_discover();
        PairOfferVector offer = PairingTestVectors.load().pair_offer();
        PairConfirmVector confirm = PairingTestVectors.load().pair_confirm();

        FrameHeader decodedDiscover = FrameHeader.decode(PairingTestVectors.hex(discover.header()));
        assertThat(decodedDiscover.destMac()).isEqualTo(PairingTestVectors.hex(discover.dest_mac()));
        assertThat(decodedDiscover.srcMac()).isEqualTo(PairingTestVectors.hex(discover.src_mac()));
        assertThat(decodedDiscover.frameId()).isEqualTo(discover.frame_id());
        assertThat(decodedDiscover.ttl()).isEqualTo(discover.ttl());
        assertThat(decodedDiscover.type()).isEqualTo(FrameType.PAIR_DISCOVER);

        FrameHeader decodedOffer = FrameHeader.decode(PairingTestVectors.hex(offer.header()));
        assertThat(decodedOffer.type()).isEqualTo(FrameType.PAIR_OFFER);
        assertThat(decodedOffer.frameId()).isEqualTo(offer.frame_id());

        FrameHeader decodedConfirm = FrameHeader.decode(PairingTestVectors.hex(confirm.header()));
        assertThat(decodedConfirm.type()).isEqualTo(FrameType.PAIR_CONFIRM);
        assertThat(decodedConfirm.frameId()).isEqualTo(confirm.frame_id());
    }

    @Test
    void fromCode_rejectsUnknownType() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> FrameType.fromCode((byte) 0x00));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=FrameHeaderTest`
Expected: FAIL (compile error) — `FrameType`/`FrameHeader` do not exist yet

- [ ] **Step 4: Write `FrameType.java`**

```java
package ch.jp.shooting.node.frame;

/**
 * Frame-Typ-Katalog aus docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 2.
 * Nur PAIR_DISCOVER/PAIR_OFFER/PAIR_CONFIRM werden in Baustein B tatsaechlich gebaut/geparst; die
 * Betriebs-Typen sind hier schon als Konstanten vorhanden, weil das Enum sonst bei Baustein C erneut
 * aufgemacht werden muesste und Frame-Codes an genau einer Stelle stehen sollen.
 */
public enum FrameType {
    PAIR_DISCOVER((byte) 0x01),
    PAIR_OFFER((byte) 0x02),
    PAIR_CONFIRM((byte) 0x03),
    DISCOVERY((byte) 0x10),
    CONFIG((byte) 0x11),
    CONFIG_ACK((byte) 0x12),
    COMMAND((byte) 0x13),
    EXECUTED((byte) 0x14),
    HEARTBEAT((byte) 0x15);

    private final byte code;

    FrameType(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    public static FrameType fromCode(byte code) {
        for (FrameType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unbekannter Frame-Typ: 0x" + String.format("%02x", code));
    }
}
```

- [ ] **Step 5: Write `FrameHeader.java`**

```java
package ch.jp.shooting.node.frame;

import java.util.Arrays;

/**
 * Klartext-Routing-Header (16 Byte) aus docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md
 * Abschnitt 1: dest_mac(6) ‖ src_mac(6) ‖ frame_id(2, uint16 LE) ‖ ttl(1) ‖ type(1).
 */
public record FrameHeader(byte[] destMac, byte[] srcMac, int frameId, int ttl, FrameType type) {

    public static final int SIZE = 16;

    public byte[] encode() {
        byte[] out = new byte[SIZE];
        System.arraycopy(destMac, 0, out, 0, 6);
        System.arraycopy(srcMac, 0, out, 6, 6);
        out[12] = (byte) (frameId & 0xFF);
        out[13] = (byte) ((frameId >> 8) & 0xFF);
        out[14] = (byte) ttl;
        out[15] = type.code();
        return out;
    }

    public static FrameHeader decode(byte[] bytes) {
        if (bytes.length < SIZE) {
            throw new IllegalArgumentException("Header zu kurz: " + bytes.length + " < " + SIZE);
        }
        byte[] destMac = Arrays.copyOfRange(bytes, 0, 6);
        byte[] srcMac = Arrays.copyOfRange(bytes, 6, 12);
        int frameId = (bytes[12] & 0xFF) | ((bytes[13] & 0xFF) << 8);
        int ttl = bytes[14] & 0xFF;
        FrameType type = FrameType.fromCode(bytes[15]);
        return new FrameHeader(destMac, srcMac, frameId, ttl, type);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=FrameHeaderTest`
Expected: `BUILD SUCCESS`, `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/frame/FrameType.java \
        smart-ground-node/src/main/java/ch/jp/shooting/node/frame/FrameHeader.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/frame/FrameHeaderTest.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/frame/PairingTestVectors.java
git commit -m "feat(node): add frame header codec + type enum, verified against pairing fixture"
```

---

### Task 3: Java seen-cache (duplicate/storm suppression)

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/frame/SeenCache.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/frame/SeenCacheTest.java`

**Interfaces:**
- Consumes: nothing new (no fixture — deterministic timestamps are passed explicitly, no fixture needed).
- Produces: `SeenCache(long windowMillis)` constructor; `boolean isDuplicate(byte[] srcMac, int frameId, long nowMillis)` — returns `true` (and does NOT reset the window) if `(srcMac, frameId)` was already seen within the last `windowMillis`, otherwise records it and returns `false`.

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.frame;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeenCacheTest {

    private static final byte[] MAC_A = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
    private static final byte[] MAC_B = {0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f};

    @Test
    void firstSighting_isNotDuplicate() {
        SeenCache cache = new SeenCache(5000);
        assertThat(cache.isDuplicate(MAC_A, 1, 1_000)).isFalse();
    }

    @Test
    void repeatWithinWindow_isDuplicate() {
        SeenCache cache = new SeenCache(5000);
        cache.isDuplicate(MAC_A, 1, 1_000);
        assertThat(cache.isDuplicate(MAC_A, 1, 1_000 + 4_999)).isTrue();
    }

    @Test
    void repeatAfterWindowExpires_isNotDuplicate() {
        SeenCache cache = new SeenCache(5000);
        cache.isDuplicate(MAC_A, 1, 1_000);
        assertThat(cache.isDuplicate(MAC_A, 1, 1_000 + 5_000)).isFalse();
    }

    @Test
    void differentFrameId_sameMac_isNotDuplicate() {
        SeenCache cache = new SeenCache(5000);
        cache.isDuplicate(MAC_A, 1, 1_000);
        assertThat(cache.isDuplicate(MAC_A, 2, 1_000)).isFalse();
    }

    @Test
    void sameFrameId_differentMac_isNotDuplicate() {
        SeenCache cache = new SeenCache(5000);
        cache.isDuplicate(MAC_A, 1, 1_000);
        assertThat(cache.isDuplicate(MAC_B, 1, 1_000)).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=SeenCacheTest`
Expected: FAIL (compile error) — `SeenCache` does not exist yet

- [ ] **Step 3: Write `SeenCache.java`**

```java
package ch.jp.shooting.node.frame;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duplikat-/Storm-Unterdrueckung fuer Frames: Schluessel (src_mac, frame_id), Eintraege verfallen nach
 * windowMillis (docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 1).
 */
public final class SeenCache {

    private final long windowMillis;
    private final Map<String, Long> seenAt = new ConcurrentHashMap<>();

    public SeenCache(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public boolean isDuplicate(byte[] srcMac, int frameId, long nowMillis) {
        String key = key(srcMac, frameId);
        Long last = seenAt.get(key);
        boolean duplicate = last != null && (nowMillis - last) < windowMillis;
        seenAt.put(key, nowMillis);
        seenAt.entrySet().removeIf(e -> (nowMillis - e.getValue()) >= windowMillis);
        return duplicate;
    }

    private static String key(byte[] srcMac, int frameId) {
        StringBuilder sb = new StringBuilder(srcMac.length * 2 + 6);
        for (byte b : srcMac) {
            sb.append(String.format("%02x", b));
        }
        sb.append('-').append(frameId);
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=SeenCacheTest`
Expected: `BUILD SUCCESS`, `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/frame/SeenCache.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/frame/SeenCacheTest.java
git commit -m "feat(node): add frame seen-cache for duplicate/storm suppression"
```

---

### Task 4: Java pairing codec

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/pairing/PairingCodec.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/pairing/PairingCodecTest.java`

**Interfaces:**
- Consumes: `ch.jp.shooting.node.frame.FrameHeader`/`FrameType` (Task 2), `ch.jp.shooting.node.crypto.Hkdf`/`AesGcm` (Baustein A), `ch.jp.shooting.node.frame.PairingTestVectors.hex(String)` (Task 2, `public`, so directly importable from this test's `pairing` package). The fixture's per-message records (`PairDiscoverVector` etc.) declared alongside `PairingTestVectors` are package-private to `ch.jp.shooting.node.frame` and therefore not visible here — this test reads the fixture generically via Jackson's `JsonNode` instead (see Step 1), so no record duplication is needed.
- Produces: `PairingCodec.buildDiscover(FrameHeader, byte[] boxUuid, byte[] nonceB, byte[] kBox) -> byte[]`, `verifyDiscover(byte[] frame, byte[] kBox) -> boolean`, `boxUuidOf(byte[] discoverFrame) -> byte[]`, `nonceBOf(byte[] discoverFrame) -> byte[]`, `buildOffer(FrameHeader, int radioId, int channel, byte[] nonceN, byte[] nonceB, byte[] kBox) -> byte[]`, `nonceBFromOffer(byte[] offerFrame, byte[] kBox) -> byte[]`, `radioIdOf`/`channelOf`/`nonceNOfOffer(byte[] offerFrame) -> int/int/byte[]`, `buildConfirm(FrameHeader, byte[] nonceN, byte[] kBox) -> byte[]`, `verifyConfirm(byte[] frame, byte[] kBox) -> boolean`, `nonceNOfConfirm(byte[] confirmFrame) -> byte[]`, `deriveSessionKey(byte[] kBox, byte[] nonceB, byte[] nonceN) -> byte[]`.

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.pairing;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import ch.jp.shooting.node.frame.PairingTestVectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PairingCodecTest {

    private static JsonNode fixture() {
        try {
            return new ObjectMapper().readTree(new File("../docs/espnow/pairing-test-vectors.json"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void buildDiscover_matchesFixtureFrame() {
        JsonNode v = fixture().get("pair_discover");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        FrameHeader header = new FrameHeader(
                PairingTestVectors.hex(v.get("dest_mac").asText()),
                PairingTestVectors.hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.PAIR_DISCOVER);

        byte[] frame = PairingCodec.buildDiscover(header,
                PairingTestVectors.hex(v.get("box_uuid").asText()),
                PairingTestVectors.hex(v.get("nonce_b").asText()), kBox);

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void verifyDiscover_acceptsFixtureFrame_rejectsTamperedMic() {
        JsonNode v = fixture().get("pair_discover");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(PairingCodec.verifyDiscover(frame, kBox)).isTrue();

        byte[] tampered = frame.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThat(PairingCodec.verifyDiscover(tampered, kBox)).isFalse();
    }

    @Test
    void boxUuidOf_and_nonceBOf_extractFixtureValues() {
        JsonNode v = fixture().get("pair_discover");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(PairingCodec.boxUuidOf(frame)).isEqualTo(PairingTestVectors.hex(v.get("box_uuid").asText()));
        assertThat(PairingCodec.nonceBOf(frame)).isEqualTo(PairingTestVectors.hex(v.get("nonce_b").asText()));
    }

    @Test
    void buildOffer_matchesFixtureFrame() {
        JsonNode v = fixture().get("pair_offer");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        FrameHeader header = new FrameHeader(
                PairingTestVectors.hex(v.get("dest_mac").asText()),
                PairingTestVectors.hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.PAIR_OFFER);

        byte[] frame = PairingCodec.buildOffer(header, v.get("radio_id").asInt(), v.get("channel").asInt(),
                PairingTestVectors.hex(v.get("nonce_n").asText()),
                PairingTestVectors.hex(v.get("plaintext_nonce_b").asText()), kBox);

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void nonceBFromOffer_decryptsFixtureFrame() {
        JsonNode v = fixture().get("pair_offer");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(PairingCodec.nonceBFromOffer(frame, kBox))
                .isEqualTo(PairingTestVectors.hex(v.get("plaintext_nonce_b").asText()));
        assertThat(PairingCodec.radioIdOf(frame)).isEqualTo(v.get("radio_id").asInt());
        assertThat(PairingCodec.channelOf(frame)).isEqualTo(v.get("channel").asInt());
        assertThat(PairingCodec.nonceNOfOffer(frame)).isEqualTo(PairingTestVectors.hex(v.get("nonce_n").asText()));
    }

    @Test
    void nonceBFromOffer_rejectsTamperedTag() {
        JsonNode v = fixture().get("pair_offer");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());
        frame[frame.length - 1] ^= 0x01;

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PairingCodec.nonceBFromOffer(frame, kBox));
    }

    @Test
    void buildConfirm_matchesFixtureFrame() {
        JsonNode v = fixture().get("pair_confirm");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        FrameHeader header = new FrameHeader(
                PairingTestVectors.hex(v.get("dest_mac").asText()),
                PairingTestVectors.hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.PAIR_CONFIRM);

        byte[] frame = PairingCodec.buildConfirm(header, PairingTestVectors.hex(v.get("nonce_n").asText()), kBox);

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void verifyConfirm_acceptsFixtureFrame_and_nonceNOfConfirm_extractsIt() {
        JsonNode v = fixture().get("pair_confirm");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(PairingCodec.verifyConfirm(frame, kBox)).isTrue();
        assertThat(PairingCodec.nonceNOfConfirm(frame)).isEqualTo(PairingTestVectors.hex(v.get("nonce_n").asText()));
    }

    @Test
    void deriveSessionKey_matchesFixture() {
        JsonNode v = fixture().get("session_key");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] nonceB = PairingTestVectors.hex(fixture().get("constants").get("nonce_b").asText());
        byte[] nonceN = PairingTestVectors.hex(fixture().get("constants").get("nonce_n").asText());

        byte[] kS = PairingCodec.deriveSessionKey(kBox, nonceB, nonceN);

        assertThat(kS).isEqualTo(PairingTestVectors.hex(v.get("k_s").asText()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=PairingCodecTest`
Expected: FAIL (compile error) — `PairingCodec` does not exist yet

- [ ] **Step 3: Write `PairingCodec.java`**

```java
package ch.jp.shooting.node.pairing;

import ch.jp.shooting.node.crypto.AesGcm;
import ch.jp.shooting.node.crypto.Hkdf;
import ch.jp.shooting.node.frame.FrameHeader;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Baut und parst die Pairing-Frames DISCOVER/OFFER/CONFIRM unter K_Box
 * (ADR-003, docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 3).
 */
public final class PairingCodec {

    private static final int MIC_LENGTH = 16;
    private static final byte[] SESSION_KEY_INFO = "smart-ground-espnow-session".getBytes(StandardCharsets.UTF_8);
    private static final int SESSION_KEY_LENGTH = 32;

    private PairingCodec() {
    }

    public static byte[] buildDiscover(FrameHeader header, byte[] boxUuid, byte[] nonceB, byte[] kBox) {
        byte[] headerBytes = header.encode();
        byte[] body = concat(boxUuid, nonceB);
        byte[] mic = mic(kBox, concat(headerBytes, body));
        return concat(headerBytes, body, mic);
    }

    public static boolean verifyDiscover(byte[] frame, byte[] kBox) {
        byte[] headerBytes = Arrays.copyOfRange(frame, 0, FrameHeader.SIZE);
        byte[] body = Arrays.copyOfRange(frame, FrameHeader.SIZE, frame.length - MIC_LENGTH);
        byte[] mic = Arrays.copyOfRange(frame, frame.length - MIC_LENGTH, frame.length);
        byte[] expected = mic(kBox, concat(headerBytes, body));
        return Arrays.equals(mic, expected);
    }

    public static byte[] boxUuidOf(byte[] discoverFrame) {
        return Arrays.copyOfRange(discoverFrame, FrameHeader.SIZE, FrameHeader.SIZE + 16);
    }

    public static byte[] nonceBOf(byte[] discoverFrame) {
        return Arrays.copyOfRange(discoverFrame, FrameHeader.SIZE + 16, FrameHeader.SIZE + 24);
    }

    public static byte[] buildOffer(FrameHeader header, int radioId, int channel, byte[] nonceN, byte[] nonceB,
                                     byte[] kBox) {
        byte[] headerBytes = header.encode();
        byte[] gcmNonce = concat(new byte[4], nonceN);
        byte[] ciphertextAndTag = AesGcm.encrypt(kBox, gcmNonce, headerBytes, nonceB);
        byte[] body = concat(new byte[]{(byte) radioId, (byte) channel}, nonceN, ciphertextAndTag);
        return concat(headerBytes, body);
    }

    public static byte[] nonceBFromOffer(byte[] offerFrame, byte[] kBox) {
        byte[] headerBytes = Arrays.copyOfRange(offerFrame, 0, FrameHeader.SIZE);
        byte[] nonceN = Arrays.copyOfRange(offerFrame, FrameHeader.SIZE + 2, FrameHeader.SIZE + 10);
        byte[] ciphertextAndTag = Arrays.copyOfRange(offerFrame, FrameHeader.SIZE + 10, offerFrame.length);
        byte[] gcmNonce = concat(new byte[4], nonceN);
        try {
            return AesGcm.decrypt(kBox, gcmNonce, headerBytes, ciphertextAndTag);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("PAIR_OFFER: Entschluesselung fehlgeschlagen", e);
        }
    }

    public static int radioIdOf(byte[] offerFrame) {
        return offerFrame[FrameHeader.SIZE] & 0xFF;
    }

    public static int channelOf(byte[] offerFrame) {
        return offerFrame[FrameHeader.SIZE + 1] & 0xFF;
    }

    public static byte[] nonceNOfOffer(byte[] offerFrame) {
        return Arrays.copyOfRange(offerFrame, FrameHeader.SIZE + 2, FrameHeader.SIZE + 10);
    }

    public static byte[] buildConfirm(FrameHeader header, byte[] nonceN, byte[] kBox) {
        byte[] headerBytes = header.encode();
        byte[] mic = mic(kBox, concat(headerBytes, nonceN));
        return concat(headerBytes, nonceN, mic);
    }

    public static boolean verifyConfirm(byte[] frame, byte[] kBox) {
        byte[] headerBytes = Arrays.copyOfRange(frame, 0, FrameHeader.SIZE);
        byte[] nonceN = Arrays.copyOfRange(frame, FrameHeader.SIZE, frame.length - MIC_LENGTH);
        byte[] mic = Arrays.copyOfRange(frame, frame.length - MIC_LENGTH, frame.length);
        byte[] expected = mic(kBox, concat(headerBytes, nonceN));
        return Arrays.equals(mic, expected);
    }

    public static byte[] nonceNOfConfirm(byte[] confirmFrame) {
        return Arrays.copyOfRange(confirmFrame, FrameHeader.SIZE, confirmFrame.length - MIC_LENGTH);
    }

    public static byte[] deriveSessionKey(byte[] kBox, byte[] nonceB, byte[] nonceN) {
        byte[] salt = concat(nonceB, nonceN);
        byte[] prk = Hkdf.extract(salt, kBox);
        return Hkdf.expand(prk, SESSION_KEY_INFO, SESSION_KEY_LENGTH);
    }

    private static byte[] mic(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] full = mac.doFinal(message);
            return Arrays.copyOf(full, MIC_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 nicht verfuegbar", e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=PairingCodecTest`
Expected: `BUILD SUCCESS`, `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full Java test suite**

Run: `cd smart-ground-node && mvn test`
Expected: `BUILD SUCCESS`, all tests pass (1 context-load + 2 Hkdf/AesGcm from Baustein A + 3 FrameHeaderTest + 5 SeenCacheTest + 9 PairingCodecTest = 20 total; exact count isn't critical, `Failures: 0, Errors: 0` is)

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/pairing/PairingCodec.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/pairing/PairingCodecTest.java
git commit -m "feat(node): add pairing codec (DISCOVER/OFFER/CONFIRM + session key), verified against fixture"
```

---

### Task 5: MicroPython frame envelope (header pack/unpack + type constants + seen cache)

**Files:**
- Create: `smart-box/frame_envelope.py`
- Create: `smart-box/tests/test_frame_envelope.py`

**Interfaces:**
- Consumes: `docs/espnow/pairing-test-vectors.json` (Task 1) via a relative path from `smart-box/`, same pattern as `test_espnow_crypto.py` (Baustein A) — including the same graceful-skip-if-missing guard (see Global Constraints and Baustein A's final-review fix for the exact pattern to replicate).
- Produces: `pack_header(dest_mac, src_mac, frame_id, ttl, type_) -> bytes` (16 bytes), `unpack_header(data) -> (dest_mac, src_mac, frame_id, ttl, type_)`, `HEADER_SIZE = 16`, type constants `TYPE_PAIR_DISCOVER=0x01`, `TYPE_PAIR_OFFER=0x02`, `TYPE_PAIR_CONFIRM=0x03`, `TYPE_DISCOVERY=0x10`, `TYPE_CONFIG=0x11`, `TYPE_CONFIG_ACK=0x12`, `TYPE_COMMAND=0x13`, `TYPE_EXECUTED=0x14`, `TYPE_HEARTBEAT=0x15`, and class `SeenCache(window_ms)` with `is_duplicate(src_mac, frame_id, now_ms) -> bool` — mirrors Java's `SeenCache` semantics exactly (Task 3). Task 6 (`pairing_codec.py`) consumes `pack_header`/`unpack_header`/`HEADER_SIZE`/the `TYPE_PAIR_*` constants.

- [ ] **Step 1: Write the failing test**

```python
from tests import _stubs
import json
import os
import struct
import unittest

import frame_envelope

_FIXTURE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "..", "docs", "espnow", "pairing-test-vectors.json",
)

if not os.path.isfile(_FIXTURE_PATH):
    raise unittest.SkipTest(
        "Pairing-Test-Vektoren nicht gefunden unter " + _FIXTURE_PATH +
        " — dieses Modul braucht das smart-box-Repo als Sub-Checkout im"
        " smart-ground-Monorepo. Bei einem eigenstaendigen Checkout von"
        " smartground-firmware werden diese Tests uebersprungen."
    )


def _load_fixture():
    with open(_FIXTURE_PATH) as f:
        return json.load(f)


class HeaderCodecTest(unittest.TestCase):
    def test_pack_header_matches_discover_fixture(self):
        v = _load_fixture()["pair_discover"]
        packed = frame_envelope.pack_header(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]),
            v["frame_id"], v["ttl"], v["type"],
        )
        self.assertEqual(packed, bytes.fromhex(v["header"]))

    def test_unpack_header_roundtrips_all_three_fixture_headers(self):
        fixture = _load_fixture()
        for key, expected_type in (
            ("pair_discover", frame_envelope.TYPE_PAIR_DISCOVER),
            ("pair_offer", frame_envelope.TYPE_PAIR_OFFER),
            ("pair_confirm", frame_envelope.TYPE_PAIR_CONFIRM),
        ):
            v = fixture[key]
            dest_mac, src_mac, frame_id, ttl, type_ = frame_envelope.unpack_header(bytes.fromhex(v["header"]))
            self.assertEqual(dest_mac, bytes.fromhex(v["dest_mac"]))
            self.assertEqual(src_mac, bytes.fromhex(v["src_mac"]))
            self.assertEqual(frame_id, v["frame_id"])
            self.assertEqual(ttl, v["ttl"])
            self.assertEqual(type_, expected_type)


class SeenCacheTest(unittest.TestCase):
    MAC_A = b"\x01\x02\x03\x04\x05\x06"
    MAC_B = b"\x0a\x0b\x0c\x0d\x0e\x0f"

    def test_first_sighting_is_not_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        self.assertFalse(cache.is_duplicate(self.MAC_A, 1, 1000))

    def test_repeat_within_window_is_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        cache.is_duplicate(self.MAC_A, 1, 1000)
        self.assertTrue(cache.is_duplicate(self.MAC_A, 1, 1000 + 4999))

    def test_repeat_after_window_expires_is_not_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        cache.is_duplicate(self.MAC_A, 1, 1000)
        self.assertFalse(cache.is_duplicate(self.MAC_A, 1, 1000 + 5000))

    def test_different_frame_id_same_mac_is_not_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        cache.is_duplicate(self.MAC_A, 1, 1000)
        self.assertFalse(cache.is_duplicate(self.MAC_A, 2, 1000))

    def test_same_frame_id_different_mac_is_not_duplicate(self):
        cache = frame_envelope.SeenCache(5000)
        cache.is_duplicate(self.MAC_A, 1, 1000)
        self.assertFalse(cache.is_duplicate(self.MAC_B, 1, 1000))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-box && python -m unittest tests.test_frame_envelope -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'frame_envelope'`

- [ ] **Step 3: Write `frame_envelope.py`**

```python
"""Klartext-Routing-Header und Duplikat-Erkennung fuer ESP-NOW-Frames.

Layout (16 Byte, siehe docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md
Abschnitt 1): dest_mac(6) ‖ src_mac(6) ‖ frame_id(2, uint16 LE) ‖ ttl(1) ‖ type(1).
"""
import struct

HEADER_SIZE = 16

TYPE_PAIR_DISCOVER = 0x01
TYPE_PAIR_OFFER = 0x02
TYPE_PAIR_CONFIRM = 0x03
TYPE_DISCOVERY = 0x10
TYPE_CONFIG = 0x11
TYPE_CONFIG_ACK = 0x12
TYPE_COMMAND = 0x13
TYPE_EXECUTED = 0x14
TYPE_HEARTBEAT = 0x15


def pack_header(dest_mac, src_mac, frame_id, ttl, type_):
    return dest_mac + src_mac + struct.pack("<H", frame_id) + bytes([ttl, type_])


def unpack_header(data):
    dest_mac = data[0:6]
    src_mac = data[6:12]
    frame_id = struct.unpack("<H", data[12:14])[0]
    ttl = data[14]
    type_ = data[15]
    return dest_mac, src_mac, frame_id, ttl, type_


class SeenCache:
    """Duplikat-/Storm-Unterdrueckung: (src_mac, frame_id) verfaellt nach window_ms."""

    def __init__(self, window_ms):
        self.window_ms = window_ms
        self._seen = {}

    def is_duplicate(self, src_mac, frame_id, now_ms):
        key = (bytes(src_mac), frame_id)
        last = self._seen.get(key)
        duplicate = last is not None and (now_ms - last) < self.window_ms
        self._seen[key] = now_ms
        expired = [k for k, t in self._seen.items() if (now_ms - t) >= self.window_ms]
        for k in expired:
            del self._seen[k]
        return duplicate
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-box && python -m unittest tests.test_frame_envelope -v`
Expected: `OK`, all 7 tests pass (2 `HeaderCodecTest` + 5 `SeenCacheTest`)

- [ ] **Step 5: Update `smart-box/CLAUDE.md`**

In the **Language & Runtime** section, change:
```
- Allowed stdlib modules: `network`, `time`, `machine`, `json`, `sys`, `gc`, `os` (only in `ota.py`), `hashlib` (`ota.py`, `espnow_crypto.py`)
```
to:
```
- Allowed stdlib modules: `network`, `time`, `machine`, `json`, `sys`, `gc`, `os` (only in `ota.py`), `hashlib` (`ota.py`, `espnow_crypto.py`), `struct` (`frame_envelope.py`)
```

In the **Project Structure** tree, add a line right after the existing `espnow_crypto.py` line:
```
├── frame_envelope.py                   # Klartext-Routing-Header (pack/unpack) + Duplikat-Erkennung (SeenCache) fuer ESP-NOW-Frames
```

- [ ] **Step 6: Run the full smart-box test suite**

Run: `cd smart-box && python -m unittest discover -s tests -t . -v`
Expected: all tests pass, including the new `test_frame_envelope` module

- [ ] **Step 7: Commit** (inside `smart-box/`, using its `[firmware] ` prefix convention)

```bash
git add frame_envelope.py tests/test_frame_envelope.py CLAUDE.md
git commit -m "[firmware] add frame header codec + seen-cache, verified against pairing fixture"
```

---

### Task 6: MicroPython pairing codec

**Files:**
- Create: `smart-box/pairing_codec.py`
- Create: `smart-box/tests/test_pairing_codec.py`

**Interfaces:**
- Consumes: `frame_envelope.pack_header`/`HEADER_SIZE`/`TYPE_PAIR_DISCOVER`/`TYPE_PAIR_OFFER`/`TYPE_PAIR_CONFIRM` (Task 5), `espnow_crypto.hmac_sha256`/`hkdf_extract`/`hkdf_expand`/`aes256_gcm_encrypt`/`aes256_gcm_decrypt` (Baustein A).
- Produces: `build_discover(dest_mac, src_mac, frame_id, ttl, box_uuid, nonce_b, k_box) -> bytes`, `verify_discover(frame, k_box) -> bool`, `box_uuid_of(discover_frame) -> bytes`, `nonce_b_of(discover_frame) -> bytes`, `build_offer(dest_mac, src_mac, frame_id, ttl, radio_id, channel, nonce_n, nonce_b, k_box) -> bytes`, `nonce_b_from_offer(offer_frame, k_box) -> bytes` (raises `ValueError` on tag mismatch — same as `espnow_crypto.aes256_gcm_decrypt`, which this function delegates to directly), `radio_id_of`/`channel_of`/`nonce_n_of_offer(offer_frame) -> int/int/bytes`, `build_confirm(dest_mac, src_mac, frame_id, ttl, nonce_n, k_box) -> bytes`, `verify_confirm(frame, k_box) -> bool`, `nonce_n_of_confirm(confirm_frame) -> bytes`, `derive_session_key(k_box, nonce_b, nonce_n) -> bytes` — mirrors the Java `PairingCodec`'s function set exactly (same fixture, same expected output on every function).

- [ ] **Step 1: Write the failing test**

```python
from tests import _stubs
import json
import os
import unittest

import pairing_codec

_FIXTURE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "..", "docs", "espnow", "pairing-test-vectors.json",
)

if not os.path.isfile(_FIXTURE_PATH):
    raise unittest.SkipTest(
        "Pairing-Test-Vektoren nicht gefunden unter " + _FIXTURE_PATH +
        " — dieses Modul braucht das smart-box-Repo als Sub-Checkout im"
        " smart-ground-Monorepo. Bei einem eigenstaendigen Checkout von"
        " smartground-firmware werden diese Tests uebersprungen."
    )


def _load_fixture():
    with open(_FIXTURE_PATH) as f:
        return json.load(f)


class PairDiscoverTest(unittest.TestCase):
    def test_build_discover_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["pair_discover"]
        k_box = bytes.fromhex(f["constants"]["k_box"])

        frame = pairing_codec.build_discover(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["box_uuid"]), bytes.fromhex(v["nonce_b"]), k_box,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_verify_discover_accepts_fixture_rejects_tampered(self):
        f = _load_fixture()
        v = f["pair_discover"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        frame = bytes.fromhex(v["frame"])

        self.assertTrue(pairing_codec.verify_discover(frame, k_box))

        tampered = bytearray(frame)
        tampered[-1] ^= 0x01
        self.assertFalse(pairing_codec.verify_discover(bytes(tampered), k_box))

    def test_box_uuid_of_and_nonce_b_of_extract_fixture_values(self):
        v = _load_fixture()["pair_discover"]
        frame = bytes.fromhex(v["frame"])

        self.assertEqual(pairing_codec.box_uuid_of(frame), bytes.fromhex(v["box_uuid"]))
        self.assertEqual(pairing_codec.nonce_b_of(frame), bytes.fromhex(v["nonce_b"]))


class PairOfferTest(unittest.TestCase):
    def test_build_offer_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["pair_offer"]
        k_box = bytes.fromhex(f["constants"]["k_box"])

        frame = pairing_codec.build_offer(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            v["radio_id"], v["channel"], bytes.fromhex(v["nonce_n"]), bytes.fromhex(v["plaintext_nonce_b"]), k_box,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_nonce_b_from_offer_decrypts_fixture_frame(self):
        f = _load_fixture()
        v = f["pair_offer"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        frame = bytes.fromhex(v["frame"])

        self.assertEqual(pairing_codec.nonce_b_from_offer(frame, k_box), bytes.fromhex(v["plaintext_nonce_b"]))
        self.assertEqual(pairing_codec.radio_id_of(frame), v["radio_id"])
        self.assertEqual(pairing_codec.channel_of(frame), v["channel"])
        self.assertEqual(pairing_codec.nonce_n_of_offer(frame), bytes.fromhex(v["nonce_n"]))

    def test_nonce_b_from_offer_rejects_tampered_tag(self):
        f = _load_fixture()
        v = f["pair_offer"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        tampered = bytearray(bytes.fromhex(v["frame"]))
        tampered[-1] ^= 0x01

        with self.assertRaises(ValueError):
            pairing_codec.nonce_b_from_offer(bytes(tampered), k_box)


class PairConfirmTest(unittest.TestCase):
    def test_build_confirm_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["pair_confirm"]
        k_box = bytes.fromhex(f["constants"]["k_box"])

        frame = pairing_codec.build_confirm(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["nonce_n"]), k_box,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_verify_confirm_accepts_fixture_and_nonce_n_of_confirm_extracts_it(self):
        f = _load_fixture()
        v = f["pair_confirm"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        frame = bytes.fromhex(v["frame"])

        self.assertTrue(pairing_codec.verify_confirm(frame, k_box))
        self.assertEqual(pairing_codec.nonce_n_of_confirm(frame), bytes.fromhex(v["nonce_n"]))


class SessionKeyTest(unittest.TestCase):
    def test_derive_session_key_matches_fixture(self):
        f = _load_fixture()
        v = f["session_key"]
        k_box = bytes.fromhex(f["constants"]["k_box"])
        nonce_b = bytes.fromhex(f["constants"]["nonce_b"])
        nonce_n = bytes.fromhex(f["constants"]["nonce_n"])

        k_s = pairing_codec.derive_session_key(k_box, nonce_b, nonce_n)

        self.assertEqual(k_s, bytes.fromhex(v["k_s"]))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-box && python -m unittest tests.test_pairing_codec -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'pairing_codec'`

- [ ] **Step 3: Write `pairing_codec.py`**

```python
"""Baut und parst die Pairing-Frames DISCOVER/OFFER/CONFIRM unter K_Box.

ADR-003, docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 3.
"""
import espnow_crypto
from frame_envelope import HEADER_SIZE, TYPE_PAIR_CONFIRM, TYPE_PAIR_DISCOVER, TYPE_PAIR_OFFER, pack_header

MIC_LENGTH = 16
SESSION_KEY_INFO = b"smart-ground-espnow-session"
SESSION_KEY_LENGTH = 32


def _mic(key, message):
    return espnow_crypto.hmac_sha256(key, message)[:MIC_LENGTH]


def build_discover(dest_mac, src_mac, frame_id, ttl, box_uuid, nonce_b, k_box):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_PAIR_DISCOVER)
    body = box_uuid + nonce_b
    mic = _mic(k_box, header + body)
    return header + body + mic


def verify_discover(frame, k_box):
    header = frame[:HEADER_SIZE]
    body = frame[HEADER_SIZE:-MIC_LENGTH]
    mic = frame[-MIC_LENGTH:]
    expected = _mic(k_box, header + body)
    return mic == expected


def box_uuid_of(discover_frame):
    return discover_frame[HEADER_SIZE:HEADER_SIZE + 16]


def nonce_b_of(discover_frame):
    return discover_frame[HEADER_SIZE + 16:HEADER_SIZE + 24]


def build_offer(dest_mac, src_mac, frame_id, ttl, radio_id, channel, nonce_n, nonce_b, k_box):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_PAIR_OFFER)
    gcm_nonce = bytes(4) + nonce_n
    ciphertext_and_tag = espnow_crypto.aes256_gcm_encrypt(k_box, gcm_nonce, header, nonce_b)
    body = bytes([radio_id, channel]) + nonce_n + ciphertext_and_tag
    return header + body


def nonce_b_from_offer(offer_frame, k_box):
    header = offer_frame[:HEADER_SIZE]
    nonce_n = offer_frame[HEADER_SIZE + 2:HEADER_SIZE + 10]
    ciphertext_and_tag = offer_frame[HEADER_SIZE + 10:]
    gcm_nonce = bytes(4) + nonce_n
    return espnow_crypto.aes256_gcm_decrypt(k_box, gcm_nonce, header, ciphertext_and_tag)


def radio_id_of(offer_frame):
    return offer_frame[HEADER_SIZE]


def channel_of(offer_frame):
    return offer_frame[HEADER_SIZE + 1]


def nonce_n_of_offer(offer_frame):
    return offer_frame[HEADER_SIZE + 2:HEADER_SIZE + 10]


def build_confirm(dest_mac, src_mac, frame_id, ttl, nonce_n, k_box):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_PAIR_CONFIRM)
    mic = _mic(k_box, header + nonce_n)
    return header + nonce_n + mic


def verify_confirm(frame, k_box):
    header = frame[:HEADER_SIZE]
    nonce_n = frame[HEADER_SIZE:-MIC_LENGTH]
    mic = frame[-MIC_LENGTH:]
    expected = _mic(k_box, header + nonce_n)
    return mic == expected


def nonce_n_of_confirm(confirm_frame):
    return confirm_frame[HEADER_SIZE:-MIC_LENGTH]


def derive_session_key(k_box, nonce_b, nonce_n):
    salt = nonce_b + nonce_n
    prk = espnow_crypto.hkdf_extract(salt, k_box)
    return espnow_crypto.hkdf_expand(prk, SESSION_KEY_INFO, SESSION_KEY_LENGTH)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-box && python -m unittest tests.test_pairing_codec -v`
Expected: `OK`, all 9 tests pass

- [ ] **Step 5: Update `smart-box/CLAUDE.md`**

In the **Project Structure** tree, add a line right after the `frame_envelope.py` line added in Task 5:
```
├── pairing_codec.py                    # Baut/parst DISCOVER/OFFER/CONFIRM unter K_Box + leitet K_S ab (ESP-NOW-Pairing, ADR-003)
```

- [ ] **Step 6: Run the full smart-box test suite**

Run: `cd smart-box && python -m unittest discover -s tests -t . -v`
Expected: all tests pass, including the new `test_pairing_codec` module

- [ ] **Step 7: Commit** (inside `smart-box/`, using its `[firmware] ` prefix convention)

```bash
git add pairing_codec.py tests/test_pairing_codec.py CLAUDE.md
git commit -m "[firmware] add pairing codec (DISCOVER/OFFER/CONFIRM + session key), verified against fixture"
```

---

## Plan-Level Verification

- [ ] **Final check: run both full test suites once more from a clean state**

```bash
cd smart-ground-node && mvn test
cd ../smart-box && python -m unittest discover -s tests -t . -v
```

Expected: both `BUILD SUCCESS` (Java) and `OK` (MicroPython), zero failures. Java and MicroPython now both build/parse byte-identical DISCOVER/OFFER/CONFIRM frames and derive the same `K_S` from the same fixture — the frame-level cross-language guarantee Baustein A established for the crypto primitives now extends to the pairing protocol itself. Frame bodies for CONFIG/COMMAND/etc. (Baustein C) and UART framing to the radio module (Baustein D) remain separate follow-on plans.

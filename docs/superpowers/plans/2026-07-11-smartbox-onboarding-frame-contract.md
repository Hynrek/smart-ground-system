# SmartBox Onboarding Frame Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the two unauthenticated onboarding ESP-NOW frames — `HELLO` (box→Node "Ich bin neu") and `ONBOARD_OFFER` (Node→box AP-handoff) — as a cross-language-verified wire contract on both the Java (`smart-ground-node`) and MicroPython (`smart-box`) sides.

**Architecture:** These frames are the foundation of the coupling flow in [2026-07-11-smartbox-coupling-design.md](../specs/2026-07-11-smartbox-coupling-design.md). They carry **no crypto** — a fabrikneue box has no `K_Box` yet, so unlike `PAIR_DISCOVER/OFFER/CONFIRM` there is no MIC or AES-GCM. `HELLO` is the box's broadcast announce; `ONBOARD_OFFER` is the Node's unicast reply carrying AP-SSID/PSK, the `box-api` base URL, the Node cert **fingerprint** (SHA-256), a one-time **provisioning token**, and an echo of the box's nonce. A single generated fixture (`docs/espnow/onboarding-test-vectors.json`) is the golden byte-layout both implementations are tested against — exactly the pattern already used for `pairing-test-vectors.json` and `crypto-test-vectors.json`.

**Tech Stack:** Java 25 + Spring Boot 4 + JUnit 5/AssertJ/Jackson (node); MicroPython 1.23+ + CPython `unittest` host tests (box); a small Python generator for the shared fixture.

## Global Constraints

- **Frame header is fixed 16 bytes** (`FrameHeader` / `frame_envelope.pack_header`): `dest_mac(6) ‖ src_mac(6) ‖ frame_id(2, uint16 LE) ‖ ttl(1) ‖ type(1)`. Do not change it.
- **Frame codes are declared in exactly one place per language:** `FrameType` (Java enum) and `frame_envelope.py` (module constants). New codes must be added to both.
- **ESP-NOW payload ceiling is 250 bytes.** `ONBOARD_OFFER` must stay under it (its worst case here is ~210 B).
- **No crypto in these frames.** No MIC, no AES-GCM — they precede `K_Box`.
- **Comments German for domain logic, identifiers/docstrings English** (both repos). Java: `mvn test` from `smart-ground-node/`. Box: `python -m unittest discover -s tests -t . -v` from `smart-box/`.
- **Cross-repo fixture path:** node tests read `../docs/espnow/onboarding-test-vectors.json`; box tests read the same and **skip with an explanatory message** if absent (standalone firmware clone), mirroring `tests/test_espnow_crypto.py`.
- **New frame type codes:** `HELLO = 0x20`, `ONBOARD_OFFER = 0x21` (the `0x2x` band, distinct from the `0x0x` pairing and `0x1x` operational bands already in use).

---

## File Structure

**Created:**
- `docs/espnow/gen_onboarding_vectors.py` — generator that emits the golden fixture (single source of the golden bytes; its concatenation logic *is* the layout spec).
- `docs/espnow/onboarding-test-vectors.json` — generated fixture, committed.
- `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/OnboardingCodec.java` — Java build/parse for both frames.
- `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/OnboardingCodecTest.java` — fixture-driven Java tests.
- `smart-box/onboarding_codec.py` — MicroPython build/parse for both frames.
- `smart-box/tests/test_onboarding_codec.py` — fixture-driven box tests.

**Modified:**
- `smart-ground-node/src/main/java/ch/jp/shooting/node/frame/FrameType.java` — add `HELLO`, `ONBOARD_OFFER`.
- `smart-box/frame_envelope.py` — add `TYPE_HELLO`, `TYPE_ONBOARD_OFFER`.

**Wire layout (both frames):**

```
HELLO           = header(16) ‖ box_nonce(8)
                  header.type = 0x20, header.dest = ff:ff:ff:ff:ff:ff (broadcast), header.src = box MAC

ONBOARD_OFFER   = header(16) ‖ echo_nonce(8) ‖ token(16) ‖ fingerprint(32)
                  ‖ ssid_len(1) ‖ ssid ‖ psk_len(1) ‖ psk ‖ url_len(1) ‖ url
                  header.type = 0x21, header.dest = box MAC, header.src = node MAC
                  ssid/psk/url are UTF-8, each preceded by a single-byte length (0..255)
```

---

### Task 1: Onboarding frame type codes (both languages)

**Files:**
- Modify: `smart-ground-node/src/main/java/ch/jp/shooting/node/frame/FrameType.java`
- Modify: `smart-box/frame_envelope.py`
- Test: `smart-ground-node/src/test/java/ch/jp/shooting/node/frame/FrameTypeTest.java` (create)

**Interfaces:**
- Produces (Java): `FrameType.HELLO` (code `0x20`), `FrameType.ONBOARD_OFFER` (code `0x21`); `FrameType.fromCode((byte)0x20) == HELLO`.
- Produces (Python): `frame_envelope.TYPE_HELLO == 0x20`, `frame_envelope.TYPE_ONBOARD_OFFER == 0x21`.

- [ ] **Step 1: Write the failing Java test**

Create `smart-ground-node/src/test/java/ch/jp/shooting/node/frame/FrameTypeTest.java`:

```java
package ch.jp.shooting.node.frame;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameTypeTest {

    @Test
    void onboardingCodes_roundTrip() {
        assertThat(FrameType.HELLO.code()).isEqualTo((byte) 0x20);
        assertThat(FrameType.ONBOARD_OFFER.code()).isEqualTo((byte) 0x21);
        assertThat(FrameType.fromCode((byte) 0x20)).isEqualTo(FrameType.HELLO);
        assertThat(FrameType.fromCode((byte) 0x21)).isEqualTo(FrameType.ONBOARD_OFFER);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -q test -Dtest=FrameTypeTest`
Expected: compile failure — `cannot find symbol: variable HELLO`.

- [ ] **Step 3: Add the two enum constants**

In `FrameType.java`, add to the enum (after `HEARTBEAT((byte) 0x15);` becomes `HEARTBEAT((byte) 0x15),`):

```java
    HEARTBEAT((byte) 0x15),
    HELLO((byte) 0x20),
    ONBOARD_OFFER((byte) 0x21);
```

- [ ] **Step 4: Run the Java test to verify it passes**

Run: `cd smart-ground-node && mvn -q test -Dtest=FrameTypeTest`
Expected: PASS (`BUILD SUCCESS`).

- [ ] **Step 5: Add the MicroPython constants**

In `smart-box/frame_envelope.py`, after `TYPE_HEARTBEAT = 0x15` add:

```python
TYPE_HEARTBEAT = 0x15
TYPE_HELLO = 0x20
TYPE_ONBOARD_OFFER = 0x21
```

- [ ] **Step 6: Verify the box constants load**

Run: `cd smart-box && python -c "import frame_envelope as f; assert f.TYPE_HELLO == 0x20 and f.TYPE_ONBOARD_OFFER == 0x21; print('ok')"`
Expected: `ok`

- [ ] **Step 7: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/frame/FrameType.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/frame/FrameTypeTest.java \
        smart-box/frame_envelope.py
git commit -m "[node][firmware] add HELLO/ONBOARD_OFFER frame type codes"
```

---

### Task 2: Shared onboarding fixture generator + golden vectors

**Files:**
- Create: `docs/espnow/gen_onboarding_vectors.py`
- Create (generated, committed): `docs/espnow/onboarding-test-vectors.json`

**Interfaces:**
- Produces: `docs/espnow/onboarding-test-vectors.json` with top-level keys `constants`, `hello`, `onboard_offer`. Each frame object carries its individual fields plus a `frame` hex string that is the full on-wire bytes. Consumed verbatim by Tasks 3 and 4.

- [ ] **Step 1: Write the generator**

Create `docs/espnow/gen_onboarding_vectors.py`:

```python
"""Erzeugt die kanonischen Onboarding-Frame-Testvektoren (docs/espnow/onboarding-test-vectors.json).

Layout siehe docs/superpowers/plans/2026-07-11-smartbox-onboarding-frame-contract.md.
Diese Datei ist die *einzige* Quelle der Golden-Bytes: sowohl der Java- (smart-ground-node)
als auch der MicroPython-Test (smart-box) prueft gegen ihre Ausgabe.
"""
import json
import struct

BROADCAST_MAC = bytes.fromhex("ffffffffffff")
BOX_MAC = bytes.fromhex("24a1600b1c2d")
NODE_MAC = bytes.fromhex("30aea41f2b3c")
BOX_NONCE = bytes.fromhex("1122334455667788")            # 8 B
TOKEN = bytes.fromhex("000102030405060708090a0b0c0d0e0f")  # 16 B
FINGERPRINT = bytes(range(32))                            # 32 B (SHA-256 des Node-Zerts, hier Dummy)
AP_SSID = "SmartGround-Node-1".encode("utf-8")
AP_PSK = "provision-pw-123".encode("utf-8")
BOX_API_URL = "https://192.168.4.1:8443".encode("utf-8")

TYPE_HELLO = 0x20
TYPE_ONBOARD_OFFER = 0x21


def pack_header(dest_mac, src_mac, frame_id, ttl, type_):
    return dest_mac + src_mac + struct.pack("<H", frame_id) + bytes([ttl, type_])


def build_hello():
    return pack_header(BROADCAST_MAC, BOX_MAC, 1, 1, TYPE_HELLO) + BOX_NONCE


def _lv(b):
    return bytes([len(b)]) + b


def build_onboard_offer():
    header = pack_header(BOX_MAC, NODE_MAC, 1, 1, TYPE_ONBOARD_OFFER)
    body = BOX_NONCE + TOKEN + FINGERPRINT + _lv(AP_SSID) + _lv(AP_PSK) + _lv(BOX_API_URL)
    return header + body


def h(b):
    return b.hex()


vectors = {
    "constants": {
        "broadcast_mac": h(BROADCAST_MAC), "box_mac": h(BOX_MAC), "node_mac": h(NODE_MAC),
        "box_nonce": h(BOX_NONCE), "token": h(TOKEN), "fingerprint": h(FINGERPRINT),
        "ap_ssid_utf8": AP_SSID.decode(), "ap_ssid_hex": h(AP_SSID),
        "ap_psk_utf8": AP_PSK.decode(), "ap_psk_hex": h(AP_PSK),
        "box_api_url_utf8": BOX_API_URL.decode(), "box_api_url_hex": h(BOX_API_URL),
    },
    "hello": {
        "dest_mac": h(BROADCAST_MAC), "src_mac": h(BOX_MAC), "frame_id": 1, "ttl": 1,
        "type": TYPE_HELLO, "box_nonce": h(BOX_NONCE), "frame": h(build_hello()),
    },
    "onboard_offer": {
        "dest_mac": h(BOX_MAC), "src_mac": h(NODE_MAC), "frame_id": 1, "ttl": 1,
        "type": TYPE_ONBOARD_OFFER, "echo_nonce": h(BOX_NONCE), "token": h(TOKEN),
        "fingerprint": h(FINGERPRINT), "ssid": h(AP_SSID), "psk": h(AP_PSK), "url": h(BOX_API_URL),
        "frame": h(build_onboard_offer()),
    },
}

if __name__ == "__main__":
    import os
    out = os.path.join(os.path.dirname(__file__), "onboarding-test-vectors.json")
    with open(out, "w") as f:
        json.dump(vectors, f, indent=2)
        f.write("\n")
    print("wrote", out)
    print("hello.frame       =", vectors["hello"]["frame"])
    print("onboard_offer.frame len(bytes) =", len(build_onboard_offer()))
```

- [ ] **Step 2: Generate the fixture and sanity-check the size**

Run: `cd docs/espnow && python gen_onboarding_vectors.py`
Expected: prints `wrote .../onboarding-test-vectors.json`, a `hello.frame` hex of 24 bytes (48 hex chars: `ffffffffffff24a1600b1c2d010020` + `1122334455667788`), and `onboard_offer.frame len(bytes)` well under 250 (should be **123**).

- [ ] **Step 3: Confirm the JSON is valid and committed-ready**

Run: `cd docs/espnow && python -c "import json; d=json.load(open('onboarding-test-vectors.json')); print(sorted(d), len(d['onboard_offer']['frame'])//2, 'bytes')"`
Expected: `['constants', 'hello', 'onboard_offer'] 123 bytes`

- [ ] **Step 4: Commit**

```bash
git add docs/espnow/gen_onboarding_vectors.py docs/espnow/onboarding-test-vectors.json
git commit -m "[docs] add onboarding frame test vectors + generator"
```

---

### Task 3: Java `OnboardingCodec` (build/parse both frames)

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/OnboardingCodec.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/OnboardingCodecTest.java`

**Interfaces:**
- Consumes: `FrameHeader` (`ch.jp.shooting.node.frame.FrameHeader`), `FrameType.HELLO`/`ONBOARD_OFFER`, `PairingTestVectors.hex(String)` (public static, reused as the hex helper).
- Produces:
  - `byte[] OnboardingCodec.buildHello(FrameHeader header, byte[] boxNonce)`
  - `byte[] OnboardingCodec.boxNonceOf(byte[] helloFrame)` → 8 bytes
  - `byte[] OnboardingCodec.buildOnboardOffer(FrameHeader header, byte[] echoNonce, byte[] token, byte[] fingerprint, byte[] ssid, byte[] psk, byte[] url)`
  - `byte[] OnboardingCodec.echoNonceOf(byte[] offerFrame)` → 8 bytes
  - `byte[] OnboardingCodec.tokenOf(byte[] offerFrame)` → 16 bytes
  - `byte[] OnboardingCodec.fingerprintOf(byte[] offerFrame)` → 32 bytes
  - `byte[] OnboardingCodec.ssidOf(byte[] offerFrame)`, `pskOf(...)`, `urlOf(...)` → variable-length UTF-8 bytes

- [ ] **Step 1: Write the failing test**

Create `smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/OnboardingCodecTest.java`:

```java
package ch.jp.shooting.node.onboarding;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import ch.jp.shooting.node.frame.PairingTestVectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static ch.jp.shooting.node.frame.PairingTestVectors.hex;
import static org.assertj.core.api.Assertions.assertThat;

class OnboardingCodecTest {

    private static JsonNode fixture() {
        try {
            return new ObjectMapper().readTree(new File("../docs/espnow/onboarding-test-vectors.json"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void buildHello_matchesFixtureFrame() {
        JsonNode v = fixture().get("hello");
        FrameHeader header = new FrameHeader(hex(v.get("dest_mac").asText()), hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.HELLO);

        byte[] frame = OnboardingCodec.buildHello(header, hex(v.get("box_nonce").asText()));

        assertThat(frame).isEqualTo(hex(v.get("frame").asText()));
    }

    @Test
    void boxNonceOf_extractsFixtureValue() {
        JsonNode v = fixture().get("hello");
        assertThat(OnboardingCodec.boxNonceOf(hex(v.get("frame").asText())))
                .isEqualTo(hex(v.get("box_nonce").asText()));
    }

    @Test
    void buildOnboardOffer_matchesFixtureFrame() {
        JsonNode v = fixture().get("onboard_offer");
        FrameHeader header = new FrameHeader(hex(v.get("dest_mac").asText()), hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.ONBOARD_OFFER);

        byte[] frame = OnboardingCodec.buildOnboardOffer(header,
                hex(v.get("echo_nonce").asText()), hex(v.get("token").asText()),
                hex(v.get("fingerprint").asText()), hex(v.get("ssid").asText()),
                hex(v.get("psk").asText()), hex(v.get("url").asText()));

        assertThat(frame).isEqualTo(hex(v.get("frame").asText()));
    }

    @Test
    void onboardOffer_accessors_extractFixtureValues() {
        JsonNode v = fixture().get("onboard_offer");
        byte[] frame = hex(v.get("frame").asText());

        assertThat(OnboardingCodec.echoNonceOf(frame)).isEqualTo(hex(v.get("echo_nonce").asText()));
        assertThat(OnboardingCodec.tokenOf(frame)).isEqualTo(hex(v.get("token").asText()));
        assertThat(OnboardingCodec.fingerprintOf(frame)).isEqualTo(hex(v.get("fingerprint").asText()));
        assertThat(OnboardingCodec.ssidOf(frame)).isEqualTo(hex(v.get("ssid").asText()));
        assertThat(OnboardingCodec.pskOf(frame)).isEqualTo(hex(v.get("psk").asText()));
        assertThat(OnboardingCodec.urlOf(frame)).isEqualTo(hex(v.get("url").asText()));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-ground-node && mvn -q test -Dtest=OnboardingCodecTest`
Expected: compile failure — `cannot find symbol: class OnboardingCodec`.

- [ ] **Step 3: Write the codec**

Create `smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/OnboardingCodec.java`:

```java
package ch.jp.shooting.node.onboarding;

import ch.jp.shooting.node.frame.FrameHeader;

import java.util.Arrays;

/**
 * Baut und parst die unauthentifizierten Onboarding-Frames HELLO und ONBOARD_OFFER.
 * Kein MIC, kein AES-GCM — eine fabrikneue Box hat noch kein K_Box (siehe
 * docs/superpowers/specs/2026-07-11-smartbox-coupling-design.md).
 *
 * HELLO         = header(16) ‖ box_nonce(8)
 * ONBOARD_OFFER = header(16) ‖ echo_nonce(8) ‖ token(16) ‖ fingerprint(32)
 *                 ‖ ssid_len(1) ‖ ssid ‖ psk_len(1) ‖ psk ‖ url_len(1) ‖ url
 */
public final class OnboardingCodec {

    private static final int NONCE_LENGTH = 8;
    private static final int TOKEN_LENGTH = 16;
    private static final int FINGERPRINT_LENGTH = 32;
    private static final int OFFER_VARFIELDS_START = FrameHeader.SIZE + NONCE_LENGTH + TOKEN_LENGTH + FINGERPRINT_LENGTH;

    private OnboardingCodec() {
    }

    public static byte[] buildHello(FrameHeader header, byte[] boxNonce) {
        return concat(header.encode(), boxNonce);
    }

    public static byte[] boxNonceOf(byte[] helloFrame) {
        return Arrays.copyOfRange(helloFrame, FrameHeader.SIZE, FrameHeader.SIZE + NONCE_LENGTH);
    }

    public static byte[] buildOnboardOffer(FrameHeader header, byte[] echoNonce, byte[] token,
                                            byte[] fingerprint, byte[] ssid, byte[] psk, byte[] url) {
        return concat(header.encode(), echoNonce, token, fingerprint,
                lengthPrefixed(ssid), lengthPrefixed(psk), lengthPrefixed(url));
    }

    public static byte[] echoNonceOf(byte[] offerFrame) {
        int start = FrameHeader.SIZE;
        return Arrays.copyOfRange(offerFrame, start, start + NONCE_LENGTH);
    }

    public static byte[] tokenOf(byte[] offerFrame) {
        int start = FrameHeader.SIZE + NONCE_LENGTH;
        return Arrays.copyOfRange(offerFrame, start, start + TOKEN_LENGTH);
    }

    public static byte[] fingerprintOf(byte[] offerFrame) {
        int start = FrameHeader.SIZE + NONCE_LENGTH + TOKEN_LENGTH;
        return Arrays.copyOfRange(offerFrame, start, start + FINGERPRINT_LENGTH);
    }

    public static byte[] ssidOf(byte[] offerFrame) {
        return varFieldAt(offerFrame, OFFER_VARFIELDS_START, 0);
    }

    public static byte[] pskOf(byte[] offerFrame) {
        return varFieldAt(offerFrame, OFFER_VARFIELDS_START, 1);
    }

    public static byte[] urlOf(byte[] offerFrame) {
        return varFieldAt(offerFrame, OFFER_VARFIELDS_START, 2);
    }

    /** Liefert das indexTh laengen-praefixierte Feld ab {@code start} (0=ssid, 1=psk, 2=url). */
    private static byte[] varFieldAt(byte[] frame, int start, int index) {
        int pos = start;
        for (int i = 0; i < index; i++) {
            int len = frame[pos] & 0xFF;
            pos += 1 + len;
        }
        int len = frame[pos] & 0xFF;
        return Arrays.copyOfRange(frame, pos + 1, pos + 1 + len);
    }

    private static byte[] lengthPrefixed(byte[] field) {
        if (field.length > 255) {
            throw new IllegalArgumentException("Feld zu lang fuer 1-Byte-Laengenpraefix: " + field.length);
        }
        byte[] out = new byte[field.length + 1];
        out[0] = (byte) field.length;
        System.arraycopy(field, 0, out, 1, field.length);
        return out;
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

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd smart-ground-node && mvn -q test -Dtest=OnboardingCodecTest`
Expected: PASS — 4 tests green.

- [ ] **Step 5: Run the whole node suite (no regressions)**

Run: `cd smart-ground-node && mvn -q test`
Expected: `BUILD SUCCESS` — existing pairing/crypto/architecture tests still pass.

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/onboarding/OnboardingCodec.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/onboarding/OnboardingCodecTest.java
git commit -m "[node] add OnboardingCodec for HELLO/ONBOARD_OFFER frames"
```

---

### Task 4: MicroPython `onboarding_codec.py` (build/parse both frames)

**Files:**
- Create: `smart-box/onboarding_codec.py`
- Create: `smart-box/tests/test_onboarding_codec.py`

**Interfaces:**
- Consumes: `frame_envelope.pack_header`, `frame_envelope.HEADER_SIZE`, `frame_envelope.TYPE_HELLO`, `frame_envelope.TYPE_ONBOARD_OFFER`.
- Produces (mirror of the Java signatures):
  - `build_hello(dest_mac, src_mac, frame_id, ttl, box_nonce)` → bytes
  - `box_nonce_of(hello_frame)` → 8 bytes
  - `build_onboard_offer(dest_mac, src_mac, frame_id, ttl, echo_nonce, token, fingerprint, ssid, psk, url)` → bytes
  - `echo_nonce_of(offer_frame)`, `token_of(offer_frame)`, `fingerprint_of(offer_frame)` → fixed slices
  - `ssid_of(offer_frame)`, `psk_of(offer_frame)`, `url_of(offer_frame)` → variable-length bytes

- [ ] **Step 1: Write the failing test**

Create `smart-box/tests/test_onboarding_codec.py`:

```python
"""Onboarding-Frame-Codec gegen die geteilten Golden-Vektoren (Cross-Language mit smart-ground-node)."""
import json
import os
import unittest

import onboarding_codec as oc
from frame_envelope import TYPE_HELLO, TYPE_ONBOARD_OFFER

FIXTURE = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "espnow", "onboarding-test-vectors.json")


def _load():
    with open(FIXTURE) as f:
        return json.load(f)


@unittest.skipUnless(os.path.exists(FIXTURE),
                     "onboarding-test-vectors.json fehlt (Standalone-Firmware-Clone ohne Monorepo-docs)")
class TestOnboardingCodec(unittest.TestCase):

    def setUp(self):
        self.v = _load()

    def test_build_hello_matches_fixture(self):
        h = self.v["hello"]
        frame = oc.build_hello(bytes.fromhex(h["dest_mac"]), bytes.fromhex(h["src_mac"]),
                               h["frame_id"], h["ttl"], bytes.fromhex(h["box_nonce"]))
        self.assertEqual(frame, bytes.fromhex(h["frame"]))
        self.assertEqual(frame[15], TYPE_HELLO)

    def test_box_nonce_of(self):
        h = self.v["hello"]
        self.assertEqual(oc.box_nonce_of(bytes.fromhex(h["frame"])), bytes.fromhex(h["box_nonce"]))

    def test_build_onboard_offer_matches_fixture(self):
        o = self.v["onboard_offer"]
        frame = oc.build_onboard_offer(
            bytes.fromhex(o["dest_mac"]), bytes.fromhex(o["src_mac"]), o["frame_id"], o["ttl"],
            bytes.fromhex(o["echo_nonce"]), bytes.fromhex(o["token"]), bytes.fromhex(o["fingerprint"]),
            bytes.fromhex(o["ssid"]), bytes.fromhex(o["psk"]), bytes.fromhex(o["url"]))
        self.assertEqual(frame, bytes.fromhex(o["frame"]))
        self.assertEqual(frame[15], TYPE_ONBOARD_OFFER)

    def test_onboard_offer_accessors(self):
        o = self.v["onboard_offer"]
        frame = bytes.fromhex(o["frame"])
        self.assertEqual(oc.echo_nonce_of(frame), bytes.fromhex(o["echo_nonce"]))
        self.assertEqual(oc.token_of(frame), bytes.fromhex(o["token"]))
        self.assertEqual(oc.fingerprint_of(frame), bytes.fromhex(o["fingerprint"]))
        self.assertEqual(oc.ssid_of(frame), bytes.fromhex(o["ssid"]))
        self.assertEqual(oc.psk_of(frame), bytes.fromhex(o["psk"]))
        self.assertEqual(oc.url_of(frame), bytes.fromhex(o["url"]))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd smart-box && python -m unittest tests.test_onboarding_codec -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'onboarding_codec'`.

- [ ] **Step 3: Write the codec**

Create `smart-box/onboarding_codec.py`:

```python
"""Baut und parst die unauthentifizierten Onboarding-Frames HELLO und ONBOARD_OFFER.

Kein MIC, kein AES-GCM — eine fabrikneue Box hat noch kein K_Box (siehe
docs/superpowers/specs/2026-07-11-smartbox-coupling-design.md). Cross-Language-Gegenstueck
zu smart-ground-node's OnboardingCodec.java; beide gegen docs/espnow/onboarding-test-vectors.json.

HELLO         = header(16) ‖ box_nonce(8)
ONBOARD_OFFER = header(16) ‖ echo_nonce(8) ‖ token(16) ‖ fingerprint(32)
                ‖ ssid_len(1) ‖ ssid ‖ psk_len(1) ‖ psk ‖ url_len(1) ‖ url
"""
from frame_envelope import HEADER_SIZE, TYPE_HELLO, TYPE_ONBOARD_OFFER, pack_header

NONCE_LENGTH = 8
TOKEN_LENGTH = 16
FINGERPRINT_LENGTH = 32
_VARFIELDS_START = HEADER_SIZE + NONCE_LENGTH + TOKEN_LENGTH + FINGERPRINT_LENGTH


def build_hello(dest_mac, src_mac, frame_id, ttl, box_nonce):
    return pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_HELLO) + box_nonce


def box_nonce_of(hello_frame):
    return hello_frame[HEADER_SIZE:HEADER_SIZE + NONCE_LENGTH]


def _lv(field):
    if len(field) > 255:
        raise ValueError("Feld zu lang fuer 1-Byte-Laengenpraefix: %d" % len(field))
    return bytes([len(field)]) + field


def build_onboard_offer(dest_mac, src_mac, frame_id, ttl, echo_nonce, token, fingerprint, ssid, psk, url):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_ONBOARD_OFFER)
    body = echo_nonce + token + fingerprint + _lv(ssid) + _lv(psk) + _lv(url)
    return header + body


def echo_nonce_of(offer_frame):
    start = HEADER_SIZE
    return offer_frame[start:start + NONCE_LENGTH]


def token_of(offer_frame):
    start = HEADER_SIZE + NONCE_LENGTH
    return offer_frame[start:start + TOKEN_LENGTH]


def fingerprint_of(offer_frame):
    start = HEADER_SIZE + NONCE_LENGTH + TOKEN_LENGTH
    return offer_frame[start:start + FINGERPRINT_LENGTH]


def _var_field_at(frame, index):
    pos = _VARFIELDS_START
    for _ in range(index):
        pos += 1 + frame[pos]
    length = frame[pos]
    return frame[pos + 1:pos + 1 + length]


def ssid_of(offer_frame):
    return _var_field_at(offer_frame, 0)


def psk_of(offer_frame):
    return _var_field_at(offer_frame, 1)


def url_of(offer_frame):
    return _var_field_at(offer_frame, 2)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd smart-box && python -m unittest tests.test_onboarding_codec -v`
Expected: PASS — 4 tests OK.

- [ ] **Step 5: Run the whole box suite (no regressions)**

Run: `cd smart-box && python -m unittest discover -s tests -t . -v`
Expected: all tests OK (existing crypto/pairing/scheduler/config tests still pass; the espnow-crypto test may `skip` in a standalone clone — that is expected).

- [ ] **Step 6: Commit**

```bash
git add smart-box/onboarding_codec.py smart-box/tests/test_onboarding_codec.py
git commit -m "[firmware] add onboarding_codec for HELLO/ONBOARD_OFFER frames"
```

---

## Self-Review

**1. Spec coverage.** This plan implements exactly one slice of [the coupling spec](../specs/2026-07-11-smartbox-coupling-design.md): the "Neue ESP-NOW-Frames (Onboarding)" section — `HELLO` and `ONBOARD_OFFER` with their contents (AP-SSID/PSK, box-api address, cert fingerprint, one-time token, echo nonce). The remaining spec sections (pending registry, node-api endpoints, token lifecycle, `box-api` provision extension, firmware state machine, Node AP, docker compose) are **out of scope by design** — they are Plans 2–5 in the decomposition and each depends on this frame contract. No spec requirement inside *this slice* is left unimplemented.

**2. Placeholder scan.** No `TBD`/`TODO`/"handle errors appropriately"/"similar to Task N". Every code step shows complete code; every run step shows the exact command and expected output.

**3. Type consistency.** Java `buildHello(FrameHeader, byte[])` / `buildOnboardOffer(FrameHeader, byte[]×6)` and their accessors (`boxNonceOf`, `echoNonceOf`, `tokenOf`, `fingerprintOf`, `ssidOf`, `pskOf`, `urlOf`) mirror the MicroPython `build_hello`/`build_onboard_offer` and `box_nonce_of`/`echo_nonce_of`/`token_of`/`fingerprint_of`/`ssid_of`/`psk_of`/`url_of` one-for-one. Field order (`echo_nonce ‖ token ‖ fingerprint ‖ ssid ‖ psk ‖ url`), the offsets (`8/16/32`), and the type codes (`0x20`/`0x21`) are identical across the generator, the Java codec, and the MicroPython codec. The single fixture is the shared oracle for all three.

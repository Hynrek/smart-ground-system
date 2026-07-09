# ESP-NOW Operational Frames + Capability Codec (Baustein C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement encode/decode for the six operational ESP-NOW frame types (`DISCOVERY`, `CONFIG`, `CONFIG_ACK`, `COMMAND`, `EXECUTED`, `HEARTBEAT`) under the session key `K_S`, including a compact enum-based encoding for `DISCOVERY`'s capability map — in both Java (`smart-ground-node`) and MicroPython (`smart-box`), cross-verified against one shared, pre-generated-and-verified test vector fixture — Baustein C of [plan-espnow-migration.md](../../plan-espnow-migration.md), building on Baustein A's crypto primitives and Baustein B's frame-header/pairing codec.

**Architecture:** A stateless `OperationalCodec`/`operational_codec` module composes the existing `FrameHeader`/`FrameType` codec (Baustein B) with the `AesGcm`/`espnow_crypto` AEAD primitive (Baustein A) to build/parse the six operational frame types under `K_S`. The counter-nonce is caller-supplied, not managed by the codec — real sequencing/persistence is Phase 2b/2d business logic, out of scope here. All byte layouts come from one canonical fixture — generated via Node.js's native crypto and cross-checked by an independent Python replay using the already-shipped `espnow_crypto` module before being written into this plan, exactly as Baustein A/B's fixtures were.

**Tech Stack:** Java 25 (`smart-ground-node`, existing `crypto`/`frame` packages), MicroPython 1.23+ (`smart-box`, existing `espnow_crypto.py`/`frame_envelope.py`).

## Global Constraints

- Shared AES-GCM wrapper for all six types (Protocol spec Abschnitt 4, [2026-07-09-espnow-protocol-contracts-design.md](../specs/2026-07-09-espnow-protocol-contracts-design.md)):
  `body = counter_nonce(4) ‖ ciphertext ‖ tag(16)`, `ciphertext‖tag = AES-256-GCM-Encrypt(K_S, nonce = 0x0000000000000000 ‖ counter_nonce, aad = header, plaintext = <type-specific body>)`.
  **The 12-byte GCM nonce is 8 zero bytes followed by the 4-byte `counter_nonce`** — the opposite padding direction from Baustein B's `PAIR_OFFER` nonce (which pads an 8-byte nonce with 4 leading zero bytes). Do not confuse the two.
- Frame bodies exactly as specified in [2026-07-09-espnow-protocol-contracts-design.md](../specs/2026-07-09-espnow-protocol-contracts-design.md) Abschnitt 4 and [2026-07-10-espnow-operational-frames-design.md](../specs/2026-07-10-espnow-operational-frames-design.md):
  - `CONFIG_ACK`, `HEARTBEAT`: empty plaintext body.
  - `COMMAND`: `command_id(16) ‖ device_id(16) ‖ command(1, ON=0/OFF=1/BLOCK=2/UNBLOCK=3) ‖ signal_duration_ms(2, uint16 LE)`.
  - `EXECUTED`: `command_id(16) ‖ device_id(16)`.
  - `CONFIG`: `device_id(16) ‖ device_index(1) ‖ device_count(1) ‖ alias_len(1) ‖ alias ‖ device_type(1, GPIO=0/LED=1) ‖ direction(1, IN=0/OUT=1) ‖ command_len(1) ‖ command ‖ signal_duration_ms(2, uint16 LE) ‖ blocked(1)`.
  - `DISCOVERY`: `app_version_major(1) ‖ app_version_minor(1) ‖ config_schema_version(1) ‖ box_type_len(1) ‖ box_type ‖ device_type_count(1) ‖ [device_type_entry]*`, where each `device_type_entry = device_type_id(1) ‖ directions_bitmask(1) ‖ commands_bitmask(1) ‖ config_field_count(1) ‖ [config_field_entry]*`, and each `config_field_entry = field_id(1) ‖ type_id(1) ‖ default_len(1) ‖ default_bytes`. Enum register: `device_type_id` GPIO=0/LED=1; `directions_bitmask` bit0=INPUT/bit1=OUTPUT; `commands_bitmask` bit0=ON/bit1=OFF; `field_id` SIGNAL_DURATION_MS=0; `type_id` INT=0/BOOL=1/STRING=2. `FrameType` (Baustein B) already defines all six type codes — do not modify it.
- One new canonical fixture file: `docs/espnow/operational-test-vectors.json` (this plan — the single source of truth for operational-frame byte layouts, never duplicated elsewhere). Do not modify `docs/espnow/crypto-test-vectors.json` (Baustein A) or `docs/espnow/pairing-test-vectors.json` (Baustein B).
- Java: package `ch.jp.shooting.node.operational` under `smart-ground-node/src/main/java/`. Reuses `ch.jp.shooting.node.crypto.AesGcm` (Baustein A) and `ch.jp.shooting.node.frame.FrameHeader`/`FrameType` (Baustein B) — do not modify those files. Comments German for domain logic, English identifiers. Build/test with system `mvn` from `smart-ground-node/`.
- MicroPython: new file `smart-box/operational_codec.py`, importing `espnow_crypto` and `frame_envelope` (both from Baustein A/B) — do not modify either. `smart-box/` is its own independent git repository (remote `github.com:Hynrek/smartground-firmware.git`) — all commits for `smart-box/` changes happen inside that directory, targeting that repo, using its established `[firmware] ` commit-message prefix. Host tests: `python -m unittest discover -s tests -t . -v` from `smart-box/`.
- Counter-nonce sequencing/persistence, Node MQTT-translation logic, `CONFIG` multi-frame accumulation/timeout state machine, and heartbeat/offline-detection timers are **not** part of this plan (Phase 2b/2d business logic). UART framing (Baustein D) is a separate follow-on plan.

---

### Task 1: Canonical operational-frame test vector fixture

**Files:**
- Create: `docs/espnow/operational-test-vectors.json`

**Interfaces:**
- Produces: a JSON file with a `constants` object (`k_s`, `box_mac`, `node_mac`) and six frame objects (`discovery`, `config`, `config_ack`, `command`, `executed`, `heartbeat`), each with the fields needed to both build and verify that frame (header fields, plaintext, counter_nonce, gcm_nonce, ciphertext, tag, full frame, and a `decoded` object with the typed field values). All hex values were generated via Node.js's native `crypto` module (`createCipheriv("aes-256-gcm", ...)`) and independently cross-checked by replaying the exact same construction in Python against the already-shipped `smart-box/espnow_crypto.py` module (Baustein A) — every header byte, plaintext byte, ciphertext, tag, and full frame matched byte-for-byte on replay, including a decrypt round-trip. Do not hand-edit any hex value in this file.

- [ ] **Step 1: Write the fixture file**

```json
{
  "constants": {
    "k_s": "9f3c1a7e2b5d8046c1e9a37f204b6d8e5a1c3f7092b4d6e8a0c2e4f6081a3c5d",
    "box_mac": "aabbccddeeff",
    "node_mac": "001122334455"
  },
  "discovery": {
    "dest_mac": "001122334455",
    "src_mac": "aabbccddeeff",
    "frame_id": 10,
    "ttl": 1,
    "type": 16,
    "header": "001122334455aabbccddeeff0a000110",
    "plaintext": "01000108536d617274426f780200030301000004000000000102010100000496000000",
    "counter_nonce": "00000000",
    "gcm_nonce": "000000000000000000000000",
    "ciphertext": "a93ce05d6cbd3d201fdcecbc20f243745cc06f7ec0706ae8161ec2bb3c566b8a147102",
    "tag": "e3c6e349818a3d97c6e7bd532a766875",
    "frame": "001122334455aabbccddeeff0a00011000000000a93ce05d6cbd3d201fdcecbc20f243745cc06f7ec0706ae8161ec2bb3c566b8a147102e3c6e349818a3d97c6e7bd532a766875",
    "decoded": {
      "app_version": "1.0",
      "config_schema_version": 1,
      "box_type": "SmartBox",
      "device_types": [
        {
          "device_type_id": 0,
          "directions_bitmask": 3,
          "commands_bitmask": 3,
          "config_fields": [
            { "field_id": 0, "type_id": 0, "default": 0 }
          ]
        },
        {
          "device_type_id": 1,
          "directions_bitmask": 2,
          "commands_bitmask": 1,
          "config_fields": [
            { "field_id": 0, "type_id": 0, "default": 150 }
          ]
        }
      ]
    }
  },
  "config": {
    "dest_mac": "aabbccddeeff",
    "src_mac": "001122334455",
    "frame_id": 11,
    "ttl": 1,
    "type": 17,
    "header": "aabbccddeeff0011223344550b000111",
    "plaintext": "0102030405060708090a0b0c0d0e0f1000010857657266657220310001024f4ef40100",
    "counter_nonce": "01000000",
    "gcm_nonce": "000000000000000001000000",
    "ciphertext": "edab8fbca8f2c9ae786fde18fac3e5a23f40bcca0a2f9e696bf5d48944fc76fd23250a",
    "tag": "453a82ec243d440b21da9b64ff4bf10a",
    "frame": "aabbccddeeff0011223344550b00011101000000edab8fbca8f2c9ae786fde18fac3e5a23f40bcca0a2f9e696bf5d48944fc76fd23250a453a82ec243d440b21da9b64ff4bf10a",
    "decoded": {
      "device_id": "0102030405060708090a0b0c0d0e0f10",
      "device_index": 0,
      "device_count": 1,
      "alias": "Werfer 1",
      "device_type": 0,
      "direction": 1,
      "command": "ON",
      "signal_duration_ms": 500,
      "blocked": false
    }
  },
  "config_ack": {
    "dest_mac": "001122334455",
    "src_mac": "aabbccddeeff",
    "frame_id": 12,
    "ttl": 1,
    "type": 18,
    "header": "001122334455aabbccddeeff0c000112",
    "plaintext": "",
    "counter_nonce": "02000000",
    "gcm_nonce": "000000000000000002000000",
    "ciphertext": "",
    "tag": "5c41278ab8dc9b72f5c153cfa30096ca",
    "frame": "001122334455aabbccddeeff0c000112020000005c41278ab8dc9b72f5c153cfa30096ca"
  },
  "command": {
    "dest_mac": "aabbccddeeff",
    "src_mac": "001122334455",
    "frame_id": 13,
    "ttl": 1,
    "type": 19,
    "header": "aabbccddeeff0011223344550d000113",
    "plaintext": "202122232425262728292a2b2c2d2e2f0102030405060708090a0b0c0d0e0f1000f401",
    "counter_nonce": "03000000",
    "gcm_nonce": "000000000000000003000000",
    "ciphertext": "2dc3c53bc085880220c534f3a3f36e66e750a5b2220745bfb7cdba61e4b4d6afd1cf0d",
    "tag": "86d75d661a00e2153569cc11fd4f4626",
    "frame": "aabbccddeeff0011223344550d000113030000002dc3c53bc085880220c534f3a3f36e66e750a5b2220745bfb7cdba61e4b4d6afd1cf0d86d75d661a00e2153569cc11fd4f4626",
    "decoded": {
      "command_id": "202122232425262728292a2b2c2d2e2f",
      "device_id": "0102030405060708090a0b0c0d0e0f10",
      "command": 0,
      "signal_duration_ms": 500
    }
  },
  "executed": {
    "dest_mac": "001122334455",
    "src_mac": "aabbccddeeff",
    "frame_id": 14,
    "ttl": 1,
    "type": 20,
    "header": "001122334455aabbccddeeff0e000114",
    "plaintext": "202122232425262728292a2b2c2d2e2f0102030405060708090a0b0c0d0e0f10",
    "counter_nonce": "04000000",
    "gcm_nonce": "000000000000000004000000",
    "ciphertext": "3761967d7ded97b698e1dcecd40e77dd57ad831f821c4e90bf0cdabb8d298f0c",
    "tag": "2933c5f3d9ac608d7a0d1b1f7c3b974e",
    "frame": "001122334455aabbccddeeff0e000114040000003761967d7ded97b698e1dcecd40e77dd57ad831f821c4e90bf0cdabb8d298f0c2933c5f3d9ac608d7a0d1b1f7c3b974e",
    "decoded": {
      "command_id": "202122232425262728292a2b2c2d2e2f",
      "device_id": "0102030405060708090a0b0c0d0e0f10"
    }
  },
  "heartbeat": {
    "dest_mac": "001122334455",
    "src_mac": "aabbccddeeff",
    "frame_id": 15,
    "ttl": 1,
    "type": 21,
    "header": "001122334455aabbccddeeff0f000115",
    "plaintext": "",
    "counter_nonce": "05000000",
    "gcm_nonce": "000000000000000005000000",
    "ciphertext": "",
    "tag": "46c471b1f0ac48a6e2dd6fca8a6250a0",
    "frame": "001122334455aabbccddeeff0f0001150500000046c471b1f0ac48a6e2dd6fca8a6250a0"
  }
}
```

- [ ] **Step 2: Verify the file is valid JSON**

Run: `node -e "JSON.parse(require('fs').readFileSync('docs/espnow/operational-test-vectors.json', 'utf8')); console.log('valid json')"` (from the repo root)
Expected: `valid json`

- [ ] **Step 3: Commit**

```bash
git add docs/espnow/operational-test-vectors.json
git commit -m "docs: add canonical ESP-NOW operational-frame test vector fixture (DISCOVERY/CONFIG/CONFIG_ACK/COMMAND/EXECUTED/HEARTBEAT)"
```

---

### Task 2: Java operational codec — CONFIG/CONFIG_ACK/COMMAND/EXECUTED/HEARTBEAT

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/operational/OperationalCodec.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/operational/OperationalCodecTest.java`

**Interfaces:**
- Consumes: `docs/espnow/operational-test-vectors.json` (Task 1), resolved as `../docs/espnow/operational-test-vectors.json` relative to the Maven working directory. `ch.jp.shooting.node.frame.FrameHeader`/`FrameType` (Baustein B), `ch.jp.shooting.node.frame.PairingTestVectors.hex(String)` (Baustein B, `public`, directly importable). `ch.jp.shooting.node.crypto.AesGcm.encrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) -> byte[]` / `AesGcm.decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertextAndTag) -> byte[]` (throws `GeneralSecurityException`) (Baustein A).
- Produces: `OperationalCodec.ConfigBody`/`CommandBody`/`ExecutedBody` records; `buildConfig(...)`/`parseConfig(byte[], byte[]) -> ConfigBody`; `buildConfigAck(...)`/`verifyConfigAck(byte[], byte[]) -> boolean`; `buildCommand(...)`/`parseCommand(byte[], byte[]) -> CommandBody`; `buildExecuted(...)`/`parseExecuted(byte[], byte[]) -> ExecutedBody`; `buildHeartbeat(...)`/`verifyHeartbeat(byte[], byte[]) -> boolean`; `counterNonceOf(byte[]) -> byte[]`. Task 3 (DISCOVERY) extends this same class/file and reuses its private `wrap`/`unwrap` helpers and package-private `concat`/`u16le`/`u16leAt` helpers.

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.operational;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import ch.jp.shooting.node.frame.PairingTestVectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalCodecTest {

    private static JsonNode fixture() {
        try {
            return new ObjectMapper().readTree(new File("../docs/espnow/operational-test-vectors.json"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FrameHeader headerFrom(JsonNode v, FrameType type) {
        return new FrameHeader(
                PairingTestVectors.hex(v.get("dest_mac").asText()),
                PairingTestVectors.hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), type);
    }

    private static byte[] kS() {
        return PairingTestVectors.hex(fixture().get("constants").get("k_s").asText());
    }

    @Test
    void buildConfig_matchesFixtureFrame() {
        JsonNode v = fixture().get("config");
        JsonNode d = v.get("decoded");
        FrameHeader header = headerFrom(v, FrameType.CONFIG);

        byte[] frame = OperationalCodec.buildConfig(header, PairingTestVectors.hex(v.get("counter_nonce").asText()),
                kS(), PairingTestVectors.hex(d.get("device_id").asText()), d.get("device_index").asInt(),
                d.get("device_count").asInt(), d.get("alias").asText(), d.get("device_type").asInt(),
                d.get("direction").asInt(), d.get("command").asText(), d.get("signal_duration_ms").asInt(),
                d.get("blocked").asBoolean());

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void parseConfig_extractsFixtureFields() {
        JsonNode v = fixture().get("config");
        JsonNode d = v.get("decoded");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        OperationalCodec.ConfigBody body = OperationalCodec.parseConfig(frame, kS());

        assertThat(body.deviceId()).isEqualTo(PairingTestVectors.hex(d.get("device_id").asText()));
        assertThat(body.deviceIndex()).isEqualTo(d.get("device_index").asInt());
        assertThat(body.deviceCount()).isEqualTo(d.get("device_count").asInt());
        assertThat(body.alias()).isEqualTo(d.get("alias").asText());
        assertThat(body.deviceType()).isEqualTo(d.get("device_type").asInt());
        assertThat(body.direction()).isEqualTo(d.get("direction").asInt());
        assertThat(body.command()).isEqualTo(d.get("command").asText());
        assertThat(body.signalDurationMs()).isEqualTo(d.get("signal_duration_ms").asInt());
        assertThat(body.blocked()).isEqualTo(d.get("blocked").asBoolean());
    }

    @Test
    void buildConfigAck_matchesFixtureFrame_and_verifyConfigAck_accepts() {
        JsonNode v = fixture().get("config_ack");
        FrameHeader header = headerFrom(v, FrameType.CONFIG_ACK);

        byte[] frame = OperationalCodec.buildConfigAck(header, PairingTestVectors.hex(v.get("counter_nonce").asText()), kS());

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
        assertThat(OperationalCodec.verifyConfigAck(frame, kS())).isTrue();
    }

    @Test
    void verifyConfigAck_rejectsTamperedTag() {
        JsonNode v = fixture().get("config_ack");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());
        frame[frame.length - 1] ^= 0x01;

        assertThat(OperationalCodec.verifyConfigAck(frame, kS())).isFalse();
    }

    @Test
    void buildCommand_matchesFixtureFrame() {
        JsonNode v = fixture().get("command");
        JsonNode d = v.get("decoded");
        FrameHeader header = headerFrom(v, FrameType.COMMAND);

        byte[] frame = OperationalCodec.buildCommand(header, PairingTestVectors.hex(v.get("counter_nonce").asText()),
                kS(), PairingTestVectors.hex(d.get("command_id").asText()), PairingTestVectors.hex(d.get("device_id").asText()),
                d.get("command").asInt(), d.get("signal_duration_ms").asInt());

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void parseCommand_extractsFixtureFields() {
        JsonNode v = fixture().get("command");
        JsonNode d = v.get("decoded");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        OperationalCodec.CommandBody body = OperationalCodec.parseCommand(frame, kS());

        assertThat(body.commandId()).isEqualTo(PairingTestVectors.hex(d.get("command_id").asText()));
        assertThat(body.deviceId()).isEqualTo(PairingTestVectors.hex(d.get("device_id").asText()));
        assertThat(body.command()).isEqualTo(d.get("command").asInt());
        assertThat(body.signalDurationMs()).isEqualTo(d.get("signal_duration_ms").asInt());
    }

    @Test
    void buildExecuted_matchesFixtureFrame() {
        JsonNode v = fixture().get("executed");
        JsonNode d = v.get("decoded");
        FrameHeader header = headerFrom(v, FrameType.EXECUTED);

        byte[] frame = OperationalCodec.buildExecuted(header, PairingTestVectors.hex(v.get("counter_nonce").asText()),
                kS(), PairingTestVectors.hex(d.get("command_id").asText()), PairingTestVectors.hex(d.get("device_id").asText()));

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void parseExecuted_extractsFixtureFields() {
        JsonNode v = fixture().get("executed");
        JsonNode d = v.get("decoded");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        OperationalCodec.ExecutedBody body = OperationalCodec.parseExecuted(frame, kS());

        assertThat(body.commandId()).isEqualTo(PairingTestVectors.hex(d.get("command_id").asText()));
        assertThat(body.deviceId()).isEqualTo(PairingTestVectors.hex(d.get("device_id").asText()));
    }

    @Test
    void buildHeartbeat_matchesFixtureFrame_and_verifyHeartbeat_accepts() {
        JsonNode v = fixture().get("heartbeat");
        FrameHeader header = headerFrom(v, FrameType.HEARTBEAT);

        byte[] frame = OperationalCodec.buildHeartbeat(header, PairingTestVectors.hex(v.get("counter_nonce").asText()), kS());

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
        assertThat(OperationalCodec.verifyHeartbeat(frame, kS())).isTrue();
    }

    @Test
    void counterNonceOf_extractsFixtureValue() {
        JsonNode v = fixture().get("command");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(OperationalCodec.counterNonceOf(frame)).isEqualTo(PairingTestVectors.hex(v.get("counter_nonce").asText()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=OperationalCodecTest`
Expected: FAIL (compile error) — `OperationalCodec` does not exist yet

- [ ] **Step 3: Write `OperationalCodec.java`**

```java
package ch.jp.shooting.node.operational;

import ch.jp.shooting.node.crypto.AesGcm;
import ch.jp.shooting.node.frame.FrameHeader;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Baut und parst die Betriebs-Frames (CONFIG, CONFIG_ACK, COMMAND, EXECUTED, HEARTBEAT) unter K_S.
 * DISCOVERY (mit Capability-Codierung) ist eine Erweiterung dieser Klasse, siehe Baustein-C-Folgeaufgabe.
 * docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 4,
 * docs/superpowers/specs/2026-07-10-espnow-operational-frames-design.md.
 */
public final class OperationalCodec {

    private static final int COUNTER_NONCE_LENGTH = 4;

    private OperationalCodec() {
    }

    // --- Gemeinsamer AES-GCM-Wrapper (Nonce = 8 Null-Bytes ‖ counter_nonce(4)) ---

    private static byte[] wrap(FrameHeader header, byte[] counterNonce, byte[] plaintext, byte[] kS) {
        byte[] headerBytes = header.encode();
        byte[] gcmNonce = concat(new byte[8], counterNonce);
        byte[] ciphertextAndTag = AesGcm.encrypt(kS, gcmNonce, headerBytes, plaintext);
        return concat(headerBytes, counterNonce, ciphertextAndTag);
    }

    private static byte[] unwrap(byte[] frame, byte[] kS) {
        byte[] headerBytes = Arrays.copyOfRange(frame, 0, FrameHeader.SIZE);
        byte[] counterNonce = Arrays.copyOfRange(frame, FrameHeader.SIZE, FrameHeader.SIZE + COUNTER_NONCE_LENGTH);
        byte[] ciphertextAndTag = Arrays.copyOfRange(frame, FrameHeader.SIZE + COUNTER_NONCE_LENGTH, frame.length);
        byte[] gcmNonce = concat(new byte[8], counterNonce);
        try {
            return AesGcm.decrypt(kS, gcmNonce, headerBytes, ciphertextAndTag);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Betriebs-Frame: Entschluesselung fehlgeschlagen", e);
        }
    }

    public static byte[] counterNonceOf(byte[] frame) {
        return Arrays.copyOfRange(frame, FrameHeader.SIZE, FrameHeader.SIZE + COUNTER_NONCE_LENGTH);
    }

    // --- CONFIG ---

    public record ConfigBody(byte[] deviceId, int deviceIndex, int deviceCount, String alias, int deviceType,
                              int direction, String command, int signalDurationMs, boolean blocked) {
    }

    public static byte[] buildConfig(FrameHeader header, byte[] counterNonce, byte[] kS, byte[] deviceId,
                                      int deviceIndex, int deviceCount, String alias, int deviceType, int direction,
                                      String command, int signalDurationMs, boolean blocked) {
        byte[] aliasBytes = alias.getBytes(StandardCharsets.UTF_8);
        byte[] commandBytes = command.getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = concat(
                deviceId,
                new byte[]{(byte) deviceIndex, (byte) deviceCount, (byte) aliasBytes.length},
                aliasBytes,
                new byte[]{(byte) deviceType, (byte) direction, (byte) commandBytes.length},
                commandBytes,
                u16le(signalDurationMs),
                new byte[]{(byte) (blocked ? 1 : 0)}
        );
        return wrap(header, counterNonce, plaintext, kS);
    }

    public static ConfigBody parseConfig(byte[] frame, byte[] kS) {
        byte[] p = unwrap(frame, kS);
        int pos = 0;
        byte[] deviceId = Arrays.copyOfRange(p, pos, pos + 16);
        pos += 16;
        int deviceIndex = p[pos++] & 0xFF;
        int deviceCount = p[pos++] & 0xFF;
        int aliasLen = p[pos++] & 0xFF;
        String alias = new String(p, pos, aliasLen, StandardCharsets.UTF_8);
        pos += aliasLen;
        int deviceType = p[pos++] & 0xFF;
        int direction = p[pos++] & 0xFF;
        int commandLen = p[pos++] & 0xFF;
        String command = new String(p, pos, commandLen, StandardCharsets.UTF_8);
        pos += commandLen;
        int signalDurationMs = u16leAt(p, pos);
        pos += 2;
        boolean blocked = p[pos] != 0;
        return new ConfigBody(deviceId, deviceIndex, deviceCount, alias, deviceType, direction, command,
                signalDurationMs, blocked);
    }

    // --- CONFIG_ACK ---

    public static byte[] buildConfigAck(FrameHeader header, byte[] counterNonce, byte[] kS) {
        return wrap(header, counterNonce, new byte[0], kS);
    }

    public static boolean verifyConfigAck(byte[] frame, byte[] kS) {
        try {
            unwrap(frame, kS);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // --- COMMAND ---

    public record CommandBody(byte[] commandId, byte[] deviceId, int command, int signalDurationMs) {
    }

    public static byte[] buildCommand(FrameHeader header, byte[] counterNonce, byte[] kS, byte[] commandId,
                                       byte[] deviceId, int command, int signalDurationMs) {
        byte[] plaintext = concat(commandId, deviceId, new byte[]{(byte) command}, u16le(signalDurationMs));
        return wrap(header, counterNonce, plaintext, kS);
    }

    public static CommandBody parseCommand(byte[] frame, byte[] kS) {
        byte[] p = unwrap(frame, kS);
        byte[] commandId = Arrays.copyOfRange(p, 0, 16);
        byte[] deviceId = Arrays.copyOfRange(p, 16, 32);
        int command = p[32] & 0xFF;
        int signalDurationMs = u16leAt(p, 33);
        return new CommandBody(commandId, deviceId, command, signalDurationMs);
    }

    // --- EXECUTED ---

    public record ExecutedBody(byte[] commandId, byte[] deviceId) {
    }

    public static byte[] buildExecuted(FrameHeader header, byte[] counterNonce, byte[] kS, byte[] commandId,
                                        byte[] deviceId) {
        byte[] plaintext = concat(commandId, deviceId);
        return wrap(header, counterNonce, plaintext, kS);
    }

    public static ExecutedBody parseExecuted(byte[] frame, byte[] kS) {
        byte[] p = unwrap(frame, kS);
        byte[] commandId = Arrays.copyOfRange(p, 0, 16);
        byte[] deviceId = Arrays.copyOfRange(p, 16, 32);
        return new ExecutedBody(commandId, deviceId);
    }

    // --- HEARTBEAT ---

    public static byte[] buildHeartbeat(FrameHeader header, byte[] counterNonce, byte[] kS) {
        return wrap(header, counterNonce, new byte[0], kS);
    }

    public static boolean verifyHeartbeat(byte[] frame, byte[] kS) {
        try {
            unwrap(frame, kS);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // --- Hilfsfunktionen ---

    static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return out;
    }

    static byte[] u16le(int value) {
        return new byte[]{(byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF)};
    }

    static int u16leAt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=OperationalCodecTest`
Expected: `BUILD SUCCESS`, `Tests run: 10, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full Java test suite**

Run: `cd smart-ground-node && mvn test`
Expected: `BUILD SUCCESS`, all tests pass, `Failures: 0, Errors: 0` (no regressions in Baustein A/B tests)

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/operational/OperationalCodec.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/operational/OperationalCodecTest.java
git commit -m "feat(node): add operational frame codec (CONFIG/CONFIG_ACK/COMMAND/EXECUTED/HEARTBEAT), verified against fixture"
```

---

### Task 3: Java DISCOVERY codec + compact capability encoding

**Files:**
- Modify: `smart-ground-node/src/main/java/ch/jp/shooting/node/operational/OperationalCodec.java` (add imports, records, `buildDiscovery`/`parseDiscovery`, `i32le`/`i32leAt` helpers)
- Modify: `smart-ground-node/src/test/java/ch/jp/shooting/node/operational/OperationalCodecTest.java` (add `DISCOVERY` tests)

**Interfaces:**
- Consumes: same fixture as Task 2 (`discovery` entry), `OperationalCodec`'s private `wrap`/`unwrap` and package-private `concat` helpers (Task 2, same file).
- Produces: `OperationalCodec.ConfigField(int fieldId, int typeId, byte[] defaultBytes)`, `OperationalCodec.DeviceTypeCapability(int deviceTypeId, int directionsBitmask, int commandsBitmask, List<ConfigField> configFields)`, `OperationalCodec.DiscoveryBody(int appVersionMajor, int appVersionMinor, int configSchemaVersion, String boxType, List<DeviceTypeCapability> deviceTypes)`; `buildDiscovery(FrameHeader, byte[] counterNonce, byte[] kS, int appVersionMajor, int appVersionMinor, int configSchemaVersion, String boxType, List<DeviceTypeCapability> deviceTypes) -> byte[]`; `parseDiscovery(byte[], byte[]) -> DiscoveryBody`; `public static byte[] i32le(int)` / `public static int i32leAt(byte[], int)` (public utility for encoding/decoding 4-byte LE config-field defaults).

- [ ] **Step 1: Write the failing test — append to `OperationalCodecTest.java`**

Add these imports at the top of the file (alongside the existing ones):

```java
import java.util.ArrayList;
import java.util.List;
```

Add these test methods inside the `OperationalCodecTest` class:

```java
    @Test
    void buildDiscovery_matchesFixtureFrame() {
        JsonNode v = fixture().get("discovery");
        JsonNode d = v.get("decoded");
        FrameHeader header = headerFrom(v, FrameType.DISCOVERY);
        String[] versionParts = d.get("app_version").asText().split("\\.");

        List<OperationalCodec.DeviceTypeCapability> deviceTypes = new ArrayList<>();
        for (JsonNode dt : d.get("device_types")) {
            List<OperationalCodec.ConfigField> fields = new ArrayList<>();
            for (JsonNode f : dt.get("config_fields")) {
                fields.add(new OperationalCodec.ConfigField(f.get("field_id").asInt(), f.get("type_id").asInt(),
                        OperationalCodec.i32le(f.get("default").asInt())));
            }
            deviceTypes.add(new OperationalCodec.DeviceTypeCapability(dt.get("device_type_id").asInt(),
                    dt.get("directions_bitmask").asInt(), dt.get("commands_bitmask").asInt(), fields));
        }

        byte[] frame = OperationalCodec.buildDiscovery(header, PairingTestVectors.hex(v.get("counter_nonce").asText()),
                kS(), Integer.parseInt(versionParts[0]), Integer.parseInt(versionParts[1]),
                d.get("config_schema_version").asInt(), d.get("box_type").asText(), deviceTypes);

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void parseDiscovery_extractsFixtureFields() {
        JsonNode v = fixture().get("discovery");
        JsonNode d = v.get("decoded");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());
        String[] versionParts = d.get("app_version").asText().split("\\.");

        OperationalCodec.DiscoveryBody body = OperationalCodec.parseDiscovery(frame, kS());

        assertThat(body.appVersionMajor()).isEqualTo(Integer.parseInt(versionParts[0]));
        assertThat(body.appVersionMinor()).isEqualTo(Integer.parseInt(versionParts[1]));
        assertThat(body.configSchemaVersion()).isEqualTo(d.get("config_schema_version").asInt());
        assertThat(body.boxType()).isEqualTo(d.get("box_type").asText());
        assertThat(body.deviceTypes()).hasSize(d.get("device_types").size());

        for (int i = 0; i < body.deviceTypes().size(); i++) {
            OperationalCodec.DeviceTypeCapability parsed = body.deviceTypes().get(i);
            JsonNode expected = d.get("device_types").get(i);
            assertThat(parsed.deviceTypeId()).isEqualTo(expected.get("device_type_id").asInt());
            assertThat(parsed.directionsBitmask()).isEqualTo(expected.get("directions_bitmask").asInt());
            assertThat(parsed.commandsBitmask()).isEqualTo(expected.get("commands_bitmask").asInt());
            assertThat(parsed.configFields()).hasSize(expected.get("config_fields").size());
            for (int j = 0; j < parsed.configFields().size(); j++) {
                OperationalCodec.ConfigField parsedField = parsed.configFields().get(j);
                JsonNode expectedField = expected.get("config_fields").get(j);
                assertThat(parsedField.fieldId()).isEqualTo(expectedField.get("field_id").asInt());
                assertThat(parsedField.typeId()).isEqualTo(expectedField.get("type_id").asInt());
                assertThat(OperationalCodec.i32leAt(parsedField.defaultBytes(), 0))
                        .isEqualTo(expectedField.get("default").asInt());
            }
        }
    }

    @Test
    void parseDiscovery_rejectsTamperedTag() {
        byte[] frame = PairingTestVectors.hex(fixture().get("discovery").get("frame").asText());
        frame[frame.length - 1] ^= 0x01;

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> OperationalCodec.parseDiscovery(frame, kS()));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=OperationalCodecTest`
Expected: FAIL (compile error) — `buildDiscovery`/`parseDiscovery`/`ConfigField`/`DeviceTypeCapability`/`DiscoveryBody`/`i32le`/`i32leAt` do not exist yet

- [ ] **Step 3: Extend `OperationalCodec.java`**

Add these imports at the top of `OperationalCodec.java` (alongside the existing ones):

```java
import java.util.ArrayList;
import java.util.List;
```

Add these members inside the `OperationalCodec` class (anywhere after the `HEARTBEAT` section, before the `// --- Hilfsfunktionen ---` section):

```java
    // --- DISCOVERY ---

    public record ConfigField(int fieldId, int typeId, byte[] defaultBytes) {
    }

    public record DeviceTypeCapability(int deviceTypeId, int directionsBitmask, int commandsBitmask,
                                        List<ConfigField> configFields) {
    }

    public record DiscoveryBody(int appVersionMajor, int appVersionMinor, int configSchemaVersion, String boxType,
                                 List<DeviceTypeCapability> deviceTypes) {
    }

    public static byte[] buildDiscovery(FrameHeader header, byte[] counterNonce, byte[] kS, int appVersionMajor,
                                         int appVersionMinor, int configSchemaVersion, String boxType,
                                         List<DeviceTypeCapability> deviceTypes) {
        byte[] boxTypeBytes = boxType.getBytes(StandardCharsets.UTF_8);
        List<byte[]> parts = new ArrayList<>();
        parts.add(new byte[]{(byte) appVersionMajor, (byte) appVersionMinor, (byte) configSchemaVersion,
                (byte) boxTypeBytes.length});
        parts.add(boxTypeBytes);
        parts.add(new byte[]{(byte) deviceTypes.size()});
        for (DeviceTypeCapability dt : deviceTypes) {
            parts.add(new byte[]{(byte) dt.deviceTypeId(), (byte) dt.directionsBitmask(), (byte) dt.commandsBitmask(),
                    (byte) dt.configFields().size()});
            for (ConfigField f : dt.configFields()) {
                parts.add(new byte[]{(byte) f.fieldId(), (byte) f.typeId(), (byte) f.defaultBytes().length});
                parts.add(f.defaultBytes());
            }
        }
        byte[] plaintext = concat(parts.toArray(new byte[0][]));
        return wrap(header, counterNonce, plaintext, kS);
    }

    public static DiscoveryBody parseDiscovery(byte[] frame, byte[] kS) {
        byte[] p = unwrap(frame, kS);
        int pos = 0;
        int major = p[pos++] & 0xFF;
        int minor = p[pos++] & 0xFF;
        int schemaVersion = p[pos++] & 0xFF;
        int boxTypeLen = p[pos++] & 0xFF;
        String boxType = new String(p, pos, boxTypeLen, StandardCharsets.UTF_8);
        pos += boxTypeLen;
        int deviceTypeCount = p[pos++] & 0xFF;
        List<DeviceTypeCapability> deviceTypes = new ArrayList<>();
        for (int i = 0; i < deviceTypeCount; i++) {
            int deviceTypeId = p[pos++] & 0xFF;
            int directionsBitmask = p[pos++] & 0xFF;
            int commandsBitmask = p[pos++] & 0xFF;
            int fieldCount = p[pos++] & 0xFF;
            List<ConfigField> fields = new ArrayList<>();
            for (int j = 0; j < fieldCount; j++) {
                int fieldId = p[pos++] & 0xFF;
                int typeId = p[pos++] & 0xFF;
                int defaultLen = p[pos++] & 0xFF;
                byte[] defaultBytes = Arrays.copyOfRange(p, pos, pos + defaultLen);
                pos += defaultLen;
                fields.add(new ConfigField(fieldId, typeId, defaultBytes));
            }
            deviceTypes.add(new DeviceTypeCapability(deviceTypeId, directionsBitmask, commandsBitmask, fields));
        }
        return new DiscoveryBody(major, minor, schemaVersion, boxType, deviceTypes);
    }
```

Add these helpers next to the existing `u16le`/`u16leAt` methods in the `// --- Hilfsfunktionen ---` section:

```java
    public static byte[] i32le(int value) {
        return new byte[]{
                (byte) (value & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 24) & 0xFF)
        };
    }

    public static int i32leAt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=OperationalCodecTest`
Expected: `BUILD SUCCESS`, `Tests run: 13, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full Java test suite**

Run: `cd smart-ground-node && mvn test`
Expected: `BUILD SUCCESS`, all tests pass, `Failures: 0, Errors: 0` (exact total isn't critical, no regressions is)

- [ ] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/operational/OperationalCodec.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/operational/OperationalCodecTest.java
git commit -m "feat(node): add DISCOVERY codec with compact capability encoding, verified against fixture"
```

---

### Task 4: MicroPython operational codec — CONFIG/CONFIG_ACK/COMMAND/EXECUTED/HEARTBEAT

**Files:**
- Create: `smart-box/operational_codec.py`
- Create: `smart-box/tests/test_operational_codec.py`

**Interfaces:**
- Consumes: `docs/espnow/operational-test-vectors.json` (Task 1) via a relative path from `smart-box/`, same graceful-skip-if-missing guard pattern as `test_espnow_crypto.py`/`test_pairing_codec.py`. `frame_envelope.pack_header`/`HEADER_SIZE`/`TYPE_CONFIG`/`TYPE_CONFIG_ACK`/`TYPE_COMMAND`/`TYPE_EXECUTED`/`TYPE_HEARTBEAT` (Baustein B), `espnow_crypto.aes256_gcm_encrypt`/`aes256_gcm_decrypt` (Baustein A).
- Produces: `build_config(...) -> bytes`, `parse_config(frame, k_s) -> dict`, `build_config_ack(...) -> bytes`, `verify_config_ack(frame, k_s) -> bool`, `build_command(...) -> bytes`, `parse_command(frame, k_s) -> dict`, `build_executed(...) -> bytes`, `parse_executed(frame, k_s) -> dict`, `build_heartbeat(...) -> bytes`, `verify_heartbeat(frame, k_s) -> bool`, `counter_nonce_of(frame) -> bytes` — mirrors the Java `OperationalCodec`'s function set exactly (same fixture, same expected output on every function). Task 5 (DISCOVERY) extends this same module and reuses its private `_wrap`/`_unwrap` helpers.

- [ ] **Step 1: Write the failing test**

```python
from tests import _stubs
import json
import os
import unittest

import operational_codec

_FIXTURE_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "..", "docs", "espnow", "operational-test-vectors.json",
)

if not os.path.isfile(_FIXTURE_PATH):
    raise unittest.SkipTest(
        "Betriebs-Frame-Test-Vektoren nicht gefunden unter " + _FIXTURE_PATH +
        " — dieses Modul braucht das smart-box-Repo als Sub-Checkout im"
        " smart-ground-Monorepo. Bei einem eigenstaendigen Checkout von"
        " smartground-firmware werden diese Tests uebersprungen."
    )


def _load_fixture():
    with open(_FIXTURE_PATH) as f:
        return json.load(f)


class ConfigTest(unittest.TestCase):
    def test_build_config_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["config"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_config(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
            bytes.fromhex(d["device_id"]), d["device_index"], d["device_count"], d["alias"],
            d["device_type"], d["direction"], d["command"], d["signal_duration_ms"], d["blocked"],
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_parse_config_extracts_fixture_fields(self):
        f = _load_fixture()
        v = f["config"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        frame = bytes.fromhex(v["frame"])

        body = operational_codec.parse_config(frame, k_s)

        self.assertEqual(body["device_id"], bytes.fromhex(d["device_id"]))
        self.assertEqual(body["device_index"], d["device_index"])
        self.assertEqual(body["device_count"], d["device_count"])
        self.assertEqual(body["alias"], d["alias"])
        self.assertEqual(body["device_type"], d["device_type"])
        self.assertEqual(body["direction"], d["direction"])
        self.assertEqual(body["command"], d["command"])
        self.assertEqual(body["signal_duration_ms"], d["signal_duration_ms"])
        self.assertEqual(body["blocked"], d["blocked"])


class ConfigAckTest(unittest.TestCase):
    def test_build_config_ack_matches_fixture_and_verify_accepts(self):
        f = _load_fixture()
        v = f["config_ack"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_config_ack(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))
        self.assertTrue(operational_codec.verify_config_ack(frame, k_s))

    def test_verify_config_ack_rejects_tampered_tag(self):
        f = _load_fixture()
        v = f["config_ack"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        tampered = bytearray(bytes.fromhex(v["frame"]))
        tampered[-1] ^= 0x01

        self.assertFalse(operational_codec.verify_config_ack(bytes(tampered), k_s))


class CommandTest(unittest.TestCase):
    def test_build_command_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["command"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_command(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
            bytes.fromhex(d["command_id"]), bytes.fromhex(d["device_id"]), d["command"], d["signal_duration_ms"],
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_parse_command_extracts_fixture_fields(self):
        f = _load_fixture()
        v = f["command"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        frame = bytes.fromhex(v["frame"])

        body = operational_codec.parse_command(frame, k_s)

        self.assertEqual(body["command_id"], bytes.fromhex(d["command_id"]))
        self.assertEqual(body["device_id"], bytes.fromhex(d["device_id"]))
        self.assertEqual(body["command"], d["command"])
        self.assertEqual(body["signal_duration_ms"], d["signal_duration_ms"])


class ExecutedTest(unittest.TestCase):
    def test_build_executed_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["executed"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_executed(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
            bytes.fromhex(d["command_id"]), bytes.fromhex(d["device_id"]),
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_parse_executed_extracts_fixture_fields(self):
        f = _load_fixture()
        v = f["executed"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        frame = bytes.fromhex(v["frame"])

        body = operational_codec.parse_executed(frame, k_s)

        self.assertEqual(body["command_id"], bytes.fromhex(d["command_id"]))
        self.assertEqual(body["device_id"], bytes.fromhex(d["device_id"]))


class HeartbeatTest(unittest.TestCase):
    def test_build_heartbeat_matches_fixture_and_verify_accepts(self):
        f = _load_fixture()
        v = f["heartbeat"]
        k_s = bytes.fromhex(f["constants"]["k_s"])

        frame = operational_codec.build_heartbeat(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))
        self.assertTrue(operational_codec.verify_heartbeat(frame, k_s))


class CounterNonceTest(unittest.TestCase):
    def test_counter_nonce_of_extracts_fixture_value(self):
        v = _load_fixture()["command"]
        frame = bytes.fromhex(v["frame"])

        self.assertEqual(operational_codec.counter_nonce_of(frame), bytes.fromhex(v["counter_nonce"]))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-box && python -m unittest tests.test_operational_codec -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'operational_codec'`

- [ ] **Step 3: Write `operational_codec.py`**

```python
"""Baut und parst die Betriebs-Frames (CONFIG, CONFIG_ACK, COMMAND, EXECUTED, HEARTBEAT) unter K_S.

DISCOVERY (mit Capability-Codierung) folgt als Erweiterung dieses Moduls (siehe Baustein-C-Folgeaufgabe).
docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 4,
docs/superpowers/specs/2026-07-10-espnow-operational-frames-design.md.
"""
import espnow_crypto
from frame_envelope import (
    HEADER_SIZE,
    TYPE_CONFIG,
    TYPE_CONFIG_ACK,
    TYPE_COMMAND,
    TYPE_EXECUTED,
    TYPE_HEARTBEAT,
    pack_header,
)

COUNTER_NONCE_LENGTH = 4


def _wrap(header, counter_nonce, plaintext, k_s):
    gcm_nonce = bytes(8) + counter_nonce
    ciphertext_and_tag = espnow_crypto.aes256_gcm_encrypt(k_s, gcm_nonce, header, plaintext)
    return header + counter_nonce + ciphertext_and_tag


def _unwrap(frame, k_s):
    header = frame[:HEADER_SIZE]
    counter_nonce = frame[HEADER_SIZE:HEADER_SIZE + COUNTER_NONCE_LENGTH]
    ciphertext_and_tag = frame[HEADER_SIZE + COUNTER_NONCE_LENGTH:]
    gcm_nonce = bytes(8) + counter_nonce
    return espnow_crypto.aes256_gcm_decrypt(k_s, gcm_nonce, header, ciphertext_and_tag)


def counter_nonce_of(frame):
    return frame[HEADER_SIZE:HEADER_SIZE + COUNTER_NONCE_LENGTH]


def build_config(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s, device_id, device_index, device_count,
                  alias, device_type, direction, command, signal_duration_ms, blocked):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_CONFIG)
    alias_bytes = alias.encode("utf-8")
    command_bytes = command.encode("utf-8")
    plaintext = (
        device_id
        + bytes([device_index, device_count, len(alias_bytes)])
        + alias_bytes
        + bytes([device_type, direction, len(command_bytes)])
        + command_bytes
        + bytes([signal_duration_ms & 0xFF, (signal_duration_ms >> 8) & 0xFF])
        + bytes([1 if blocked else 0])
    )
    return _wrap(header, counter_nonce, plaintext, k_s)


def parse_config(frame, k_s):
    p = _unwrap(frame, k_s)
    pos = 0
    device_id = p[pos:pos + 16]
    pos += 16
    device_index = p[pos]
    pos += 1
    device_count = p[pos]
    pos += 1
    alias_len = p[pos]
    pos += 1
    alias = p[pos:pos + alias_len].decode("utf-8")
    pos += alias_len
    device_type = p[pos]
    pos += 1
    direction = p[pos]
    pos += 1
    command_len = p[pos]
    pos += 1
    command = p[pos:pos + command_len].decode("utf-8")
    pos += command_len
    signal_duration_ms = p[pos] | (p[pos + 1] << 8)
    pos += 2
    blocked = p[pos] != 0
    return {
        "device_id": device_id, "device_index": device_index, "device_count": device_count,
        "alias": alias, "device_type": device_type, "direction": direction, "command": command,
        "signal_duration_ms": signal_duration_ms, "blocked": blocked,
    }


def build_config_ack(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_CONFIG_ACK)
    return _wrap(header, counter_nonce, b"", k_s)


def verify_config_ack(frame, k_s):
    try:
        _unwrap(frame, k_s)
        return True
    except ValueError:
        return False


def build_command(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s, command_id, device_id, command,
                   signal_duration_ms):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_COMMAND)
    plaintext = command_id + device_id + bytes([command]) + bytes(
        [signal_duration_ms & 0xFF, (signal_duration_ms >> 8) & 0xFF])
    return _wrap(header, counter_nonce, plaintext, k_s)


def parse_command(frame, k_s):
    p = _unwrap(frame, k_s)
    command_id = p[0:16]
    device_id = p[16:32]
    command = p[32]
    signal_duration_ms = p[33] | (p[34] << 8)
    return {"command_id": command_id, "device_id": device_id, "command": command,
            "signal_duration_ms": signal_duration_ms}


def build_executed(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s, command_id, device_id):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_EXECUTED)
    plaintext = command_id + device_id
    return _wrap(header, counter_nonce, plaintext, k_s)


def parse_executed(frame, k_s):
    p = _unwrap(frame, k_s)
    return {"command_id": p[0:16], "device_id": p[16:32]}


def build_heartbeat(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_HEARTBEAT)
    return _wrap(header, counter_nonce, b"", k_s)


def verify_heartbeat(frame, k_s):
    try:
        _unwrap(frame, k_s)
        return True
    except ValueError:
        return False
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-box && python -m unittest tests.test_operational_codec -v`
Expected: `OK`, all 10 tests pass

- [ ] **Step 5: Run the full smart-box test suite**

Run: `cd smart-box && python -m unittest discover -s tests -t . -v`
Expected: all tests pass, including the new `test_operational_codec` module, no regressions

- [ ] **Step 6: Commit** (inside `smart-box/`, using its `[firmware] ` prefix convention)

```bash
git add operational_codec.py tests/test_operational_codec.py
git commit -m "[firmware] add operational frame codec (CONFIG/CONFIG_ACK/COMMAND/EXECUTED/HEARTBEAT), verified against fixture"
```

---

### Task 5: MicroPython DISCOVERY codec + compact capability encoding

**Files:**
- Modify: `smart-box/operational_codec.py` (extend import line, add `build_discovery`/`parse_discovery`)
- Modify: `smart-box/tests/test_operational_codec.py` (add `DiscoveryTest`)

**Interfaces:**
- Consumes: same fixture as Task 4 (`discovery` entry), `TYPE_DISCOVERY` (Baustein B, `frame_envelope`), `operational_codec`'s private `_wrap`/`_unwrap` helpers (Task 4, same file).
- Produces: `build_discovery(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s, app_version_major, app_version_minor, config_schema_version, box_type, device_types) -> bytes` (`device_types` is a list of dicts, each `{"device_type_id", "directions_bitmask", "commands_bitmask", "config_fields"}` where `config_fields` is a list of `{"field_id", "type_id", "default_bytes"}`), `parse_discovery(frame, k_s) -> dict` — mirrors the Java `OperationalCodec.buildDiscovery`/`parseDiscovery` exactly (same fixture, same expected output).

- [ ] **Step 1: Write the failing test — append to `test_operational_codec.py`**

```python
class DiscoveryTest(unittest.TestCase):
    def test_build_discovery_matches_fixture_frame(self):
        f = _load_fixture()
        v = f["discovery"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        major, minor = (int(x) for x in d["app_version"].split("."))

        device_types = []
        for dt in d["device_types"]:
            fields = [
                {
                    "field_id": field["field_id"],
                    "type_id": field["type_id"],
                    "default_bytes": field["default"].to_bytes(4, "little"),
                }
                for field in dt["config_fields"]
            ]
            device_types.append({
                "device_type_id": dt["device_type_id"],
                "directions_bitmask": dt["directions_bitmask"],
                "commands_bitmask": dt["commands_bitmask"],
                "config_fields": fields,
            })

        frame = operational_codec.build_discovery(
            bytes.fromhex(v["dest_mac"]), bytes.fromhex(v["src_mac"]), v["frame_id"], v["ttl"],
            bytes.fromhex(v["counter_nonce"]), k_s,
            major, minor, d["config_schema_version"], d["box_type"], device_types,
        )

        self.assertEqual(frame, bytes.fromhex(v["frame"]))

    def test_parse_discovery_extracts_fixture_fields(self):
        f = _load_fixture()
        v = f["discovery"]
        d = v["decoded"]
        k_s = bytes.fromhex(f["constants"]["k_s"])
        frame = bytes.fromhex(v["frame"])
        major, minor = (int(x) for x in d["app_version"].split("."))

        body = operational_codec.parse_discovery(frame, k_s)

        self.assertEqual(body["app_version_major"], major)
        self.assertEqual(body["app_version_minor"], minor)
        self.assertEqual(body["config_schema_version"], d["config_schema_version"])
        self.assertEqual(body["box_type"], d["box_type"])
        self.assertEqual(len(body["device_types"]), len(d["device_types"]))

        for parsed_dt, fixture_dt in zip(body["device_types"], d["device_types"]):
            self.assertEqual(parsed_dt["device_type_id"], fixture_dt["device_type_id"])
            self.assertEqual(parsed_dt["directions_bitmask"], fixture_dt["directions_bitmask"])
            self.assertEqual(parsed_dt["commands_bitmask"], fixture_dt["commands_bitmask"])
            for parsed_field, fixture_field in zip(parsed_dt["config_fields"], fixture_dt["config_fields"]):
                self.assertEqual(parsed_field["field_id"], fixture_field["field_id"])
                self.assertEqual(parsed_field["type_id"], fixture_field["type_id"])
                self.assertEqual(
                    int.from_bytes(parsed_field["default_bytes"], "little"), fixture_field["default"]
                )

    def test_parse_discovery_rejects_tampered_tag(self):
        f = _load_fixture()
        k_s = bytes.fromhex(f["constants"]["k_s"])
        tampered = bytearray(bytes.fromhex(f["discovery"]["frame"]))
        tampered[-1] ^= 0x01

        with self.assertRaises(ValueError):
            operational_codec.parse_discovery(bytes(tampered), k_s)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd smart-box && python -m unittest tests.test_operational_codec.DiscoveryTest -v`
Expected: FAIL — `AttributeError: module 'operational_codec' has no attribute 'build_discovery'`

- [ ] **Step 3: Extend `operational_codec.py`**

Replace the existing `from frame_envelope import (...)` block at the top of the file with:

```python
from frame_envelope import (
    HEADER_SIZE,
    TYPE_CONFIG,
    TYPE_CONFIG_ACK,
    TYPE_COMMAND,
    TYPE_DISCOVERY,
    TYPE_EXECUTED,
    TYPE_HEARTBEAT,
    pack_header,
)
```

Add these functions at the end of the file:

```python
def build_discovery(dest_mac, src_mac, frame_id, ttl, counter_nonce, k_s, app_version_major, app_version_minor,
                     config_schema_version, box_type, device_types):
    header = pack_header(dest_mac, src_mac, frame_id, ttl, TYPE_DISCOVERY)
    box_type_bytes = box_type.encode("utf-8")
    plaintext = bytes([app_version_major, app_version_minor, config_schema_version, len(box_type_bytes)])
    plaintext += box_type_bytes
    plaintext += bytes([len(device_types)])
    for dt in device_types:
        fields = dt["config_fields"]
        plaintext += bytes([dt["device_type_id"], dt["directions_bitmask"], dt["commands_bitmask"], len(fields)])
        for field in fields:
            default_bytes = field["default_bytes"]
            plaintext += bytes([field["field_id"], field["type_id"], len(default_bytes)])
            plaintext += default_bytes
    return _wrap(header, counter_nonce, plaintext, k_s)


def parse_discovery(frame, k_s):
    p = _unwrap(frame, k_s)
    pos = 0
    app_version_major = p[pos]
    pos += 1
    app_version_minor = p[pos]
    pos += 1
    config_schema_version = p[pos]
    pos += 1
    box_type_len = p[pos]
    pos += 1
    box_type = p[pos:pos + box_type_len].decode("utf-8")
    pos += box_type_len
    device_type_count = p[pos]
    pos += 1
    device_types = []
    for _ in range(device_type_count):
        device_type_id = p[pos]
        pos += 1
        directions_bitmask = p[pos]
        pos += 1
        commands_bitmask = p[pos]
        pos += 1
        field_count = p[pos]
        pos += 1
        fields = []
        for _ in range(field_count):
            field_id = p[pos]
            pos += 1
            type_id = p[pos]
            pos += 1
            default_len = p[pos]
            pos += 1
            default_bytes = p[pos:pos + default_len]
            pos += default_len
            fields.append({"field_id": field_id, "type_id": type_id, "default_bytes": default_bytes})
        device_types.append({
            "device_type_id": device_type_id, "directions_bitmask": directions_bitmask,
            "commands_bitmask": commands_bitmask, "config_fields": fields,
        })
    return {
        "app_version_major": app_version_major, "app_version_minor": app_version_minor,
        "config_schema_version": config_schema_version, "box_type": box_type, "device_types": device_types,
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd smart-box && python -m unittest tests.test_operational_codec -v`
Expected: `OK`, all 13 tests pass

- [ ] **Step 5: Run the full smart-box test suite**

Run: `cd smart-box && python -m unittest discover -s tests -t . -v`
Expected: all tests pass, no regressions

- [ ] **Step 6: Commit** (inside `smart-box/`, using its `[firmware] ` prefix convention)

```bash
git add operational_codec.py tests/test_operational_codec.py
git commit -m "[firmware] add DISCOVERY codec with compact capability encoding, verified against fixture"
```

---

## Plan-Level Verification

- [ ] **Final check: run both full test suites once more from a clean state**

```bash
cd smart-ground-node && mvn test
cd ../smart-box && python -m unittest discover -s tests -t . -v
```

Expected: both `BUILD SUCCESS` (Java) and `OK` (MicroPython), zero failures. Java and MicroPython now both build/parse byte-identical `DISCOVERY`/`CONFIG`/`CONFIG_ACK`/`COMMAND`/`EXECUTED`/`HEARTBEAT` frames under `K_S` — the frame-level cross-language guarantee established in Baustein A/B now extends to the full operational protocol. Counter-nonce sequencing, Node MQTT-translation, `CONFIG` multi-frame accumulation, and heartbeat/offline-detection timers (Phase 2b/2d) and UART framing (Baustein D) remain separate follow-on work.

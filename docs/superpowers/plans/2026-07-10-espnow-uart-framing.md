# UART Framing Node ↔ Funkmodul (Baustein D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Implement the Node-side (Java, `smart-ground-node`) codec for the UART protocol between `smart-ground-node` and the ESP32 radio module — CRC-16 utility, command/response frame encode/decode, and a stateful streaming decoder that buffers partial serial reads and resynchronizes past corruption — per [2026-07-10-espnow-uart-framing-design.md](../specs/2026-07-10-espnow-uart-framing-design.md) (Baustein D of [plan-espnow-migration.md](../../plan-espnow-migration.md)).

**Architecture:** A stateless `Crc16` utility (CRC-16/CCITT-FALSE), a stateless `UartCodec` that builds the five Node→Radio command frames and parses the three Radio→Node response/event bodies from an already-decoded frame, and a stateful `UartFrameDecoder` that turns an arbitrary byte stream into a sequence of decoded frames, resynchronizing to the next `0x7E` on CRC mismatch or implausible length. `SEND`'s and `RECV`'s `esp_now_frame` payload is handled as opaque `byte[]` — no dependency on `FrameHeader`/`OperationalCodec`/`PairingCodec`.

**Tech Stack:** Java 25 (`smart-ground-node`, new package `ch.jp.shooting.node.uart`). Build/test with system `mvn` from `smart-ground-node/`.

## Global Constraints

- Java-only in this plan. No radio-module firmware, no real serial I/O (`jSerialComm`), no `RadioInterface`, no ACK-timeout/retry state machine — those are separate later work (Phase 2a/2b of the migration plan).
- Package `ch.jp.shooting.node.uart` under `smart-ground-node/src/main/java/`, tests under `smart-ground-node/src/test/java/ch/jp/shooting/node/uart/`. Comments German for domain logic, English identifiers (matches existing `ch.jp.shooting.node.*` packages).
- Reuse `ch.jp.shooting.node.frame.PairingTestVectors.hex(String)` (Baustein B, `public`, directly importable) for hex-literal byte arrays in tests — do not duplicate a hex-parsing helper.
- Frame layout (fixed, from the protocol spec): `start-byte(1, always 0x7E) ‖ length(2, uint16 LE, length of cmd_id..body) ‖ cmd_id(1) ‖ cmd(1) ‖ body(N) ‖ crc16(2, uint16 LE)`. CRC covers `cmd_id..body` (offset 3 through `4+N`), not the start byte or length field.
- CRC-16/CCITT-FALSE: `poly=0x1021`, `init=0xFFFF`, no reflection, no XOR-out. Standard check value: `crc16("123456789") == 0x29B1`.
- No byte-stuffing/escaping — `body`/`crc16` may contain `0x7E` anywhere without ambiguity, since the frame is length-prefixed, not delimiter-terminated. The start byte is a resync anchor only.
- `cmd` byte registry (fixed by this plan, invented here — no prior wire values existed):

  | cmd | value | direction | body |
  |---|---|---|---|
  | `SET_CHANNEL` | `0x01` | Node→Radio | `channel(1)` |
  | `ADD_PEER` | `0x02` | Node→Radio | `mac(6)` |
  | `DEL_PEER` | `0x03` | Node→Radio | `mac(6)` |
  | `SEND` | `0x04` | Node→Radio | `dest_mac(6) ‖ esp_now_frame(N)` |
  | `STATUS` | `0x05` | Node→Radio | — |
  | `ACK` | `0x80` | Radio→Node | `cmd_id(1) ‖ ok(1)`, and for a `STATUS` request additionally `‖ uptime_s(4, uint32 LE) ‖ free_heap(4, uint32 LE)` |
  | `RECV` | `0x81` | Radio→Node | `src_mac(6) ‖ rssi(1, signed int8) ‖ esp_now_frame(N)` |
  | `MAC_ACK` | `0x82` | Radio→Node | `dest_mac(6) ‖ frame_id(2, uint16 LE) ‖ ok(1)` |

- `ACK`'s outer `cmd_id` field echoes the request's `cmd_id`; the body always starts with `cmd_id(1) ‖ ok(1)`, with `STATUS`'s response appending `uptime_s`/`free_heap`. A generic `parseAck` call on a `STATUS` response body still returns the correct `cmdId`/`ok` (it only reads the first two bytes).
- `UartFrameDecoder` rejects (and resyncs past) any frame whose `length` field exceeds `MAX_FRAME_LENGTH = 512` — a sanity bound well above the largest real frame (`SEND` body ≤ 6 + 250 = 256 bytes, so `cmd_id..body` ≤ 258), guarding against unbounded buffering on garbage input.

---

### Task 1: CRC-16/CCITT-FALSE utility

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/uart/Crc16.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/uart/Crc16Test.java`

**Interfaces:**
- Produces: `Crc16.ccittFalse(byte[] data) -> int` (returns a value in `0..0xFFFF`). Used by Task 2 (`UartCodec`) and Task 3 (`UartFrameDecoder`).

- [x] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.uart;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Crc16Test {

    @Test
    void ccittFalse_matchesStandardCheckValue() {
        byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);

        assertThat(Crc16.ccittFalse(data)).isEqualTo(0x29B1);
    }

    @Test
    void ccittFalse_matchesSetChannelFrameExample() {
        byte[] data = {0x01, 0x01, 0x06};

        assertThat(Crc16.ccittFalse(data)).isEqualTo(0xA85B);
    }

    @Test
    void ccittFalse_ofEmptyInput_returnsInitValue() {
        assertThat(Crc16.ccittFalse(new byte[0])).isEqualTo(0xFFFF);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=Crc16Test`
Expected: FAIL (compile error) — `Crc16` does not exist yet

- [x] **Step 3: Write `Crc16.java`**

```java
package ch.jp.shooting.node.uart;

/**
 * CRC-16/CCITT-FALSE (poly=0x1021, init=0xFFFF, keine Reflektion, kein XOR-Out).
 * Sichert das UART-Framing zwischen smart-ground-node und dem Funkmodul ab.
 * docs/superpowers/specs/2026-07-10-espnow-uart-framing-design.md.
 */
public final class Crc16 {

    private static final int POLY = 0x1021;
    private static final int INIT = 0xFFFF;

    private Crc16() {
    }

    public static int ccittFalse(byte[] data) {
        int crc = INIT;
        for (byte value : data) {
            crc ^= (value & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ POLY) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }
        return crc;
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=Crc16Test`
Expected: `BUILD SUCCESS`, `Tests run: 3, Failures: 0, Errors: 0`

- [x] **Step 5: Run the full Java test suite**

Run: `cd smart-ground-node && mvn test`
Expected: `BUILD SUCCESS`, all tests pass (no regressions in Baustein A/B/C tests)

- [x] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/uart/Crc16.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/uart/Crc16Test.java
git commit -m "feat(node): add CRC-16/CCITT-FALSE utility for UART framing (Baustein D)"
```

---

### Task 2: UartCodec — command encode + response/event parse

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/uart/UartCodec.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/uart/UartCodecTest.java`

**Interfaces:**
- Consumes: `Crc16.ccittFalse(byte[]) -> int` (Task 1). `ch.jp.shooting.node.frame.PairingTestVectors.hex(String)` (Baustein B, test-only).
- Produces: `UartCodec.DecodedFrame(int cmdId, int cmd, byte[] body)` record (also used by Task 3 as the `UartFrameDecoder` output type); `UartCodec.AckBody(int cmdId, boolean ok)`, `UartCodec.StatusAckBody(int cmdId, boolean ok, long uptimeS, long freeHeap)`, `UartCodec.RecvBody(byte[] srcMac, int rssi, byte[] espNowFrame)`, `UartCodec.MacAckBody(byte[] destMac, int frameId, boolean ok)` records. `public static final int CMD_SET_CHANNEL/CMD_ADD_PEER/CMD_DEL_PEER/CMD_SEND/CMD_STATUS/CMD_ACK/CMD_RECV/CMD_MAC_ACK` constants (used by Task 3's tests to construct example frames). `encodeSetChannel(int cmdId, int channel) -> byte[]`, `encodeAddPeer(int cmdId, byte[] mac) -> byte[]`, `encodeDelPeer(int cmdId, byte[] mac) -> byte[]`, `encodeSend(int cmdId, byte[] destMac, byte[] espNowFrame) -> byte[]`, `encodeStatus(int cmdId) -> byte[]`; `parseAck(DecodedFrame) -> AckBody`, `parseStatusAck(DecodedFrame) -> StatusAckBody`, `parseRecv(DecodedFrame) -> RecvBody`, `parseMacAck(DecodedFrame) -> MacAckBody` (each throws `IllegalArgumentException` if `frame.cmd()` doesn't match the expected `cmd`, or if `frame.body()` is too short/wrong length for the target shape).

- [x] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.uart;

import ch.jp.shooting.node.frame.PairingTestVectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UartCodecTest {

    @Test
    void encodeSetChannel_matchesExampleFrame() {
        byte[] frame = UartCodec.encodeSetChannel(0x01, 0x06);

        assertThat(frame).isEqualTo(PairingTestVectors.hex("7e03000101065ba8"));
    }

    @Test
    void encodeAddPeer_matchesExampleFrame() {
        byte[] mac = PairingTestVectors.hex("aabbccddeeff");

        byte[] frame = UartCodec.encodeAddPeer(0x02, mac);

        assertThat(frame).isEqualTo(PairingTestVectors.hex("7e08000202aabbccddeeff8e8d"));
    }

    @Test
    void encodeAddPeer_rejectsWrongMacLength() {
        byte[] shortMac = PairingTestVectors.hex("aabbcc");

        assertThatThrownBy(() -> UartCodec.encodeAddPeer(0x02, shortMac))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodeDelPeer_matchesExampleFrame() {
        byte[] mac = PairingTestVectors.hex("001122334455");

        byte[] frame = UartCodec.encodeDelPeer(0x03, mac);

        assertThat(frame).isEqualTo(PairingTestVectors.hex("7e080003030011223344554902"));
    }

    @Test
    void encodeSend_matchesExampleFrame() {
        byte[] destMac = PairingTestVectors.hex("aabbccddeeff");
        byte[] espNowFrame = PairingTestVectors.hex("deadbeef");

        byte[] frame = UartCodec.encodeSend(0x04, destMac, espNowFrame);

        assertThat(frame).isEqualTo(PairingTestVectors.hex("7e0c000404aabbccddeeffdeadbeef78fa"));
    }

    @Test
    void encodeStatus_matchesExampleFrame() {
        byte[] frame = UartCodec.encodeStatus(0x05);

        assertThat(frame).isEqualTo(PairingTestVectors.hex("7e020005055fb2"));
    }

    @Test
    void parseAck_extractsGenericAckFields() {
        UartCodec.DecodedFrame decoded = new UartCodec.DecodedFrame(0x01, UartCodec.CMD_ACK,
                PairingTestVectors.hex("0101"));

        UartCodec.AckBody body = UartCodec.parseAck(decoded);

        assertThat(body.cmdId()).isEqualTo(0x01);
        assertThat(body.ok()).isTrue();
    }

    @Test
    void parseAck_rejectsWrongCmd() {
        UartCodec.DecodedFrame decoded = new UartCodec.DecodedFrame(0x01, UartCodec.CMD_RECV,
                PairingTestVectors.hex("0101"));

        assertThatThrownBy(() -> UartCodec.parseAck(decoded)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseStatusAck_extractsUptimeAndFreeHeap() {
        UartCodec.DecodedFrame decoded = new UartCodec.DecodedFrame(0x05, UartCodec.CMD_ACK,
                PairingTestVectors.hex("0501e803000000000200"));

        UartCodec.StatusAckBody body = UartCodec.parseStatusAck(decoded);

        assertThat(body.cmdId()).isEqualTo(0x05);
        assertThat(body.ok()).isTrue();
        assertThat(body.uptimeS()).isEqualTo(1000L);
        assertThat(body.freeHeap()).isEqualTo(131072L);
    }

    @Test
    void parseStatusAck_rejectsTooShortBody() {
        UartCodec.DecodedFrame decoded = new UartCodec.DecodedFrame(0x01, UartCodec.CMD_ACK,
                PairingTestVectors.hex("0101"));

        assertThatThrownBy(() -> UartCodec.parseStatusAck(decoded)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseRecv_extractsFieldsAndOpaqueFrame() {
        UartCodec.DecodedFrame decoded = new UartCodec.DecodedFrame(0x00, UartCodec.CMD_RECV,
                PairingTestVectors.hex("112233445566cecafe"));

        UartCodec.RecvBody body = UartCodec.parseRecv(decoded);

        assertThat(body.srcMac()).isEqualTo(PairingTestVectors.hex("112233445566"));
        assertThat(body.rssi()).isEqualTo(-50);
        assertThat(body.espNowFrame()).isEqualTo(PairingTestVectors.hex("cafe"));
    }

    @Test
    void parseMacAck_extractsFields() {
        UartCodec.DecodedFrame decoded = new UartCodec.DecodedFrame(0x00, UartCodec.CMD_MAC_ACK,
                PairingTestVectors.hex("aabbccddeeff0a0001"));

        UartCodec.MacAckBody body = UartCodec.parseMacAck(decoded);

        assertThat(body.destMac()).isEqualTo(PairingTestVectors.hex("aabbccddeeff"));
        assertThat(body.frameId()).isEqualTo(10);
        assertThat(body.ok()).isTrue();
    }

    @Test
    void parseMacAck_rejectsWrongBodyLength() {
        UartCodec.DecodedFrame decoded = new UartCodec.DecodedFrame(0x00, UartCodec.CMD_MAC_ACK,
                PairingTestVectors.hex("aabbccddeeff0a00"));

        assertThatThrownBy(() -> UartCodec.parseMacAck(decoded)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=UartCodecTest`
Expected: FAIL (compile error) — `UartCodec` does not exist yet

- [x] **Step 3: Write `UartCodec.java`**

```java
package ch.jp.shooting.node.uart;

import java.util.Arrays;

/**
 * Baut Node->Radio-Kommando-Frames und parst Radio->Node-Antwort-/Ereignis-Bodies
 * fuer das UART-Protokoll zum Funkmodul (dumme Pipe, ADR-002).
 * docs/superpowers/specs/2026-07-10-espnow-uart-framing-design.md.
 */
public final class UartCodec {

    public static final int CMD_SET_CHANNEL = 0x01;
    public static final int CMD_ADD_PEER = 0x02;
    public static final int CMD_DEL_PEER = 0x03;
    public static final int CMD_SEND = 0x04;
    public static final int CMD_STATUS = 0x05;
    public static final int CMD_ACK = 0x80;
    public static final int CMD_RECV = 0x81;
    public static final int CMD_MAC_ACK = 0x82;

    private static final int MAC_LENGTH = 6;

    private UartCodec() {
    }

    public record DecodedFrame(int cmdId, int cmd, byte[] body) {
    }

    public record AckBody(int cmdId, boolean ok) {
    }

    public record StatusAckBody(int cmdId, boolean ok, long uptimeS, long freeHeap) {
    }

    public record RecvBody(byte[] srcMac, int rssi, byte[] espNowFrame) {
    }

    public record MacAckBody(byte[] destMac, int frameId, boolean ok) {
    }

    // --- Kommando-Encoding (Node -> Radio) ---

    public static byte[] encodeSetChannel(int cmdId, int channel) {
        return buildFrame(cmdId, CMD_SET_CHANNEL, new byte[]{(byte) channel});
    }

    public static byte[] encodeAddPeer(int cmdId, byte[] mac) {
        requireMac(mac);
        return buildFrame(cmdId, CMD_ADD_PEER, mac);
    }

    public static byte[] encodeDelPeer(int cmdId, byte[] mac) {
        requireMac(mac);
        return buildFrame(cmdId, CMD_DEL_PEER, mac);
    }

    public static byte[] encodeSend(int cmdId, byte[] destMac, byte[] espNowFrame) {
        requireMac(destMac);
        return buildFrame(cmdId, CMD_SEND, concat(destMac, espNowFrame));
    }

    public static byte[] encodeStatus(int cmdId) {
        return buildFrame(cmdId, CMD_STATUS, new byte[0]);
    }

    // --- Antwort-/Ereignis-Parsing (Radio -> Node) ---

    public static AckBody parseAck(DecodedFrame frame) {
        requireCmd(frame, CMD_ACK);
        byte[] body = frame.body();
        if (body.length < 2) {
            throw new IllegalArgumentException("ACK-Body zu kurz: " + body.length + " Byte");
        }
        return new AckBody(body[0] & 0xFF, body[1] != 0);
    }

    public static StatusAckBody parseStatusAck(DecodedFrame frame) {
        requireCmd(frame, CMD_ACK);
        byte[] body = frame.body();
        if (body.length < 10) {
            throw new IllegalArgumentException("STATUS-ACK-Body zu kurz: " + body.length + " Byte");
        }
        int cmdId = body[0] & 0xFF;
        boolean ok = body[1] != 0;
        long uptimeS = u32leAt(body, 2);
        long freeHeap = u32leAt(body, 6);
        return new StatusAckBody(cmdId, ok, uptimeS, freeHeap);
    }

    public static RecvBody parseRecv(DecodedFrame frame) {
        requireCmd(frame, CMD_RECV);
        byte[] body = frame.body();
        if (body.length < MAC_LENGTH + 1) {
            throw new IllegalArgumentException("RECV-Body zu kurz: " + body.length + " Byte");
        }
        byte[] srcMac = Arrays.copyOfRange(body, 0, MAC_LENGTH);
        int rssi = body[MAC_LENGTH];
        byte[] espNowFrame = Arrays.copyOfRange(body, MAC_LENGTH + 1, body.length);
        return new RecvBody(srcMac, rssi, espNowFrame);
    }

    public static MacAckBody parseMacAck(DecodedFrame frame) {
        requireCmd(frame, CMD_MAC_ACK);
        byte[] body = frame.body();
        if (body.length != MAC_LENGTH + 3) {
            throw new IllegalArgumentException("MAC_ACK-Body falsche Laenge: " + body.length + " Byte");
        }
        byte[] destMac = Arrays.copyOfRange(body, 0, MAC_LENGTH);
        int frameId = u16leAt(body, MAC_LENGTH);
        boolean ok = body[MAC_LENGTH + 2] != 0;
        return new MacAckBody(destMac, frameId, ok);
    }

    // --- Hilfsfunktionen ---

    private static void requireCmd(DecodedFrame frame, int expectedCmd) {
        if (frame.cmd() != expectedCmd) {
            throw new IllegalArgumentException(
                    "Erwartetes cmd 0x" + Integer.toHexString(expectedCmd) + ", erhalten 0x" + Integer.toHexString(frame.cmd()));
        }
    }

    private static void requireMac(byte[] mac) {
        if (mac.length != MAC_LENGTH) {
            throw new IllegalArgumentException("MAC muss " + MAC_LENGTH + " Byte lang sein, war " + mac.length);
        }
    }

    private static byte[] buildFrame(int cmdId, int cmd, byte[] body) {
        byte[] cmdIdCmdBody = concat(new byte[]{(byte) cmdId, (byte) cmd}, body);
        int crc = Crc16.ccittFalse(cmdIdCmdBody);
        int length = cmdIdCmdBody.length;
        byte[] header = new byte[]{0x7E, (byte) (length & 0xFF), (byte) ((length >> 8) & 0xFF)};
        byte[] crcBytes = new byte[]{(byte) (crc & 0xFF), (byte) ((crc >> 8) & 0xFF)};
        return concat(header, cmdIdCmdBody, crcBytes);
    }

    private static byte[] concat(byte[]... parts) {
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

    private static int u16leAt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long u32leAt(byte[] data, int offset) {
        return (data[offset] & 0xFFL) | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16) | ((data[offset + 3] & 0xFFL) << 24);
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=UartCodecTest`
Expected: `BUILD SUCCESS`, `Tests run: 12, Failures: 0, Errors: 0`

- [x] **Step 5: Run the full Java test suite**

Run: `cd smart-ground-node && mvn test`
Expected: `BUILD SUCCESS`, all tests pass, no regressions

- [x] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/uart/UartCodec.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/uart/UartCodecTest.java
git commit -m "feat(node): add UartCodec for UART command/response frame encode-decode (Baustein D)"
```

---

### Task 3: UartFrameDecoder — stateful streaming decode with resync

**Files:**
- Create: `smart-ground-node/src/main/java/ch/jp/shooting/node/uart/UartFrameDecoder.java`
- Create: `smart-ground-node/src/test/java/ch/jp/shooting/node/uart/UartFrameDecoderTest.java`

**Interfaces:**
- Consumes: `Crc16.ccittFalse(byte[]) -> int` (Task 1); `UartCodec.DecodedFrame` record and `UartCodec.CMD_*` constants (Task 2); `ch.jp.shooting.node.frame.PairingTestVectors.hex(String)` (test-only).
- Produces: `new UartFrameDecoder()` (stateful instance, one per serial connection); `feed(byte[] chunk) -> List<UartCodec.DecodedFrame>`.

- [x] **Step 1: Write the failing test**

```java
package ch.jp.shooting.node.uart;

import ch.jp.shooting.node.frame.PairingTestVectors;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UartFrameDecoderTest {

    private static final byte[] SET_CHANNEL_FRAME = PairingTestVectors.hex("7e03000101065ba8");
    private static final byte[] SET_CHANNEL_FRAME_CORRUPTED = PairingTestVectors.hex("7e03000101065ba9");
    private static final byte[] ADD_PEER_FRAME = PairingTestVectors.hex("7e08000202aabbccddeeff8e8d");
    private static final byte[] SEND_FRAME_WITH_EMBEDDED_START_BYTE =
            PairingTestVectors.hex("7e0b000604aabbccddeeff7e01021eb3");

    @Test
    void feed_decodesSingleCompleteFrame() {
        UartFrameDecoder decoder = new UartFrameDecoder();

        List<UartCodec.DecodedFrame> frames = decoder.feed(SET_CHANNEL_FRAME);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmdId()).isEqualTo(0x01);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
        assertThat(frames.get(0).body()).isEqualTo(new byte[]{0x06});
    }

    @Test
    void feed_decodesMultipleFramesInOneChunk() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] chunk = concat(SET_CHANNEL_FRAME, ADD_PEER_FRAME);

        List<UartCodec.DecodedFrame> frames = decoder.feed(chunk);

        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
        assertThat(frames.get(1).cmd()).isEqualTo(UartCodec.CMD_ADD_PEER);
        assertThat(frames.get(1).body()).isEqualTo(PairingTestVectors.hex("aabbccddeeff"));
    }

    @Test
    void feed_reassemblesFrameSplitAcrossTwoChunks() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] firstHalf = new byte[]{SET_CHANNEL_FRAME[0], SET_CHANNEL_FRAME[1], SET_CHANNEL_FRAME[2], SET_CHANNEL_FRAME[3]};
        byte[] secondHalf = new byte[]{SET_CHANNEL_FRAME[4], SET_CHANNEL_FRAME[5], SET_CHANNEL_FRAME[6], SET_CHANNEL_FRAME[7]};

        List<UartCodec.DecodedFrame> afterFirst = decoder.feed(firstHalf);
        List<UartCodec.DecodedFrame> afterSecond = decoder.feed(secondHalf);

        assertThat(afterFirst).isEmpty();
        assertThat(afterSecond).hasSize(1);
        assertThat(afterSecond.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
    }

    @Test
    void feed_discardsGarbageBytesBeforeValidFrame() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] chunk = concat(new byte[]{0x00, (byte) 0xFF}, SET_CHANNEL_FRAME);

        List<UartCodec.DecodedFrame> frames = decoder.feed(chunk);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
    }

    @Test
    void feed_resyncsPastCrcCorruptedFrameToNextValidFrame() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] chunk = concat(SET_CHANNEL_FRAME_CORRUPTED, ADD_PEER_FRAME);

        List<UartCodec.DecodedFrame> frames = decoder.feed(chunk);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_ADD_PEER);
    }

    @Test
    void feed_resyncsPastImplausibleLengthField() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] fakeHeader = new byte[]{0x7E, (byte) 0xFF, (byte) 0xFF, 0x00, 0x01, 0x02};
        byte[] chunk = concat(fakeHeader, SET_CHANNEL_FRAME);

        List<UartCodec.DecodedFrame> frames = decoder.feed(chunk);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
    }

    @Test
    void feed_doesNotMisinterpretStartByteEmbeddedInBody() {
        UartFrameDecoder decoder = new UartFrameDecoder();

        List<UartCodec.DecodedFrame> frames = decoder.feed(SEND_FRAME_WITH_EMBEDDED_START_BYTE);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SEND);
        assertThat(frames.get(0).body()).isEqualTo(PairingTestVectors.hex("aabbccddeeff7e0102"));
    }

    private static byte[] concat(byte[]... parts) {
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
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd smart-ground-node && mvn test -Dtest=UartFrameDecoderTest`
Expected: FAIL (compile error) — `UartFrameDecoder` does not exist yet

- [x] **Step 3: Write `UartFrameDecoder.java`**

```java
package ch.jp.shooting.node.uart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Zustandsbehafteter Decoder fuer den UART-Bytestrom vom Funkmodul: puffert
 * fragmentierte Reads, sucht Start-Bytes und synchronisiert sich nach CRC-
 * Fehlern oder unplausiblen Laengen auf das naechste 0x7E.
 * docs/superpowers/specs/2026-07-10-espnow-uart-framing-design.md.
 */
public final class UartFrameDecoder {

    private static final int START_BYTE = 0x7E;
    private static final int HEADER_SIZE = 3; // start-byte + length(2)
    private static final int CMD_ID_CMD_SIZE = 2;
    private static final int CRC_SIZE = 2;
    private static final int MAX_FRAME_LENGTH = 512;

    private byte[] buffer = new byte[0];

    public List<UartCodec.DecodedFrame> feed(byte[] chunk) {
        buffer = concat(buffer, chunk);
        List<UartCodec.DecodedFrame> decoded = new ArrayList<>();

        while (true) {
            int start = indexOfStartByte();
            if (start < 0) {
                buffer = new byte[0];
                break;
            }
            if (start > 0) {
                buffer = Arrays.copyOfRange(buffer, start, buffer.length);
            }
            if (buffer.length < HEADER_SIZE) {
                break;
            }
            int length = u16leAt(buffer, 1);
            if (length > MAX_FRAME_LENGTH) {
                buffer = Arrays.copyOfRange(buffer, 1, buffer.length);
                continue;
            }
            int frameSize = HEADER_SIZE + length + CRC_SIZE;
            if (buffer.length < frameSize) {
                break;
            }
            byte[] cmdIdCmdBody = Arrays.copyOfRange(buffer, HEADER_SIZE, HEADER_SIZE + length);
            int expectedCrc = Crc16.ccittFalse(cmdIdCmdBody);
            int actualCrc = u16leAt(buffer, HEADER_SIZE + length);
            if (expectedCrc != actualCrc) {
                buffer = Arrays.copyOfRange(buffer, 1, buffer.length);
                continue;
            }
            int cmdId = cmdIdCmdBody[0] & 0xFF;
            int cmd = cmdIdCmdBody[1] & 0xFF;
            byte[] body = Arrays.copyOfRange(cmdIdCmdBody, CMD_ID_CMD_SIZE, cmdIdCmdBody.length);
            decoded.add(new UartCodec.DecodedFrame(cmdId, cmd, body));
            buffer = Arrays.copyOfRange(buffer, frameSize, buffer.length);
        }

        return decoded;
    }

    private int indexOfStartByte() {
        for (int i = 0; i < buffer.length; i++) {
            if ((buffer[i] & 0xFF) == START_BYTE) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static int u16leAt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd smart-ground-node && mvn test -Dtest=UartFrameDecoderTest`
Expected: `BUILD SUCCESS`, `Tests run: 7, Failures: 0, Errors: 0`

- [x] **Step 5: Run the full Java test suite**

Run: `cd smart-ground-node && mvn test`
Expected: `BUILD SUCCESS`, all tests pass, no regressions

- [x] **Step 6: Commit**

```bash
git add smart-ground-node/src/main/java/ch/jp/shooting/node/uart/UartFrameDecoder.java \
        smart-ground-node/src/test/java/ch/jp/shooting/node/uart/UartFrameDecoderTest.java
git commit -m "feat(node): add UartFrameDecoder with resync-on-corruption (Baustein D)"
```

---

## Plan-Level Verification

- [x] **Final check: run the full Java test suite once more from a clean state**

```bash
cd smart-ground-node && mvn test
```

Expected: `BUILD SUCCESS`, zero failures. `smart-ground-node` can now encode all five Node→Radio UART commands and decode all three Radio→Node responses/events from a live, arbitrarily-fragmented byte stream, resynchronizing past corruption — the UART transport layer for Baustein B/C's ESP-NOW frames is now in place. Real serial I/O (`jSerialComm`), `RadioInterface`, the ACK-timeout/retry state machine, and the radio-module firmware itself remain separate follow-on work (Phase 2a/2b of the migration plan).

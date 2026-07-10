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

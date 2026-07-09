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

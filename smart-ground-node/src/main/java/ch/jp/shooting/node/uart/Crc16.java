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

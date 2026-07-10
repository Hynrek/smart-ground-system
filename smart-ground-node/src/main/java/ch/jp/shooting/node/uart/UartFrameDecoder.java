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

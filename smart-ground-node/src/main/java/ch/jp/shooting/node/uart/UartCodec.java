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

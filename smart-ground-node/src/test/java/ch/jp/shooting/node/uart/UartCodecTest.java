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

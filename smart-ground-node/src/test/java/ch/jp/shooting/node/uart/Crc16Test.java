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

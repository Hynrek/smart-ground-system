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

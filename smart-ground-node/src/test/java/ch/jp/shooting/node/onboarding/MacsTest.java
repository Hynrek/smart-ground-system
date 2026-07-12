package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MacsTest {

    @Test
    void parse_colonForm() {
        assertThat(Macs.parse("AA:BB:CC:DD:EE:01"))
                .containsExactly(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0x01);
    }

    @Test
    void parse_plainHex_caseInsensitive() {
        assertThat(Macs.parse("aabbccddee01"))
                .containsExactly(0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0x01);
    }

    @Test
    void parse_wrongLength_throws() {
        assertThatThrownBy(() -> Macs.parse("AA:BB:CC")).isInstanceOf(IllegalArgumentException.class);
    }
}

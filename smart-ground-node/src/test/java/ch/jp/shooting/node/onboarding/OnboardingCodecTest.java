package ch.jp.shooting.node.onboarding;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import ch.jp.shooting.node.frame.PairingTestVectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static ch.jp.shooting.node.frame.PairingTestVectors.hex;
import static org.assertj.core.api.Assertions.assertThat;

class OnboardingCodecTest {

    private static JsonNode fixture() {
        try {
            return new ObjectMapper().readTree(new File("../docs/espnow/onboarding-test-vectors.json"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void buildHello_matchesFixtureFrame() {
        JsonNode v = fixture().get("hello");
        FrameHeader header = new FrameHeader(hex(v.get("dest_mac").asText()), hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.HELLO);

        byte[] frame = OnboardingCodec.buildHello(header, hex(v.get("box_nonce").asText()));

        assertThat(frame).isEqualTo(hex(v.get("frame").asText()));
    }

    @Test
    void boxNonceOf_extractsFixtureValue() {
        JsonNode v = fixture().get("hello");
        assertThat(OnboardingCodec.boxNonceOf(hex(v.get("frame").asText())))
                .isEqualTo(hex(v.get("box_nonce").asText()));
    }

    @Test
    void buildOnboardOffer_matchesFixtureFrame() {
        JsonNode v = fixture().get("onboard_offer");
        FrameHeader header = new FrameHeader(hex(v.get("dest_mac").asText()), hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.ONBOARD_OFFER);

        byte[] frame = OnboardingCodec.buildOnboardOffer(header,
                hex(v.get("echo_nonce").asText()), hex(v.get("token").asText()),
                hex(v.get("fingerprint").asText()), hex(v.get("ssid").asText()),
                hex(v.get("psk").asText()), hex(v.get("url").asText()));

        assertThat(frame).isEqualTo(hex(v.get("frame").asText()));
    }

    @Test
    void onboardOffer_accessors_extractFixtureValues() {
        JsonNode v = fixture().get("onboard_offer");
        byte[] frame = hex(v.get("frame").asText());

        assertThat(OnboardingCodec.echoNonceOf(frame)).isEqualTo(hex(v.get("echo_nonce").asText()));
        assertThat(OnboardingCodec.tokenOf(frame)).isEqualTo(hex(v.get("token").asText()));
        assertThat(OnboardingCodec.fingerprintOf(frame)).isEqualTo(hex(v.get("fingerprint").asText()));
        assertThat(OnboardingCodec.ssidOf(frame)).isEqualTo(hex(v.get("ssid").asText()));
        assertThat(OnboardingCodec.pskOf(frame)).isEqualTo(hex(v.get("psk").asText()));
        assertThat(OnboardingCodec.urlOf(frame)).isEqualTo(hex(v.get("url").asText()));
    }
}

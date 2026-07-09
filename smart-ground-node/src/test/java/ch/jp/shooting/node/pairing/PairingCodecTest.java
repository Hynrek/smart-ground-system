package ch.jp.shooting.node.pairing;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import ch.jp.shooting.node.frame.PairingTestVectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PairingCodecTest {

    private static JsonNode fixture() {
        try {
            return new ObjectMapper().readTree(new File("../docs/espnow/pairing-test-vectors.json"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void buildDiscover_matchesFixtureFrame() {
        JsonNode v = fixture().get("pair_discover");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        FrameHeader header = new FrameHeader(
                PairingTestVectors.hex(v.get("dest_mac").asText()),
                PairingTestVectors.hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.PAIR_DISCOVER);

        byte[] frame = PairingCodec.buildDiscover(header,
                PairingTestVectors.hex(v.get("box_uuid").asText()),
                PairingTestVectors.hex(v.get("nonce_b").asText()), kBox);

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void verifyDiscover_acceptsFixtureFrame_rejectsTamperedMic() {
        JsonNode v = fixture().get("pair_discover");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(PairingCodec.verifyDiscover(frame, kBox)).isTrue();

        byte[] tampered = frame.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThat(PairingCodec.verifyDiscover(tampered, kBox)).isFalse();
    }

    @Test
    void boxUuidOf_and_nonceBOf_extractFixtureValues() {
        JsonNode v = fixture().get("pair_discover");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(PairingCodec.boxUuidOf(frame)).isEqualTo(PairingTestVectors.hex(v.get("box_uuid").asText()));
        assertThat(PairingCodec.nonceBOf(frame)).isEqualTo(PairingTestVectors.hex(v.get("nonce_b").asText()));
    }

    @Test
    void buildOffer_matchesFixtureFrame() {
        JsonNode v = fixture().get("pair_offer");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        FrameHeader header = new FrameHeader(
                PairingTestVectors.hex(v.get("dest_mac").asText()),
                PairingTestVectors.hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.PAIR_OFFER);

        byte[] frame = PairingCodec.buildOffer(header, v.get("radio_id").asInt(), v.get("channel").asInt(),
                PairingTestVectors.hex(v.get("nonce_n").asText()),
                PairingTestVectors.hex(v.get("plaintext_nonce_b").asText()), kBox);

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void nonceBFromOffer_decryptsFixtureFrame() {
        JsonNode v = fixture().get("pair_offer");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(PairingCodec.nonceBFromOffer(frame, kBox))
                .isEqualTo(PairingTestVectors.hex(v.get("plaintext_nonce_b").asText()));
        assertThat(PairingCodec.radioIdOf(frame)).isEqualTo(v.get("radio_id").asInt());
        assertThat(PairingCodec.channelOf(frame)).isEqualTo(v.get("channel").asInt());
        assertThat(PairingCodec.nonceNOfOffer(frame)).isEqualTo(PairingTestVectors.hex(v.get("nonce_n").asText()));
    }

    @Test
    void nonceBFromOffer_rejectsTamperedTag() {
        JsonNode v = fixture().get("pair_offer");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());
        frame[frame.length - 1] ^= 0x01;

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PairingCodec.nonceBFromOffer(frame, kBox));
    }

    @Test
    void buildConfirm_matchesFixtureFrame() {
        JsonNode v = fixture().get("pair_confirm");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        FrameHeader header = new FrameHeader(
                PairingTestVectors.hex(v.get("dest_mac").asText()),
                PairingTestVectors.hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), FrameType.PAIR_CONFIRM);

        byte[] frame = PairingCodec.buildConfirm(header, PairingTestVectors.hex(v.get("nonce_n").asText()), kBox);

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void verifyConfirm_acceptsFixtureFrame_and_nonceNOfConfirm_extractsIt() {
        JsonNode v = fixture().get("pair_confirm");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(PairingCodec.verifyConfirm(frame, kBox)).isTrue();
        assertThat(PairingCodec.nonceNOfConfirm(frame)).isEqualTo(PairingTestVectors.hex(v.get("nonce_n").asText()));
    }

    @Test
    void deriveSessionKey_matchesFixture() {
        JsonNode v = fixture().get("session_key");
        byte[] kBox = PairingTestVectors.hex(fixture().get("constants").get("k_box").asText());
        byte[] nonceB = PairingTestVectors.hex(fixture().get("constants").get("nonce_b").asText());
        byte[] nonceN = PairingTestVectors.hex(fixture().get("constants").get("nonce_n").asText());

        byte[] kS = PairingCodec.deriveSessionKey(kBox, nonceB, nonceN);

        assertThat(kS).isEqualTo(PairingTestVectors.hex(v.get("k_s").asText()));
    }
}

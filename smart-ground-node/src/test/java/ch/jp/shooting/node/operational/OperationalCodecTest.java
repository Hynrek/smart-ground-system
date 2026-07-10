package ch.jp.shooting.node.operational;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import ch.jp.shooting.node.frame.PairingTestVectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalCodecTest {

    private static JsonNode fixture() {
        try {
            return new ObjectMapper().readTree(new File("../docs/espnow/operational-test-vectors.json"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FrameHeader headerFrom(JsonNode v, FrameType type) {
        return new FrameHeader(
                PairingTestVectors.hex(v.get("dest_mac").asText()),
                PairingTestVectors.hex(v.get("src_mac").asText()),
                v.get("frame_id").asInt(), v.get("ttl").asInt(), type);
    }

    private static byte[] kS() {
        return PairingTestVectors.hex(fixture().get("constants").get("k_s").asText());
    }

    @Test
    void buildConfig_matchesFixtureFrame() {
        JsonNode v = fixture().get("config");
        JsonNode d = v.get("decoded");
        FrameHeader header = headerFrom(v, FrameType.CONFIG);

        byte[] frame = OperationalCodec.buildConfig(header, PairingTestVectors.hex(v.get("counter_nonce").asText()),
                kS(), PairingTestVectors.hex(d.get("device_id").asText()), d.get("device_index").asInt(),
                d.get("device_count").asInt(), d.get("alias").asText(), d.get("device_type").asInt(),
                d.get("direction").asInt(), d.get("command").asText(), d.get("signal_duration_ms").asInt(),
                d.get("blocked").asBoolean());

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void parseConfig_extractsFixtureFields() {
        JsonNode v = fixture().get("config");
        JsonNode d = v.get("decoded");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        OperationalCodec.ConfigBody body = OperationalCodec.parseConfig(frame, kS());

        assertThat(body.deviceId()).isEqualTo(PairingTestVectors.hex(d.get("device_id").asText()));
        assertThat(body.deviceIndex()).isEqualTo(d.get("device_index").asInt());
        assertThat(body.deviceCount()).isEqualTo(d.get("device_count").asInt());
        assertThat(body.alias()).isEqualTo(d.get("alias").asText());
        assertThat(body.deviceType()).isEqualTo(d.get("device_type").asInt());
        assertThat(body.direction()).isEqualTo(d.get("direction").asInt());
        assertThat(body.command()).isEqualTo(d.get("command").asText());
        assertThat(body.signalDurationMs()).isEqualTo(d.get("signal_duration_ms").asInt());
        assertThat(body.blocked()).isEqualTo(d.get("blocked").asBoolean());
    }

    @Test
    void buildConfigAck_matchesFixtureFrame_and_verifyConfigAck_accepts() {
        JsonNode v = fixture().get("config_ack");
        FrameHeader header = headerFrom(v, FrameType.CONFIG_ACK);

        byte[] frame = OperationalCodec.buildConfigAck(header, PairingTestVectors.hex(v.get("counter_nonce").asText()), kS());

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
        assertThat(OperationalCodec.verifyConfigAck(frame, kS())).isTrue();
    }

    @Test
    void verifyConfigAck_rejectsTamperedTag() {
        JsonNode v = fixture().get("config_ack");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());
        frame[frame.length - 1] ^= 0x01;

        assertThat(OperationalCodec.verifyConfigAck(frame, kS())).isFalse();
    }

    @Test
    void buildCommand_matchesFixtureFrame() {
        JsonNode v = fixture().get("command");
        JsonNode d = v.get("decoded");
        FrameHeader header = headerFrom(v, FrameType.COMMAND);

        byte[] frame = OperationalCodec.buildCommand(header, PairingTestVectors.hex(v.get("counter_nonce").asText()),
                kS(), PairingTestVectors.hex(d.get("command_id").asText()), PairingTestVectors.hex(d.get("device_id").asText()),
                d.get("command").asInt(), d.get("signal_duration_ms").asInt());

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void parseCommand_extractsFixtureFields() {
        JsonNode v = fixture().get("command");
        JsonNode d = v.get("decoded");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        OperationalCodec.CommandBody body = OperationalCodec.parseCommand(frame, kS());

        assertThat(body.commandId()).isEqualTo(PairingTestVectors.hex(d.get("command_id").asText()));
        assertThat(body.deviceId()).isEqualTo(PairingTestVectors.hex(d.get("device_id").asText()));
        assertThat(body.command()).isEqualTo(d.get("command").asInt());
        assertThat(body.signalDurationMs()).isEqualTo(d.get("signal_duration_ms").asInt());
    }

    @Test
    void buildExecuted_matchesFixtureFrame() {
        JsonNode v = fixture().get("executed");
        JsonNode d = v.get("decoded");
        FrameHeader header = headerFrom(v, FrameType.EXECUTED);

        byte[] frame = OperationalCodec.buildExecuted(header, PairingTestVectors.hex(v.get("counter_nonce").asText()),
                kS(), PairingTestVectors.hex(d.get("command_id").asText()), PairingTestVectors.hex(d.get("device_id").asText()));

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void parseExecuted_extractsFixtureFields() {
        JsonNode v = fixture().get("executed");
        JsonNode d = v.get("decoded");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        OperationalCodec.ExecutedBody body = OperationalCodec.parseExecuted(frame, kS());

        assertThat(body.commandId()).isEqualTo(PairingTestVectors.hex(d.get("command_id").asText()));
        assertThat(body.deviceId()).isEqualTo(PairingTestVectors.hex(d.get("device_id").asText()));
    }

    @Test
    void buildHeartbeat_matchesFixtureFrame_and_verifyHeartbeat_accepts() {
        JsonNode v = fixture().get("heartbeat");
        FrameHeader header = headerFrom(v, FrameType.HEARTBEAT);

        byte[] frame = OperationalCodec.buildHeartbeat(header, PairingTestVectors.hex(v.get("counter_nonce").asText()), kS());

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
        assertThat(OperationalCodec.verifyHeartbeat(frame, kS())).isTrue();
    }

    @Test
    void counterNonceOf_extractsFixtureValue() {
        JsonNode v = fixture().get("command");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());

        assertThat(OperationalCodec.counterNonceOf(frame)).isEqualTo(PairingTestVectors.hex(v.get("counter_nonce").asText()));
    }
}

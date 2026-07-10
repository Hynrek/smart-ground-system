package ch.jp.shooting.node.operational;

import ch.jp.shooting.node.frame.FrameHeader;
import ch.jp.shooting.node.frame.FrameType;
import ch.jp.shooting.node.frame.PairingTestVectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    @Test
    void buildDiscovery_matchesFixtureFrame() {
        JsonNode v = fixture().get("discovery");
        JsonNode d = v.get("decoded");
        FrameHeader header = headerFrom(v, FrameType.DISCOVERY);
        String[] versionParts = d.get("app_version").asText().split("\\.");

        List<OperationalCodec.DeviceTypeCapability> deviceTypes = new ArrayList<>();
        for (JsonNode dt : d.get("device_types")) {
            List<OperationalCodec.ConfigField> fields = new ArrayList<>();
            for (JsonNode f : dt.get("config_fields")) {
                fields.add(new OperationalCodec.ConfigField(f.get("field_id").asInt(), f.get("type_id").asInt(),
                        OperationalCodec.i32le(f.get("default").asInt())));
            }
            deviceTypes.add(new OperationalCodec.DeviceTypeCapability(dt.get("device_type_id").asInt(),
                    dt.get("directions_bitmask").asInt(), dt.get("commands_bitmask").asInt(), fields));
        }

        byte[] frame = OperationalCodec.buildDiscovery(header, PairingTestVectors.hex(v.get("counter_nonce").asText()),
                kS(), Integer.parseInt(versionParts[0]), Integer.parseInt(versionParts[1]),
                d.get("config_schema_version").asInt(), d.get("box_type").asText(), deviceTypes);

        assertThat(frame).isEqualTo(PairingTestVectors.hex(v.get("frame").asText()));
    }

    @Test
    void parseDiscovery_extractsFixtureFields() {
        JsonNode v = fixture().get("discovery");
        JsonNode d = v.get("decoded");
        byte[] frame = PairingTestVectors.hex(v.get("frame").asText());
        String[] versionParts = d.get("app_version").asText().split("\\.");

        OperationalCodec.DiscoveryBody body = OperationalCodec.parseDiscovery(frame, kS());

        assertThat(body.appVersionMajor()).isEqualTo(Integer.parseInt(versionParts[0]));
        assertThat(body.appVersionMinor()).isEqualTo(Integer.parseInt(versionParts[1]));
        assertThat(body.configSchemaVersion()).isEqualTo(d.get("config_schema_version").asInt());
        assertThat(body.boxType()).isEqualTo(d.get("box_type").asText());
        assertThat(body.deviceTypes()).hasSize(d.get("device_types").size());

        for (int i = 0; i < body.deviceTypes().size(); i++) {
            OperationalCodec.DeviceTypeCapability parsed = body.deviceTypes().get(i);
            JsonNode expected = d.get("device_types").get(i);
            assertThat(parsed.deviceTypeId()).isEqualTo(expected.get("device_type_id").asInt());
            assertThat(parsed.directionsBitmask()).isEqualTo(expected.get("directions_bitmask").asInt());
            assertThat(parsed.commandsBitmask()).isEqualTo(expected.get("commands_bitmask").asInt());
            assertThat(parsed.configFields()).hasSize(expected.get("config_fields").size());
            for (int j = 0; j < parsed.configFields().size(); j++) {
                OperationalCodec.ConfigField parsedField = parsed.configFields().get(j);
                JsonNode expectedField = expected.get("config_fields").get(j);
                assertThat(parsedField.fieldId()).isEqualTo(expectedField.get("field_id").asInt());
                assertThat(parsedField.typeId()).isEqualTo(expectedField.get("type_id").asInt());
                assertThat(OperationalCodec.i32leAt(parsedField.defaultBytes(), 0))
                        .isEqualTo(expectedField.get("default").asInt());
            }
        }
    }

    @Test
    void verifyConfigAck_rejectsTruncatedFrame() {
        // FrameHeader.SIZE (16) + counter-nonce (4) = 20 bytes minimum; well short of that here
        // (shorter than the header alone), so unwrap must reject it instead of throwing
        // ArrayIndexOutOfBoundsException on a manipulated/truncated ESP-NOW frame.
        byte[] shortFrame = new byte[FrameHeader.SIZE - 6];

        assertThat(OperationalCodec.verifyConfigAck(shortFrame, kS())).isFalse();
    }

    @Test
    void parseDiscovery_rejectsTamperedTag() {
        byte[] frame = PairingTestVectors.hex(fixture().get("discovery").get("frame").asText());
        frame[frame.length - 1] ^= 0x01;

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> OperationalCodec.parseDiscovery(frame, kS()));
    }
}

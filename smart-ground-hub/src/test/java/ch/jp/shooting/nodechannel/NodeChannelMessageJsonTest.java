package ch.jp.shooting.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodeChannelMessageJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void command_roundTrips_withStableFieldNamesAndVersion() throws Exception {
        var id = UUID.randomUUID();
        var msg = NodeChannelMessage.command(id, "FIRE", "{\"deviceId\":\"d1\"}");

        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"v\":1").contains("\"type\":\"COMMAND\"")
            .contains("\"commandId\":\"" + id + "\"").contains("\"commandType\":\"FIRE\"");
        // NON_NULL: unused fields are omitted, not serialized as null
        assertThat(json).doesNotContain("\"token\"").doesNotContain("\"outcome\"");

        NodeChannelMessage back = mapper.readValue(json, NodeChannelMessage.class);
        assertThat(back.v()).isEqualTo(NodeChannelTypes.PROTOCOL_VERSION);
        assertThat(back.commandId()).isEqualTo(id);
        assertThat(back.commandType()).isEqualTo("FIRE");
    }

    @Test
    void hello_carriesNodeIdAndToken() throws Exception {
        String json = mapper.writeValueAsString(NodeChannelMessage.hello("node-1", "secret"));
        NodeChannelMessage back = mapper.readValue(json, NodeChannelMessage.class);
        assertThat(back.type()).isEqualTo(NodeChannelTypes.TYPE_HELLO);
        assertThat(back.nodeId()).isEqualTo("node-1");
        assertThat(back.token()).isEqualTo("secret");
    }
}

package ch.jp.shooting.node.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelMessage;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** (De)serialisiert das node-channel-Envelope mit Jackson 3 (Node-Default, tools.jackson). */
@Component
public class NodeChannelCodec {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    public String toJson(NodeChannelMessage message) {
        return mapper.writeValueAsString(message);
    }

    public NodeChannelMessage fromJson(String json) {
        return mapper.readValue(json, NodeChannelMessage.class);
    }
}

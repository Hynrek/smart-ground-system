package ch.jp.shooting.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/** (De)serialisiert das node-channel-Envelope mit einem dedizierten Jackson-2-Mapper (nicht dem HTTP-Konverter). */
@Component
@NullMarked
public class NodeChannelCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    public String toJson(NodeChannelMessage message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("node-channel: Serialisierung fehlgeschlagen", e);
        }
    }

    public NodeChannelMessage fromJson(String json) {
        try {
            return mapper.readValue(json, NodeChannelMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("node-channel: ungültiges Envelope", e);
        }
    }
}

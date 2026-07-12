package ch.jp.smartground.nodechannel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Versioniertes Nachrichten-Envelope des node-channel (Hub<->Node, Kein REST).
 * Ein Record mit optionalen Feldern, serialisierbar über Jackson 2 (Hub) und Jackson 3 (Node);
 * die com.fasterxml.jackson.annotation-Annotationen gelten in beiden Major-Versionen.
 * Immer gesetzt: v, type. Alles andere ist typabhängig und darf null sein.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NodeChannelMessage(
        @JsonProperty("v") int v,
        @JsonProperty("type") String type,
        @JsonProperty("nodeId") String nodeId,
        @JsonProperty("token") String token,
        @JsonProperty("commandId") UUID commandId,
        @JsonProperty("commandType") String commandType,
        @JsonProperty("payloadJson") String payloadJson,
        @JsonProperty("outcome") String outcome
) {
    /** HELLO: Node meldet sich mit Identität + Token an. */
    public static NodeChannelMessage hello(String nodeId, String token) {
        return new NodeChannelMessage(NodeChannelTypes.PROTOCOL_VERSION, NodeChannelTypes.TYPE_HELLO,
                nodeId, token, null, null, null, null);
    }

    /** HELLO_ACK: Hub bestätigt die Anmeldung. */
    public static NodeChannelMessage helloAck() {
        return new NodeChannelMessage(NodeChannelTypes.PROTOCOL_VERSION, NodeChannelTypes.TYPE_HELLO_ACK,
                null, null, null, null, null, null);
    }

    /** HEARTBEAT: Liveness vom Node. */
    public static NodeChannelMessage heartbeat(String nodeId) {
        return new NodeChannelMessage(NodeChannelTypes.PROTOCOL_VERSION, NodeChannelTypes.TYPE_HEARTBEAT,
                nodeId, null, null, null, null, null);
    }

    /** COMMAND: Hub->Node, trägt eine idempotente UUID. */
    public static NodeChannelMessage command(UUID commandId, String commandType, String payloadJson) {
        return new NodeChannelMessage(NodeChannelTypes.PROTOCOL_VERSION, NodeChannelTypes.TYPE_COMMAND,
                null, null, commandId, commandType, payloadJson, null);
    }

    /** COMMAND_ACK: Node->Hub, Ergebnis eines Commands. */
    public static NodeChannelMessage commandAck(UUID commandId, String outcome) {
        return new NodeChannelMessage(NodeChannelTypes.PROTOCOL_VERSION, NodeChannelTypes.TYPE_COMMAND_ACK,
                null, null, commandId, null, null, outcome);
    }
}

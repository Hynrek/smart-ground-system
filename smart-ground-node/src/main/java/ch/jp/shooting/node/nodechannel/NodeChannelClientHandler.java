package ch.jp.shooting.node.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Client-Seite des node-channel. Sendet beim Verbindungsaufbau HELLO; verarbeitet eingehende COMMANDs
 * (idempotent via CommandDeduplicator, ausgeführt über die NodeCommandHandler-Seam) und antwortet mit
 * COMMAND_ACK. onClose meldet dem Client, dass er neu verbinden soll.
 */
public class NodeChannelClientHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NodeChannelClientHandler.class);

    private final NodeChannelProperties props;
    private final NodeChannelCodec codec;
    private final NodeCommandHandler commandHandler;
    private final CommandDeduplicator deduplicator;
    private final Runnable onClosed;

    public NodeChannelClientHandler(NodeChannelProperties props, NodeChannelCodec codec,
                                    NodeCommandHandler commandHandler, CommandDeduplicator deduplicator,
                                    Runnable onClosed) {
        this.props = props;
        this.codec = codec;
        this.commandHandler = commandHandler;
        this.deduplicator = deduplicator;
        this.onClosed = onClosed;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        session.sendMessage(new TextMessage(
                codec.toJson(NodeChannelMessage.hello(props.getNodeId(), props.getToken()))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        NodeChannelMessage msg = codec.fromJson(message.getPayload());
        switch (msg.type()) {
            case NodeChannelTypes.TYPE_HELLO_ACK -> log.info("node-channel: mit Hub verbunden");
            case NodeChannelTypes.TYPE_COMMAND -> onCommand(session, msg);
            default -> log.debug("node-channel: unbekannter Typ ignoriert: {}", msg.type());
        }
    }

    private void onCommand(WebSocketSession session, NodeChannelMessage msg) throws Exception {
        // Idempotent auf der Command-UUID: eine erneut gelieferte UUID wird nicht erneut ausgeführt,
        // sondern mit dem gespeicherten Ergebnis re-acked.
        String outcome = deduplicator.handle(msg.commandId(),
                () -> commandHandler.handle(msg.commandType(), msg.payloadJson()));
        session.sendMessage(new TextMessage(codec.toJson(NodeChannelMessage.commandAck(msg.commandId(), outcome))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("node-channel: Verbindung geschlossen ({}), reconnect folgt", status);
        onClosed.run();
    }
}

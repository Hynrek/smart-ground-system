package ch.jp.shooting.node.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.function.Consumer;

/**
 * Client-Seite des node-channel. Sendet beim Verbindungsaufbau HELLO; verarbeitet eingehende COMMANDs
 * (idempotent via CommandDeduplicator, ausgeführt über die NodeCommandHandler-Seam) und antwortet mit
 * COMMAND_ACK. onClose meldet dem Client, dass er neu verbinden soll.
 */
public class NodeChannelClientHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NodeChannelClientHandler.class);

    // WebSocketSession.sendMessage ist nicht thread-safe: HEARTBEAT (Scheduler-Thread in NodeChannelClient)
    // und HELLO/COMMAND_ACK (dieser Handler, WS-Callback-Thread) laufen auf derselben Session. Diese Klasse
    // wrappt die rohe Session genau einmal in afterConnectionEstablished und reicht die dekorierte Instanz
    // per onConnected-Callback an NodeChannelClient weiter, damit Heartbeat und Handler dieselbe
    // Decorator-Instanz teilen (der Decorator ist nur pro Instanz thread-sicher, nicht pro Socket).
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 8192;

    private final NodeChannelProperties props;
    private final NodeChannelCodec codec;
    private final NodeCommandHandler commandHandler;
    private final CommandDeduplicator deduplicator;
    private final Runnable onClosed;
    private final Consumer<WebSocketSession> onConnected;

    private volatile WebSocketSession safeSession;

    public NodeChannelClientHandler(NodeChannelProperties props, NodeChannelCodec codec,
                                    NodeCommandHandler commandHandler, CommandDeduplicator deduplicator,
                                    Runnable onClosed) {
        this(props, codec, commandHandler, deduplicator, onClosed, s -> { });
    }

    public NodeChannelClientHandler(NodeChannelProperties props, NodeChannelCodec codec,
                                    NodeCommandHandler commandHandler, CommandDeduplicator deduplicator,
                                    Runnable onClosed, Consumer<WebSocketSession> onConnected) {
        this.props = props;
        this.codec = codec;
        this.commandHandler = commandHandler;
        this.deduplicator = deduplicator;
        this.onClosed = onClosed;
        this.onConnected = onConnected;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT_BYTES);
        this.safeSession = safe;
        onConnected.accept(safe);
        safe.sendMessage(new TextMessage(
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

    /**
     * Liefert die dekorierte Session, die diese Verbindung teilt — normalerweise die in
     * afterConnectionEstablished angelegte Instanz. Fällt (nur zur Robustheit, z.B. falls je aufgerufen
     * ohne vorherigen Connect-Callback) auf ein Lazy-Wrap der übergebenen rohen Session zurück, statt
     * mit NPE zu scheitern.
     */
    private WebSocketSession safeSession(WebSocketSession rawSession) {
        WebSocketSession safe = this.safeSession;
        if (safe == null) {
            synchronized (this) {
                safe = this.safeSession;
                if (safe == null) {
                    safe = new ConcurrentWebSocketSessionDecorator(
                            rawSession, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT_BYTES);
                    this.safeSession = safe;
                }
            }
        }
        return safe;
    }

    private void onCommand(WebSocketSession session, NodeChannelMessage msg) throws Exception {
        // Idempotent auf der Command-UUID: eine erneut gelieferte UUID wird nicht erneut ausgeführt,
        // sondern mit dem gespeicherten Ergebnis re-acked.
        String outcome = deduplicator.handle(msg.commandId(),
                () -> commandHandler.handle(msg.commandType(), msg.payloadJson()));
        safeSession(session).sendMessage(
                new TextMessage(codec.toJson(NodeChannelMessage.commandAck(msg.commandId(), outcome))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("node-channel: Verbindung geschlossen ({}), reconnect folgt", status);
        onClosed.run();
    }
}

package ch.jp.shooting.nodechannel;

import ch.jp.shooting.config.NodeChannelProperties;
import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Objects;

/**
 * Server-Seite des node-channel unter /node-channel. Node-initiiert: der Hub akzeptiert die Verbindung,
 * die App-Level-Auth passiert im HELLO (nicht im TLS-Handshake), damit sie hinter Proxys/CGNAT trägt.
 * Auth ist ein konfiguriertes (nodeId, token)-Paar — Übergangslösung bis zum Service-Token (#6).
 */
@Component
@NullMarked
public class NodeChannelHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NodeChannelHandler.class);

    // WebSocketSession.sendMessage ist nicht thread-safe: HELLO_ACK (dieser Thread) und spätere COMMANDs
    // (NodeChannelService.dispatchCommand, von einem beliebigen Aufrufer-Thread) landen auf derselben
    // Session. 5s Sendezeit-Limit / 8KB Puffer sind grosszügige, aber begrenzte Defaults für die kleinen
    // node-channel-Envelopes — kein eigenes Config-Knob dafür nötig.
    private static final int SEND_TIME_LIMIT_MS = 5000;
    private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 8192;

    private final NodeConnectionRegistry registry;
    private final NodeChannelCodec codec;
    private final NodeChannelService service;
    private final NodeChannelProperties props;

    public NodeChannelHandler(NodeConnectionRegistry registry, NodeChannelCodec codec,
                              NodeChannelService service, NodeChannelProperties props) {
        this.registry = registry;
        this.codec = codec;
        this.service = service;
        this.props = props;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        NodeChannelMessage msg = codec.fromJson(message.getPayload());
        switch (msg.type()) {
            case NodeChannelTypes.TYPE_HELLO -> onHello(session, msg);
            case NodeChannelTypes.TYPE_HEARTBEAT -> {
                if (msg.nodeId() != null) registry.heartbeat(msg.nodeId(), Instant.now());
            }
            case NodeChannelTypes.TYPE_COMMAND_ACK -> {
                if (msg.commandId() != null && msg.outcome() != null) {
                    service.onCommandAck(msg.commandId(), msg.outcome());
                }
            }
            default -> log.debug("node-channel: unbekannter Typ ignoriert: {}", msg.type());
        }
    }

    private void onHello(WebSocketSession session, NodeChannelMessage msg) throws Exception {
        String expected = msg.nodeId() == null ? null : props.getTokens().get(msg.nodeId());
        if (expected == null || !Objects.equals(expected, msg.token())) {
            log.warn("node-channel: HELLO abgelehnt für nodeId={}", msg.nodeId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        // Ab hier läuft jeder sendMessage-Aufruf auf dieser Session über den Decorator — die Registry
        // gibt die dekorierte Session an alles zurück, was sie später ausliest (z.B. dispatchCommand).
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT_BYTES);
        registry.register(msg.nodeId(), safeSession, Instant.now());
        safeSession.sendMessage(new TextMessage(codec.toJson(NodeChannelMessage.helloAck())));
        log.info("node-channel: Node {} verbunden", msg.nodeId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.removeBySession(session);
    }
}

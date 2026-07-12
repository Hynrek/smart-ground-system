package ch.jp.shooting.nodechannel;

import ch.jp.shooting.config.NodeChannelProperties;
import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NodeChannelHandlerTest {

    private NodeChannelProperties props;
    private NodeConnectionRegistry registry;
    private NodeChannelCodec codec;
    private NodeChannelService service;
    private NodeChannelHandler handler;

    @BeforeEach
    void setUp() {
        props = new NodeChannelProperties();
        props.setTokens(Map.of("node-1", "secret"));
        registry = new NodeConnectionRegistry();
        codec = new NodeChannelCodec();
        service = mock(NodeChannelService.class);
        handler = new NodeChannelHandler(registry, codec, service, props);
    }

    private WebSocketSession openSession(String id) throws Exception {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void hello_validToken_registersAndAcks() throws Exception {
        var session = openSession("s1");
        handler.handleTextMessage(session, new TextMessage(codec.toJson(NodeChannelMessage.hello("node-1", "secret"))));

        // registered → live
        assertThat(registry.liveSessionFor("node-1", java.time.Instant.now(), props.getStaleAfter())).isPresent();
        // HELLO_ACK sent
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        assertThat(codec.fromJson(sent.getValue().getPayload()).type()).isEqualTo(NodeChannelTypes.TYPE_HELLO_ACK);
    }

    @Test
    void hello_wrongToken_closesWithoutRegistering() throws Exception {
        var session = openSession("s1");
        handler.handleTextMessage(session, new TextMessage(codec.toJson(NodeChannelMessage.hello("node-1", "WRONG"))));

        assertThat(registry.liveSessionFor("node-1", java.time.Instant.now(), props.getStaleAfter())).isEmpty();
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void commandAck_isRoutedToService() throws Exception {
        var session = openSession("s1");
        var id = java.util.UUID.randomUUID();
        handler.handleTextMessage(session,
            new TextMessage(codec.toJson(NodeChannelMessage.commandAck(id, NodeChannelTypes.OUTCOME_OK))));
        verify(service).onCommandAck(id, NodeChannelTypes.OUTCOME_OK);
    }
}

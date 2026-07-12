package ch.jp.shooting.nodechannel;

import ch.jp.shooting.config.NodeChannelProperties;
import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NodeChannelServiceTest {

    private NodeConnectionRegistry registry;
    private NodeChannelCodec codec;
    private NodeChannelProperties props;
    private NodeChannelService service;

    @BeforeEach
    void setUp() {
        registry = new NodeConnectionRegistry();
        codec = new NodeChannelCodec();
        props = new NodeChannelProperties();
        props.setCommandTimeout(Duration.ofMillis(200));
        service = new NodeChannelService(registry, codec, props);
    }

    @Test
    void dispatch_toUnknownNode_returnsNodeUnreachable() {
        var outcome = service.dispatchCommand("ghost", "FIRE", "{}");
        assertThat(outcome).isEqualTo(CommandOutcome.NODE_UNREACHABLE);
    }

    @Test
    void dispatch_ackedOk_returnsOk() throws Exception {
        var session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s1");
        registry.register("node-1", session, Instant.now());

        // Ack asynchronously as soon as the COMMAND is sent: capture the commandId from the sent frame.
        doAnswer(inv -> {
            TextMessage sent = inv.getArgument(0);
            var cmd = codec.fromJson(sent.getPayload());
            CompletableFuture.runAsync(() ->
                service.onCommandAck(cmd.commandId(), NodeChannelTypes.OUTCOME_OK));
            return null;
        }).when(session).sendMessage(any(TextMessage.class));

        var outcome = service.dispatchCommand("node-1", "FIRE", "{}");
        assertThat(outcome).isEqualTo(CommandOutcome.OK);
    }

    @Test
    void dispatch_noAckBeforeTimeout_returnsCommandOutcomeUnknown() throws Exception {
        var session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s1");
        registry.register("node-1", session, Instant.now());
        // sendMessage does nothing → no ack ever arrives → timeout

        var outcome = service.dispatchCommand("node-1", "FIRE", "{}");
        assertThat(outcome).isEqualTo(CommandOutcome.COMMAND_OUTCOME_UNKNOWN);
    }
}

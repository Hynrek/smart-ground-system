package ch.jp.shooting.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "node-channel.tokens.node-1=secret",
        "node-channel.command-timeout=300ms"
})
class NodeChannelIntegrationTest {

    @LocalServerPort int port;
    @Autowired NodeChannelService service;
    @Autowired NodeChannelCodec codec;

    /** Minimaler Fake-Node: sendet HELLO, kann optional jedes COMMAND acken. */
    private final class FakeNode extends TextWebSocketHandler {
        final BlockingQueue<NodeChannelMessage> received = new LinkedBlockingQueue<>();
        volatile boolean autoAck;
        volatile WebSocketSession session;

        FakeNode(boolean autoAck) { this.autoAck = autoAck; }

        @Override public void afterConnectionEstablished(WebSocketSession s) throws Exception {
            this.session = s;
            s.sendMessage(new TextMessage(codec.toJson(NodeChannelMessage.hello("node-1", "secret"))));
        }
        @Override protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception {
            var msg = codec.fromJson(m.getPayload());
            received.add(msg);
            if (autoAck && NodeChannelTypes.TYPE_COMMAND.equals(msg.type())) {
                s.sendMessage(new TextMessage(
                        codec.toJson(NodeChannelMessage.commandAck(msg.commandId(), NodeChannelTypes.OUTCOME_OK))));
            }
        }
    }

    private FakeNode connect(boolean autoAck) throws Exception {
        var node = new FakeNode(autoAck);
        new StandardWebSocketClient()
                .execute(node, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + port + "/node-channel"))
                .get(5, TimeUnit.SECONDS);
        // wait for HELLO_ACK so the registry has us
        NodeChannelMessage ack = node.received.poll(5, TimeUnit.SECONDS);
        assertThat(ack).isNotNull();
        assertThat(ack.type()).isEqualTo(NodeChannelTypes.TYPE_HELLO_ACK);
        return node;
    }

    @Test
    void dispatch_reachesNode_andReturnsOk_withoutKnowingItsIp() throws Exception {
        var node = connect(true);
        CommandOutcome outcome = service.dispatchCommand("node-1", "FIRE", "{\"deviceId\":\"d1\"}");
        assertThat(outcome).isEqualTo(CommandOutcome.OK);
        // the node actually received a COMMAND frame
        assertThat(node.received).anySatisfy(m -> assertThat(m.type()).isEqualTo(NodeChannelTypes.TYPE_COMMAND));
    }

    @Test
    void dispatch_whenBackhaulCutAfterCommandSent_returnsCommandOutcomeUnknown() throws Exception {
        var node = connect(false);              // never acks, session stays live for now

        // dispatchCommand blocks until ack-or-timeout, so run it on its own thread
        CompletableFuture<CommandOutcome> future = CompletableFuture.supplyAsync(
                () -> service.dispatchCommand("node-1", "FIRE", "{}"));

        // wait until the command genuinely left the Hub over the still-live connection
        NodeChannelMessage command = node.received.poll(5, TimeUnit.SECONDS);
        assertThat(command).isNotNull();
        assertThat(command.type()).isEqualTo(NodeChannelTypes.TYPE_COMMAND);

        // only now cut the backhaul — simulates the connection dying mid-command, after send, before any ack
        node.session.close();

        CommandOutcome outcome = future.get(5, TimeUnit.SECONDS);
        assertThat(outcome).isEqualTo(CommandOutcome.COMMAND_OUTCOME_UNKNOWN);
    }
}

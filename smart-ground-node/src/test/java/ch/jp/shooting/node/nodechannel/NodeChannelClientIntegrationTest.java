package ch.jp.shooting.node.nodechannel;

import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testet den echten NodeChannelClientHandler gegen ein Stand-in, ohne echten Socket: der Handler ist
 * gegen WebSocketSession programmiert, also treiben wir ihn mit einer Fake-Session. Der Cross-Prozess-
 * Socket-Pfad wird manuell verifiziert (Task 6, Schritt 3).
 */
class NodeChannelClientIntegrationTest {

    private final NodeChannelCodec codec = new NodeChannelCodec();

    @Test
    void onConnect_sendsHello_thenAcksACommandViaSeam() throws Exception {
        var sent = new LinkedBlockingQueue<String>();
        var fakeSession = new FakeSession(sent);
        var props = new NodeChannelProperties();
        props.setNodeId("node-1");
        props.setToken("secret");
        var handler = new NodeChannelClientHandler(props, codec,
                (type, payload) -> NodeChannelTypes.OUTCOME_OK, new CommandDeduplicator(100), () -> {});

        // 1) connection established → HELLO sent
        handler.afterConnectionEstablished(fakeSession);
        var hello = codec.fromJson(sent.poll(1, TimeUnit.SECONDS));
        assertThat(hello.type()).isEqualTo(NodeChannelTypes.TYPE_HELLO);
        assertThat(hello.nodeId()).isEqualTo("node-1");

        // 2) receive a COMMAND → COMMAND_ACK with OK
        var id = UUID.randomUUID();
        handler.handleTextMessage(fakeSession,
                new org.springframework.web.socket.TextMessage(
                        codec.toJson(NodeChannelMessage.command(id, "FIRE", "{}"))));
        var ack = codec.fromJson(sent.poll(1, TimeUnit.SECONDS));
        assertThat(ack.type()).isEqualTo(NodeChannelTypes.TYPE_COMMAND_ACK);
        assertThat(ack.commandId()).isEqualTo(id);
        assertThat(ack.outcome()).isEqualTo(NodeChannelTypes.OUTCOME_OK);
    }

    @Test
    void repeatedCommandId_isAckedTwiceButExecutedOnce() throws Exception {
        var sent = new LinkedBlockingQueue<String>();
        var fakeSession = new FakeSession(sent);
        var props = new NodeChannelProperties();
        var runs = new int[]{0};
        var handler = new NodeChannelClientHandler(props, codec,
                (type, payload) -> { runs[0]++; return NodeChannelTypes.OUTCOME_OK; },
                new CommandDeduplicator(100), () -> {});

        var id = UUID.randomUUID();
        var frame = new org.springframework.web.socket.TextMessage(
                codec.toJson(NodeChannelMessage.command(id, "FIRE", "{}")));
        handler.handleTextMessage(fakeSession, frame);
        handler.handleTextMessage(fakeSession, frame);   // Hub retried the same UUID

        assertThat(runs[0]).isEqualTo(1);                // executed once
        assertThat(sent).hasSize(2);                     // acked both times
    }
}

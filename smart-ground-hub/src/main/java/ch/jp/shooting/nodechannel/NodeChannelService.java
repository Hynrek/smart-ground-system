package ch.jp.shooting.nodechannel;

import ch.jp.shooting.config.NodeChannelProperties;
import ch.jp.smartground.nodechannel.NodeChannelMessage;
import ch.jp.smartground.nodechannel.NodeChannelTypes;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Dispatcht ein Command über den node-channel und korreliert das COMMAND_ACK per UUID.
 * Der Hub kennt die IP des Node nie — er schreibt in dessen offene Session. Timeout → COMMAND_OUTCOME_UNKNOWN.
 */
@Service
@NullMarked
public class NodeChannelService {

    private static final Logger log = LoggerFactory.getLogger(NodeChannelService.class);

    private final NodeConnectionRegistry registry;
    private final NodeChannelCodec codec;
    private final NodeChannelProperties props;
    private final ConcurrentHashMap<UUID, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    public NodeChannelService(NodeConnectionRegistry registry, NodeChannelCodec codec,
                              NodeChannelProperties props) {
        this.registry = registry;
        this.codec = codec;
        this.props = props;
    }

    public CommandOutcome dispatchCommand(String nodeId, String commandType, String payloadJson) {
        var session = registry.liveSessionFor(nodeId, Instant.now(), props.getStaleAfter()).orElse(null);
        if (session == null || !session.isOpen()) {
            return CommandOutcome.NODE_UNREACHABLE;
        }

        UUID commandId = UUID.randomUUID();
        var future = new CompletableFuture<String>();
        pending.put(commandId, future);
        try {
            session.sendMessage(new TextMessage(
                    codec.toJson(NodeChannelMessage.command(commandId, commandType, payloadJson))));
            String outcome = future.get(props.getCommandTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return NodeChannelTypes.OUTCOME_OK.equals(outcome) ? CommandOutcome.OK : CommandOutcome.REJECTED;
        } catch (TimeoutException e) {
            // Der Backhaul kann mitten im Command gekappt sein, das Ack verloren — NICHT „fehlgeschlagen".
            log.warn("node-channel: kein Ack für Command {} an {} — COMMAND_OUTCOME_UNKNOWN", commandId, nodeId);
            return CommandOutcome.COMMAND_OUTCOME_UNKNOWN;
        } catch (Exception e) {
            log.warn("node-channel: Dispatch an {} fehlgeschlagen ({}) — COMMAND_OUTCOME_UNKNOWN",
                    nodeId, e.getMessage());
            return CommandOutcome.COMMAND_OUTCOME_UNKNOWN;
        } finally {
            pending.remove(commandId);
        }
    }

    /** Vom Handler bei COMMAND_ACK aufgerufen — schliesst das wartende Future. */
    public void onCommandAck(UUID commandId, String outcome) {
        var future = pending.get(commandId);
        if (future != null) {
            future.complete(outcome);
        }
    }
}

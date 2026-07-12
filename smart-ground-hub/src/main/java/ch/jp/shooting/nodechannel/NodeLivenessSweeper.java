package ch.jp.shooting.nodechannel;

import ch.jp.shooting.config.NodeChannelProperties;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Markiert Nodes ohne Heartbeat als STALE und entfernt sie aus dem Verzeichnis. */
@Component
@NullMarked
public class NodeLivenessSweeper {

    private static final Logger log = LoggerFactory.getLogger(NodeLivenessSweeper.class);

    private final NodeConnectionRegistry registry;
    private final NodeChannelProperties props;

    public NodeLivenessSweeper(NodeConnectionRegistry registry, NodeChannelProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${node-channel.sweep-interval-ms:5000}")
    void sweep() {
        var swept = registry.sweepStale(Instant.now(), props.getStaleAfter());
        if (!swept.isEmpty()) {
            log.warn("node-channel: {} Node(s) als STALE entfernt: {}", swept.size(), swept);
        }
    }
}

package ch.jp.shooting.nodechannel;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NodeConnectionRegistryTest {

    @Test
    void liveSessionFor_presentAfterRegister_absentAfterStale() {
        var registry = new NodeConnectionRegistry();
        var session = mock(WebSocketSession.class);
        var t0 = Instant.parse("2026-07-12T10:00:00Z");

        registry.register("node-1", session, t0);
        assertThat(registry.liveSessionFor("node-1", t0.plusSeconds(5), Duration.ofSeconds(30)))
            .contains(session);

        // 40s with no heartbeat > 30s staleAfter → not live
        assertThat(registry.liveSessionFor("node-1", t0.plusSeconds(40), Duration.ofSeconds(30)))
            .isEmpty();
    }

    @Test
    void heartbeat_refreshesLiveness() {
        var registry = new NodeConnectionRegistry();
        var session = mock(WebSocketSession.class);
        var t0 = Instant.parse("2026-07-12T10:00:00Z");
        registry.register("node-1", session, t0);
        registry.heartbeat("node-1", t0.plusSeconds(25));

        assertThat(registry.liveSessionFor("node-1", t0.plusSeconds(50), Duration.ofSeconds(30)))
            .contains(session);
    }

    @Test
    void sweepStale_returnsAndDropsStaleNodes() {
        var registry = new NodeConnectionRegistry();
        var t0 = Instant.parse("2026-07-12T10:00:00Z");
        registry.register("fresh", mock(WebSocketSession.class), t0.plusSeconds(35));
        registry.register("stale", mock(WebSocketSession.class), t0);

        var swept = registry.sweepStale(t0.plusSeconds(40), Duration.ofSeconds(30));

        assertThat(swept).containsExactly("stale");
        assertThat(registry.liveSessionFor("stale", t0.plusSeconds(40), Duration.ofSeconds(30))).isEmpty();
        assertThat(registry.liveSessionFor("fresh", t0.plusSeconds(40), Duration.ofSeconds(30)))
            .isPresent();
    }
}

package ch.jp.shooting.nodechannel;

import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-RAM-Verzeichnis der offenen node-channel-Verbindungen: nodeId -> Session + letzter Heartbeat.
 * Liveness hat pro Hop genau eine Quelle (die Spec): der Hub kennt einen Node ausschliesslich über
 * dessen offenen, heartbeatenden Kanal. Nach staleAfter ohne Beat gilt der Node als STALE und wird
 * nicht mehr als lebendig gemeldet — Commands dorthin werden abgelehnt statt in einen toten Socket
 * dispatcht. Bewusst nicht persistent: eine Verbindung überlebt keinen Hub-Neustart.
 */
@Component
@NullMarked
public class NodeConnectionRegistry {

    private record Entry(WebSocketSession session, Instant lastBeat) {}

    private final ConcurrentHashMap<String, Entry> byNode = new ConcurrentHashMap<>();

    public void register(String nodeId, WebSocketSession session, Instant now) {
        byNode.put(nodeId, new Entry(session, now));
    }

    public void heartbeat(String nodeId, Instant now) {
        byNode.computeIfPresent(nodeId, (id, e) -> new Entry(e.session(), now));
    }

    public void removeBySession(WebSocketSession session) {
        byNode.values().removeIf(e -> e.session().getId().equals(session.getId()));
    }

    /** Session, falls der Node registriert UND nicht stale ist; sonst leer. */
    public Optional<WebSocketSession> liveSessionFor(String nodeId, Instant now, Duration staleAfter) {
        Entry e = byNode.get(nodeId);
        if (e == null) return Optional.empty();
        if (Duration.between(e.lastBeat(), now).compareTo(staleAfter) > 0) return Optional.empty();
        return Optional.of(e.session());
    }

    /** Entfernt alle stale gewordenen Nodes und gibt ihre Ids zurück (für den Sweeper zum Loggen/Schliessen). */
    public List<String> sweepStale(Instant now, Duration staleAfter) {
        List<String> swept = new ArrayList<>();
        byNode.forEach((nodeId, e) -> {
            if (Duration.between(e.lastBeat(), now).compareTo(staleAfter) > 0) {
                swept.add(nodeId);
            }
        });
        swept.forEach(byNode::remove);
        return swept;
    }
}

package ch.jp.shooting.node.nodechannel;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Idempotenz auf der Command-UUID: ein bereits gesehenes Command wird nicht erneut ausgeführt, sondern
 * mit dem gespeicherten Ergebnis re-acked. Beschränkt (LRU) — ein Timeout auf Hub-Seite darf denselben
 * Command erneut schicken, ohne dass die Box zweimal auslöst.
 */
@Component
public class CommandDeduplicator {

    private final int maxEntries;
    private final Map<UUID, String> seen;

    public CommandDeduplicator() {
        this(1000);
    }

    public CommandDeduplicator(int maxEntries) {
        this.maxEntries = maxEntries;
        this.seen = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
                return size() > CommandDeduplicator.this.maxEntries;
            }
        };
    }

    public synchronized String handle(UUID commandId, Supplier<String> work) {
        String cached = seen.get(commandId);
        if (cached != null) {
            return cached;
        }
        String outcome = work.get();
        seen.put(commandId, outcome);
        return outcome;
    }
}

package ch.jp.shooting.node.onboarding;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-RAM-Register der pending Boxen, gefüllt über die Ingest-Seam {@link #onHello}.
 * Bewusst flüchtig: eine Box, die aufhört zu senden, veraltet; nach Node-Neustart
 * ist die Liste leer, bis wieder HELLO eintrifft. Keine Serial-Anbindung in Plan 2 —
 * {@code onHello} wird von Tests (und später der Radio-Receive-Schleife) aufgerufen.
 */
@Component
public class PendingBoxRegistry {

    private final ConcurrentHashMap<String, PendingBox> byMac = new ConcurrentHashMap<>();

    public void onHello(String mac, int rssi, byte[] boxNonce) {
        Instant now = Instant.now();
        byte[] nonceCopy = boxNonce.clone();
        byMac.compute(mac, (key, existing) -> {
            Instant firstSeen = existing == null ? now : existing.firstSeen();
            return new PendingBox(mac, rssi, firstSeen, now, nonceCopy);
        });
    }

    public Collection<PendingBox> list() {
        return List.copyOf(byMac.values());
    }

    public Optional<PendingBox> find(String mac) {
        return Optional.ofNullable(byMac.get(mac));
    }

    public void remove(String mac) {
        byMac.remove(mac);
    }
}

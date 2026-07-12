package ch.jp.shooting.node.onboarding;

import java.time.Instant;

/** node-api-Sicht auf eine pending Box. Ohne boxNonce — die geht die Bediener-UI nichts an. */
public record PendingBoxResponse(String mac, int rssi, Instant firstSeen, Instant lastSeen) {

    public static PendingBoxResponse from(PendingBox box) {
        return new PendingBoxResponse(box.mac(), box.rssi(), box.firstSeen(), box.lastSeen());
    }
}

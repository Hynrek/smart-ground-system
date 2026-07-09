package ch.jp.shooting.node.frame;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duplikat-/Storm-Unterdrueckung fuer Frames: Schluessel (src_mac, frame_id), Eintraege verfallen nach
 * windowMillis (docs/superpowers/specs/2026-07-09-espnow-protocol-contracts-design.md Abschnitt 1).
 */
public final class SeenCache {

    private final long windowMillis;
    private final Map<String, Long> seenAt = new ConcurrentHashMap<>();

    public SeenCache(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public boolean isDuplicate(byte[] srcMac, int frameId, long nowMillis) {
        String key = key(srcMac, frameId);
        Long last = seenAt.get(key);
        boolean duplicate = last != null && (nowMillis - last) < windowMillis;
        seenAt.put(key, nowMillis);
        seenAt.entrySet().removeIf(e -> (nowMillis - e.getValue()) >= windowMillis);
        return duplicate;
    }

    private static String key(byte[] srcMac, int frameId) {
        StringBuilder sb = new StringBuilder(srcMac.length * 2 + 6);
        for (byte b : srcMac) {
            sb.append(String.format("%02x", b));
        }
        sb.append('-').append(frameId);
        return sb.toString();
    }
}

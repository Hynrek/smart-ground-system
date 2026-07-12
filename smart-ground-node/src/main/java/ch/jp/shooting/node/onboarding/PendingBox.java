package ch.jp.shooting.node.onboarding;

import java.time.Instant;

/** Ein noch nicht gekoppeltes Gerät, das per HELLO-Broadcast aufgetaucht ist. */
public record PendingBox(String mac, int rssi, Instant firstSeen, Instant lastSeen, byte[] boxNonce) {
}

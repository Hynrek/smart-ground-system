package ch.jp.shooting.node.onboarding;

import java.time.Instant;

/** Ergebnis eines couple()-Aufrufs für die node-api-Antwort. */
public record CoupleResult(String mac, String status, Instant tokenExpiresAt) {
}

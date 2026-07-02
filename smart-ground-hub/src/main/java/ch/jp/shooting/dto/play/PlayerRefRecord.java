package ch.jp.shooting.dto.play;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

// userId: Account-Verknüpfung bei QR-Checkin; null für anonyme Platzhalter und Gäste
public record PlayerRefRecord(String id, String type, String displayName, @Nullable UUID userId) {}

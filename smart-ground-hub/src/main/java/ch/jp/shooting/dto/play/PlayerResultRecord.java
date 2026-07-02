package ch.jp.shooting.dto.play;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

// userId: Account-Verknüpfung bei QR-Checkin; null für anonyme Platzhalter und Gäste
public record PlayerResultRecord(
    String playerId,
    String displayName,
    int totalPoints,
    int maxPoints,
    List<StepStateRecord> stepStates,
    @Nullable UUID userId
) {}

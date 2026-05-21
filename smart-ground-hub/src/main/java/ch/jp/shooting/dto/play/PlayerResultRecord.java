package ch.jp.shooting.dto.play;

import java.util.List;

public record PlayerResultRecord(
    String playerId,
    String displayName,
    int totalPoints,
    int maxPoints,
    List<StepStateRecord> stepStates
) {}

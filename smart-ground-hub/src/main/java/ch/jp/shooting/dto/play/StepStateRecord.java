package ch.jp.shooting.dto.play;

public record StepStateRecord(
    String playerId,
    int ablaufIndex,
    int stepIndex,
    String state,
    int pointValue,
    int noBirds,
    int pointsEarned
) {}

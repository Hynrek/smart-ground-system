package ch.jp.shooting.dto.play;

import java.util.List;
import java.util.UUID;

public record PlayPhaseRecord(
    int phaseIndex,
    UUID programmeId,
    String programmeName,
    String status,
    List<PlayBlockRecord> blocks
) {}

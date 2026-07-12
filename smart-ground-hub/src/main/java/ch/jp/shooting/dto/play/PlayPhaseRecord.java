package ch.jp.shooting.dto.play;

import java.util.List;
import java.util.UUID;

public record PlayPhaseRecord(
    int phaseIndex,
    UUID passeId,
    String passeName,
    String status,
    List<PlayBlockRecord> blocks
) {}

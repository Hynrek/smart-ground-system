package ch.jp.shooting.dto.play;

import java.util.List;
import java.util.UUID;

public record TrainingProgrammeRecord(
    UUID id,
    String name,
    List<EmbeddedAblaufRecord> ablaeufe
) {}

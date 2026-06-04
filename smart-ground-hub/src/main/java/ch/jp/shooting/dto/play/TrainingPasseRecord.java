package ch.jp.shooting.dto.play;

import java.util.List;
import java.util.UUID;

public record TrainingPasseRecord(
    UUID id,
    String name,
    List<EmbeddedSerieRecord> serien
) {}

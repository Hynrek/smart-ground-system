package ch.jp.shooting.dto.play;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

public record EmbeddedAblaufRecord(
    UUID id,
    String alias,
    @Nullable UUID rangeId,
    @Nullable String rangeName,
    List<StepRecord> steps
) {}

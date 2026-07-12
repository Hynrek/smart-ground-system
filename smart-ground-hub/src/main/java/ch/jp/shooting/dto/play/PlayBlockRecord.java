package ch.jp.shooting.dto.play;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayBlockRecord(
    UUID blockId,
    UUID serieId,
    String serieAlias,
    @Nullable UUID rangeId,
    @Nullable String rangeName,
    List<StepRecord> steps,
    String status,
    @Nullable Instant completedAt,
    @Nullable BlockResultRecord result
) {}

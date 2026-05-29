package ch.jp.shooting.dto.play;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepRecord(
    String id,
    String type,
    /** Positions-Label der primären Position (z.B. "A"). */
    @Nullable String posId,
    @Nullable String alias,
    /** Positions-Labels für Doppelwurf-Schritte. */
    @Nullable String posId1,
    @Nullable String posId2,
    @Nullable String alias1,
    @Nullable String alias2
) {}

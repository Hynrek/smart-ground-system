package ch.jp.shooting.dto.play;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepRecord(
    String id,
    String type,
    @Nullable String posId,
    @Nullable String alias,
    @Nullable String posId1,
    @Nullable String posId2,
    @Nullable String alias1,
    @Nullable String alias2,
    /** Buchstabe der Schussposition (A, B, C …) für die Anzeige. */
    @Nullable String letter
) {}

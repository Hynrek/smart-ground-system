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
    /** Buchstabe der Schussposition (A, B, C …) für Solo/Raffale-Schritte. */
    @Nullable String letter,
    /** Buchstabe der ersten Schussposition für Pair/a-Schuss-Schritte. */
    @Nullable String letter1,
    /** Buchstabe der zweiten Schussposition für Pair/a-Schuss-Schritte. */
    @Nullable String letter2
) {}

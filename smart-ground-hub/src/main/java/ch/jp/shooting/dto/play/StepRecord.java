package ch.jp.shooting.dto.play;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepRecord(
    String id,
    String type,
    @Nullable UUID deviceId,
    @Nullable String alias,
    @Nullable String letter,
    @Nullable UUID deviceId1,
    @Nullable UUID deviceId2,
    @Nullable String alias1,
    @Nullable String alias2,
    @Nullable String letter1,
    @Nullable String letter2
) {}

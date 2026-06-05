package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@NullMarked
public class CompetitionSerieResultResponse {
    @JsonProperty("id")         public UUID id;
    @JsonProperty("sessionId")  public UUID sessionId;
    @JsonProperty("groupId")    public UUID groupId;
    @JsonProperty("passeIndex") public int passeIndex;
    @JsonProperty("serieId")    public UUID serieId;
    @JsonProperty("playInstanceId") @Nullable public UUID playInstanceId;
    @JsonProperty("completedAt") public Instant completedAt;

    public CompetitionSerieResultResponse() {}
}

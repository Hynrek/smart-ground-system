package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@NullMarked
public class CompleteSerieRequest {

    @JsonProperty("passeIndex")
    public int passeIndex;

    @JsonProperty("playInstanceId")
    @Nullable
    public UUID playInstanceId;

    /**
     * Rohergebnisse vom Play.
     * Erwartetes Format: { "players": [{ "playerId": "uuid", "totalPoints": 8, "maxPoints": 10 }] }
     */
    @JsonProperty("results")
    @Nullable
    public Object results;

    public CompleteSerieRequest() {}
}

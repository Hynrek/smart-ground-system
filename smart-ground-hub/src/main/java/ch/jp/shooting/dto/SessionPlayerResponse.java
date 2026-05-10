package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Response mit Spieler-Information in einer Session.
 */
@NullMarked
public class SessionPlayerResponse {
    @JsonProperty("id")
    public UUID id;

    @JsonProperty("type")
    public String type; // USER oder GUEST

    @JsonProperty("displayName")
    public String displayName;

    @JsonProperty("userId")
    @Nullable
    public UUID userId;

    @JsonProperty("createdAt")
    public Instant createdAt;

    public SessionPlayerResponse() {
    }
}

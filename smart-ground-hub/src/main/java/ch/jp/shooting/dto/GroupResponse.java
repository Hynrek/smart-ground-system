package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Response mit Gruppe-Information.
 */
@NullMarked
public class GroupResponse {
    @JsonProperty("id")
    public UUID id;

    @JsonProperty("name")
    public String name;

    @JsonProperty("members")
    public List<SessionPlayerResponse> members = new ArrayList<>();

    @JsonProperty("progress")
    @Nullable
    public String progress; // JSON: GroupProgress[]

    @JsonProperty("createdAt")
    public Instant createdAt;

    public GroupResponse() {
    }
}

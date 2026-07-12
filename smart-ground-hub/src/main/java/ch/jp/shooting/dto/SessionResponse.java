package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Response mit vollständiger Session-Information.
 */
@NullMarked
public class SessionResponse {
    @JsonProperty("id")
    public UUID id;

    @JsonProperty("name")
    @Nullable
    public String name;

    @JsonProperty("type")
    public String type;

    @JsonProperty("status")
    public String status;

    @JsonProperty("templateId")
    @Nullable
    public UUID templateId;

    @JsonProperty("groups")
    public List<GroupResponse> groups = new ArrayList<>();

    @JsonProperty("playerResults")
    public List<PlayerResultResponse> playerResults = new ArrayList<>();

    @JsonProperty("programSnapshots")
    @Nullable
    public String programSnapshots;

    @JsonProperty("rangeSegmentMap")
    @Nullable
    public String rangeSegmentMap;

    @JsonProperty("startedAt")
    @Nullable
    public Instant startedAt;

    @JsonProperty("completedAt")
    @Nullable
    public Instant completedAt;

    @JsonProperty("createdAt")
    public Instant createdAt;

    public SessionResponse() {
    }
}

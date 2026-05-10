package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Progress-Tracking pro Programm in einer Gruppe.
 */
@NullMarked
public class GroupProgress {
    @JsonProperty("programId")
    public UUID programId;

    @JsonProperty("completedSegmentIds")
    public List<UUID> completedSegmentIds = new ArrayList<>();

    @JsonProperty("activeRangeId")
    @Nullable
    public UUID activeRangeId; // null wenn unterwegs

    @JsonProperty("activeSegmentId")
    @Nullable
    public UUID activeSegmentId; // null wenn unterwegs

    public GroupProgress() {
    }

    public GroupProgress(UUID programId) {
        this.programId = programId;
    }
}

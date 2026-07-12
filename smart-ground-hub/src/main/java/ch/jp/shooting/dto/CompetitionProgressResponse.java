package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NullMarked
public class CompetitionProgressResponse {

    @JsonProperty("groups")
    public List<GroupProgress> groups = new ArrayList<>();

    public static class GroupProgress {
        @JsonProperty("groupId")           public UUID groupId;
        @JsonProperty("groupName")         public String groupName;
        @JsonProperty("currentPasseIndex") public int currentPasseIndex;
        @JsonProperty("completedSerien")   public List<SerieCompletion> completedSerien = new ArrayList<>();
    }

    public static class SerieCompletion {
        @JsonProperty("passeIndex")  public int passeIndex;
        @JsonProperty("serieId")     public UUID serieId;
        @JsonProperty("completedAt") public Instant completedAt;
    }
}

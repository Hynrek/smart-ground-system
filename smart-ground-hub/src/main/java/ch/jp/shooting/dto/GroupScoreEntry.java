package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Score-Eintrag für eine Gruppe im Scoreboard.
 */
@NullMarked
public class GroupScoreEntry {
    @JsonProperty("groupId")
    public UUID groupId;

    @JsonProperty("groupName")
    public String groupName;

    @JsonProperty("totalScore")
    public int totalScore;

    @JsonProperty("completedSegments")
    public int completedSegments;

    @JsonProperty("ranking")
    public List<PlayerScoreEntry> ranking = new ArrayList<>();

    public GroupScoreEntry() {
    }

    public GroupScoreEntry(UUID groupId, String groupName) {
        this.groupId = groupId;
        this.groupName = groupName;
    }
}

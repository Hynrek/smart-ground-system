package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Score-Eintrag für einen Spieler im Scoreboard.
 */
@NullMarked
public class PlayerScoreEntry {
    @JsonProperty("playerId")
    public UUID playerId;

    @JsonProperty("groupId")
    public UUID groupId;

    @JsonProperty("displayName")
    public String displayName;

    @JsonProperty("totalScore")
    public int totalScore;

    @JsonProperty("maxScore")
    public int maxScore;

    @JsonProperty("rank")
    public int rank;

    @JsonProperty("segmentScores")
    public List<SegmentScoreEntry> segmentScores = new ArrayList<>();

    public PlayerScoreEntry() {
    }
}

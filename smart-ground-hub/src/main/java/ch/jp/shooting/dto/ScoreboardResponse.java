package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Response mit Scoreboard-Daten (live Ranking).
 */
@NullMarked
public class ScoreboardResponse {
    @JsonProperty("playerScores")
    public List<PlayerScoreEntry> playerScores = new ArrayList<>();

    @JsonProperty("groupScores")
    public List<GroupScoreEntry> groupScores = new ArrayList<>();

    public ScoreboardResponse() {
    }
}

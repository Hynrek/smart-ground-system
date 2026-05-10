package ch.jp.shooting.dto;

import java.util.List;
import java.util.UUID;

/**
 * DTO für Session-Leaderboard mit Spieler- und Gruppen-Rankings.
 */
public class SessionLeaderboardResponse {
    private UUID sessionId;
    private String status;
    private List<PlayerScoreEntry> playerScores;
    private List<GroupScoreEntry> groupScores;

    public SessionLeaderboardResponse(
            UUID sessionId,
            String status,
            List<PlayerScoreEntry> playerScores,
            List<GroupScoreEntry> groupScores) {
        this.sessionId = sessionId;
        this.status = status;
        this.playerScores = playerScores;
        this.groupScores = groupScores;
    }

    // ── Getter & Setter ──
    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<PlayerScoreEntry> getPlayerScores() {
        return playerScores;
    }

    public void setPlayerScores(List<PlayerScoreEntry> playerScores) {
        this.playerScores = playerScores;
    }

    public List<GroupScoreEntry> getGroupScores() {
        return groupScores;
    }

    public void setGroupScores(List<GroupScoreEntry> groupScores) {
        this.groupScores = groupScores;
    }

    // ── Nested Klassen ──
    public static class PlayerScoreEntry {
        private UUID playerId;
        private String displayName;
        private int totalScore;
        private int maxScore;
        private int rank;

        public PlayerScoreEntry(UUID playerId, String displayName, int totalScore, int maxScore, int rank) {
            this.playerId = playerId;
            this.displayName = displayName;
            this.totalScore = totalScore;
            this.maxScore = maxScore;
            this.rank = rank;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public void setPlayerId(UUID playerId) {
            this.playerId = playerId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public int getTotalScore() {
            return totalScore;
        }

        public void setTotalScore(int totalScore) {
            this.totalScore = totalScore;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public void setMaxScore(int maxScore) {
            this.maxScore = maxScore;
        }

        public int getRank() {
            return rank;
        }

        public void setRank(int rank) {
            this.rank = rank;
        }
    }

    public static class GroupScoreEntry {
        private UUID groupId;
        private String groupName;
        private int totalScore;
        private int maxScore;
        private int rank;

        public GroupScoreEntry(UUID groupId, String groupName, int totalScore, int maxScore, int rank) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.totalScore = totalScore;
            this.maxScore = maxScore;
            this.rank = rank;
        }

        public UUID getGroupId() {
            return groupId;
        }

        public void setGroupId(UUID groupId) {
            this.groupId = groupId;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public int getTotalScore() {
            return totalScore;
        }

        public void setTotalScore(int totalScore) {
            this.totalScore = totalScore;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public void setMaxScore(int maxScore) {
            this.maxScore = maxScore;
        }

        public int getRank() {
            return rank;
        }

        public void setRank(int rank) {
            this.rank = rank;
        }
    }
}

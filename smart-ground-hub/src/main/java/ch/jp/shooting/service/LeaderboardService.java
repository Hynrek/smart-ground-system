package ch.jp.shooting.service;

import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.PlayerResult;
import ch.jp.shooting.model.SessionPlayer;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.PlayerResultRepository;
import ch.jp.smartground.model.LeaderboardEntry;
import ch.jp.smartground.model.SessionLeaderboardResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@NullMarked
public class LeaderboardService {
    private final LiveSessionRepository sessionRepository;
    private final PlayerResultRepository playerResultRepository;
    private final ObjectMapper objectMapper;

    public LeaderboardService(LiveSessionRepository sessionRepository,
                            PlayerResultRepository playerResultRepository,
                            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.playerResultRepository = playerResultRepository;
        this.objectMapper = objectMapper;
    }

    public SessionLeaderboardResponse getLeaderboard(UUID sessionId) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        SessionLeaderboardResponse response = new SessionLeaderboardResponse();
        response.setSessionId(session.getId());

        List<LeaderboardEntry> standings = new ArrayList<>();
        int rank = 1;
        for (SessionPlayer player : session.getGroups().stream()
                .flatMap(g -> g.getMembers().stream())
                .collect(Collectors.toList())) {
            LeaderboardEntry entry = new LeaderboardEntry();
            entry.setRank(rank++);
            entry.setPlayerId(player.getId());
            entry.setDisplayName(player.getDisplayName());

            // Berechne Scores aus Player-Ergebnissen
            Optional<PlayerResult> result = playerResultRepository.findBySessionIdAndPlayerId(sessionId, player.getId());
            if (result.isPresent()) {
                ScoreCalculation scores = calculateScoresFromResult(result.get());
                entry.setTotalScore(scores.totalScore);
                entry.setMaxScore(scores.maxScore);
                entry.setAccuracy(scores.accuracy);
            } else {
                entry.setTotalScore(0);
                entry.setMaxScore(0);
                entry.setAccuracy(0.0f);
            }

            standings.add(entry);
        }

        // Sortiere nach Gesamtpunkte (absteigend)
        standings.sort((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()));

        // Aktualisiere Ränge nach Sortierung
        for (int i = 0; i < standings.size(); i++) {
            standings.get(i).setRank(i + 1);
        }

        response.setStandings(standings);
        return response;
    }

    /**
     * Berechnet Gesamtpunkte, Maximalpunkte und Treffergenauigkeit aus Player-Ergebnissen.
     */
    private ScoreCalculation calculateScoresFromResult(PlayerResult playerResult) {
        int totalScore = 0;
        int maxScore = 0;
        int hits = 0;
        int totalShots = 0;

        @Nullable String programResultsJson = playerResult.getProgramResults();
        if (programResultsJson == null || programResultsJson.isEmpty()) {
            return new ScoreCalculation(0, 0, 0.0f);
        }

        try {
            JsonNode programsArray = objectMapper.readTree(programResultsJson);

            // Iteriere durch alle Programme
            for (JsonNode programNode : programsArray) {
                JsonNode segmentResults = programNode.get("segmentResults");

                if (segmentResults != null && segmentResults.isArray()) {
                    // Iteriere durch alle Segmente
                    for (JsonNode segmentNode : segmentResults) {
                        // Berechne Score und Max-Score pro Segment
                        int segmentScore = segmentNode.has("score")
                            ? segmentNode.get("score").asInt(0)
                            : 0;
                        int segmentMaxScore = segmentNode.has("maxScore")
                            ? segmentNode.get("maxScore").asInt(0)
                            : 0;

                        totalScore += segmentScore;
                        maxScore += segmentMaxScore;

                        // Berechne Treffer pro Segment (aus stepResults)
                        JsonNode stepResults = segmentNode.get("stepResults");
                        if (stepResults != null && stepResults.isArray()) {
                            for (JsonNode stepNode : stepResults) {
                                String state = stepNode.has("state")
                                    ? stepNode.get("state").asText("")
                                    : "";

                                if ("HIT".equals(state)) {
                                    hits++;
                                }
                                totalShots++;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Falls JSON-Parsing fehlschlägt, gebe 0 zurück
            return new ScoreCalculation(0, 0, 0.0f);
        }

        // Berechne Treffergenauigkeit
        float accuracy = totalShots > 0 ? (float) hits / totalShots : 0.0f;

        return new ScoreCalculation(totalScore, maxScore, accuracy);
    }

    /**
     * Hilfsklasse für Punkte-Berechnung.
     */
    private static class ScoreCalculation {
        int totalScore;
        int maxScore;
        float accuracy;

        ScoreCalculation(int totalScore, int maxScore, float accuracy) {
            this.totalScore = totalScore;
            this.maxScore = maxScore;
            this.accuracy = accuracy;
        }
    }
}

package ch.jp.shooting.service;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.exception.SessionNotFoundException;
import ch.jp.shooting.mapper.CareerStatsMapper;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.CareerStatsRepository;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.PlayerResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für Wettkampf-Verwaltung: Leaderboard-Berechnung, Karriere-Statistiken.
 */
@Service
@NullMarked
public class CompetitionService {
    private final LiveSessionRepository sessionRepository;
    private final PlayerResultRepository playerResultRepository;
    private final CareerStatsRepository careerStatsRepository;
    private final CareerStatsMapper careerStatsMapper;
    private final ObjectMapper objectMapper;

    public CompetitionService(
            LiveSessionRepository sessionRepository,
            PlayerResultRepository playerResultRepository,
            CareerStatsRepository careerStatsRepository,
            CareerStatsMapper careerStatsMapper,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.playerResultRepository = playerResultRepository;
        this.careerStatsRepository = careerStatsRepository;
        this.careerStatsMapper = careerStatsMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Berechnet das Leaderboard für eine abgeschlossene Sitzung.
     * Aggregiert alle Spieler-Ergebnisse und sortiert nach Gesamtpunkten.
     */
    @Transactional(readOnly = true)
    public SessionLeaderboardResponse computeLeaderboard(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        // Lade alle Spieler-Ergebnisse für diese Session
        List<PlayerResult> allResults = playerResultRepository.findBySessionId(sessionId);

        // Aggregiere Spieler-Scores
        Map<UUID, SessionLeaderboardResponse.PlayerScoreEntry> playerScores = new HashMap<>();
        for (PlayerResult result : allResults) {
            SessionPlayer player = result.getPlayer();
            if (player == null) continue;

            playerScores.putIfAbsent(player.getId(),
                new SessionLeaderboardResponse.PlayerScoreEntry(
                    player.getId(),
                    player.getDisplayName(),
                    0,
                    0,
                    1  // Rang wird später gesetzt
                ));

            // Parse ProgramResult[] aus JSON
            ProgramResult[] programResults = parseProgramResults(result.getProgramResults());
            int playerTotal = 0;
            int playerMax = 0;
            for (ProgramResult prog : programResults) {
                for (SegmentResult seg : prog.getSegmentResults()) {
                    for (StepResult step : seg.getStepResults()) {
                        playerTotal += step.getPointsEarned();
                        playerMax += step.getPointValue();
                    }
                }
            }

            SessionLeaderboardResponse.PlayerScoreEntry entry = playerScores.get(player.getId());
            entry.setTotalScore(entry.getTotalScore() + playerTotal);
            entry.setMaxScore(entry.getMaxScore() + playerMax);
        }

        // Sortiere nach Punkte (absteigend) und vergebe Ränge
        List<SessionLeaderboardResponse.PlayerScoreEntry> sortedScores = playerScores.values()
            .stream()
            .sorted((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()))
            .collect(Collectors.toList());

        for (int i = 0; i < sortedScores.size(); i++) {
            sortedScores.get(i).setRank(i + 1);
        }

        return new SessionLeaderboardResponse(
            sessionId,
            session.getStatus().toString(),
            sortedScores,
            Collections.emptyList()  // Gruppen-Scores folgen später
        );
    }

    /**
     * Aktualisiert die Karriere-Statistiken eines Spielers nach Session-Abschluss.
     * Wird aufgerufen, wenn eine Session den Status COMPLETED erreicht.
     */
    @Transactional
    public void updateCareerStatsForSession(UUID sessionId) throws Exception {
        SessionLeaderboardResponse leaderboard = computeLeaderboard(sessionId);

        // Für jeden Spieler auf dem Leaderboard aktualisiere oder erstelle CareerStats
        for (SessionLeaderboardResponse.PlayerScoreEntry playerScore : leaderboard.getPlayerScores()) {
            CareerStats stats = careerStatsRepository.findByUserId(playerScore.getPlayerId())
                .orElse(new CareerStats());

            stats.setUserId(playerScore.getPlayerId());
            stats.setParticipations(stats.getParticipations() + 1);

            // Nur Rang 1 = Sieg
            if (playerScore.getRank() == 1) {
                stats.setTotalWins(stats.getTotalWins() + 1);
            }

            stats.setTotalScore(stats.getTotalScore() + playerScore.getTotalScore());
            stats.setAvgScore((double) stats.getTotalScore() / stats.getParticipations());
            stats.setLastCompeted(Instant.now());
            stats.setUpdatedAt(Instant.now());

            careerStatsRepository.save(stats);
        }
    }

    /**
     * Holt die Karriere-Statistiken für einen Spieler.
     */
    @Transactional(readOnly = true)
    public CareerStatsResponse getCareerStats(UUID userId) {
        CareerStats stats = careerStatsRepository.findByUserId(userId)
            .orElse(new CareerStats());
        return careerStatsMapper.toCareerStatsResponse(stats);
    }

    /**
     * Gibt die Top-Spieler nach Gesamtpunkten zurück (Leaderboard).
     */
    @Transactional(readOnly = true)
    public Page<CareerStatsResponse> getTopPlayers(Pageable pageable) {
        return careerStatsRepository.findLeaderboard(pageable)
            .map(careerStatsMapper::toCareerStatsResponse);
    }

    /**
     * Gibt die Top-Spieler nach Siegesanzahl zurück.
     */
    @Transactional(readOnly = true)
    public Page<CareerStatsResponse> getTopPlayersByWins(Pageable pageable) {
        return careerStatsRepository.findLeaderboardByWins(pageable)
            .map(careerStatsMapper::toCareerStatsResponse);
    }

    // ── Hilfsmetoden ──

    private ProgramResult[] parseProgramResults(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return new ProgramResult[0];
        }
        return objectMapper.readValue(json, ProgramResult[].class);
    }
}

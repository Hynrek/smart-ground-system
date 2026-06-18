package ch.jp.shooting.service;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.exception.SessionNotFoundException;
import ch.jp.shooting.mapper.CareerStatsMapper;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.CareerStatsRepository;
import ch.jp.shooting.repository.CompetitionSerieResultRepository;
import ch.jp.shooting.repository.CompetitionTiebreakerRepository;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.PlayerResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.util.*;

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
    private final CompetitionTiebreakerRepository tiebreakerRepository;
    private final TieResolver tieResolver;
    private final CompetitionSerieResultRepository serieResultRepository;

    public CompetitionService(
            LiveSessionRepository sessionRepository,
            PlayerResultRepository playerResultRepository,
            CareerStatsRepository careerStatsRepository,
            CareerStatsMapper careerStatsMapper,
            ObjectMapper objectMapper,
            CompetitionTiebreakerRepository tiebreakerRepository,
            TieResolver tieResolver,
            CompetitionSerieResultRepository serieResultRepository) {
        this.sessionRepository = sessionRepository;
        this.playerResultRepository = playerResultRepository;
        this.careerStatsRepository = careerStatsRepository;
        this.careerStatsMapper = careerStatsMapper;
        this.objectMapper = objectMapper;
        this.tiebreakerRepository = tiebreakerRepository;
        this.tieResolver = tieResolver;
        this.serieResultRepository = serieResultRepository;
    }

    /**
     * Liefert alle persistierten Serie-Ergebnisse einer Sitzung inkl. der rohen
     * Play-Ergebnisse (mit stepStates) für die Schritt-für-Schritt-Auswertung.
     */
    @Transactional(readOnly = true)
    public List<CompetitionSerieResultDetailResponse> getSerieResults(UUID sessionId) throws Exception {
        if (!sessionRepository.existsById(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        List<CompetitionSerieResultDetailResponse> out = new ArrayList<>();
        for (CompetitionSerieResult csr : serieResultRepository.findBySessionId(sessionId)) {
            CompetitionSerieResultDetailResponse dto = new CompetitionSerieResultDetailResponse();
            dto.groupId = csr.getGroup().getId();
            dto.passeIndex = csr.getPasseIndex();
            dto.serieId = csr.getSerieId();
            dto.playInstanceId = csr.getPlayInstanceId();
            dto.completedAt = csr.getCompletedAt();
            String raw = csr.getResults();
            if (raw != null && !raw.isBlank()) {
                dto.results = objectMapper.readTree(raw);
            }
            out.add(dto);
        }
        return out;
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

            // Summiere die pro Serie gespeicherten Punkte. CompetitionProgressService
            // schreibt programResults als flache Liste
            // [{passeIndex, serieId, totalPoints, maxPoints, completedAt}].
            int playerTotal = 0;
            int playerMax = 0;
            String raw = result.getProgramResults();
            var node = objectMapper.readTree(raw == null || raw.isBlank() ? "[]" : raw);
            if (node.isArray()) {
                for (var serie : node) {
                    playerTotal += serie.path("totalPoints").asInt(0);
                    playerMax   += serie.path("maxPoints").asInt(0);
                }
            }

            SessionLeaderboardResponse.PlayerScoreEntry entry = playerScores.get(player.getId());
            entry.setTotalScore(entry.getTotalScore() + playerTotal);
            entry.setMaxScore(entry.getMaxScore() + playerMax);
        }

        // Punktgleiche Blöcke über TieResolver + Stechen-Runden ordnen und ranken.
        List<TieResolver.PlayerStanding> standings = new ArrayList<>();
        for (SessionLeaderboardResponse.PlayerScoreEntry e : playerScores.values()) {
            standings.add(new TieResolver.PlayerStanding(
                e.getPlayerId(), e.getDisplayName(), e.getTotalScore(), e.getMaxScore()));
        }

        // Abgeschlossene Stechen-Runden in TieResolver-Runden übersetzen.
        List<TieResolver.TiebreakerRound> rounds = new ArrayList<>();
        for (CompetitionTiebreaker tb : tiebreakerRepository.findBySessionId(sessionId)) {
            if (tb.getStatus() != TiebreakerStatus.COMPLETED || tb.getResultsJson() == null) {
                continue;
            }
            rounds.add(new TieResolver.TiebreakerRound(
                tb.getTieGroupId(),
                tb.getRoundNumber(),
                parseUuidList(tb.getParticipantsJson()),
                parseScores(tb.getResultsJson())));
        }

        var resolved = tieResolver.resolve(standings, rounds);

        // In Auflösungsreihenfolge neu aufbauen; Rang + Tie-Flags auf die Einträge übertragen.
        List<SessionLeaderboardResponse.PlayerScoreEntry> sortedScores = new ArrayList<>();
        for (TieResolver.ResolvedStanding rs : resolved) {
            SessionLeaderboardResponse.PlayerScoreEntry entry = playerScores.get(rs.playerId());
            entry.setRank(rs.rank());
            entry.setTied(rs.tied());
            entry.setTieResolvedByStechen(rs.tieResolvedByStechen());
            sortedScores.add(entry);
        }

        return new SessionLeaderboardResponse(
            sessionId,
            session.getStatus().toString(),
            sortedScores,
            Collections.emptyList()  // Gruppen-Scores folgen später
        );
    }

    /** Parst eine JSON-Array-Liste von UUID-Strings (participantsJson). */
    private List<UUID> parseUuidList(String json) throws Exception {
        var node = objectMapper.readTree(json == null || json.isBlank() ? "[]" : json);
        var ids = new ArrayList<UUID>();
        if (node.isArray()) {
            for (JsonNode n : node) {
                String s = n.asText(null);
                if (s != null && !s.isBlank()) {
                    ids.add(UUID.fromString(s));
                }
            }
        }
        return ids;
    }

    /** Parst resultsJson [{playerId,totalPoints,...}] zu playerId → totalPoints. */
    private Map<UUID, Integer> parseScores(String json) throws Exception {
        var node = objectMapper.readTree(json == null || json.isBlank() ? "[]" : json);
        var scores = new HashMap<UUID, Integer>();
        if (node.isArray()) {
            for (JsonNode s : node) {
                String pid = s.path("playerId").asText(null);
                if (pid != null && !pid.isBlank()) {
                    scores.put(UUID.fromString(pid), s.path("totalPoints").asInt(0));
                }
            }
        }
        return scores;
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

}

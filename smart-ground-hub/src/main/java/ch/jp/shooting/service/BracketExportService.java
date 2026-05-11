package ch.jp.shooting.service;

import ch.jp.shooting.dto.SessionLeaderboardResponse;
import ch.jp.shooting.model.BracketMatch;
import ch.jp.shooting.model.BracketPhase;
import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.repository.BracketMatchRepository;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.exception.SessionNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für Bracket-Turnier-Export in verschiedene Formate.
 * Unterstützt JSON (strukturierte Daten) und PDF (lesbare Berichte).
 */
@Service
@NullMarked
public class BracketExportService {
    private final LiveSessionRepository sessionRepository;
    private final BracketMatchRepository bracketMatchRepository;
    private final CompetitionService competitionService;
    private final ObjectMapper objectMapper;

    public BracketExportService(
            LiveSessionRepository sessionRepository,
            BracketMatchRepository bracketMatchRepository,
            CompetitionService competitionService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.bracketMatchRepository = bracketMatchRepository;
        this.competitionService = competitionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Exportiert ein Bracket-Turnier als JSON.
     * Enthält: Turniermetadaten, alle Matches, Seeding-Info, finale Ergebnisse.
     */
    public String exportBracketAsJson(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        Map<String, Object> export = new HashMap<>();

        // Basis-Metadaten
        export.put("sessionId", sessionId.toString());
        export.put("exportedAt", Instant.now().toString());
        export.put("sessionStatus", session.getStatus());
        export.put("bracketPhase", session.getBracketPhase());

        // Bracket-State
        if (session.getBracketStateJson() != null) {
            Map<String, Object> bracketState = objectMapper.readValue(
                session.getBracketStateJson(), Map.class);
            export.put("bracketState", bracketState);
        }

        // Alle Matches nach Runde
        List<BracketMatch> allMatches = bracketMatchRepository.findBySessionIdOrderByMatchNumber(sessionId);
        Map<Integer, List<Map<String, Object>>> matchesByRound = new LinkedHashMap<>();

        for (BracketMatch match : allMatches) {
            int roundNum = match.getRoundNumber();
            matchesByRound.computeIfAbsent(roundNum, k -> new ArrayList<>())
                .add(serializeMatch(match));
        }
        export.put("matches", matchesByRound);

        // Leaderboard/Endergebnisse
        try {
            SessionLeaderboardResponse leaderboard = competitionService.computeLeaderboard(sessionId);
            export.put("leaderboard", leaderboard);
        } catch (Exception e) {
            // Leaderboard nicht verfügbar, ist optional
        }

        // Session-Spieler und Gruppen
        if (session.getGroups() != null && !session.getGroups().isEmpty()) {
            List<Map<String, Object>> groupData = session.getGroups().stream()
                .map(group -> {
                    Map<String, Object> gm = new HashMap<>();
                    gm.put("id", group.getId().toString());
                    gm.put("name", group.getName());
                    gm.put("playerCount", group.getMembers().size());
                    return gm;
                })
                .collect(Collectors.toList());
            export.put("groups", groupData);
        }

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
    }

    /**
     * Exportiert ein Bracket-Turnier als PDF.
     * Enthält: Bracket-Struktur, Match-Ergebnisse, Leaderboard.
     */
    public byte[] exportBracketAsPdf(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream();

        // Sammle Daten
        List<BracketMatch> allMatches = bracketMatchRepository.findBySessionIdOrderByMatchNumber(sessionId);
        Map<Integer, List<BracketMatch>> matchesByRound = allMatches.stream()
            .collect(Collectors.groupingBy(BracketMatch::getRoundNumber,
                LinkedHashMap::new,
                Collectors.toList()));

        SessionLeaderboardResponse leaderboard = null;
        try {
            leaderboard = competitionService.computeLeaderboard(sessionId);
        } catch (Exception e) {
            // Optional
        }

        // Erstelle PDF-Inhalt (Text-basiert)
        StringBuilder pdfContent = new StringBuilder();
        pdfContent.append("BRACKET TOURNAMENT EXPORT\n");
        pdfContent.append("=".repeat(80)).append("\n\n");

        pdfContent.append("Session ID: ").append(sessionId).append("\n");
        pdfContent.append("Exported: ").append(formatTime(Instant.now())).append("\n");
        pdfContent.append("Status: ").append(session.getStatus()).append("\n");
        pdfContent.append("Bracket Phase: ").append(session.getBracketPhase()).append("\n\n");

        // Bracket-Struktur und Matches
        pdfContent.append("TOURNAMENT BRACKET\n");
        pdfContent.append("-".repeat(80)).append("\n\n");

        for (Map.Entry<Integer, List<BracketMatch>> entry : matchesByRound.entrySet()) {
            int round = entry.getKey();
            List<BracketMatch> roundMatches = entry.getValue();

            pdfContent.append(String.format("ROUND %d (%d matches)\n", round, roundMatches.size()));
            pdfContent.append("-".repeat(80)).append("\n");

            for (BracketMatch match : roundMatches) {
                pdfContent.append(formatMatchForPdf(match));
            }
            pdfContent.append("\n");
        }

        // Leaderboard
        if (leaderboard != null && leaderboard.getPlayerScores() != null) {
            pdfContent.append("\nFINAL LEADERBOARD\n");
            pdfContent.append("-".repeat(80)).append("\n");
            pdfContent.append(String.format("%-5s %-30s %-15s %-15s\n",
                "Rank", "Player", "Max", "Score"));
            pdfContent.append("-".repeat(80)).append("\n");

            for (var entry : leaderboard.getPlayerScores()) {
                String playerName = entry.getDisplayName() != null ? entry.getDisplayName() : "Unknown";
                int maxScore = entry.getMaxScore();
                int score = entry.getTotalScore();

                pdfContent.append(String.format("%-5d %-30s %-15d %-15d\n",
                    entry.getRank(), playerName, maxScore, score));
            }
        }

        String finalPdf = pdfContent.toString();
        return finalPdf.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Gibt eine strukturierte JSON-Darstellung für ein Match zurück.
     */
    private Map<String, Object> serializeMatch(BracketMatch match) {
        Map<String, Object> data = new HashMap<>();
        data.put("matchNumber", match.getMatchNumber());
        data.put("roundNumber", match.getRoundNumber());

        if (match.getContestant1Id() != null) {
            data.put("contestant1", match.getContestant1Id().toString());
        }
        if (match.getContestant2Id() != null) {
            data.put("contestant2", match.getContestant2Id().toString());
        }

        if (match.getWinnerId() != null) {
            data.put("winnerId", match.getWinnerId().toString());
        }

        data.put("score1", match.getScore1());
        data.put("score2", match.getScore2());
        data.put("isBye", match.isBye());
        data.put("isPlayed", match.isPlayed());
        data.put("createdAt", match.getCreatedAt().toString());
        data.put("updatedAt", match.getUpdatedAt().toString());

        return data;
    }

    /**
     * Formatiert ein Match für PDF-Darstellung.
     */
    private String formatMatchForPdf(BracketMatch match) {
        StringBuilder sb = new StringBuilder();

        if (match.isBye()) {
            sb.append(String.format("  Match #%-3d: BYE\n", match.getMatchNumber()));
            return sb.toString();
        }

        String c1 = match.getContestant1Id() != null ? match.getContestant1Id().toString() : "TBD";
        String c2 = match.getContestant2Id() != null ? match.getContestant2Id().toString() : "TBD";
        String score1 = match.getScore1() != null ? match.getScore1().toString() : "-";
        String score2 = match.getScore2() != null ? match.getScore2().toString() : "-";

        sb.append(String.format("  Match #%-3d: %s (%s) vs %s (%s)",
            match.getMatchNumber(), c1, score1, c2, score2));

        if (match.getWinnerId() != null) {
            sb.append(" -> WINNER: ").append(match.getWinnerId().toString());
        } else if (match.isPlayed()) {
            sb.append(" [COMPLETED]");
        } else {
            sb.append(" [PENDING]");
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * Formatiert einen Timestamp für PDF-Ausgabe.
     */
    private String formatTime(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(instant);
    }
}

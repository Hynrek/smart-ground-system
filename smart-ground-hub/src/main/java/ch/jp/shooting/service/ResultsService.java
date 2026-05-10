package ch.jp.shooting.service;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.PlayerResult;
import ch.jp.shooting.model.SessionPlayer;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.PlayerResultRepository;
import ch.jp.shooting.repository.SessionPlayerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service für Ergebnis-Verwaltung: Append/Upsert und Leaderboard-Berechnung.
 */
@Service
@Transactional
@NullMarked
public class ResultsService {
    private final LiveSessionRepository sessionRepository;
    private final PlayerResultRepository resultRepository;
    private final SessionPlayerRepository playerRepository;
    private final ScoringService scoringService;
    private final ObjectMapper objectMapper;

    public ResultsService(
            LiveSessionRepository sessionRepository,
            PlayerResultRepository resultRepository,
            SessionPlayerRepository playerRepository,
            ScoringService scoringService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.resultRepository = resultRepository;
        this.playerRepository = playerRepository;
        this.scoringService = scoringService;
        this.objectMapper = objectMapper;
    }

    /**
     * Fügt Ergebnisse für einen Spielerdurchlauf hinzu oder aktualisiert bestehende.
     */
    public PlayerResultResponse submitPlayerResults(UUID sessionId, SubmitResultRequest req) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        SessionPlayer player = playerRepository.findById(req.playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        // Upsert: Ergebnis laden oder erstellen
        PlayerResult result = resultRepository.findBySessionIdAndPlayerId(sessionId, req.playerId)
                .orElse(new PlayerResult(session, player));

        // Programm-Ergebnisse laden/erstellen
        ProgramResult[] programs = result.getProgramResults() != null
                ? objectMapper.readValue(result.getProgramResults(), ProgramResult[].class)
                : new ProgramResult[0];

        ProgramResult targetProgram = findOrCreateProgramResult(programs, req.programId);

        // Segment-Ergebnis laden/erstellen
        SegmentResult targetSegment = findOrCreateSegmentResult(targetProgram, req.segmentId);

        // StepResults hinzufügen (replace)
        targetSegment.stepResults = req.stepResults;

        // Scores berechnen
        int segmentScore = req.stepResults.stream()
                .mapToInt(s -> s.pointsEarned)
                .sum();
        targetSegment.score = segmentScore;

        // Programm-Scores neu berechnen
        int programScore = Arrays.stream(programs)
                .flatMap(p -> p.segmentResults.stream())
                .mapToInt(s -> s.score)
                .sum();

        // Speichern
        result.setProgramResults(objectMapper.writeValueAsString(programs));
        result.setUpdatedAt(Instant.now());
        result = resultRepository.save(result);

        return mapResultToResponse(result);
    }

    /**
     * Gibt das aktuelle Leaderboard für eine Session zurück (sortiert nach Punkte).
     */
    public ScoreboardResponse getScoreboard(UUID sessionId) throws Exception {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        ScoreboardResponse resp = new ScoreboardResponse();

        // Player Scores berechnen und sortieren
        List<PlayerScoreEntry> playerScores = new ArrayList<>();
        for (PlayerResult result : session.getPlayerResults()) {
            PlayerScoreEntry entry = computePlayerScore(result);
            playerScores.add(entry);
        }

        // Nach Punkte sortieren (descending)
        playerScores.sort((a, b) -> Integer.compare(b.totalScore, a.totalScore));

        // Rank vergeben
        int rank = 1;
        for (PlayerScoreEntry entry : playerScores) {
            entry.rank = rank++;
        }

        resp.playerScores = playerScores;

        // Group Scores berechnen (aggregiert von Player Scores)
        Map<UUID, GroupScoreEntry> groupMap = new HashMap<>();
        for (PlayerScoreEntry playerScore : playerScores) {
            groupMap.computeIfAbsent(playerScore.groupId, groupId -> {
                GroupScoreEntry gentry = new GroupScoreEntry();
                gentry.groupId = groupId;
                gentry.groupName = "Group " + groupId; // TODO: name from group
                return gentry;
            }).ranking.add(playerScore);
        }

        resp.groupScores = new ArrayList<>(groupMap.values());

        return resp;
    }

    /**
     * Berechnet Scores für einen Spieler (aus JSON).
     */
    private PlayerScoreEntry computePlayerScore(PlayerResult result) throws Exception {
        PlayerScoreEntry entry = new PlayerScoreEntry();
        entry.playerId = result.getPlayer().getId();
        entry.displayName = result.getPlayer().getDisplayName();
        // groupId wird später gesetzt (aus Group lookup)

        int totalScore = 0;
        int maxScore = 0;

        if (result.getProgramResults() != null) {
            ProgramResult[] programs = objectMapper.readValue(
                    result.getProgramResults(),
                    ProgramResult[].class);

            for (ProgramResult prog : programs) {
                for (SegmentResult seg : prog.segmentResults) {
                    totalScore += seg.score;
                    maxScore += seg.maxScore;

                    SegmentScoreEntry segEntry = new SegmentScoreEntry(
                            seg.segmentId,
                            seg.score,
                            seg.maxScore,
                            computeSegmentCompletion(seg)
                    );
                    entry.segmentScores.add(segEntry);
                }
            }
        }

        entry.totalScore = totalScore;
        entry.maxScore = maxScore;

        return entry;
    }

    /**
     * Berechnet Completion-Prozentsatz für ein Segment.
     */
    private int computeSegmentCompletion(SegmentResult segment) {
        if (segment.stepResults.isEmpty()) {
            return 0;
        }
        long completed = segment.stepResults.stream()
                .filter(s -> !s.state.equals("pending"))
                .count();
        return (int) ((completed * 100) / segment.stepResults.size());
    }

    // ── Hilfsmethoden ──

    private ProgramResult findOrCreateProgramResult(ProgramResult[] programs, UUID programId) {
        for (ProgramResult prog : programs) {
            if (prog.programId.equals(programId)) {
                return prog;
            }
        }
        // Nicht gefunden → neue erstellen
        ProgramResult newProg = new ProgramResult(programId);
        ProgramResult[] extended = Arrays.copyOf(programs, programs.length + 1);
        extended[programs.length] = newProg;
        return newProg;
    }

    private SegmentResult findOrCreateSegmentResult(ProgramResult program, UUID segmentId) {
        for (SegmentResult seg : program.segmentResults) {
            if (seg.segmentId.equals(segmentId)) {
                return seg;
            }
        }
        // Nicht gefunden → neue erstellen
        SegmentResult newSeg = new SegmentResult(segmentId, null); // groupId wird später gesetzt
        program.segmentResults.add(newSeg);
        return newSeg;
    }

    private PlayerResultResponse mapResultToResponse(PlayerResult entity) {
        PlayerResultResponse resp = new PlayerResultResponse();
        resp.id = entity.getId();
        resp.playerId = entity.getPlayer().getId();
        resp.displayName = entity.getPlayer().getDisplayName();
        resp.programResults = entity.getProgramResults();
        resp.createdAt = entity.getCreatedAt();
        resp.updatedAt = entity.getUpdatedAt();

        // Scores berechnen
        computeResultScores(entity, resp);

        return resp;
    }

    private void computeResultScores(PlayerResult entity, PlayerResultResponse resp) {
        int totalScore = 0;
        int maxScore = 0;
        int completedSteps = 0;
        int totalSteps = 0;

        if (entity.getProgramResults() != null) {
            try {
                ProgramResult[] programs = objectMapper.readValue(
                        entity.getProgramResults(),
                        ProgramResult[].class);

                for (ProgramResult prog : programs) {
                    for (SegmentResult seg : prog.segmentResults) {
                        totalScore += seg.score;
                        maxScore += seg.maxScore;

                        for (StepResult step : seg.stepResults) {
                            totalSteps++;
                            if (!step.state.equals("pending")) {
                                completedSteps++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // JSON parsing fehler
            }
        }

        resp.totalScore = totalScore;
        resp.maxScore = maxScore;
        resp.completionPct = totalSteps > 0 ? (completedSteps * 100) / totalSteps : 0;
    }
}

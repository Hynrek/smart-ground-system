package ch.jp.shooting.api;

import ch.jp.shooting.dto.PlayerResultResponse;
import ch.jp.shooting.dto.ScoreboardResponse;
import ch.jp.shooting.dto.SubmitResultRequest;
import ch.jp.shooting.service.ResultsService;
import ch.jp.shooting.service.SessionWebSocketService;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST-Controller für Ergebnis-Verwaltung und Leaderboard.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}")
@NullMarked
public class ResultsController {
    private final ResultsService resultsService;
    private final SessionWebSocketService webSocketService;

    public ResultsController(
            ResultsService resultsService,
            SessionWebSocketService webSocketService) {
        this.resultsService = resultsService;
        this.webSocketService = webSocketService;
    }

    /**
     * POST /api/sessions/{sessionId}/results
     * Reicht Spieler-Ergebnisse nach Segment-Durchlauf ein.
     * Body: { groupId, playerId, programId, segmentId, stepResults[] }
     * Publiziert WebSocket-Update mit neuem Leaderboard.
     */
    @PostMapping("/results")
    public ResponseEntity<PlayerResultResponse> submitResults(
            @PathVariable UUID sessionId,
            @RequestBody SubmitResultRequest request) throws Exception {
        PlayerResultResponse response = resultsService.submitPlayerResults(sessionId, request);
        // WebSocket-Broadcast: aktuelles Leaderboard an alle Tablets
        ScoreboardResponse scoreboard = resultsService.getScoreboard(sessionId);
        webSocketService.publishScoreboardUpdate(sessionId, scoreboard);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/sessions/{sessionId}/scoreboard
     * Gibt aktuelles Leaderboard zurück (live Ranking).
     * Response: { playerScores: [{ playerId, displayName, totalScore, rank }], groupScores: [...] }
     */
    @GetMapping("/scoreboard")
    public ResponseEntity<ScoreboardResponse> getScoreboard(@PathVariable UUID sessionId) throws Exception {
        ScoreboardResponse response = resultsService.getScoreboard(sessionId);
        return ResponseEntity.ok(response);
    }
}

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

    // TODO: Implement submitPlayerResults and getScoreboard methods in ResultsService
}

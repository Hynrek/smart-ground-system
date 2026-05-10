package ch.jp.shooting.api;

import ch.jp.shooting.dto.SessionLeaderboardResponse;
import ch.jp.shooting.model.BracketMatch;
import ch.jp.shooting.model.BracketPhase;
import ch.jp.shooting.service.BracketService;
import ch.jp.shooting.service.BracketSeedingService;
import ch.jp.shooting.service.SessionWebSocketService;
import ch.jp.shooting.service.TiebreakerResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST-Controller für Bracket-Turniere (Single/Double Elimination).
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/bracket")
@NullMarked
public class BracketController {
    private final BracketService bracketService;
    private final SessionWebSocketService webSocketService;

    public BracketController(BracketService bracketService, SessionWebSocketService webSocketService) {
        this.bracketService = bracketService;
        this.webSocketService = webSocketService;
    }

    // ── Bracket Initialization ──

    /**
     * POST /api/sessions/{sessionId}/bracket
     * Initialisiert ein neues Bracket-Turnier.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> initializeBracket(
            @PathVariable UUID sessionId,
            @RequestBody InitializeBracketRequest request) {
        try {
            List<TiebreakerResolver.TiebreakerCriteria> tiebreakers = null;
            if (request.tiebreakers() != null && !request.tiebreakers().isEmpty()) {
                tiebreakers = request.tiebreakers().stream()
                    .map(TiebreakerResolver.TiebreakerCriteria::valueOf)
                    .collect(Collectors.toList());
            }

            bracketService.initializeBracket(
                sessionId,
                request.bracketType(),
                BracketSeedingService.SeedingStrategy.valueOf(request.seedingStrategy()),
                tiebreakers
            );

            Map<String, Object> state = bracketService.getBracketState(sessionId);
            webSocketService.publishSessionMessage(sessionId, "bracket_initialized",
                "Bracket initialized: " + request.bracketType());
            return ResponseEntity.status(HttpStatus.CREATED).body(state);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /api/sessions/{sessionId}/bracket
     * Holt den aktuellen Bracket-State.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getBracketState(@PathVariable UUID sessionId) {
        try {
            Map<String, Object> state = bracketService.getBracketState(sessionId);
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/sessions/{sessionId}/bracket/phase
     * Holt die aktuelle Bracket-Phase.
     */
    @GetMapping("/phase")
    public ResponseEntity<String> getBracketPhase(@PathVariable UUID sessionId) {
        try {
            BracketPhase phase = bracketService.getBracketPhase(sessionId);
            return ResponseEntity.ok(phase != null ? phase.toString() : "NONE");
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Seeding and Play Control ──

    /**
     * PUT /api/sessions/{sessionId}/bracket/seeding
     * Bestätigt das Seeding und startet die SEEDING Phase.
     */
    @PutMapping("/seeding")
    public ResponseEntity<Map<String, Object>> confirmSeeding(@PathVariable UUID sessionId) {
        try {
            bracketService.confirmSeeding(sessionId);
            webSocketService.publishSessionMessage(sessionId, "seeding_confirmed", "Seeding confirmed");
            return ResponseEntity.ok(bracketService.getBracketState(sessionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PUT /api/sessions/{sessionId}/bracket/start
     * Startet die Bracket-Spielphase (SEEDING → IN_PROGRESS).
     */
    @PutMapping("/start")
    public ResponseEntity<Map<String, Object>> startBracketPlay(@PathVariable UUID sessionId) {
        try {
            bracketService.startBracketPlay(sessionId);
            webSocketService.publishSessionMessage(sessionId, "bracket_started", "Bracket play started");
            return ResponseEntity.ok(bracketService.getBracketState(sessionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Match Management ──

    /**
     * GET /api/sessions/{sessionId}/bracket/matches
     * Listet alle Matches auf (optional gefiltert nach Runde).
     */
    @GetMapping("/matches")
    public ResponseEntity<List<Map<String, Object>>> listMatches(
            @PathVariable UUID sessionId,
            @RequestParam(required = false) Integer round) {
        try {
            // TODO: Implementieren mit BracketMatchRepository
            return ResponseEntity.ok(Collections.emptyList());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /api/sessions/{sessionId}/bracket/matches/next
     * Holt das nächste zu spielende Match.
     */
    @GetMapping("/matches/next")
    public ResponseEntity<Map<String, Object>> getNextMatch(@PathVariable UUID sessionId) {
        try {
            List<BracketMatch> nextMatches = bracketService.getNextMatchesForRange(sessionId, null);
            if (nextMatches.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            BracketMatch match = nextMatches.get(0);
            Map<String, Object> response = new HashMap<>();
            response.put("matchNumber", match.getMatchNumber());
            response.put("roundNumber", match.getRoundNumber());
            response.put("contestant1", match.getContestant1Id());
            response.put("contestant2", match.getContestant2Id());
            response.put("isBye", match.isBye());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/sessions/{sessionId}/bracket/matches/{matchNumber}
     * Zeichnet ein Match-Ergebnis auf.
     */
    @PostMapping("/matches/{matchNumber}")
    public ResponseEntity<Map<String, Object>> recordMatchWinner(
            @PathVariable UUID sessionId,
            @PathVariable int matchNumber,
            @RequestBody RecordMatchResultRequest request) {
        try {
            bracketService.recordMatchWinner(sessionId, matchNumber, request.winnerId(),
                request.score1(), request.score2());

            webSocketService.publishSessionMessage(sessionId, "match_recorded",
                "Match " + matchNumber + " recorded");

            return ResponseEntity.ok(Map.of(
                "matchNumber", matchNumber,
                "winnerId", request.winnerId(),
                "score1", request.score1(),
                "score2", request.score2()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Leaderboard ──

    /**
     * GET /api/sessions/{sessionId}/bracket/leaderboard
     * Holt das Leaderboard eines abgeschlossenen Bracket-Turniers.
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<SessionLeaderboardResponse> getBracketLeaderboard(@PathVariable UUID sessionId) {
        try {
            if (!bracketService.isBracketComplete(sessionId)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .build(); // Bracket noch nicht abgeschlossen
            }
            return ResponseEntity.ok(bracketService.getBracketLeaderboard(sessionId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── DTOs ──

    public record InitializeBracketRequest(
        String bracketType,        // SINGLE_ELIMINATION, DOUBLE_ELIMINATION
        String seedingStrategy,    // BY_CAREER_STATS, MANUAL, BALANCED
        @Nullable List<String> tiebreakers  // ["TOTAL_SCORE", "WINS", ...]
    ) {
    }

    public record RecordMatchResultRequest(
        UUID winnerId,
        @Nullable Integer score1,
        @Nullable Integer score2
    ) {
    }
}

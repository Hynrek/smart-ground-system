package ch.jp.shooting.api;

import ch.jp.shooting.dto.SessionLeaderboardResponse;
import ch.jp.shooting.model.BracketMatch;
import ch.jp.shooting.model.BracketPhase;
import ch.jp.shooting.service.BracketService;
import ch.jp.shooting.service.BracketSeedingService;
import ch.jp.shooting.service.BracketExportService;
import ch.jp.shooting.service.SessionWebSocketService;
import ch.jp.shooting.service.TiebreakerResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final BracketExportService exportService;
    private final SessionWebSocketService webSocketService;

    public BracketController(BracketService bracketService, BracketExportService exportService, SessionWebSocketService webSocketService) {
        this.bracketService = bracketService;
        this.exportService = exportService;
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
            @RequestBody ch.jp.smartground.model.InitializeBracketRequest request) {
        try {
            bracketService.initializeBracket(sessionId, request);
            webSocketService.publishSessionMessage(sessionId, "bracket_initialized",
                "Bracket initialized: " + request.getBracketType());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("sessionId", sessionId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // TODO: Implement remaining bracket endpoints (getBracketState, confirmSeeding, startBracketPlay, etc.)
    // These require full BracketService implementation

    // ── Export ──

    /**
     * GET /api/sessions/{sessionId}/bracket/export?format=json|pdf
     * Exportiert das Bracket-Turnier in verschiedene Formate.
     */
    @GetMapping("/export")
    public ResponseEntity<?> exportBracket(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "json") String format) {
        try {
            if ("json".equalsIgnoreCase(format)) {
                String jsonData = exportService.exportBracketAsJson(sessionId);
                return ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Disposition", "attachment; filename=bracket_" + sessionId + ".json")
                    .body(jsonData);
            } else if ("pdf".equalsIgnoreCase(format)) {
                byte[] pdfData = exportService.exportBracketAsPdf(sessionId);
                return ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "attachment; filename=bracket_" + sessionId + ".pdf")
                    .body(pdfData);
            } else {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unsupported format. Use 'json' or 'pdf'"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

}

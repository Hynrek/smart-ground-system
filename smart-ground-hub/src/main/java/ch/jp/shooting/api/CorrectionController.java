package ch.jp.shooting.api;

import ch.jp.shooting.dto.CorrectionRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST-Controller für Admin-Korrekturen von Schrittergebnissen.
 * Nur ADMIN autorisiert.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/corrections")
@NullMarked
public class CorrectionController {

    /**
     * POST /api/sessions/{sessionId}/corrections
     * Admin korrigiert ein Schrittergebnis (hit → miss, etc.).
     * Authorization: ADMIN nur
     * Body: { playerId, programId, segmentId, stepId, oldState, newState }
     * Publiziert WebSocket-Update mit aktualisierten Ergebnissen.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> postCorrection(
            @PathVariable UUID sessionId,
            @RequestBody CorrectionRequest request) {
        // TODO: Service-Call implementieren (für Phase 3.5 oder später)
        // resultsService.applyCorrection(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * GET /api/sessions/{sessionId}/corrections
     * Admin sieht alle Korrektionen in dieser Session.
     * Authorization: ADMIN nur
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getCorrections(@PathVariable UUID sessionId) {
        // TODO: Service-Call: resultsService.getCorrections(sessionId);
        return ResponseEntity.ok().build();
    }
}

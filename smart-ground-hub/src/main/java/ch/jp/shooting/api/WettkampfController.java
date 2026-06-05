package ch.jp.shooting.api;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.service.CompetitionProgressService;
import ch.jp.shooting.service.CompetitionService;
import ch.jp.shooting.service.SessionService;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Wettkampf-spezifische REST-Endpunkte.
 * Ergänzt SessionController um Status-Wechsel, Löschen, Mitglieder-Verwaltung
 * und Fortschritts-Tracking.
 */
@RestController
@RequestMapping("/api/sessions")
@NullMarked
public class WettkampfController {

    private final SessionService sessionService;
    private final CompetitionProgressService progressService;
    private final CompetitionService competitionService;

    public WettkampfController(SessionService sessionService,
                                CompetitionProgressService progressService,
                                CompetitionService competitionService) {
        this.sessionService = sessionService;
        this.progressService = progressService;
        this.competitionService = competitionService;
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    public ResponseEntity<SessionResponse> patchStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) return ResponseEntity.badRequest().build();
        SessionResponse response = sessionService.updateSessionStatus(id, status.toUpperCase());
        if ("COMPLETED".equalsIgnoreCase(status)) {
            try {
                competitionService.updateCareerStatsForSession(id);
            } catch (Exception e) {
                // Log but don't fail the status transition
                System.err.println("[WettkampfController] Career stats update failed: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    // ── Gruppe (Rotte) management ─────────────────────────────────────────────
    // NOTE: POST /api/sessions/{id}/groups is handled by SessionController (via generated SessionApi).
    // WettkampfController only handles update and delete operations on existing groups.

    @PutMapping("/{id}/groups/{groupId}")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @RequestBody GroupCreateRequest request) {
        return ResponseEntity.ok(sessionService.updateGroup(id, groupId, request));
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable UUID id,
            @PathVariable UUID groupId) {
        sessionService.deleteGroup(id, groupId);
        return ResponseEntity.noContent().build();
    }

    // ── Mitglieder (Schützen) management ─────────────────────────────────────

    @PostMapping("/{id}/groups/{groupId}/members")
    public ResponseEntity<SessionPlayerResponse> addMember(
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @RequestBody SessionPlayerCreateRequest request) {
        return ResponseEntity.status(201).body(sessionService.addMember(id, groupId, request));
    }

    @DeleteMapping("/{id}/groups/{groupId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        sessionService.removeMember(id, groupId, memberId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/groups/{groupId}/members/{memberId}")
    public ResponseEntity<SessionPlayerResponse> patchMember(
            @PathVariable UUID id,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @RequestBody PatchMemberRequest request) {
        return ResponseEntity.ok(sessionService.patchMember(id, groupId, memberId, request));
    }

    // ── Wettkampf-Fortschritt ─────────────────────────────────────────────────

    @PostMapping("/{sessionId}/groups/{groupId}/serien/{serieId}/complete")
    public ResponseEntity<CompetitionSerieResultResponse> completeSerie(
            @PathVariable UUID sessionId,
            @PathVariable UUID groupId,
            @PathVariable UUID serieId,
            @RequestBody CompleteSerieRequest request) throws Exception {
        return ResponseEntity.status(201)
            .body(progressService.completeSerie(sessionId, groupId, serieId, request));
    }

    @GetMapping("/{sessionId}/progress")
    public ResponseEntity<CompetitionProgressResponse> getProgress(
            @PathVariable UUID sessionId) throws Exception {
        return ResponseEntity.ok(progressService.getProgress(sessionId));
    }
}

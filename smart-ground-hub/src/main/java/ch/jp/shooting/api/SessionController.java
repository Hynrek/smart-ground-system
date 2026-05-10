package ch.jp.shooting.api;

import ch.jp.shooting.dto.*;
import ch.jp.shooting.model.SessionStatus;
import ch.jp.shooting.model.SessionType;
import ch.jp.shooting.service.SessionService;
import ch.jp.shooting.service.SessionWebSocketService;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST-Controller für Session-Management.
 * Endpoints: POST /api/sessions, GET /api/sessions/{id}, PATCH status, etc.
 */
@RestController
@RequestMapping("/api/sessions")
@NullMarked
public class SessionController {
    private final SessionService sessionService;
    private final SessionWebSocketService webSocketService;

    public SessionController(
            SessionService sessionService,
            SessionWebSocketService webSocketService) {
        this.sessionService = sessionService;
        this.webSocketService = webSocketService;
    }

    /**
     * POST /api/sessions
     * Erstellt eine neue Spielsession.
     */
    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@RequestBody CreateSessionRequest request) throws Exception {
        SessionResponse response = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/sessions/{id}
     * Lädt vollständige Session-Information (für Resume nach Disconnect).
     */
    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable UUID id) {
        SessionResponse response = sessionService.getSession(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/sessions
     * Listet Sessions auf mit optionalen Filtern (status, type, pagination).
     */
    @GetMapping
    public ResponseEntity<Page<SessionResponse>> listSessions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            Pageable pageable) {
        SessionStatus statusEnum = status != null ? SessionStatus.valueOf(status.toUpperCase()) : null;
        SessionType typeEnum = type != null ? SessionType.valueOf(type.toUpperCase()) : null;

        Page<SessionResponse> page = sessionService.listSessions(statusEnum, typeEnum, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * PATCH /api/sessions/{id}/status
     * Aktualisiert den Session-Status (active, paused, completed, abandoned).
     * Publiziert WebSocket-Update an alle Tablets.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<SessionResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody SessionStatusUpdateRequest request) {
        SessionResponse response = sessionService.updateSessionStatus(id, request.status);
        // WebSocket-Broadcast an alle Tablets dieser Session
        webSocketService.publishSessionMessage(id, "status_changed", "Session status changed to: " + request.status);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/sessions/{id}/groups
     * Gibt alle Gruppen einer Session zurück.
     */
    @GetMapping("/{id}/groups")
    public ResponseEntity<?> getGroups(@PathVariable UUID id) {
        var groups = sessionService.getGroups(id);
        return ResponseEntity.ok(groups);
    }

    /**
     * POST /api/sessions/{id}/groups
     * Erstellt eine neue Gruppe in einer Session (nur im SETUP-Status).
     */
    @PostMapping("/{id}/groups")
    public ResponseEntity<GroupResponse> createGroup(
            @PathVariable UUID id,
            @RequestBody GroupCreateRequest request) {
        // Session laden
        SessionResponse session = sessionService.getSession(id);
        // TODO: Umwandlung zurück zu Entity für Service-Call (vereinfachen)
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * PUT /api/sessions/{id}/groups/{groupId}
     * Aktualisiert eine Gruppe (nur im SETUP-Status).
     */
    @PutMapping("/{sessionId}/groups/{groupId}")
    public ResponseEntity<GroupResponse> updateGroup(
            @PathVariable UUID sessionId,
            @PathVariable UUID groupId,
            @RequestBody GroupCreateRequest request) {
        GroupResponse response = sessionService.updateGroup(sessionId, groupId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/sessions/{id}/groups/{groupId}
     * Löscht eine Gruppe (nur im SETUP-Status).
     */
    @DeleteMapping("/{sessionId}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable UUID sessionId,
            @PathVariable UUID groupId) {
        sessionService.deleteGroup(sessionId, groupId);
        return ResponseEntity.noContent().build();
    }
}

package ch.jp.shooting.api;

import ch.jp.shooting.dto.GroupRegistrationRequest;
import ch.jp.shooting.dto.GroupResponse;
import ch.jp.shooting.service.GroupRegistrationService;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST-Controller für Gruppen-Registrierung an Bereichen.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/groups/{groupId}")
@NullMarked
public class GroupController {
    private final GroupRegistrationService registrationService;

    public GroupController(GroupRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * POST /api/sessions/{sessionId}/groups/{groupId}/register
     * Registriert eine Gruppe an einem Bereich für ein Segment.
     * Validation:
     *  - Segment gehört zu Range
     *  - Segment nicht bereits abgeschlossen
     *  - Keine andere Gruppe an dieser Range registriert
     * Publiziert WebSocket-Updates.
     */
    @PostMapping("/register")
    public ResponseEntity<GroupResponse> registerGroupAtRange(
            @PathVariable UUID sessionId,
            @PathVariable UUID groupId,
            @RequestBody GroupRegistrationRequest request) throws Exception {
        GroupResponse response = registrationService.registerGroupAtRange(
                sessionId,
                groupId,
                request.rangeId,
                request.segmentId
        );
        // TODO: WebSocket-Broadcast an /topic/sessions/{sessionId} und /topic/sessions/{sessionId}/range/{rangeId}
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/sessions/{sessionId}/groups/{groupId}/register
     * Hebt Registrierung auf, markiert Segment als abgeschlossen.
     * Publiziert WebSocket-Updates.
     */
    @DeleteMapping("/register")
    public ResponseEntity<Void> unregisterGroupFromRange(
            @PathVariable UUID sessionId,
            @PathVariable UUID groupId) throws Exception {
        registrationService.unregisterGroupFromRange(sessionId, groupId);
        // TODO: WebSocket-Broadcast
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/sessions/{sessionId}/range/{rangeId}/groups
     * Gibt aktive und wartende Gruppen für einen Bereich zurück.
     */
    @GetMapping("/range/{rangeId}/groups")
    public ResponseEntity<Map<String, Object>> getGroupsForRange(
            @PathVariable UUID sessionId,
            @PathVariable UUID rangeId) throws Exception {
        Map<String, Object> response = registrationService.getGroupsForRange(sessionId, rangeId);
        return ResponseEntity.ok(response);
    }
}

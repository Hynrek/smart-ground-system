package ch.jp.shooting.service;

import ch.jp.shooting.dto.SessionLeaderboardResponse;
import ch.jp.shooting.dto.ScoreboardResponse;
import ch.jp.shooting.model.BracketPhase;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service für WebSocket-Broadcasting an alle Tablets einer Spielsitzung.
 * Nutzt STOMP über SockJS für Echtzeit-Updates.
 */
@Service
@NullMarked
public class SessionWebSocketService {
    private final SimpMessagingTemplate messagingTemplate;

    public SessionWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Sendet ein Session-Status-Update an alle Subscriber der Sitzung.
     * Ziel: /topic/sessions/{sessionId}
     */
    public void publishSessionUpdate(UUID sessionId, String message) {
        Map<String, String> update = new HashMap<>();
        update.put("type", "session_update");
        update.put("message", message);
        update.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String destination = "/topic/sessions/" + sessionId;
        messagingTemplate.convertAndSend(destination, (Object) update);
    }

    /**
     * Sendet ein Leaderboard-Update für eine Sitzung.
     * Ziel: /topic/sessions/{sessionId}/leaderboard
     */
    public void publishLeaderboardUpdate(UUID sessionId, SessionLeaderboardResponse leaderboard) {
        String destination = "/topic/sessions/" + sessionId + "/leaderboard";
        messagingTemplate.convertAndSend(destination, leaderboard);
    }

    /**
     * Sendet ein Scoreboard-Update (Punkte-Änderungen) an alle Tablets.
     * Ziel: /topic/sessions/{sessionId}/scoreboard
     */
    public void publishScoreboardUpdate(UUID sessionId, ScoreboardResponse scoreboard) {
        String destination = "/topic/sessions/" + sessionId + "/scoreboard";
        messagingTemplate.convertAndSend(destination, scoreboard);
    }

    /**
     * Sendet Range-Queue-Updates für einen bestimmten Bereich an den Operator.
     * Ziel: /topic/sessions/{sessionId}/range/{rangeId}
     */
    public void publishRangeUpdate(UUID sessionId, UUID rangeId, Map<String, Object> rangeData) {
        String destination = "/topic/sessions/" + sessionId + "/range/" + rangeId;
        messagingTemplate.convertAndSend((Object) destination, rangeData);
    }

    /**
     * Sendet eine generische Sitzungs-Nachricht.
     * Wird für Fehler, Warnungen und andere Meldungen verwendet.
     * Ziel: /topic/sessions/{sessionId}/messages
     */
    public void publishSessionMessage(UUID sessionId, String messageType, String message) {
        Map<String, String> msg = new HashMap<>();
        msg.put("type", messageType);
        msg.put("message", message);
        msg.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String destination = "/topic/sessions/" + sessionId + "/messages";
        messagingTemplate.convertAndSend(destination, (Object) msg);
    }

    /**
     * Sendet einen Fehler an die Tablets einer Sitzung.
     * Ziel: /topic/sessions/{sessionId}/errors
     */
    public void publishSessionError(UUID sessionId, String errorMessage) {
        Map<String, String> error = new HashMap<>();
        error.put("error", errorMessage);
        error.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String destination = "/topic/sessions/" + sessionId + "/errors";
        messagingTemplate.convertAndSend(destination, (Object) error);
    }

    // ── Bracket-spezifische Broadcasts ──

    /**
     * Sendet ein vollständiges Bracket-State-Update an alle Subscriber.
     * Wird nach Bracket-Initialisierung und Phase-Änderungen gesendet.
     * Ziel: /topic/sessions/{sessionId}/bracket/state
     */
    public void publishBracketStateUpdate(UUID sessionId, Map<String, Object> bracketState) {
        Map<String, Object> update = new HashMap<>(bracketState);
        update.put("timestamp", System.currentTimeMillis());
        update.put("type", "bracket_state_update");
        String destination = "/topic/sessions/" + sessionId + "/bracket/state";
        messagingTemplate.convertAndSend(destination, (Object) update);
    }

    /**
     * Sendet ein Match-Ergebnis-Update (Gewinner recorded, propagiert zur nächsten Runde).
     * Ziel: /topic/sessions/{sessionId}/bracket/match/{matchNumber}
     */
    public void publishMatchResultUpdate(UUID sessionId, int matchNumber, UUID winnerId,
                                         @Nullable Integer score1, @Nullable Integer score2,
                                         @Nullable List<Map<String, Object>> nextMatches) {
        Map<String, Object> update = new HashMap<>();
        update.put("type", "match_result_update");
        update.put("matchNumber", matchNumber);
        update.put("winnerId", winnerId);
        update.put("score1", score1);
        update.put("score2", score2);
        update.put("timestamp", System.currentTimeMillis());

        if (nextMatches != null) {
            update.put("nextMatches", nextMatches);
        }

        String matchDestination = "/topic/sessions/" + sessionId + "/bracket/match/" + matchNumber;
        messagingTemplate.convertAndSend(matchDestination, (Object) update);
        // Auch broadcast zu allgemeinem Bracket-Topic
        String stateDestination = "/topic/sessions/" + sessionId + "/bracket/state";
        messagingTemplate.convertAndSend(stateDestination, (Object) update);
    }

    /**
     * Sendet eine Bracket-Phase-Änderung (z.B. SETUP → SEEDING → IN_PROGRESS).
     * Ziel: /topic/sessions/{sessionId}/bracket/phase
     */
    public void publishBracketPhaseChange(UUID sessionId, @Nullable BracketPhase oldPhase, BracketPhase newPhase) {
        Map<String, Object> update = new HashMap<>();
        update.put("type", "bracket_phase_changed");
        update.put("oldPhase", oldPhase != null ? oldPhase.toString() : null);
        update.put("newPhase", newPhase.toString());
        update.put("timestamp", System.currentTimeMillis());
        String destination = "/topic/sessions/" + sessionId + "/bracket/phase";
        messagingTemplate.convertAndSend(destination, (Object) update);
    }

    /**
     * Sendet eine Runden-Abschluss-Benachrichtigung (alle Matches einer Runde gespielt).
     * Ziel: /topic/sessions/{sessionId}/bracket/round/{roundNumber}
     */
    public void publishRoundCompletion(UUID sessionId, int roundNumber, int totalMatches,
                                       int completedMatches, @Nullable List<Map<String, Object>> nextRoundMatches) {
        Map<String, Object> update = new HashMap<>();
        update.put("type", "round_completed");
        update.put("roundNumber", roundNumber);
        update.put("totalMatches", totalMatches);
        update.put("completedMatches", completedMatches);
        update.put("timestamp", System.currentTimeMillis());

        if (nextRoundMatches != null && !nextRoundMatches.isEmpty()) {
            update.put("nextRoundMatches", nextRoundMatches);
            update.put("nextRoundNumber", roundNumber + 1);
        } else {
            update.put("isFinal", true); // Finale erreicht
        }

        String roundDestination = "/topic/sessions/" + sessionId + "/bracket/round/" + roundNumber;
        messagingTemplate.convertAndSend(roundDestination, (Object) update);
        // Auch zu allgemeinem Bracket-Topic
        String stateDestination = "/topic/sessions/" + sessionId + "/bracket/state";
        messagingTemplate.convertAndSend(stateDestination, (Object) update);
    }

    /**
     * Sendet eine Benachrichtigung zum Turnier-Abschluss.
     * Ziel: /topic/sessions/{sessionId}/bracket/completed
     */
    public void publishBracketCompleted(UUID sessionId, UUID championId, String championName) {
        Map<String, Object> update = new HashMap<>();
        update.put("type", "bracket_completed");
        update.put("championId", championId);
        update.put("championName", championName);
        update.put("timestamp", System.currentTimeMillis());
        String destination = "/topic/sessions/" + sessionId + "/bracket/completed";
        messagingTemplate.convertAndSend(destination, (Object) update);
    }
}

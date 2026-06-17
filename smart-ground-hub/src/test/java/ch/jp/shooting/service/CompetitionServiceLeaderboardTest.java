package ch.jp.shooting.service;

import ch.jp.shooting.dto.SessionLeaderboardResponse;
import ch.jp.shooting.mapper.CareerStatsMapper;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.CareerStatsRepository;
import ch.jp.shooting.repository.CompetitionTiebreakerRepository;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.PlayerResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Verifies that tied blocks on the leaderboard are ordered/ranked via Stechen rounds
 * (TieResolver) rather than by arbitrary insertion order.
 */
@ExtendWith(MockitoExtension.class)
class CompetitionServiceLeaderboardTest {

    @Mock LiveSessionRepository sessionRepository;
    @Mock PlayerResultRepository playerResultRepository;
    @Mock CareerStatsRepository careerStatsRepository;
    @Mock CareerStatsMapper careerStatsMapper;
    @Mock CompetitionTiebreakerRepository tiebreakerRepository;

    private CompetitionService service;
    private UUID sessionId;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        service = new CompetitionService(sessionRepository, playerResultRepository,
            careerStatsRepository, careerStatsMapper, new ObjectMapper(),
            tiebreakerRepository, new TieResolver());
        sessionId = UUID.randomUUID();
        session = new LiveSession(SessionType.COMPETITION, SessionStatus.ACTIVE);
    }

    private PlayerResult playerWith(UUID id, String name, String programResultsJson) {
        SessionPlayer p = new SessionPlayer(null, PlayerType.GUEST, name);
        p.setId(id);
        PlayerResult pr = new PlayerResult(session, p);
        pr.setProgramResults(programResultsJson);
        return pr;
    }

    @Test
    void computeLeaderboard_resolvesTiedBlockViaStechen() throws Exception {
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        // Beide Spieler auf 24 Hauptpunkte (12 + 12).
        PlayerResult winner = playerWith(winnerId, "Winner",
            "[{\"totalPoints\":12,\"maxPoints\":25},{\"totalPoints\":12,\"maxPoints\":25}]");
        PlayerResult loser = playerWith(loserId, "Loser",
            "[{\"totalPoints\":12,\"maxPoints\":25},{\"totalPoints\":12,\"maxPoints\":25}]");
        when(playerResultRepository.findBySessionId(sessionId))
            .thenReturn(List.of(loser, winner)); // bewusst loser zuerst → Reihenfolge muss vom Stechen kommen

        // Ein abgeschlossenes Stechen, das Winner vor Loser setzt.
        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, UUID.randomUUID(), 1, 1);
        tb.setStatus(TiebreakerStatus.COMPLETED);
        tb.setParticipantsJson("[\"" + winnerId + "\",\"" + loserId + "\"]");
        tb.setResultsJson(
            "[{\"playerId\":\"" + winnerId + "\",\"totalPoints\":10,\"maxPoints\":10},"
          + "{\"playerId\":\"" + loserId + "\",\"totalPoints\":7,\"maxPoints\":10}]");
        when(tiebreakerRepository.findBySessionId(sessionId)).thenReturn(List.of(tb));

        SessionLeaderboardResponse resp = service.computeLeaderboard(sessionId);

        List<SessionLeaderboardResponse.PlayerScoreEntry> scores = resp.getPlayerScores();
        assertEquals(2, scores.size());

        SessionLeaderboardResponse.PlayerScoreEntry first = scores.get(0);
        SessionLeaderboardResponse.PlayerScoreEntry second = scores.get(1);

        assertEquals("Winner", first.getDisplayName());
        assertEquals(1, first.getRank());
        assertFalse(first.isTied());
        assertTrue(first.isTieResolvedByStechen());

        assertEquals("Loser", second.getDisplayName());
        assertEquals(2, second.getRank());
        assertTrue(second.isTieResolvedByStechen());
    }
}

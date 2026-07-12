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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

    @Mock LiveSessionRepository sessionRepository;
    @Mock PlayerResultRepository playerResultRepository;
    @Mock CareerStatsRepository careerStatsRepository;
    @Mock CareerStatsMapper careerStatsMapper;
    @Mock CompetitionTiebreakerRepository tiebreakerRepository;
    @Mock ch.jp.shooting.repository.CompetitionSerieResultRepository serieResultRepository;

    private CompetitionService service;
    private UUID sessionId;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        service = new CompetitionService(sessionRepository, playerResultRepository,
            careerStatsRepository, careerStatsMapper, new ObjectMapper(),
            tiebreakerRepository, new TieResolver(), serieResultRepository);
        sessionId = UUID.randomUUID();
        session = new LiveSession(SessionType.COMPETITION, SessionStatus.ACTIVE);
    }

    private PlayerResult playerWith(String name, String programResultsJson) {
        SessionPlayer p = new SessionPlayer(null, PlayerType.GUEST, name);
        p.setId(UUID.randomUUID());
        PlayerResult pr = new PlayerResult(session, p);
        pr.setProgramResults(programResultsJson);
        return pr;
    }

    @Test
    void computeLeaderboard_sumsFlatSerieScoresAndRanks() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        PlayerResult alice = playerWith("Alice",
            "[{\"passeIndex\":0,\"serieId\":\"s1\",\"totalPoints\":8,\"maxPoints\":10},"
          + "{\"passeIndex\":1,\"serieId\":\"s2\",\"totalPoints\":7,\"maxPoints\":10}]");
        PlayerResult bob = playerWith("Bob",
            "[{\"passeIndex\":0,\"serieId\":\"s1\",\"totalPoints\":5,\"maxPoints\":10}]");
        when(playerResultRepository.findBySessionId(sessionId)).thenReturn(List.of(alice, bob));

        SessionLeaderboardResponse resp = service.computeLeaderboard(sessionId);

        List<SessionLeaderboardResponse.PlayerScoreEntry> scores = resp.getPlayerScores();
        assertEquals(2, scores.size());
        // Alice 15/20 → rank 1, Bob 5/10 → rank 2
        assertEquals("Alice", scores.get(0).getDisplayName());
        assertEquals(15, scores.get(0).getTotalScore());
        assertEquals(20, scores.get(0).getMaxScore());
        assertEquals(1, scores.get(0).getRank());
        assertEquals("Bob", scores.get(1).getDisplayName());
        assertEquals(5, scores.get(1).getTotalScore());
        assertEquals(2, scores.get(1).getRank());
    }

    @Test
    void computeLeaderboard_handlesEmptyProgramResults() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(playerResultRepository.findBySessionId(sessionId))
            .thenReturn(List.of(playerWith("Solo", null)));

        SessionLeaderboardResponse resp = service.computeLeaderboard(sessionId);

        assertEquals(1, resp.getPlayerScores().size());
        assertEquals(0, resp.getPlayerScores().get(0).getTotalScore());
    }
}

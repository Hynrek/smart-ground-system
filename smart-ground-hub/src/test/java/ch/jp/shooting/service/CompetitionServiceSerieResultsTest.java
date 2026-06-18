package ch.jp.shooting.service;

import ch.jp.shooting.dto.CompetitionSerieResultDetailResponse;
import ch.jp.shooting.exception.SessionNotFoundException;
import ch.jp.shooting.mapper.CareerStatsMapper;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.CareerStatsRepository;
import ch.jp.shooting.repository.CompetitionSerieResultRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Verifies that getSerieResults returns the persisted per-Serie play results
 * (including stepStates) so completed competitions can show a per-step scorecard.
 */
@ExtendWith(MockitoExtension.class)
class CompetitionServiceSerieResultsTest {

    @Mock LiveSessionRepository sessionRepository;
    @Mock PlayerResultRepository playerResultRepository;
    @Mock CareerStatsRepository careerStatsRepository;
    @Mock CareerStatsMapper careerStatsMapper;
    @Mock CompetitionTiebreakerRepository tiebreakerRepository;
    @Mock CompetitionSerieResultRepository serieResultRepository;

    private CompetitionService service;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        service = new CompetitionService(sessionRepository, playerResultRepository,
            careerStatsRepository, careerStatsMapper, new ObjectMapper(),
            tiebreakerRepository, new TieResolver(), serieResultRepository);
        sessionId = UUID.randomUUID();
    }

    @Test
    void getSerieResults_returnsPersistedRowsWithParsedStepStates() throws Exception {
        when(sessionRepository.existsById(sessionId)).thenReturn(true);

        LiveSession session = new LiveSession(SessionType.COMPETITION, SessionStatus.COMPLETED);
        ShooterGroup group = new ShooterGroup();
        UUID groupId = UUID.randomUUID();
        group.setId(groupId);
        UUID serieId = UUID.randomUUID();

        CompetitionSerieResult csr = new CompetitionSerieResult(session, group, 1, serieId);
        csr.setResults("[{\"playerId\":\"p1\",\"totalPoints\":8,\"maxPoints\":10,"
            + "\"stepStates\":[{\"serieIndex\":0,\"stepIndex\":0,\"state\":\"DONE\","
            + "\"pointsEarned\":2,\"pointValue\":2}]}]");
        when(serieResultRepository.findBySessionId(sessionId)).thenReturn(List.of(csr));

        List<CompetitionSerieResultDetailResponse> out = service.getSerieResults(sessionId);

        assertEquals(1, out.size());
        CompetitionSerieResultDetailResponse dto = out.get(0);
        assertEquals(groupId, dto.groupId);
        assertEquals(1, dto.passeIndex);
        assertEquals(serieId, dto.serieId);
        assertNotNull(dto.results);
        assertEquals("p1", dto.results.get(0).get("playerId").asText());
        assertEquals("DONE", dto.results.get(0).get("stepStates").get(0).get("state").asText());
    }

    @Test
    void getSerieResults_throwsWhenSessionMissing() {
        when(sessionRepository.existsById(sessionId)).thenReturn(false);
        assertThrows(SessionNotFoundException.class, () -> service.getSerieResults(sessionId));
    }
}

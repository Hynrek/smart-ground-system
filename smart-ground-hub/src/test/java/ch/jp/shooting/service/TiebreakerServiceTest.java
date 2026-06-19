package ch.jp.shooting.service;

import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TiebreakerServiceTest {

    @Mock LiveSessionRepository sessionRepo;
    @Mock CompetitionTiebreakerRepository tbRepo;
    @Mock PlayerResultRepository playerResultRepo;
    @Mock SessionPlayerRepository playerRepo;
    @Mock PlayInstanceService playInstanceService;
    @Mock SerieRepository serieRepo;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @Spy TieResolver tieResolver = new TieResolver();

    @InjectMocks TiebreakerService service;

    private LiveSession session;
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        session = new LiveSession(SessionType.COMPETITION, SessionStatus.PRE_COMPLETE);
        session.setId(sessionId);
        lenient().when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(session));
    }

    @Test
    void startTiebreaker_loadsSerie_buildsSnapshot_startsSerieRun() throws Exception {
        UUID serieId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        Serie serie = new Serie();
        serie.setId(serieId);
        serie.setName("Stech-Serie");
        serie.setStepsJson("[]");
        when(serieRepo.findById(serieId)).thenReturn(Optional.of(serie));

        when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of());
        when(tbRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        SessionPlayer sp1 = new SessionPlayer();
        sp1.setId(p1); sp1.setDisplayName("Anna"); sp1.setType(PlayerType.USER);
        SessionPlayer sp2 = new SessionPlayer();
        sp2.setId(p2); sp2.setDisplayName("Ben"); sp2.setType(PlayerType.USER);
        when(playerRepo.findAllById(List.of(p1, p2))).thenReturn(List.of(sp1, sp2));

        var instanceResp = new ch.jp.smartground.model.PlayInstanceResponse();
        UUID instanceId = UUID.randomUUID();
        instanceResp.setInstanceId(instanceId);
        when(playInstanceService.startSerieInstance(eq(serieId), eq("Stech-Serie"), anyString(), anyList()))
                .thenReturn(instanceResp);

        var req = new ch.jp.smartground.model.StartTiebreakerRequest();
        req.setPlayerIds(List.of(p1, p2));
        req.setTemplateId(serieId);
        req.setTiePosition(1);

        var resp = service.startTiebreaker(sessionId, req);

        assertEquals("Stech-Serie", resp.getTemplateName());
        assertEquals(instanceId, resp.getPlayInstanceId().get());

        ArgumentCaptor<CompetitionTiebreaker> captor = ArgumentCaptor.forClass(CompetitionTiebreaker.class);
        verify(tbRepo).save(captor.capture());
        CompetitionTiebreaker saved = captor.getValue();
        assertEquals(TiebreakerStatus.ACTIVE, saved.getStatus());
        assertEquals(serieId, saved.getTemplateId());
        assertNotNull(saved.getProgramSnapshot());

        verify(playInstanceService).startSerieInstance(eq(serieId), eq("Stech-Serie"), anyString(), anyList());
        verify(playerResultRepo, never()).save(any());
    }

    @Test
    void startTiebreaker_rejectedWhenSessionNotPreComplete() {
        session.setStatus(SessionStatus.ACTIVE);
        var req = new ch.jp.smartground.model.StartTiebreakerRequest();
        req.setPlayerIds(List.of(UUID.randomUUID()));
        req.setTemplateId(UUID.randomUUID());

        assertThrows(ch.jp.shooting.exception.InvalidTiebreakerStateException.class,
                () -> service.startTiebreaker(sessionId, req));
    }

    @Test
    void submitResults_doesNotTouchPlayerResults() throws Exception {
        UUID tbId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, UUID.randomUUID(), 1, 1);
        tb.setStatus(TiebreakerStatus.ACTIVE);
        tb.setParticipantsJson(objectMapper.writeValueAsString(List.of(p1.toString())));
        when(tbRepo.findById(tbId)).thenReturn(Optional.of(tb));
        when(tbRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of(tb));
        when(playerResultRepo.findBySessionId(sessionId)).thenReturn(List.of());

        var req = new ch.jp.smartground.model.SubmitTiebreakerResultsRequest();
        var score = new ch.jp.smartground.model.TiebreakerPlayerScore();
        score.setPlayerId(p1); score.setTotalPoints(8); score.setMaxPoints(10);
        req.setResults(List.of(score));

        service.submitResults(sessionId, tbId, req);

        assertEquals(TiebreakerStatus.COMPLETED, tb.getStatus());
        verify(playerResultRepo, never()).save(any());
    }

    @Test
    void submitResults_onCompletedRound_throwsInvalidState() throws Exception {
        UUID tbId = UUID.randomUUID();
        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, UUID.randomUUID(), 1, 1);
        tb.setStatus(TiebreakerStatus.COMPLETED);
        when(tbRepo.findById(tbId)).thenReturn(Optional.of(tb));

        var req = new ch.jp.smartground.model.SubmitTiebreakerResultsRequest();
        req.setResults(List.of());

        assertThrows(ch.jp.shooting.exception.InvalidTiebreakerStateException.class,
                () -> service.submitResults(sessionId, tbId, req));
    }
}

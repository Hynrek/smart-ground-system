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

        var activeInst = new ch.jp.smartground.model.PlayInstanceResponse();
        activeInst.setStatus(ch.jp.smartground.model.PlayInstanceStatus.ACTIVE);
        activeInst.blocks(List.of(new ch.jp.smartground.model.PlayBlock()
            .blockId(UUID.randomUUID()).steps(List.of())));
        when(playInstanceService.getPlayInstance(any())).thenReturn(activeInst);

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
        var snap = objectMapper.readTree(saved.getProgramSnapshot());
        assertTrue(snap.isArray());
        assertEquals(1, snap.size());
        var node = snap.get(0);
        assertEquals(serieId.toString(), node.get("id").asText());
        assertEquals("Stech-Serie", node.get("alias").asText());
        assertTrue(node.get("steps").isArray());

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
    void startTiebreaker_serieNotFound_throwsSerieNotFound() {
        UUID missingSerieId = UUID.randomUUID();
        when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of());
        when(serieRepo.findById(missingSerieId)).thenReturn(Optional.empty());

        var req = new ch.jp.smartground.model.StartTiebreakerRequest();
        req.setPlayerIds(List.of(UUID.randomUUID()));
        req.setTemplateId(missingSerieId);
        req.setTiePosition(1);

        assertThrows(ch.jp.shooting.exception.SerieNotFoundException.class,
                () -> service.startTiebreaker(sessionId, req));
    }

    @Test
    void startTiebreaker_snapshotIncludesRangeWhenSeriePresent() throws Exception {
        UUID serieId = UUID.randomUUID();
        UUID rangeId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();

        Range range = new Range();
        range.setId(rangeId);
        range.setName("Stand 1");

        Serie serie = new Serie();
        serie.setId(serieId);
        serie.setName("Stech-Serie");
        serie.setStepsJson("[]");
        serie.setRange(range);
        when(serieRepo.findById(serieId)).thenReturn(Optional.of(serie));

        when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of());
        when(tbRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        SessionPlayer sp1 = new SessionPlayer();
        sp1.setId(p1); sp1.setDisplayName("Anna"); sp1.setType(PlayerType.USER);
        when(playerRepo.findAllById(List.of(p1))).thenReturn(List.of(sp1));

        var instanceResp = new ch.jp.smartground.model.PlayInstanceResponse();
        instanceResp.setInstanceId(UUID.randomUUID());
        when(playInstanceService.startSerieInstance(eq(serieId), eq("Stech-Serie"), anyString(), anyList()))
                .thenReturn(instanceResp);

        var activeInst = new ch.jp.smartground.model.PlayInstanceResponse();
        activeInst.setStatus(ch.jp.smartground.model.PlayInstanceStatus.ACTIVE);
        activeInst.blocks(List.of(new ch.jp.smartground.model.PlayBlock()
            .blockId(UUID.randomUUID()).steps(List.of())));
        when(playInstanceService.getPlayInstance(any())).thenReturn(activeInst);

        var req = new ch.jp.smartground.model.StartTiebreakerRequest();
        req.setPlayerIds(List.of(p1));
        req.setTemplateId(serieId);
        req.setTiePosition(1);

        var resp = service.startTiebreaker(sessionId, req);

        ArgumentCaptor<CompetitionTiebreaker> captor = ArgumentCaptor.forClass(CompetitionTiebreaker.class);
        verify(tbRepo).save(captor.capture());
        var node = objectMapper.readTree(captor.getValue().getProgramSnapshot()).get(0);
        assertEquals(rangeId.toString(), node.get("rangeId").asText());
        assertEquals("Stand 1", node.get("rangeName").asText());
    }

    @Test
    void listTies_completedRun_autoResolvesTiebreaker() throws Exception {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, UUID.randomUUID(), 1, 1);
        tb.setStatus(TiebreakerStatus.ACTIVE);
        tb.setPlayInstanceId(instanceId);
        tb.setParticipantsJson(objectMapper.writeValueAsString(List.of(p1.toString(), p2.toString())));
        when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of(tb));
        when(tbRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(playerResultRepo.findBySessionId(sessionId)).thenReturn(List.of());

        SessionPlayer sp1 = new SessionPlayer(); sp1.setId(p1); sp1.setDisplayName("Anna");
        SessionPlayer sp2 = new SessionPlayer(); sp2.setId(p2); sp2.setDisplayName("Ben");
        lenient().when(playerRepo.findAllById(anyList())).thenReturn(List.of(sp1, sp2));

        var inst = new ch.jp.smartground.model.PlayInstanceResponse();
        inst.setStatus(ch.jp.smartground.model.PlayInstanceStatus.COMPLETED);
        var block = new ch.jp.smartground.model.PlayBlock().blockId(UUID.randomUUID());
        block.result(new ch.jp.smartground.model.BlockResult().playerResults(List.of(
            new ch.jp.smartground.model.PlayerResult().playerId(p1.toString()).totalPoints(9).maxPoints(10),
            new ch.jp.smartground.model.PlayerResult().playerId(p2.toString()).totalPoints(7).maxPoints(10))));
        inst.blocks(List.of(block));
        when(playInstanceService.getPlayInstance(instanceId)).thenReturn(inst);

        service.listTies(sessionId);

        assertEquals(TiebreakerStatus.COMPLETED, tb.getStatus());
        assertNotNull(tb.getResultsJson());
        assertEquals(2, objectMapper.readTree(tb.getResultsJson()).size());
        verify(playerResultRepo, never()).save(any());
    }

    @Test
    void listTiebreakers_activeRun_attachesRunBlock() throws Exception {
        UUID instanceId = UUID.randomUUID();
        UUID rangeId = UUID.randomUUID();
        UUID serieId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();

        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, UUID.randomUUID(), 1, 1);
        tb.setStatus(TiebreakerStatus.ACTIVE);
        tb.setPlayInstanceId(instanceId);
        tb.setParticipantsJson("[]");
        when(tbRepo.findBySessionId(sessionId)).thenReturn(List.of(tb));
        lenient().when(playerRepo.findAllById(anyList())).thenReturn(List.of());

        var inst = new ch.jp.smartground.model.PlayInstanceResponse();
        inst.setStatus(ch.jp.smartground.model.PlayInstanceStatus.ACTIVE);
        inst.blocks(List.of(new ch.jp.smartground.model.PlayBlock()
            .blockId(blockId).serieId(serieId).serieAlias("Stech-Serie")
            .rangeId(rangeId).rangeName("Stand 1").steps(List.of())));
        when(playInstanceService.getPlayInstance(instanceId)).thenReturn(inst);

        var rounds = service.listTiebreakers(sessionId);

        assertEquals(1, rounds.size());
        var r = rounds.get(0);
        assertEquals(blockId, r.getBlockId().orElse(null));
        assertEquals(rangeId, r.getRun().getRangeId().orElse(null));
        assertEquals("Stech-Serie", r.getRun().getAlias());
    }
}

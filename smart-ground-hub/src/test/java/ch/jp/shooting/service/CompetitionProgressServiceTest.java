package ch.jp.shooting.service;

import ch.jp.shooting.dto.PasseSnapshot;
import ch.jp.shooting.dto.play.SerieSnapshotRecord;
import ch.jp.shooting.dto.play.StepRecord;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
import ch.jp.smartground.model.CompleteSerieRequest;
import ch.jp.smartground.model.SessionProgressResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetitionProgressServiceTest {

    @Mock CompetitionSerieResultRepository csrRepository;
    @Mock LiveSessionRepository sessionRepository;
    @Mock ShooterGroupRepository groupRepository;
    @Mock PlayerResultRepository playerResultRepository;
    @Mock SerieRepository serieRepository;
    @Mock PositionLabelResolver positionLabelResolver;
    @Mock UserScoreService userScoreService;
    @InjectMocks CompetitionProgressService progressService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LiveSession session;
    private ShooterGroup group;
    private UUID sessionId;
    private UUID groupId;
    private UUID serieId;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper.findAndRegisterModules();
        Field f = CompetitionProgressService.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(progressService, objectMapper);

        sessionId = UUID.randomUUID();
        groupId   = UUID.randomUUID();
        serieId   = UUID.randomUUID();

        session = new LiveSession(SessionType.COMPETITION, SessionStatus.ACTIVE);
        session.setName("Test Session");

        PasseSnapshot passe = new PasseSnapshot();
        passe.id = "passe-1";
        passe.name = "Passe 1";
        passe.serieIds = List.of(serieId.toString());
        session.setProgramSnapshots(objectMapper.writeValueAsString(List.of(passe)));

        group = new ShooterGroup(session, "Rotte A");
        group.setMembers(new ArrayList<>());
    }

    @Test
    void completeSerie_insertsRow() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
            any(), any(), anyInt(), any())).thenReturn(false);
        when(csrRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of());

        CompleteSerieRequest req = new CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));

        progressService.completeSerie(sessionId, groupId, serieId, req);

        verify(csrRepository).save(argThat(r ->
            r.getPasseIndex() == 0 && r.getSerieId().equals(serieId)
        ));
    }

    @Test
    void completeSerie_recordsUserScores() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
            any(), any(), anyInt(), any())).thenReturn(false);
        when(csrRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of());

        CompleteSerieRequest req = new CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));

        progressService.completeSerie(sessionId, groupId, serieId, req);

        verify(userScoreService).recordCompetitionSerie(
            any(CompetitionSerieResult.class), any(ShooterGroup.class), anyString(), anyList(), eq(false));
    }

    @Test
    void completeSerie_freezesResolvedSerieSnapshotOnRow() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
            any(), any(), anyInt(), any())).thenReturn(false);
        when(csrRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of());

        // Live serie with one solo step carrying a stale letter
        var serie = new Serie();
        serie.setId(serieId);
        serie.setName("Serie 1");
        serie.setOwnership("user");
        serie.setStepsJson(
            "[{\"id\":\"1\",\"type\":\"solo\",\"posId\":\"p-1\",\"letter\":\"OLD\"}]");
        when(serieRepository.findById(serieId)).thenReturn(Optional.of(serie));
        when(positionLabelResolver.resolveSteps(anyList())).thenReturn(List.of(
            new StepRecord("1", "solo", "p-1", "A1", null, null, null, null, "A1", null, null)));

        var captor = org.mockito.ArgumentCaptor.forClass(CompetitionSerieResult.class);

        CompleteSerieRequest req = new CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));
        req.setResults(List.of());
        progressService.completeSerie(sessionId, groupId, serieId, req);

        verify(csrRepository).save(captor.capture());
        String json = captor.getValue().getSerieSnapshotJson();
        assertNotNull(json);
        SerieSnapshotRecord snapshot = objectMapper.readValue(json, SerieSnapshotRecord.class);
        assertEquals("Serie 1", snapshot.serieName());
        assertEquals(1, snapshot.steps().size());
        assertEquals("A1", snapshot.steps().get(0).letter()); // resolved, not "OLD"
    }

    @Test
    void completeSerie_throwsIfAlreadyCompleted() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
            any(), any(), anyInt(), any())).thenReturn(true);

        CompleteSerieRequest req = new CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));

        assertThrows(IllegalStateException.class,
            () -> progressService.completeSerie(sessionId, groupId, serieId, req));
    }

    @Test
    void getProgress_returnsGroupWithNoCompletions() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(sessionId)).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of());

        SessionProgressResponse resp = progressService.getProgress(sessionId);

        assertEquals(1, resp.getGroups().size());
        assertEquals("Rotte A", resp.getGroups().get(0).getGroupName());
        assertEquals(0, resp.getGroups().get(0).getPassenCompleted());
        assertEquals(1, resp.getGroups().get(0).getPassenTotal());
        assertTrue(resp.getGroups().get(0).getCompletedSerien().isEmpty());
    }

    @Test
    void getProgress_exposesCompletedSerienPerGroup() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(sessionId)).thenReturn(List.of(group));
        var csr = new CompetitionSerieResult(session, group, 0, serieId);
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of(csr));

        SessionProgressResponse resp = progressService.getProgress(sessionId);

        var gp = resp.getGroups().get(0);
        assertEquals(1, gp.getCompletedSerien().size());
        assertEquals(0, gp.getCompletedSerien().get(0).getPasseIndex());
        assertEquals(serieId, gp.getCompletedSerien().get(0).getSerieId());
        // the only Serie of Passe 0 is done → the Passe counts as completed
        assertEquals(1, gp.getPassenCompleted());
    }

    @Test
    void getProgress_includesReleasedPasseIndex() throws Exception {
        session.setReleasedPasseIndex(1);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(sessionId)).thenReturn(List.of());
        SessionProgressResponse resp = progressService.getProgress(sessionId);
        assertEquals(1, resp.getReleasedPasseIndex());
    }

    private void twoPasseSnapshots(UUID serie2) throws Exception {
        PasseSnapshot p0 = new PasseSnapshot(); p0.id = "p0"; p0.name = "Passe 1";
        p0.serieIds = List.of(serieId.toString());
        PasseSnapshot p1 = new PasseSnapshot(); p1.id = "p1"; p1.name = "Passe 2";
        p1.serieIds = List.of(serie2.toString());
        session.setProgramSnapshots(objectMapper.writeValueAsString(List.of(p0, p1)));
    }

    @Test
    void releaseNextPasse_advancesWhenCurrentPasseCompleteByAllGroups() throws Exception {
        twoPasseSnapshots(UUID.randomUUID());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));
        when(csrRepository.findBySessionId(any()))
            .thenReturn(List.of(new CompetitionSerieResult(session, group, 0, serieId)));
        when(csrRepository.findBySessionIdAndGroupId(any(), any()))
            .thenReturn(List.of(new CompetitionSerieResult(session, group, 0, serieId)));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SessionProgressResponse resp = progressService.releaseNextPasse(sessionId);

        assertEquals(1, session.getReleasedPasseIndex());
        assertEquals(1, resp.getReleasedPasseIndex());
        verify(sessionRepository).save(session);
    }

    @Test
    void releaseNextPasse_rejectsWhenCurrentPasseIncomplete() throws Exception {
        twoPasseSnapshots(UUID.randomUUID());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));
        when(csrRepository.findBySessionId(any())).thenReturn(List.of());

        assertThrows(ch.jp.shooting.exception.ConflictException.class,
            () -> progressService.releaseNextPasse(sessionId));
        assertEquals(0, session.getReleasedPasseIndex());
    }

    @Test
    void releaseNextPasse_rejectsAtLastPasse() throws Exception {
        twoPasseSnapshots(UUID.randomUUID());
        session.setReleasedPasseIndex(1); // already the last (index 1 of 2)
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThrows(ch.jp.shooting.exception.ConflictException.class,
            () -> progressService.releaseNextPasse(sessionId));
        assertEquals(1, session.getReleasedPasseIndex());
    }

    private void setPlayerId(SessionPlayer p, UUID id) {
        try { var f = SessionPlayer.class.getDeclaredField("id"); f.setAccessible(true); f.set(p, id); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void correctSerieResult_overwritesRowAndReplacesPlayerResultEntry() throws Exception {
        session.setStatus(SessionStatus.PRE_COMPLETE);
        UUID memberId = UUID.randomUUID();
        SessionPlayer m1 = new SessionPlayer(); setPlayerId(m1, memberId); m1.setDisplayName("Max");
        group.getMembers().add(m1);
        var existingCsr = new CompetitionSerieResult(session, group, 0, serieId);
        existingCsr.setResults("[]");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.findBySessionIdAndGroupIdAndPasseIndexAndSerieId(any(), any(), anyInt(), any()))
            .thenReturn(Optional.of(existingCsr));
        when(csrRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var pr = new PlayerResult(session, m1);
        pr.setProgramResults(objectMapper.writeValueAsString(List.of(
            java.util.Map.of("passeIndex", 0, "serieId", serieId.toString(), "totalPoints", 1, "maxPoints", 2))));
        when(playerResultRepository.findBySessionIdAndPlayerId(any(), eq(memberId))).thenReturn(Optional.of(pr));
        when(playerResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of(existingCsr));

        var req = new ch.jp.smartground.model.CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));
        var pres = new ch.jp.smartground.model.PlayerResult();
        pres.setPlayerId(memberId.toString()); pres.setTotalPoints(2); pres.setMaxPoints(2);
        req.setResults(List.of(pres));

        progressService.correctSerieResult(sessionId, groupId, serieId, req);

        verify(csrRepository).save(argThat(c -> c.getResults() != null && c.getResults().contains("\"totalPoints\":2")));
        verify(playerResultRepository).save(argThat(saved -> {
            try {
                var arr = objectMapper.readValue(saved.getProgramResults(), java.util.Map[].class);
                var forSerie = java.util.Arrays.stream(arr)
                    .filter(m -> serieId.toString().equals(m.get("serieId"))).toList();
                return forSerie.size() == 1
                    && ((Number) forSerie.get(0).get("totalPoints")).intValue() == 2;
            } catch (Exception e) { return false; }
        }));
    }

    @Test
    void correctSerie_recordsUserScoresWithReplace() throws Exception {
        session.setStatus(SessionStatus.PRE_COMPLETE);
        UUID memberId = UUID.randomUUID();
        SessionPlayer m1 = new SessionPlayer(); setPlayerId(m1, memberId); m1.setDisplayName("Max");
        group.getMembers().add(m1);
        var existingCsr = new CompetitionSerieResult(session, group, 0, serieId);
        existingCsr.setResults("[]");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.findBySessionIdAndGroupIdAndPasseIndexAndSerieId(any(), any(), anyInt(), any()))
            .thenReturn(Optional.of(existingCsr));
        when(csrRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var pr = new PlayerResult(session, m1);
        pr.setProgramResults(objectMapper.writeValueAsString(List.of(
            java.util.Map.of("passeIndex", 0, "serieId", serieId.toString(), "totalPoints", 1, "maxPoints", 2))));
        when(playerResultRepository.findBySessionIdAndPlayerId(any(), eq(memberId))).thenReturn(Optional.of(pr));
        when(playerResultRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupRepository.findBySessionId(any())).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of(existingCsr));

        var req = new ch.jp.smartground.model.CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));
        var pres = new ch.jp.smartground.model.PlayerResult();
        pres.setPlayerId(memberId.toString()); pres.setTotalPoints(2); pres.setMaxPoints(2);
        req.setResults(List.of(pres));

        progressService.correctSerieResult(sessionId, groupId, serieId, req);

        verify(userScoreService).recordCompetitionSerie(
            any(CompetitionSerieResult.class), any(ShooterGroup.class), anyString(), anyList(), eq(true));
    }

    @Test
    void correctSerieResult_rejectsWhenNotPreComplete() {
        session.setStatus(SessionStatus.ACTIVE);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        var req = new ch.jp.smartground.model.CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));
        assertThrows(ch.jp.shooting.exception.ConflictException.class,
            () -> progressService.correctSerieResult(sessionId, groupId, serieId, req));
    }

    @Test
    void correctSerieResult_rejectsWhenSerieNotCompleted() {
        session.setStatus(SessionStatus.PRE_COMPLETE);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.findBySessionIdAndGroupIdAndPasseIndexAndSerieId(any(), any(), anyInt(), any()))
            .thenReturn(Optional.empty());
        var req = new ch.jp.smartground.model.CompleteSerieRequest();
        req.setPasseIndex(org.openapitools.jackson.nullable.JsonNullable.of(0));
        assertThrows(ch.jp.shooting.exception.ConflictException.class,
            () -> progressService.correctSerieResult(sessionId, groupId, serieId, req));
    }
}

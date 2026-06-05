package ch.jp.shooting.service;

import ch.jp.shooting.dto.CompleteSerieRequest;
import ch.jp.shooting.dto.CompetitionProgressResponse;
import ch.jp.shooting.dto.PasseSnapshot;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
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
        // inject real ObjectMapper (not a @Mock)
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

        CompleteSerieRequest req = new CompleteSerieRequest();
        req.passeIndex = 0;

        progressService.completeSerie(sessionId, groupId, serieId, req);

        verify(csrRepository).save(argThat(r ->
            r.getPasseIndex() == 0 && r.getSerieId().equals(serieId)
        ));
    }

    @Test
    void completeSerie_throwsIfAlreadyCompleted() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findByIdAndSessionId(groupId, sessionId)).thenReturn(Optional.of(group));
        when(csrRepository.existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
            any(), any(), anyInt(), any())).thenReturn(true);

        CompleteSerieRequest req = new CompleteSerieRequest();
        req.passeIndex = 0;

        assertThrows(IllegalStateException.class,
            () -> progressService.completeSerie(sessionId, groupId, serieId, req));
    }

    @Test
    void getProgress_returnsGroupWithCurrentPasseIndexZero() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(groupRepository.findBySessionId(sessionId)).thenReturn(List.of(group));
        when(csrRepository.findBySessionIdAndGroupId(any(), any())).thenReturn(List.of());

        CompetitionProgressResponse resp = progressService.getProgress(sessionId);

        assertEquals(1, resp.groups.size());
        assertEquals("Rotte A", resp.groups.get(0).groupName);
        assertEquals(0, resp.groups.get(0).currentPasseIndex);
    }
}

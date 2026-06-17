package ch.jp.shooting.service;

import ch.jp.shooting.exception.UnresolvedTiesException;
import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.SessionStatus;
import ch.jp.shooting.model.SessionType;
import ch.jp.shooting.repository.LiveSessionRepository;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.PlayerResultRepository;
import ch.jp.shooting.repository.SessionPlayerRepository;
import ch.jp.shooting.repository.SessionTemplateRepository;
import ch.jp.shooting.repository.ShooterGroupRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.smartground.model.SessionTiesResponse;
import ch.jp.smartground.model.TiedBlock;
import ch.jp.smartground.model.UpdateSessionStatusRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceFinishGuardTest {

    @Mock LiveSessionRepository sessionRepository;
    @Mock ShooterGroupRepository groupRepository;
    @Mock SessionPlayerRepository playerRepository;
    @Mock PlayerResultRepository resultRepository;
    @Mock SessionTemplateRepository templateRepository;
    @Mock UserRepository userRepository;
    @Mock PasseRepository passeRepository;
    @Mock TiebreakerService tiebreakerService;

    SessionService service;

    private UUID sessionId;
    private LiveSession session;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                sessionRepository,
                groupRepository,
                playerRepository,
                resultRepository,
                templateRepository,
                userRepository,
                passeRepository,
                new ObjectMapper(),
                tiebreakerService);

        sessionId = UUID.randomUUID();
        session = new LiveSession();
        session.setName("Wettkampf");
        session.setType(SessionType.COMPETITION);
        session.setStatus(SessionStatus.PRE_COMPLETE);
    }

    @Test
    void patchSessionStatus_toCompleted_withDecisiveUnresolvedTie_throwsAndDoesNotSave() throws Exception {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(tiebreakerService.listTies(sessionId)).thenReturn(
                new SessionTiesResponse().addTiedBlocksItem(
                        new TiedBlock().tiePosition(1).resolved(false)));

        UpdateSessionStatusRequest req = new UpdateSessionStatusRequest();
        req.setStatus("completed");
        // force is null

        assertThrows(UnresolvedTiesException.class,
                () -> service.patchSessionStatus(sessionId, req));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void patchSessionStatus_toCompleted_withForce_succeedsAndSaves() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(LiveSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateSessionStatusRequest req = new UpdateSessionStatusRequest();
        req.setStatus("completed");
        req.setForce(true);

        service.patchSessionStatus(sessionId, req);

        assertEquals(SessionStatus.COMPLETED, session.getStatus());
        verify(sessionRepository).save(any(LiveSession.class));
    }
}

package ch.jp.shooting.service;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTransitionTest {

    @Mock LiveSessionRepository sessionRepository;
    @Mock ShooterGroupRepository groupRepository;
    @Mock SessionPlayerRepository playerRepository;
    @Mock PlayerResultRepository resultRepository;
    @Mock SessionTemplateRepository templateRepository;
    @Mock UserRepository userRepository;
    @Mock PasseRepository passeRepository;
    @Mock TiebreakerService tiebreakerService;

    SessionService service;
    UUID sessionId;
    LiveSession session;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                sessionRepository, groupRepository, playerRepository, resultRepository,
                templateRepository, userRepository, passeRepository,
                new ObjectMapper(), tiebreakerService);
        sessionId = UUID.randomUUID();
        session = new LiveSession();
        session.setName("Wettkampf");
        session.setType(SessionType.COMPETITION);
        session.setStatus(SessionStatus.SETUP);
        lenient().when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        lenient().when(sessionRepository.save(any(LiveSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void updateSessionStatus_setupToActive_isAllowed() {
        service.updateSessionStatus(sessionId, "active");
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
    }

    @Test
    void updateSessionStatus_setupToCompleted_isRejected() {
        assertThrows(IllegalStateException.class,
                () -> service.updateSessionStatus(sessionId, "completed"));
    }
}

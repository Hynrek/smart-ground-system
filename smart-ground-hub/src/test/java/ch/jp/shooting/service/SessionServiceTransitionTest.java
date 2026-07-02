package ch.jp.shooting.service;

import ch.jp.shooting.dto.PasseSnapshot;
import ch.jp.shooting.exception.PasseNotFoundException;
import ch.jp.shooting.exception.SessionStatusTransitionException;
import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.Passe;
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

import java.util.List;
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
    @Mock PasseService passeService;

    SessionService service;
    UUID sessionId;
    LiveSession session;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                sessionRepository, groupRepository, playerRepository, resultRepository,
                templateRepository, userRepository, passeRepository,
                new ObjectMapper(), tiebreakerService, passeService);
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

    @Test
    void deleteSession_inSetupStatus_isAllowed() {
        session.setStatus(SessionStatus.SETUP);
        service.deleteSession(sessionId);
        org.mockito.Mockito.verify(sessionRepository).delete(session);
    }

    @Test
    void deleteSession_inActiveStatus_isAllowed() {
        session.setStatus(SessionStatus.ACTIVE);
        service.deleteSession(sessionId);
        org.mockito.Mockito.verify(sessionRepository).delete(session);
    }

    @Test
    void deleteSession_inCompletedStatus_isRejected() {
        session.setStatus(SessionStatus.COMPLETED);
        assertThrows(SessionStatusTransitionException.class,
                () -> service.deleteSession(sessionId));
    }

    private static PasseSnapshot passeSnapshot(UUID id, String name) {
        PasseSnapshot s = new PasseSnapshot();
        s.id = id.toString();
        s.name = name;
        return s;
    }

    private void givenPassenOnSession(UUID... ids) throws Exception {
        List<PasseSnapshot> snapshots = java.util.Arrays.stream(ids)
                .map(id -> passeSnapshot(id, "Passe-" + id))
                .toList();
        session.setProgramSnapshots(new ObjectMapper().writeValueAsString(snapshots));
    }

    @Test
    void reorderPassen_validPermutation_updatesOrder() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        givenPassenOnSession(a, b, c);

        var response = service.reorderPassen(sessionId,
                new ch.jp.smartground.model.ReorderPassenRequest(List.of(c, a, b)));

        assertEquals(List.of(c, a, b), response.getPassen().stream()
                .map(ch.jp.smartground.model.PasseReference::getId).toList());
    }

    @Test
    void reorderPassen_notSetupStatus_isRejected() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenPassenOnSession(a, b);
        session.setStatus(SessionStatus.ACTIVE);

        assertThrows(SessionStatusTransitionException.class,
                () -> service.reorderPassen(sessionId,
                        new ch.jp.smartground.model.ReorderPassenRequest(List.of(b, a))));
    }

    @Test
    void reorderPassen_notAPermutation_isRejected() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenPassenOnSession(a, b);

        assertThrows(IllegalArgumentException.class,
                () -> service.reorderPassen(sessionId,
                        new ch.jp.smartground.model.ReorderPassenRequest(List.of(a, UUID.randomUUID()))));
    }

    @Test
    void addPasseToSession_setupStatus_appendsSnapshotAndReturnsReference() throws Exception {
        UUID passeId = UUID.randomUUID();
        Passe passe = new Passe();
        passe.setId(passeId);
        passe.setName("Neue Passe");
        passe.setSerieIdsJson("[]");
        when(passeRepository.findById(passeId)).thenReturn(Optional.of(passe));
        when(passeService.resolveLiveSerien(passe)).thenReturn(List.of());

        var response = service.addPasseToSession(
                sessionId, new ch.jp.smartground.model.AddPasseToSessionRequest(passeId));

        assertEquals(passeId, response.getId());
        assertEquals("Neue Passe", response.getName());
        PasseSnapshot[] saved = new ObjectMapper().readValue(session.getProgramSnapshots(), PasseSnapshot[].class);
        assertEquals(1, saved.length);
        assertEquals(passeId.toString(), saved[0].id);
    }

    @Test
    void addPasseToSession_alreadyOnSession_isRejected() throws Exception {
        UUID passeId = UUID.randomUUID();
        givenPassenOnSession(passeId);
        Passe passe = new Passe();
        passe.setId(passeId);
        passe.setName("X");
        lenient().when(passeRepository.findById(passeId)).thenReturn(Optional.of(passe));

        assertThrows(IllegalArgumentException.class,
                () -> service.addPasseToSession(
                        sessionId, new ch.jp.smartground.model.AddPasseToSessionRequest(passeId)));
    }

    @Test
    void addPasseToSession_notSetupStatus_isRejected() {
        session.setStatus(SessionStatus.ACTIVE);
        assertThrows(SessionStatusTransitionException.class,
                () -> service.addPasseToSession(
                        sessionId, new ch.jp.smartground.model.AddPasseToSessionRequest(UUID.randomUUID())));
    }

    @Test
    void addPasseToSession_passeNotFound_throws() {
        UUID passeId = UUID.randomUUID();
        when(passeRepository.findById(passeId)).thenReturn(Optional.empty());
        assertThrows(PasseNotFoundException.class,
                () -> service.addPasseToSession(
                        sessionId, new ch.jp.smartground.model.AddPasseToSessionRequest(passeId)));
    }

    @Test
    void removePasseFromSession_existingPasse_removesIt() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        givenPassenOnSession(a, b);

        service.removePasseFromSession(sessionId, a);

        PasseSnapshot[] saved = new ObjectMapper().readValue(session.getProgramSnapshots(), PasseSnapshot[].class);
        assertEquals(1, saved.length);
        assertEquals(b.toString(), saved[0].id);
    }

    @Test
    void removePasseFromSession_notOnSession_throwsNotFound() throws Exception {
        givenPassenOnSession(UUID.randomUUID());
        assertThrows(PasseNotFoundException.class,
                () -> service.removePasseFromSession(sessionId, UUID.randomUUID()));
    }

    @Test
    void removePasseFromSession_notSetupStatus_isRejected() throws Exception {
        UUID a = UUID.randomUUID();
        givenPassenOnSession(a);
        session.setStatus(SessionStatus.ACTIVE);

        assertThrows(SessionStatusTransitionException.class,
                () -> service.removePasseFromSession(sessionId, a));
    }
}

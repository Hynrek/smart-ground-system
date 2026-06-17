package ch.jp.shooting.repository;

import ch.jp.shooting.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Hinweis: Spring Boot 4 hat den @DataJpaTest-Slice in ein separates Modul
// (spring-boot-data-jpa-test) ausgelagert, das in dieser Offline-Umgebung nicht
// verfuegbar ist. Daher wird der Persistenz-Test ueber den vollen Kontext (@SpringBootTest)
// mit H2-Profil gefahren.
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class CompetitionTiebreakerRepositoryTest {

    @Autowired CompetitionTiebreakerRepository repo;
    @Autowired LiveSessionRepository sessionRepo;

    @Test
    void persistsAndFindsBySessionAndTieGroup() {
        LiveSession session = new LiveSession(SessionType.COMPETITION, SessionStatus.PRE_COMPLETE);
        session.setName("Stechen-Test"); // LiveSession.name ist nicht-nullbar
        session = sessionRepo.save(session);
        UUID tieGroup = UUID.randomUUID();

        CompetitionTiebreaker tb = new CompetitionTiebreaker(session, tieGroup, 1, 1);
        tb.setTemplateType("passe");
        tb.setTemplateId(UUID.randomUUID());
        tb.setTemplateName("Stech-Passe");
        tb.setParticipantsJson("[]");
        tb.setStatus(TiebreakerStatus.PENDING);
        repo.save(tb);

        List<CompetitionTiebreaker> bySession = repo.findBySessionId(session.getId());
        assertEquals(1, bySession.size());

        List<CompetitionTiebreaker> byGroup =
                repo.findBySessionIdAndTieGroupIdOrderByRoundNumberAsc(session.getId(), tieGroup);
        assertEquals(1, byGroup.size());
        assertEquals(TiebreakerStatus.PENDING, byGroup.get(0).getStatus());
    }
}

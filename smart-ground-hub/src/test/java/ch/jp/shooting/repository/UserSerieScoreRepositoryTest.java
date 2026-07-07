package ch.jp.shooting.repository;

import ch.jp.shooting.model.UserSerieScore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Hinweis: Spring Boot 4 hat den @DataJpaTest-Slice in ein separates Modul
// (spring-boot-data-jpa-test) ausgelagert, das in dieser Offline-Umgebung nicht
// verfuegbar ist. Daher wird der Persistenz-Test ueber den vollen Kontext (@SpringBootTest)
// mit H2-Profil gefahren.
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class UserSerieScoreRepositoryTest {

    @Autowired UserSerieScoreRepository repository;

    private UserSerieScore score(UUID userId, UUID sourceId, String context, int points, Instant completedAt) {
        var s = new UserSerieScore();
        s.setUserId(userId);
        s.setSourceId(sourceId);
        s.setContext(context);
        s.setTotalPoints(points);
        s.setMaxPoints(10);
        s.setSerieId(UUID.randomUUID());
        s.setSerieAlias("Serie A");
        s.setCompletedAt(completedAt);
        return s;
    }

    @Test
    void uniqueConstraint_rejectsDuplicateSourceAndUser() {
        var userId = UUID.randomUUID();
        var sourceId = UUID.randomUUID();
        repository.saveAndFlush(score(userId, sourceId, "TRAINING", 5, Instant.now()));
        assertThrows(DataIntegrityViolationException.class,
            () -> repository.saveAndFlush(score(userId, sourceId, "TRAINING", 7, Instant.now())));
    }

    @Test
    void findFiltered_appliesNullableFilters() {
        var userId = UUID.randomUUID();
        var now = Instant.now();
        repository.save(score(userId, UUID.randomUUID(), "TRAINING", 5, now.minusSeconds(3600)));
        repository.save(score(userId, UUID.randomUUID(), "COMPETITION", 8, now));
        repository.save(score(UUID.randomUUID(), UUID.randomUUID(), "TRAINING", 9, now));

        // no filters: both rows of this user, newest first
        var all = repository.findFiltered(userId, null, null, null,
            Instant.EPOCH, now.plusSeconds(60), PageRequest.of(0, 20));
        assertEquals(2, all.getTotalElements());
        assertEquals("COMPETITION", all.getContent().get(0).getContext());

        // context filter
        var training = repository.findFiltered(userId, "TRAINING", null, null,
            Instant.EPOCH, now.plusSeconds(60), PageRequest.of(0, 20));
        assertEquals(1, training.getTotalElements());

        // time window excludes the older row
        var recent = repository.findFiltered(userId, null, null, null,
            now.minusSeconds(60), now.plusSeconds(60), PageRequest.of(0, 20));
        assertEquals(1, recent.getTotalElements());
    }

    @Test
    void findForLeaderboard_filtersByContextAndFrom() {
        var now = Instant.now();
        var s1 = score(UUID.randomUUID(), UUID.randomUUID(), "TRAINING", 5, now);
        s1.setRangeId(UUID.randomUUID());
        repository.save(s1);
        repository.save(score(UUID.randomUUID(), UUID.randomUUID(), "COMPETITION", 8, now));

        assertEquals(1, repository.findForLeaderboard("TRAINING", null, null, null, Instant.EPOCH).size());
        assertEquals(2, repository.findForLeaderboard(null, null, null, null, Instant.EPOCH).size());
        assertEquals(0, repository.findForLeaderboard(null, null, null, null, now.plusSeconds(60)).size());
    }

    @Test
    void findByUserIdAndKind_returnsOnlyThatKind() {
        var userId = java.util.UUID.randomUUID();
        var now = java.time.Instant.now();
        var serie = score(userId, java.util.UUID.randomUUID(), "TRAINING", 5, now);
        serie.setKind("SERIE");
        var passe = score(userId, java.util.UUID.randomUUID(), "TRAINING", 7, now.minusSeconds(60));
        passe.setKind("PASSE");
        repository.save(serie);
        repository.save(passe);

        var serien = repository.findByUserIdAndKindOrderByCompletedAtDesc(userId, "SERIE");
        assertEquals(1, serien.size());
        assertEquals("SERIE", serien.get(0).getKind());
    }
}

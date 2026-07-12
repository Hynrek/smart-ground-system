package ch.jp.shooting.repository;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Hinweis: Spring Boot 4 hat den @DataJpaTest-Slice in ein separates Modul
// (spring-boot-data-jpa-test) ausgelagert, das in dieser Offline-Umgebung nicht
// verfuegbar ist. Daher wird der Persistenz-Test ueber den vollen Kontext (@SpringBootTest)
// mit H2-Profil gefahren.
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class OtaReleaseRepositoryTest {

    @Autowired
    OtaReleaseRepository repository;

    @Test
    void savesAndFindsByTypeAndVersion() {
        OtaRelease r = new OtaRelease();
        r.setType(OtaType.APP);
        r.setVersion("0.7");
        r.setSha256("ab".repeat(32));
        r.setSizeBytes(123L);
        r.setCreatedAt(Instant.now());
        repository.save(r);

        Optional<OtaRelease> found = repository.findByTypeAndVersion(OtaType.APP, "0.7");
        assertThat(found).isPresent();
        assertThat(found.get().getSha256()).isEqualTo("ab".repeat(32));
    }
}

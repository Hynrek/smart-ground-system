package ch.jp.shooting.repository;

import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class SerieRepositorySyncTest {

    @Autowired SerieRepository serieRepository;
    @Autowired UserRepository userRepository;

    private User persistOwner() {
        var u = new User("owner-" + UUID.randomUUID() + "@example.com", "Owner", "Test");
        u.setUsername("owner-" + UUID.randomUUID());
        u.setPasswordHash("x");
        return userRepository.save(u);
    }

    private Serie persistSerie(User owner, String name, boolean deleted) {
        var s = new Serie();
        s.setName(name);
        s.setOwnership("user");
        s.setStepsJson("[]");
        s.setOwner(owner);
        s.setDeleted(deleted);
        return serieRepository.save(s);
    }

    @Test
    void findForSyncFrom_includesTombstones_whileFindersExcludeThem() {
        var owner = persistOwner();
        persistSerie(owner, "alive", false);
        var dead = persistSerie(owner, "dead", true);

        // Normal finder honours @SQLRestriction — the tombstone is invisible.
        List<Serie> visible = serieRepository.findByOwner(owner);
        assertThat(visible).extracting(Serie::getName).containsExactly("alive");

        // Native sync query bypasses @SQLRestriction — the tombstone comes back.
        List<Serie> synced = serieRepository.findForSyncFrom(Instant.EPOCH, 500);
        assertThat(synced).extracting(Serie::getName).contains("alive", "dead");
        assertThat(synced).filteredOn(s -> s.getId().equals(dead.getId()))
            .singleElement().matches(Serie::isDeleted);
    }

    @Test
    void findForSyncFrom_respectsLimit_andAscendingOrder() {
        var owner = persistOwner();
        for (int i = 0; i < 3; i++) {
            persistSerie(owner, "s" + i, false);
        }
        List<Serie> page = serieRepository.findForSyncFrom(Instant.EPOCH, 2);
        assertThat(page).hasSize(2);
        assertThat(page.get(0).getUpdatedAt())
            .isBeforeOrEqualTo(page.get(1).getUpdatedAt());
    }
}

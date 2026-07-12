package ch.jp.shooting.node.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SyncedSerieRepositoryTest {

    @Autowired SyncedSerieRepository repository;

    private SyncedSerie row(UUID id, String name, boolean deleted) {
        var s = new SyncedSerie();
        s.setId(id);
        s.setName(name);
        s.setOwnership("user");
        s.setOwnerId(UUID.randomUUID());
        s.setStepsJson("[]");
        s.setPublished(false);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        s.setDeleted(deleted);
        return s;
    }

    @Test
    void save_withAssignedId_upsertsInPlace() {
        var id = UUID.randomUUID();
        repository.save(row(id, "first", false));
        // second save with the SAME id updates, not inserts a duplicate
        repository.save(row(id, "second", true));

        var found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("second");
        assertThat(found.get().isDeleted()).isTrue();
        assertThat(repository.count()).isEqualTo(1);
    }
}

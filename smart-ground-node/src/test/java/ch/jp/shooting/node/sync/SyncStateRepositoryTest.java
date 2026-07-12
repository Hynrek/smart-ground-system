package ch.jp.shooting.node.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb-sync-state;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class SyncStateRepositoryTest {

    @Autowired SyncStateRepository repository;

    @Test
    void cursor_roundTripsById() {
        var cursor = Instant.parse("2026-07-12T10:00:00Z");
        var state = new SyncState();
        state.setEntity("serie");
        state.setCursor(cursor);
        repository.save(state);

        var found = repository.findById("serie");
        assertThat(found).isPresent();
        assertThat(found.get().getCursor()).isEqualTo(cursor);
    }
}

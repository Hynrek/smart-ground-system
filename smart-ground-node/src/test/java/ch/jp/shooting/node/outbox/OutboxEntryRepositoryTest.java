package ch.jp.shooting.node.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OutboxEntryRepositoryTest {

    @Autowired OutboxEntryRepository repository;

    private OutboxEntry entry(String entityType, String status) {
        var e = new OutboxEntry();
        e.setEntityType(entityType);
        e.setEntityId(UUID.randomUUID());
        e.setPayloadJson("{}");
        e.setStatus(status);
        e.setCreatedAt(Instant.now());
        e.setAttempts(0);
        return repository.save(e);
    }

    @Test
    void findByStatus_returnsOnlyMatchingRows_inInsertionOrder_acrossEntityTypes() {
        var first = entry("SERIE", "PENDING");
        var second = entry("PLAY_INSTANCE", "PENDING");
        entry("SERIE", "SENT"); // must not appear

        var pending = repository.findByStatusOrderBySequenceAsc("PENDING");

        assertThat(pending).extracting(OutboxEntry::getSequence)
                .containsExactly(first.getSequence(), second.getSequence());
    }

    @Test
    void findByEntityType_returnsOnlyMatchingRows_inInsertionOrder() {
        var s1 = entry("SERIE", "PENDING");
        entry("PLAY_INSTANCE", "PENDING");
        var s2 = entry("SERIE", "FAILED");

        var series = repository.findByEntityTypeOrderBySequenceAsc("SERIE");

        assertThat(series).extracting(OutboxEntry::getSequence)
                .containsExactly(s1.getSequence(), s2.getSequence());
    }
}

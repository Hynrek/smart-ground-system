package ch.jp.shooting.node.outbox;

import ch.jp.smartground.model.SerieOutboxItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SerieOutboxServiceTest {

    @Autowired SerieOutboxService service;
    @Autowired OutboxEntryRepository outboxRepository;
    @Autowired @Qualifier("outboxObjectMapper") ObjectMapper objectMapper;

    @Test
    void createSerieLocally_enqueuesPendingSerieEntry_neverCallsHub() throws Exception {
        var ownerId = UUID.randomUUID();

        UUID id = service.createSerieLocally("Offline-Serie", "user", null, ownerId, "[]");

        var pending = outboxRepository.findByStatusOrderBySequenceAsc("PENDING");
        assertThat(pending).hasSize(1);
        var entry = pending.get(0);
        assertThat(entry.getEntityType()).isEqualTo("SERIE");
        assertThat(entry.getEntityId()).isEqualTo(id);

        var item = objectMapper.readValue(entry.getPayloadJson(), SerieOutboxItem.class);
        assertThat(item.getId()).isEqualTo(id);
        assertThat(item.getName()).isEqualTo("Offline-Serie");
        assertThat(item.getOwnerId()).isEqualTo(ownerId);
    }
}

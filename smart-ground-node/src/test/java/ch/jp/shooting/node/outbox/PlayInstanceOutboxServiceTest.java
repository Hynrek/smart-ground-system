package ch.jp.shooting.node.outbox;

import ch.jp.smartground.model.PlayInstanceOutboxItem;
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
class PlayInstanceOutboxServiceTest {

    @Autowired PlayInstanceOutboxService service;
    @Autowired OutboxEntryRepository outboxRepository;
    @Autowired @Qualifier("outboxObjectMapper") ObjectMapper objectMapper;

    @Test
    void startSerieInstanceLocally_enqueuesPendingPlayInstanceEntry_referencingTemplate() throws Exception {
        var templateSerieId = UUID.randomUUID();
        var ownerId = UUID.randomUUID();

        UUID instanceId = service.startSerieInstanceLocally(templateSerieId, "Offline-Serie", ownerId, "[]");

        var pending = outboxRepository.findByStatusOrderBySequenceAsc("PENDING");
        assertThat(pending).hasSize(1);
        var entry = pending.get(0);
        assertThat(entry.getEntityType()).isEqualTo("PLAY_INSTANCE");
        assertThat(entry.getEntityId()).isEqualTo(instanceId);

        var item = objectMapper.readValue(entry.getPayloadJson(), PlayInstanceOutboxItem.class);
        assertThat(item.getInstanceId()).isEqualTo(instanceId);
        assertThat(item.getTemplateId()).isEqualTo(templateSerieId);
        assertThat(item.getType()).isEqualTo("serie");
    }
}

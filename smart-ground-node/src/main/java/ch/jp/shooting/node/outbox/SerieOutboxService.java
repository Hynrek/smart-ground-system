package ch.jp.shooting.node.outbox;

import ch.jp.smartground.model.SerieOutboxItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Node-lokale Serie-Erstellung (Teilprojekt #3): schreibt sofort in die Outbox, wartet nie auf den Hub. */
@Service
public class SerieOutboxService {

    private final OutboxEntryRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SerieOutboxService(OutboxEntryRepository outboxRepository,
                               @Qualifier("outboxObjectMapper") ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID createSerieLocally(String name, String ownership, UUID rangeId, UUID ownerId, String stepsJson) {
        UUID id = UUID.randomUUID();
        var item = new SerieOutboxItem()
                .id(id)
                .name(name)
                .ownership(ownership)
                .rangeId(rangeId)
                .ownerId(ownerId)
                .stepsJson(stepsJson)
                .published(false)
                .baseVersion(null);

        var entry = new OutboxEntry();
        entry.setEntityType("SERIE");
        entry.setEntityId(id);
        entry.setPayloadJson(writeJson(item));
        entry.setStatus("PENDING");
        entry.setCreatedAt(Instant.now());
        entry.setAttempts(0);
        outboxRepository.save(entry);
        return id;
    }

    private String writeJson(SerieOutboxItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize SerieOutboxItem", e);
        }
    }
}

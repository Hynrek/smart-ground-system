package ch.jp.shooting.node.outbox;

import ch.jp.smartground.model.PlayInstanceOutboxItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Node-lokales "sofort schiessen" (Teilprojekt #3): startet eine PlayInstance ohne den Hub zu brauchen. */
@Service
public class PlayInstanceOutboxService {

    private final OutboxEntryRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PlayInstanceOutboxService(OutboxEntryRepository outboxRepository,
                                      @Qualifier("outboxObjectMapper") ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID startSerieInstanceLocally(UUID templateSerieId, String templateName, UUID ownerId, String playersJson) {
        UUID id = UUID.randomUUID();
        var item = new PlayInstanceOutboxItem()
                .instanceId(id)
                .type("serie")
                .templateId(templateSerieId)
                .templateName(templateName)
                .ownerId(ownerId)
                .playersJson(playersJson)
                .stateJson("[]")
                .status("active")
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(null);

        var entry = new OutboxEntry();
        entry.setEntityType("PLAY_INSTANCE");
        entry.setEntityId(id);
        entry.setPayloadJson(writeJson(item));
        entry.setStatus("PENDING");
        entry.setCreatedAt(Instant.now());
        entry.setAttempts(0);
        outboxRepository.save(entry);
        return id;
    }

    private String writeJson(PlayInstanceOutboxItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize PlayInstanceOutboxItem", e);
        }
    }
}

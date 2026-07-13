package ch.jp.shooting.node.outbox;

import ch.jp.shooting.node.hub.HubClient;
import ch.jp.smartground.model.PlayInstanceOutboxItem;
import ch.jp.smartground.model.PlayInstanceOutboxResult;
import ch.jp.smartground.model.SerieOutboxItem;
import ch.jp.smartground.model.SerieOutboxResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIFO/Single-Flight-Drain der Outbox (hub-api, aufwärts). Stoppt beim ersten nicht
 * akzeptierten Eintrag: ein Netzwerkfehler bleibt PENDING (der nächste Tick versucht es
 * erneut), eine Hub-Ablehnung wird FAILED und blockiert die Warteschlange bewusst — ein
 * späterer Eintrag könnte auf den blockierten verweisen (z. B. eine PlayInstance auf ihre
 * Serie), und ihn zu überspringen würde genau die Kausalitätsgarantie brechen, die die
 * gemeinsame Sequenz in OutboxEntry eigentlich gibt.
 */
@Service
public class OutboxDrainService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainService.class);

    private final OutboxEntryRepository outboxRepository;
    private final HubClient hubClient;
    private final ObjectMapper objectMapper;

    public OutboxDrainService(OutboxEntryRepository outboxRepository, HubClient hubClient,
                               @Qualifier("outboxObjectMapper") ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.hubClient = hubClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int drainOnce() {
        int sent = 0;
        for (OutboxEntry entry : outboxRepository.findByStatusOrderBySequenceAsc("PENDING")) {
            entry.setAttempts(entry.getAttempts() + 1);
            boolean advanced = switch (entry.getEntityType()) {
                case "SERIE" -> pushSerie(entry);
                case "PLAY_INSTANCE" -> pushPlayInstance(entry);
                default -> throw new IllegalStateException("unknown outbox entityType: " + entry.getEntityType());
            };
            outboxRepository.save(entry);
            if (!advanced) {
                break;
            }
            sent++;
        }
        return sent;
    }

    private boolean pushSerie(OutboxEntry entry) {
        SerieOutboxItem item = readJson(entry.getPayloadJson(), SerieOutboxItem.class);
        try {
            SerieOutboxResult result = hubClient.pushSerieOutboxItem(item);
            if (result.getStatus() == SerieOutboxResult.StatusEnum.ACCEPTED) {
                entry.setStatus("SENT");
                return true;
            }
            entry.setStatus("FAILED");
            entry.setLastError(result.getStatus() + (result.getMessage() != null ? ": " + result.getMessage() : ""));
            return false;
        } catch (RuntimeException e) {
            log.debug("Outbox push (SERIE {}) failed, staying PENDING: {}", entry.getEntityId(), e.getMessage());
            entry.setLastError(e.getMessage());
            return false;
        }
    }

    private boolean pushPlayInstance(OutboxEntry entry) {
        PlayInstanceOutboxItem item = readJson(entry.getPayloadJson(), PlayInstanceOutboxItem.class);
        try {
            PlayInstanceOutboxResult result = hubClient.pushPlayInstanceOutboxItem(item);
            if (result.getStatus() == PlayInstanceOutboxResult.StatusEnum.ACCEPTED) {
                entry.setStatus("SENT");
                return true;
            }
            entry.setStatus("FAILED");
            entry.setLastError(result.getStatus() + (result.getMessage() != null ? ": " + result.getMessage() : ""));
            return false;
        } catch (RuntimeException e) {
            log.debug("Outbox push (PLAY_INSTANCE {}) failed, staying PENDING: {}", entry.getEntityId(), e.getMessage());
            entry.setLastError(e.getMessage());
            return false;
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corrupt outbox payload", e);
        }
    }
}

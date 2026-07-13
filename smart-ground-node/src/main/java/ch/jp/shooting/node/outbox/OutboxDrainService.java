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
                default -> {
                    // Unknown entityType can never become recognized on retry -> unrecoverable, same as corrupt JSON below.
                    entry.setStatus("FAILED");
                    entry.setLastError("unknown outbox entityType: " + entry.getEntityType());
                    yield false;
                }
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
        try {
            SerieOutboxItem item = readJson(entry.getPayloadJson(), SerieOutboxItem.class);
            SerieOutboxResult result = hubClient.pushSerieOutboxItem(item);
            if (result.getStatus() == SerieOutboxResult.StatusEnum.ACCEPTED) {
                entry.setStatus("SENT");
                return true;
            }
            entry.setStatus("FAILED");
            entry.setLastError(result.getStatus() + (result.getMessage() != null ? ": " + result.getMessage() : ""));
            return false;
        } catch (CorruptPayloadException e) {
            // Corrupt JSON on disk won't parse differently on the next tick -> unrecoverable, mark FAILED (not PENDING).
            log.warn("Outbox entry (SERIE {}) has corrupt payload, marking FAILED: {}", entry.getEntityId(), e.getMessage());
            entry.setStatus("FAILED");
            entry.setLastError(e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.debug("Outbox push (SERIE {}) failed, staying PENDING: {}", entry.getEntityId(), e.getMessage());
            entry.setLastError(e.getMessage());
            return false;
        }
    }

    private boolean pushPlayInstance(OutboxEntry entry) {
        try {
            PlayInstanceOutboxItem item = readJson(entry.getPayloadJson(), PlayInstanceOutboxItem.class);
            PlayInstanceOutboxResult result = hubClient.pushPlayInstanceOutboxItem(item);
            if (result.getStatus() == PlayInstanceOutboxResult.StatusEnum.ACCEPTED) {
                entry.setStatus("SENT");
                return true;
            }
            entry.setStatus("FAILED");
            entry.setLastError(result.getStatus() + (result.getMessage() != null ? ": " + result.getMessage() : ""));
            return false;
        } catch (CorruptPayloadException e) {
            // Corrupt JSON on disk won't parse differently on the next tick -> unrecoverable, mark FAILED (not PENDING).
            log.warn("Outbox entry (PLAY_INSTANCE {}) has corrupt payload, marking FAILED: {}", entry.getEntityId(), e.getMessage());
            entry.setStatus("FAILED");
            entry.setLastError(e.getMessage());
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
            throw new CorruptPayloadException("corrupt outbox payload", e);
        }
    }

    /** Internal marker for a deserialization failure, caught locally in pushSerie/pushPlayInstance and never rethrown. */
    private static final class CorruptPayloadException extends RuntimeException {
        CorruptPayloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

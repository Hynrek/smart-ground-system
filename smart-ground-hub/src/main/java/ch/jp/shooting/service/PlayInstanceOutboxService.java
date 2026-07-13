package ch.jp.shooting.service;

import ch.jp.shooting.repository.PlayInstanceRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.smartground.model.PlayInstanceOutboxItem;
import ch.jp.smartground.model.PlayInstanceOutboxResult;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Push-Endpunkt der Outbox (hub-api, aufwärts) für PlayInstance-Resultate. Append-only:
 * unbekannte instanceId → CREATE (natives INSERT — siehe PlayInstanceRepository); bekannte
 * instanceId → idempotenter No-op. Resultate werden nicht editiert (Schreibklasse
 * "Resultate" in der Spec) — kein Konfliktbegriff, kein Update-Zweig.
 */
@Service
public class PlayInstanceOutboxService {

    private final PlayInstanceRepository playInstanceRepository;
    private final UserRepository userRepository;

    public PlayInstanceOutboxService(PlayInstanceRepository playInstanceRepository, UserRepository userRepository) {
        this.playInstanceRepository = playInstanceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PlayInstanceOutboxResult push(PlayInstanceOutboxItem item) {
        if (playInstanceRepository.existsById(item.getInstanceId())) {
            return new PlayInstanceOutboxResult().status(PlayInstanceOutboxResult.StatusEnum.ACCEPTED);
        }

        var owner = userRepository.findById(item.getOwnerId());
        if (owner.isEmpty()) {
            return new PlayInstanceOutboxResult()
                    .status(PlayInstanceOutboxResult.StatusEnum.REJECTED)
                    .message("owner not found: " + item.getOwnerId());
        }

        OffsetDateTime completedAt = unwrapCompletedAt(item);
        playInstanceRepository.insertOutboxCreatedPlayInstance(
                item.getInstanceId(), item.getType(), item.getTemplateId(), item.getTemplateName(),
                item.getStatus(), owner.get().getId(), item.getPlayersJson(), item.getStateJson(),
                item.getStartedAt().toInstant(), completedAt != null ? completedAt.toInstant() : null);
        return new PlayInstanceOutboxResult().status(PlayInstanceOutboxResult.StatusEnum.ACCEPTED);
    }

    private static OffsetDateTime unwrapCompletedAt(PlayInstanceOutboxItem item) {
        JsonNullable<OffsetDateTime> value = item.getCompletedAt();
        return (value != null && value.isPresent()) ? value.get() : null;
    }
}

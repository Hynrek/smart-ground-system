package ch.jp.shooting.service;

import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.smartground.model.SerieOutboxItem;
import ch.jp.smartground.model.SerieOutboxResult;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Push-Endpunkt der Outbox (hub-api, aufwärts) für Serie. Die Node-vergebene id ist der
 * Idempotenz-Schlüssel: unbekannte id → CREATE (natives INSERT, s. u.); bekannte id mit
 * identischem Inhalt → idempotenter No-op (schützt vor doppeltem Upload); bekannte id mit
 * abweichendem Inhalt und veralteter base_version → CONFLICT, der Hub behält seine Version.
 * Auth folgt mit #6.
 *
 * CREATE geht bewusst NICHT über serieRepository.save(new Serie()): Hibernate 7.2.7 wirft für
 * ein @GeneratedValue(GenerationType.UUID)-Entity mit einer client-vergebenen, in der DB noch
 * unbekannten id eine StaleObjectStateException statt eines stillen INSERT-Fallbacks — merge()
 * geht davon aus, dass eine nicht-null id bereits existiert. Der native INSERT
 * (SerieRepository.insertOutboxCreatedSerie) umgeht das, genau wie die native Sync-Query aus #2
 * bewusst @SQLRestriction umgeht. Der UPDATE-Zweig ist davon nicht betroffen: findById liefert
 * eine Zeile, die Hibernate bereits kennt, also läuft save()/merge() dort normal.
 */
@Service
public class SerieOutboxService {

    private final SerieRepository serieRepository;
    private final UserRepository userRepository;
    private final RangeRepository rangeRepository;

    public SerieOutboxService(SerieRepository serieRepository, UserRepository userRepository,
                               RangeRepository rangeRepository) {
        this.serieRepository = serieRepository;
        this.userRepository = userRepository;
        this.rangeRepository = rangeRepository;
    }

    @Transactional
    public SerieOutboxResult push(SerieOutboxItem item) {
        var owner = userRepository.findById(item.getOwnerId());
        if (owner.isEmpty()) {
            return new SerieOutboxResult()
                    .status(SerieOutboxResult.StatusEnum.REJECTED)
                    .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .message("owner not found: " + item.getOwnerId());
        }

        var existing = serieRepository.findById(item.getId());
        if (existing.isEmpty()) {
            UUID requestedRangeId = unwrapUuid(item.getRangeId());
            UUID rangeId = requestedRangeId != null && rangeRepository.existsById(requestedRangeId)
                    ? requestedRangeId : null;
            Instant now = Instant.now();
            serieRepository.insertOutboxCreatedSerie(
                    item.getId(), item.getName(), item.getOwnership(), rangeId, owner.get().getId(),
                    item.getStepsJson(), now, now, Boolean.TRUE.equals(item.getPublished()));
            return accepted(now);
        }

        Serie current = existing.get();
        if (contentMatches(current, item)) {
            // Idempotenter Retry desselben Pushs (z. B. nach verlorenem Ack) — keine
            // Änderung, kein falscher Konflikt. "Doppelter Upload erzeugt eine Zeile."
            return accepted(current.getUpdatedAt());
        }

        Instant baseVersion = unwrapInstant(item.getBaseVersion());
        if (baseVersion == null || !baseVersion.equals(current.getUpdatedAt())) {
            return new SerieOutboxResult()
                    .status(SerieOutboxResult.StatusEnum.CONFLICT)
                    .updatedAt(OffsetDateTime.ofInstant(current.getUpdatedAt(), ZoneOffset.UTC))
                    .message("base_version mismatch");
        }

        applyFields(current, item, owner.get());
        Serie saved = serieRepository.save(current); // vorhandene Zeile: merge() läuft normal, kein Generator involviert
        return accepted(saved.getUpdatedAt());
    }

    private void applyFields(Serie serie, SerieOutboxItem item, User owner) {
        serie.setName(item.getName());
        serie.setOwnership(item.getOwnership());
        serie.setOwner(owner);
        serie.setStepsJson(item.getStepsJson());
        serie.setPublished(Boolean.TRUE.equals(item.getPublished()));
        UUID rangeId = unwrapUuid(item.getRangeId());
        serie.setRange(rangeId != null ? rangeRepository.findById(rangeId).orElse(null) : null);
    }

    private boolean contentMatches(Serie current, SerieOutboxItem item) {
        UUID currentRangeId = current.getRange() != null ? current.getRange().getId() : null;
        return Objects.equals(current.getName(), item.getName())
                && Objects.equals(current.getOwnership(), item.getOwnership())
                && Objects.equals(current.getOwner().getId(), item.getOwnerId())
                && Objects.equals(current.getStepsJson(), item.getStepsJson())
                && current.isPublished() == Boolean.TRUE.equals(item.getPublished())
                && Objects.equals(currentRangeId, unwrapUuid(item.getRangeId()));
    }

    private SerieOutboxResult accepted(Instant updatedAt) {
        return new SerieOutboxResult()
                .status(SerieOutboxResult.StatusEnum.ACCEPTED)
                .updatedAt(OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
    }

    private static UUID unwrapUuid(JsonNullable<UUID> value) {
        return (value != null && value.isPresent()) ? value.get() : null;
    }

    private static Instant unwrapInstant(JsonNullable<OffsetDateTime> value) {
        return (value != null && value.isPresent() && value.get() != null) ? value.get().toInstant() : null;
    }
}

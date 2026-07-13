package ch.jp.shooting.repository;

import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@NullMarked
public interface PlayInstanceRepository extends JpaRepository<PlayInstance, UUID> {
    List<PlayInstance> findByOwnerAndStatus(User owner, String status);

    /** Für Range-Live-View: alle aktiven Instanzen eines Owners */
    List<PlayInstance> findByOwnerAndStatusIn(User owner, List<String> statuses);

    /** Ergebnisliste: abgeschlossene Instanzen, neueste zuerst */
    Page<PlayInstance> findByOwnerAndStatusOrderByCompletedAtDesc(
        User owner, String status, Pageable pageable);

    /**
     * Natives INSERT für die Outbox (#3, aufwärts) — gleicher Grund wie
     * SerieRepository.insertOutboxCreatedSerie: @GeneratedValue(GenerationType.UUID) +
     * client-vergebene, der DB unbekannte id wirft über merge() eine
     * StaleObjectStateException statt einen INSERT-Fallback auszuführen. PlayInstance ist
     * append-only (Schreibklasse "Resultate"), also ist das der einzige Schreibpfad.
     */
    @Modifying
    @Query(value = "INSERT INTO play_instances (instance_id, type, template_id, template_name, status, owner_id, players_json, state_json, started_at, completed_at) "
                 + "VALUES (:instanceId, :type, :templateId, :templateName, :status, :ownerId, :playersJson, :stateJson, :startedAt, :completedAt)",
           nativeQuery = true)
    void insertOutboxCreatedPlayInstance(@Param("instanceId") UUID instanceId, @Param("type") String type,
                                          @Param("templateId") UUID templateId, @Param("templateName") String templateName,
                                          @Param("status") String status, @Param("ownerId") UUID ownerId,
                                          @Param("playersJson") String playersJson, @Param("stateJson") String stateJson,
                                          @Param("startedAt") Instant startedAt, @Param("completedAt") @Nullable Instant completedAt);
}

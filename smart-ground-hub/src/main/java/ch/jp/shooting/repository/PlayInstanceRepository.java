package ch.jp.shooting.repository;

import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Vorfilter per LIKE auf die User-UUID im JSON (H2- und PostgreSQL-kompatibel);
    // die echte Teilnahme-Prüfung macht der Service beim Parsen der Block-Resultate.
    @Query("select p from PlayInstance p where p.status = 'completed' "
        + "and (p.playersJson like concat('%', :userId, '%') or p.stateJson like concat('%', :userId, '%')) "
        + "order by p.completedAt desc")
    List<PlayInstance> findCompletedByParticipantUserId(@Param("userId") String userId);
}

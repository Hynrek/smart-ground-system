package ch.jp.shooting.repository;

import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}

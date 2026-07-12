package ch.jp.shooting.repository;

import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@NullMarked
public interface SerieRepository extends JpaRepository<Serie, UUID> {
    /** Eigene Serien ODER platzweit veröffentlichte (für Admins) */
    List<Serie> findByOwnerOrOwnership(User owner, String ownership);

    /** Eigene Serien ODER publizierte Platz-Serien (für reguläre Nutzer) */
    @Query("SELECT s FROM Serie s WHERE s.owner = :owner OR (s.ownership = 'range' AND s.published = true)")
    List<Serie> findByOwnerOrPublishedRange(@Param("owner") User owner);

    /** Eigene Serien eines Besitzers */
    List<Serie> findByOwner(User owner);

    /** Nur Serien mit ownership='range' */
    List<Serie> findByOwnership(String ownership);

    /** Nur Serien mit ownership='range' und gesetztem published-Flag */
    List<Serie> findByOwnershipAndPublished(String ownership, boolean published);

    /** Serien nach Platz-Zuordnung */
    List<Serie> findByRange_Id(UUID rangeId);

    /** Kombiniert: Besitzer-Filter + Platz-Filter */
    List<Serie> findByOwnerAndRange_Id(User owner, UUID rangeId);

    /**
     * Sync-Cursor-Query (hub-api, abwärts). NATIV, um @SQLRestriction("deleted = false")
     * bewusst zu umgehen: der Node muss Grabsteine (deleted=true) sehen, sonst erfährt er
     * nie von Löschungen. Inklusive untere Schranke (updated_at >= cursor) + idempotenter
     * Upsert am Node → doppelt gelieferte Grenzzeilen sind harmlos. Ordnung (updated_at, id)
     * macht den Cursor stabil. LIMIT läuft auf H2 wie PostgreSQL.
     */
    @Query(value = "SELECT * FROM serien WHERE updated_at >= :updatedAfter "
                 + "ORDER BY updated_at ASC, id ASC LIMIT :limit", nativeQuery = true)
    List<Serie> findForSyncFrom(@Param("updatedAfter") Instant updatedAfter, @Param("limit") int limit);
}

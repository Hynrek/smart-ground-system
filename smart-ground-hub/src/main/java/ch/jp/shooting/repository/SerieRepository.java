package ch.jp.shooting.repository;

import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

@NullMarked
public interface SerieRepository extends JpaRepository<Serie, UUID> {
    /** Eigene Serien ODER platzweit veröffentlichte */
    List<Serie> findByOwnerOrOwnership(User owner, String ownership);

    /** Eigene Serien eines Besitzers */
    List<Serie> findByOwner(User owner);

    /** Nur Serien mit ownership='range' */
    List<Serie> findByOwnership(String ownership);

    /** Serien nach Platz-Zuordnung */
    List<Serie> findByRange_Id(UUID rangeId);

    /** Kombiniert: Besitzer-Filter + Platz-Filter */
    List<Serie> findByOwnerAndRange_Id(User owner, UUID rangeId);
}

package ch.jp.shooting.repository;

import ch.jp.shooting.model.Ablauf;
import ch.jp.shooting.model.auth.User;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

@NullMarked
public interface AblaufRepository extends JpaRepository<Ablauf, UUID> {
    /** Eigene Abläufe ODER platzweit veröffentlichte */
    List<Ablauf> findByOwnerOrOwnership(User owner, String ownership);

    /** Eigene Abläufe eines Besitzers */
    List<Ablauf> findByOwner(User owner);

    /** Nur Abläufe mit ownership='range' */
    List<Ablauf> findByOwnership(String ownership);

    /** Abläufe nach Platz-Zuordnung */
    List<Ablauf> findByRange_Id(UUID rangeId);

    /** Kombiniert: Besitzer-Filter + Platz-Filter */
    List<Ablauf> findByOwnerAndRange_Id(User owner, UUID rangeId);
}

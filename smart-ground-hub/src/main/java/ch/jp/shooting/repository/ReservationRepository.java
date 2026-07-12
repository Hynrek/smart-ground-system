package ch.jp.shooting.repository;

import ch.jp.shooting.model.Reservation;
import ch.jp.shooting.model.Reservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    Optional<Reservation> findByRangeIdAndStatus(UUID rangeId, ReservationStatus status);
    Optional<Reservation> findByUsernameAndStatus(String username, ReservationStatus status);
    List<Reservation> findByStatusAndLastActivityAtBefore(ReservationStatus status, OffsetDateTime threshold);
}

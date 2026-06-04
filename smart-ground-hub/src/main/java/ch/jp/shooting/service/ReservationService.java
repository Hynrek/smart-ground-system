package ch.jp.shooting.service;

import ch.jp.shooting.dto.ReservationDTO;
import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.exception.ForbiddenException;
import ch.jp.shooting.exception.NotFoundException;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.Reservation;
import ch.jp.shooting.model.Reservation.ReservationStatus;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.ReservationRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RangeRepository rangeRepository;

    @Value("${reservation.inactivity-timeout-minutes:10}")
    private int inactivityTimeoutMinutes;

    public ReservationService(ReservationRepository reservationRepository, RangeRepository rangeRepository) {
        this.reservationRepository = reservationRepository;
        this.rangeRepository = rangeRepository;
    }

    // Benutzer reserviert eine Range
    public ReservationDTO reserve(UUID rangeId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        // Prüfe: Range existiert
        Range range = rangeRepository.findById(rangeId)
            .orElseThrow(() -> new NotFoundException("Range not found: " + rangeId));

        // Prüfe: Range ist nicht bereits aktiv reserviert
        reservationRepository.findByRangeIdAndStatus(rangeId, ReservationStatus.ACTIVE)
            .ifPresent(existing -> {
                throw new ConflictException("Range already reserved");
            });

        // Prüfe: Benutzer hat nicht bereits eine aktive Reservierung
        reservationRepository.findByUsernameAndStatus(username, ReservationStatus.ACTIVE)
            .ifPresent(existing -> {
                throw new ConflictException("User already has an active reservation");
            });

        // Erstelle neue Reservierung
        Reservation reservation = new Reservation();
        reservation.setRange(range);
        reservation.setUsername(username);
        reservation.setStartedAt(OffsetDateTime.now());
        reservation.setLastActivityAt(OffsetDateTime.now());
        reservation.setStatus(ReservationStatus.ACTIVE);

        reservation = reservationRepository.save(reservation);
        return toDTO(reservation);
    }

    // Benutzer gibt Range frei
    public void release(UUID rangeId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        Reservation reservation = reservationRepository.findByRangeIdAndStatus(rangeId, ReservationStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("No active reservation found"));

        // Prüfe: Reservierung gehört dem Benutzer
        if (!reservation.getUsername().equals(username)) {
            throw new ForbiddenException("You do not have a reservation on this range");
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);
    }

    // Admin: Erzwingt die Aufhebung einer Reservierung
    public void forceRelease(UUID rangeId) {
        Reservation reservation = reservationRepository.findByRangeIdAndStatus(rangeId, ReservationStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("No active reservation found"));

        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);
    }

    // Holt die aktive Reservierung für eine Range (oder null)
    public ReservationDTO getActiveReservation(UUID rangeId) {
        return reservationRepository.findByRangeIdAndStatus(rangeId, ReservationStatus.ACTIVE)
            .map(this::toDTO)
            .orElse(null);
    }

    // Prüft: Darf dieser Benutzer einen Befehl auf diese Range senden?
    public boolean canUserCommandRange(UUID rangeId, String username, boolean isAdmin) {
        if (isAdmin) {
            return true; // Admin kann immer
        }

        Reservation reservation = reservationRepository.findByRangeIdAndStatus(rangeId, ReservationStatus.ACTIVE)
            .orElse(null);

        if (reservation == null) {
            return true; // Niemand hat die Range reserviert, also kann jeder Befehle senden
        }

        return reservation.getUsername().equals(username); // Nur der Besitzer der Reservierung
    }

    // Aktualisiert last_activity_at für eine Reservierung
    public void markActivity(UUID reservationId) {
        reservationRepository.findById(reservationId).ifPresent(reservation -> {
            reservation.setLastActivityAt(OffsetDateTime.now());
            reservationRepository.save(reservation);
        });
    }

    // Scheduled: Abgelaufene inaktive Reservierungen freigeben
    public void expireInactiveReservations() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(inactivityTimeoutMinutes);
        List<Reservation> inactive = reservationRepository.findByStatusAndLastActivityAtBefore(ReservationStatus.ACTIVE, threshold);

        for (Reservation reservation : inactive) {
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
        }
    }

    private ReservationDTO toDTO(Reservation reservation) {
        return new ReservationDTO(
            reservation.getId(),
            reservation.getRange().getId(),
            reservation.getUsername(),
            reservation.getStartedAt(),
            reservation.getLastActivityAt(),
            reservation.getStatus().name()
        );
    }
}

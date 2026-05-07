package ch.jp.shooting.api;

import ch.jp.shooting.dto.ReservationDTO;
import ch.jp.shooting.service.ReservationService;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@NullMarked
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // Benutzer reserviert eine Range
    @PostMapping("/ranges/{rangeId}/reserve")
    public ResponseEntity<ReservationDTO> reserve(
            @PathVariable UUID rangeId) {

        ReservationDTO reservation = reservationService.reserve(rangeId);

        return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
    }

    // Benutzer gibt Range frei
    @PostMapping("/ranges/{rangeId}/release")
    public ResponseEntity<Void> release(
            @PathVariable UUID rangeId) {

        reservationService.release(rangeId);

        return ResponseEntity.noContent().build();
    }

    // Holt aktive Reservierung für eine Range
    @GetMapping("/ranges/{rangeId}")
    public ResponseEntity<ReservationDTO> getActiveReservation(@PathVariable UUID rangeId) {
        ReservationDTO reservation = reservationService.getActiveReservation(rangeId);

        if (reservation == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(reservation);
    }

    // Admin: Erzwingt Aufhebung einer Reservierung
    @DeleteMapping("/ranges/{rangeId}")
    public ResponseEntity<Void> forceRelease(
            @PathVariable UUID rangeId,
            Authentication authentication) {

        // Prüfe Admin-Rolle
        boolean isAdmin = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(auth -> auth.equals("ROLE_ADMIN"));

        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        reservationService.forceRelease(rangeId);

        return ResponseEntity.noContent().build();
    }
}

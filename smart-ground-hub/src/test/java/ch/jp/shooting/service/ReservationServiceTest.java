package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.exception.ForbiddenException;
import ch.jp.shooting.exception.NotFoundException;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.Reservation;
import ch.jp.shooting.model.Reservation.ReservationStatus;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock RangeRepository rangeRepository;

    @InjectMocks ReservationService reservationService;

    @BeforeEach
    void setUpAuth() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("alice", null, java.util.List.of())
        );
    }

    // ── release() ──

    @Test
    void release_noActiveReservation_throwsNotFoundException() {
        when(reservationRepository.findByRangeIdAndStatus(any(), any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> reservationService.release(UUID.randomUUID()));
    }

    @Test
    void release_callerIsNotOwner_throwsForbiddenException() {
        Range range = new Range();
        Reservation reservation = new Reservation();
        reservation.setRange(range);
        reservation.setUsername("bob"); // current user is "alice"
        reservation.setStatus(ReservationStatus.ACTIVE);

        when(reservationRepository.findByRangeIdAndStatus(any(), any())).thenReturn(Optional.of(reservation));

        assertThrows(ForbiddenException.class, () -> reservationService.release(UUID.randomUUID()));
    }

    // ── reserve() ──

    @Test
    void reserve_rangeNotFound_throwsNotFoundException() {
        when(rangeRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> reservationService.reserve(UUID.randomUUID()));
    }

    @Test
    void reserve_rangeAlreadyReserved_throwsConflictException() {
        Range range = new Range();
        Reservation existing = new Reservation();
        existing.setRange(range);
        existing.setUsername("bob");
        existing.setStatus(ReservationStatus.ACTIVE);

        when(rangeRepository.findById(any())).thenReturn(Optional.of(range));
        when(reservationRepository.findByRangeIdAndStatus(any(), any())).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> reservationService.reserve(UUID.randomUUID()));
    }
}

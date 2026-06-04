package ch.jp.shooting.api;

import ch.jp.shooting.dto.ReservationDTO;
import ch.jp.shooting.service.ReservationService;
import ch.jp.smartground.api.ReservationApi;
import ch.jp.smartground.model.ReservationResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@NullMarked
public class ReservationController implements ReservationApi {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Override
    public ResponseEntity<ReservationResponse> getActiveRangeReservation(UUID id) {
        ReservationDTO dto = reservationService.getActiveReservation(id);
        if (dto == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(toResponse(dto));
    }

    @Override
    public ResponseEntity<ReservationResponse> reserveRange(UUID id) {
        ReservationDTO dto = reservationService.reserve(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(dto));
    }

    @Override
    public ResponseEntity<Void> releaseRangeReservation(UUID id) {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));

        if (isAdmin) {
            reservationService.forceRelease(id);
        } else {
            reservationService.release(id);
        }
        return ResponseEntity.noContent().build();
    }

    private ReservationResponse toResponse(ReservationDTO dto) {
        return new ReservationResponse()
                .id(dto.getId())
                .rangeId(dto.getRangeId())
                .username(dto.getUsername())
                .startedAt(dto.getStartedAt())
                .lastActivityAt(dto.getLastActivityAt())
                .status(ReservationResponse.StatusEnum.valueOf(dto.getStatus()));
    }
}

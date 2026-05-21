package ch.jp.shooting.api;

import ch.jp.shooting.service.GuestService;
import ch.jp.smartground.api.GuestApi;
import ch.jp.smartground.model.CreateGuestRequest;
import ch.jp.smartground.model.GuestResponse;
import ch.jp.smartground.model.UpdateGuestRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Implementiert GuestApi (generierte Schnittstelle)
@RestController
@NullMarked
public class GuestController implements GuestApi {

    private final GuestService guestService;

    public GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    @Override
    public ResponseEntity<List<GuestResponse>> listGuests() {
        return ResponseEntity.ok(guestService.listGuests());
    }

    @Override
    public ResponseEntity<GuestResponse> createGuest(CreateGuestRequest createGuestRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(guestService.createGuest(createGuestRequest.getDisplayName()));
    }

    @Override
    public ResponseEntity<GuestResponse> updateGuest(UUID id, UpdateGuestRequest updateGuestRequest) {
        return ResponseEntity.ok(guestService.updateGuest(id, updateGuestRequest.getDisplayName()));
    }

    @Override
    public ResponseEntity<Void> deleteGuest(UUID id) {
        guestService.deleteGuest(id);
        return ResponseEntity.noContent().build();
    }
}

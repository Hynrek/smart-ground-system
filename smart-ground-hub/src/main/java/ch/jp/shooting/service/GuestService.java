package ch.jp.shooting.service;

import ch.jp.shooting.exception.GuestNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Guest;
import ch.jp.shooting.repository.GuestRepository;
import ch.jp.smartground.model.GuestResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// Gäste sind systemweit (keine Owner-Scoping)
@Service
@NullMarked
public class GuestService {

    private final GuestRepository guestRepository;

    public GuestService(GuestRepository guestRepository) {
        this.guestRepository = guestRepository;
    }

    public List<GuestResponse> listGuests() {
        return guestRepository.findAll().stream()
            .map(PlayMapper::toGuestResponse)
            .toList();
    }

    public GuestResponse createGuest(String displayName) {
        var guest = new Guest();
        guest.setDisplayName(displayName);
        return PlayMapper.toGuestResponse(guestRepository.save(guest));
    }

    public GuestResponse updateGuest(UUID id, String displayName) {
        var guest = guestRepository.findById(id)
            .orElseThrow(() -> new GuestNotFoundException(id));
        guest.setDisplayName(displayName);
        return PlayMapper.toGuestResponse(guestRepository.save(guest));
    }

    public void deleteGuest(UUID id) {
        var guest = guestRepository.findById(id)
            .orElseThrow(() -> new GuestNotFoundException(id));
        guestRepository.delete(guest);
    }
}

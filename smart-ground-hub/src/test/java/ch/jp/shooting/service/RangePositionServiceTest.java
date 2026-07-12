package ch.jp.shooting.service;

import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.RangePositionRepository;
import ch.jp.shooting.repository.RangeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RangePositionServiceTest {

    @Mock
    private RangeRepository rangeRepository;

    @Mock
    private RangePositionRepository positionRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private RangePositionService rangePositionService;

    @Test
    void sendPositionCommand_returns501_untilNodeChannelExists() {
        UUID rangeId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();

        Range range = new Range();
        range.setId(rangeId);

        Device device = new Device();
        device.setId(UUID.randomUUID());
        device.setBlocked(false);

        RangePosition position = new RangePosition();
        position.setId(positionId);
        position.setRange(range);
        position.setDevice(device);

        when(positionRepository.findById(positionId)).thenReturn(Optional.of(position));
        when(reservationService.canUserCommandRange(rangeId, "admin", true)).thenReturn(true);

        assertThatThrownBy(() -> rangePositionService.sendPositionCommand(rangeId, positionId, "admin", true))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_IMPLEMENTED);
    }
}

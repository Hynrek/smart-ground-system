package ch.jp.shooting.api;

import ch.jp.shooting.exception.SmartBoxNotFoundException;
import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.RangePositionRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxControllerTest {

    @Mock SmartBoxRepository smartBoxRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock RangePositionRepository rangePositionRepository;

    private SmartBoxController controller() {
        return new SmartBoxController(
            smartBoxRepository, deviceRepository, rangePositionRepository);
    }

    @Test
    void deleteSoftDeletesBoxAndHardDeletesItsDevices() {
        UUID boxId = UUID.randomUUID();
        SmartBox box = new SmartBox();
        box.setId(boxId);
        Device d1 = new Device();
        Device d2 = new Device();
        when(smartBoxRepository.findByIdAndDeletedAtIsNull(boxId)).thenReturn(Optional.of(box));
        when(deviceRepository.findBySmartBoxId(boxId)).thenReturn(List.of(d1, d2));
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));

        var response = controller().deleteSmartBox(boxId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deviceRepository).deleteAll(List.of(d1, d2));
        ArgumentCaptor<SmartBox> cap = ArgumentCaptor.forClass(SmartBox.class);
        verify(smartBoxRepository).save(cap.capture());
        assertThat(cap.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void deleteUnassignsRangePositionBeforeDeletingDevice() {
        UUID boxId = UUID.randomUUID();
        SmartBox box = new SmartBox();
        box.setId(boxId);
        Device device = new Device();
        RangePosition position = new RangePosition();
        position.setDevice(device);
        device.setRangePosition(position);
        when(smartBoxRepository.findByIdAndDeletedAtIsNull(boxId)).thenReturn(Optional.of(box));
        when(deviceRepository.findBySmartBoxId(boxId)).thenReturn(List.of(device));
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));

        controller().deleteSmartBox(boxId);

        ArgumentCaptor<RangePosition> cap = ArgumentCaptor.forClass(RangePosition.class);
        verify(rangePositionRepository).save(cap.capture());
        assertThat(cap.getValue().getDevice()).isNull();
        verify(deviceRepository).deleteAll(List.of(device));
    }

    @Test
    void deleteThrowsWhenBoxMissingOrAlreadyDeleted() {
        UUID boxId = UUID.randomUUID();
        when(smartBoxRepository.findByIdAndDeletedAtIsNull(boxId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().deleteSmartBox(boxId))
            .isInstanceOf(SmartBoxNotFoundException.class);
        verify(deviceRepository, never()).deleteAll(any());
    }

    @Test
    void pushSmartBoxConfig_returns501_untilSyncFundamentExists() {
        UUID boxId = UUID.randomUUID();

        assertThatThrownBy(() -> controller().pushSmartBoxConfig(boxId))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_IMPLEMENTED);
    }
}

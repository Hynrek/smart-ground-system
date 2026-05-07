package ch.jp.shooting.api;

import ch.jp.shooting.exception.DeviceNotFoundException;
import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.DeviceType;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.DeviceTypeRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceController")
class DeviceControllerTest {

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceTypeRepository deviceTypeRepository;

    @Mock
    private SmartBoxRepository smartBoxRepository;

    @Mock
    private ch.jp.shooting.config.MqttCommandPublisher mqttCommandPublisher;

    @InjectMocks
    private DeviceController controller;

    private Device testDevice;
    private DeviceType testTemplate;
    private SmartBox testSmartBox;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        deviceId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID smartBoxId = UUID.randomUUID();

        testSmartBox = new SmartBox();
        testSmartBox.setId(smartBoxId);

        testTemplate = new DeviceType();
        testTemplate.setId(templateId);

        testDevice = new Device();
        testDevice.setId(deviceId);
        testDevice.setAlias("DEV-001");
        testDevice.setSmartBox(testSmartBox);
        testDevice.setBlocked(false);
        testDevice.setHealthy(true);
    }

    @Test
    @DisplayName("getDevice returns device when found")
    void getDevice_returnsDevice() {
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(testDevice));

        var result = controller.getDevice(deviceId);

        assertThat(result.getBody().getId()).isEqualTo(deviceId);
        assertThat(result.getBody().getAlias()).isEqualTo("DEV-001");
    }

    @Test
    @DisplayName("getDevice throws DeviceNotFoundException when not found")
    void getDevice_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(deviceRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getDevice(unknownId))
                .isInstanceOf(DeviceNotFoundException.class);
    }
}

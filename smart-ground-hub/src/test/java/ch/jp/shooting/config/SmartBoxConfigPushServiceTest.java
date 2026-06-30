package ch.jp.shooting.config;

import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.DeviceTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageChannel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxConfigPushServiceTest {

    @Mock MessageChannel mqttOutboundChannel;
    @Mock DeviceRepository deviceRepository;
    @Mock DeviceTypeRepository deviceTypeRepository;

    private SmartBox boxWithFirmware() {
        FirmwareConfig fc = new FirmwareConfig("1.0", "xiao-esp32s3");
        try {
            var idField = FirmwareConfig.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(fc, UUID.randomUUID());
        } catch (Exception e) { throw new RuntimeException(e); }
        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        try {
            var idField = SmartBox.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(box, UUID.randomUUID());
        } catch (Exception e) { throw new RuntimeException(e); }
        box.setFirmwareConfig(fc);
        return box;
    }

    @Test
    void effectiveBlockIsTrueWhenAdminBlockedEvenIfUserBlockedIsFalse() {
        SmartBox box = boxWithFirmware();

        Device device = new Device();
        device.setAlias("Werfer 1");
        device.setBlocked(false);
        device.setAdminBlocked(true);

        DeviceTypeGroup group = new DeviceTypeGroup();
        device.setDeviceTypeGroup(group);

        SignalType signal = new SignalType();
        signal.setCommunicationDirection(CommunicationDirection.OUTPUT);
        signal.setDevice(DeviceKind.GPIO);
        signal.setCommand("15");

        DeviceType dt = new DeviceType();
        dt.setSignalType(signal);
        dt.setSignalDurationMs(500);

        when(deviceRepository.findBySmartBoxId(any())).thenReturn(List.of(device));
        when(deviceTypeRepository.findByGroupIdAndSignalType_FirmwareConfigId(any(), any()))
            .thenReturn(Optional.of(dt));
        when(mqttOutboundChannel.send(any())).thenReturn(true);

        var service = new SmartBoxConfigPushService(
            mqttOutboundChannel, deviceRepository, deviceTypeRepository, new ObjectMapper());
        service.push(box);

        ArgumentCaptor<org.springframework.messaging.Message<?>> cap =
            ArgumentCaptor.forClass(org.springframework.messaging.Message.class);
        verify(mqttOutboundChannel).send(cap.capture());
        String json = (String) cap.getValue().getPayload();
        assertThat(json).contains("\"blocked\":true");
    }
}

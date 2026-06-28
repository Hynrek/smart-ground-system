package ch.jp.shooting.config;

import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.FirmwareConfigRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxDiscoveryHandlerTest {

    @Mock SmartBoxRepository smartBoxRepository;
    @Mock FirmwareConfigRepository firmwareConfigRepository;
    @Mock SmartBoxConfigPushService configPushService;

    @Test
    void capturesAppVersionAndFirmwareVersion() {
        var handler = new SmartBoxDiscoveryHandler(
            smartBoxRepository, firmwareConfigRepository, new ObjectMapper(), configPushService);
        when(smartBoxRepository.findByMacAddress("aabbccddeeff")).thenReturn(Optional.empty());
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));

        String json = "{\"mac\":\"aabbccddeeff\",\"appVersion\":\"0.7\","
                    + "\"firmwareVersion\":\"micropython-1.23.0\",\"boxType\":\"xiao-esp32s3\",\"ip\":\"1.2.3.4\"}";
        handler.handleMessage(MessageBuilder.withPayload(json.getBytes()).build());

        ArgumentCaptor<SmartBox> cap = ArgumentCaptor.forClass(SmartBox.class);
        verify(smartBoxRepository).save(cap.capture());
        assertThat(cap.getValue().getAppVersion()).isEqualTo("0.7");
        assertThat(cap.getValue().getFirmwareVersion()).isEqualTo("micropython-1.23.0");
    }
}

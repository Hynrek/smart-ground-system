package ch.jp.shooting.config;

import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.SmartBoxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxOtaStatusHandlerTest {

    @Mock SmartBoxRepository repository;

    @Test
    void updatesOtaFieldsFromPayload() {
        var handler = new SmartBoxOtaStatusHandler(repository, new ObjectMapper());
        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        when(repository.findByMacAddress("aabbccddeeff")).thenReturn(Optional.of(box));
        when(repository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));

        String json = "{\"version\":\"0.7\",\"phase\":\"DOWNLOADING\",\"progress\":40,\"detail\":\"\"}";
        handler.handleMessage(MessageBuilder.withPayload(json.getBytes())
            .setHeader("mqtt_receivedTopic", "smartboxes/aabbccddeeff/ota/status").build());

        assertThat(box.getOtaPhase()).isEqualTo("DOWNLOADING");
        assertThat(box.getOtaVersion()).isEqualTo("0.7");
        assertThat(box.getOtaProgress()).isEqualTo(40);
        assertThat(box.getOtaUpdatedAt()).isNotNull();
    }
}

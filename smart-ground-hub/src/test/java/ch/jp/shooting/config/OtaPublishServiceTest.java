package ch.jp.shooting.config;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OtaPublishServiceTest {

    @Test
    void publishesAppOtaCommandWithUrlAndHash() throws Exception {
        MessageChannel channel = mock(MessageChannel.class);
        when(channel.send(any())).thenReturn(true);
        ObjectMapper mapper = new ObjectMapper();
        OtaPublishService svc = new OtaPublishService(channel, mapper, "http://10.0.0.5:8080");

        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        OtaRelease release = new OtaRelease();
        release.setType(OtaType.APP);
        release.setVersion("0.7");
        release.setSha256("deadbeef");
        release.setSizeBytes(42L);

        svc.publish(box, release);

        ArgumentCaptor<Message<?>> cap = ArgumentCaptor.forClass(Message.class);
        verify(channel).send(cap.capture());
        Message<?> msg = cap.getValue();
        assertThat(msg.getHeaders().get("mqtt_topic")).isEqualTo("smartboxes/aabbccddeeff/ota");
        JsonNode payload = mapper.readTree((String) msg.getPayload());
        assertThat(payload.get("type").asText()).isEqualTo("APP");
        assertThat(payload.get("version").asText()).isEqualTo("0.7");
        assertThat(payload.get("url").asText()).isEqualTo("http://10.0.0.5:8080/api/ota/app/0.7");
        assertThat(payload.get("sha256").asText()).isEqualTo("deadbeef");
        assertThat(payload.get("size").asLong()).isEqualTo(42L);
    }

    @Test
    void firmwareUrlPointsAtBinEndpoint() throws Exception {
        MessageChannel channel = mock(MessageChannel.class);
        when(channel.send(any())).thenReturn(true);
        ObjectMapper mapper = new ObjectMapper();
        OtaPublishService svc = new OtaPublishService(channel, mapper, "http://10.0.0.5:8080");

        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        OtaRelease release = new OtaRelease();
        release.setType(OtaType.FIRMWARE);
        release.setVersion("mp-1.24");
        release.setSha256("cafe");

        svc.publish(box, release);

        ArgumentCaptor<Message<?>> cap = ArgumentCaptor.forClass(Message.class);
        verify(channel).send(cap.capture());
        JsonNode payload = mapper.readTree((String) cap.getValue().getPayload());
        assertThat(payload.get("url").asText()).isEqualTo("http://10.0.0.5:8080/api/ota/firmware/mp-1.24");
    }
}

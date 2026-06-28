package ch.jp.shooting.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxMqttRouterTest {

    @Mock SmartBoxDiscoveryHandler discovery;
    @Mock SmartBoxStatusHandler status;
    @Mock SmartBoxConfigAckHandler configAck;
    @Mock SmartBoxDeviceExecutedHandler executed;
    @Mock SmartBoxOtaStatusHandler otaStatus;

    @Test
    void otaStatusTopicRoutesToOtaHandlerNotStatusHandler() {
        var router = new SmartBoxMqttRouter(discovery, status, configAck, executed, otaStatus);
        var msg = MessageBuilder.withPayload("{}".getBytes())
            .setHeader("mqtt_receivedTopic", "smartboxes/aabbccddeeff/ota/status").build();
        router.handleMessage(msg);
        verify(otaStatus).handleMessage(msg);
        verify(status, never()).handleMessage(any());
    }

    @Test
    void plainStatusTopicStillRoutesToStatusHandler() {
        var router = new SmartBoxMqttRouter(discovery, status, configAck, executed, otaStatus);
        var msg = MessageBuilder.withPayload("{}".getBytes())
            .setHeader("mqtt_receivedTopic", "smartboxes/aabbccddeeff/status").build();
        router.handleMessage(msg);
        verify(status).handleMessage(msg);
        verify(otaStatus, never()).handleMessage(any());
    }
}

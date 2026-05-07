package ch.jp.shooting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class MqttCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttCommandPublisher.class);

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;

    public MqttCommandPublisher(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            ObjectMapper objectMapper) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.objectMapper = objectMapper;
    }

    public record CommandPayload(String command, String deviceId, int signalDurationMs, @Nullable Integer delaySignalDurationMs) {}

    public void publishToTopic(String topic, String command, String deviceId, int signalDurationMs, @Nullable Integer delaySignalDurationMs) {
        try {
            String payload = objectMapper.writeValueAsString(new CommandPayload(command, deviceId, signalDurationMs, delaySignalDurationMs));
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(payload)
                            .setHeader("mqtt_topic", topic)
                            .setHeader("mqtt_qos", 1)
                            .build()
            );
            log.info("Command '{}' gesendet an Topic {}", command, topic);
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Serialisieren des Commands", e);
        }
    }
}

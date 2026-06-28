package ch.jp.shooting.config;

import ch.jp.shooting.repository.SmartBoxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@NullMarked
public class SmartBoxOtaStatusHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SmartBoxOtaStatusHandler.class);

    record OtaStatusPayload(
        @Nullable String version, @Nullable String phase,
        @Nullable Integer progress, @Nullable String detail) {}

    private final SmartBoxRepository smartBoxRepository;
    private final ObjectMapper objectMapper;

    public SmartBoxOtaStatusHandler(SmartBoxRepository smartBoxRepository, ObjectMapper objectMapper) {
        this.smartBoxRepository = smartBoxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void handleMessage(Message<?> message) throws MessagingException {
        try {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            if (topic == null) return;
            String mac = extractMac(topic);

            String json = switch (message.getPayload()) {
                case byte[] b -> new String(b, StandardCharsets.UTF_8);
                case String s -> s;
                default       -> objectMapper.writeValueAsString(message.getPayload());
            };
            OtaStatusPayload p = objectMapper.readValue(json, OtaStatusPayload.class);

            smartBoxRepository.findByMacAddress(mac).ifPresent(box -> {
                box.setOtaPhase(p.phase());
                box.setOtaVersion(p.version());
                box.setOtaProgress(p.progress());
                box.setOtaDetail(p.detail());
                box.setOtaUpdatedAt(Instant.now());
                smartBoxRepository.save(box);
                log.info("OTA-Status von {}: {} {} ({}%)", mac, p.phase(), p.version(), p.progress());
            });
        } catch (Exception e) {
            log.warn("Fehler beim Verarbeiten des OTA-Status: {}", e.getMessage());
        }
    }

    // Topic-Form: smartboxes/{mac}/ota/status
    private String extractMac(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 2 ? parts[1] : "";
    }
}

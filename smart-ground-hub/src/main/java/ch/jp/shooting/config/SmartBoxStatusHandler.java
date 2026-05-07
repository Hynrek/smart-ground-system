package ch.jp.shooting.config;

import ch.jp.shooting.model.SmartBoxStates;
import ch.jp.shooting.repository.SmartBoxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@NullMarked
public class SmartBoxStatusHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SmartBoxStatusHandler.class);

    record StatusPayload(String mac) {}

    private final SmartBoxRepository smartBoxRepository;
    private final ObjectMapper    objectMapper;
    private final Counter        offlineMarkedCounter;

    @Value("${mqtt.health-check.offline-threshold-seconds:30}")
    private int offlineThresholdSeconds;

    public SmartBoxStatusHandler(
            SmartBoxRepository smartBoxRepository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.smartBoxRepository = smartBoxRepository;
        this.objectMapper       = objectMapper;
        this.offlineMarkedCounter = Counter.builder("smartbox.health.offline.marked")
                .description("Number of SmartBoxes marked offline by health check")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public void handleMessage(Message<?> message) throws MessagingException {
        try {
            String json = switch (message.getPayload()) {
                case byte[] b -> new String(b, StandardCharsets.UTF_8);
                case String s -> s;
                default       -> objectMapper.writeValueAsString(message.getPayload());
            };
            StatusPayload payload = objectMapper.readValue(json, StatusPayload.class);

            smartBoxRepository.findByMacAddress(payload.mac()).ifPresent(box -> {
                box.setLastSeen(Instant.now());
                if (box.getStatus() != SmartBoxStates.ONLINE) {
                    box.setStatus(SmartBoxStates.ONLINE);
                    log.info("SmartBox {} wieder online.", payload.mac());
                }
                smartBoxRepository.save(box);
            });

        } catch (Exception e) {
            log.warn("Fehler beim Verarbeiten des Heartbeats: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 15_000)
    @Transactional
    public void markStaleBoxesOffline() {
        Instant threshold = Instant.now().minus(offlineThresholdSeconds, ChronoUnit.SECONDS);
        int updated = smartBoxRepository.updateStaleBoxes(
                SmartBoxStates.OFFLINE,
                SmartBoxStates.ONLINE,
                threshold);
        if (updated > 0) {
            offlineMarkedCounter.increment(updated);
            log.warn("{} SmartBox(es) als OFFLINE markiert (Threshold: {}s)", updated, offlineThresholdSeconds);
        }
    }
}
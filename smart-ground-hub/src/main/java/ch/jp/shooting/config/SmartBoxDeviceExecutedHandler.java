package ch.jp.shooting.config;

import ch.jp.shooting.repository.DeviceRepository;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verarbeitet Ausführungsbestätigungen von SmartBox-Geräten.
 *
 * Topic:   smartboxes/{mac}/device/{deviceId}/executed
 * Payload: (leer oder "OK" – nur das Eintreffen der Nachricht zählt)
 *
 * Erhöht commandsProcessed und setzt lastCommandProcessedAt für das betreffende Gerät.
 */
@Component
@NullMarked
public class SmartBoxDeviceExecutedHandler implements MessageHandler {

    private static final Logger  log          = LoggerFactory.getLogger(SmartBoxDeviceExecutedHandler.class);
    private static final Pattern TOPIC_PATTERN =
        Pattern.compile("smartboxes/([^/]+)/device/([^/]+)/executed");

    private final DeviceRepository deviceRepository;

    public SmartBoxDeviceExecutedHandler(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    @Transactional
    public void handleMessage(Message<?> message) throws MessagingException {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        if (topic == null) return;

        Matcher m = TOPIC_PATTERN.matcher(topic);
        if (!m.matches()) return;

        String mac      = m.group(1);
        String deviceId = m.group(2);

        UUID uuid;
        try {
            uuid = UUID.fromString(deviceId);
        } catch (IllegalArgumentException e) {
            log.warn("Ungültige Device-UUID in Topic: {}", topic);
            return;
        }

        deviceRepository.findById(uuid).ifPresentOrElse(
            device -> {
                device.setCommandsProcessed(device.getCommandsProcessed() + 1);
                device.setLastCommandProcessedAt(Instant.now());
                deviceRepository.save(device);
                log.debug("Befehl ausgeführt bestätigt: Gerät {} (MAC: {}), gesamt: {}",
                    device.getAlias(), mac, device.getCommandsProcessed());
            },
            () -> log.warn("Executed-ACK für unbekannte Device-UUID: {} (MAC: {})", deviceId, mac)
        );
    }
}

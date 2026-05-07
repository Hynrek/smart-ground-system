package ch.jp.shooting.config;

import ch.jp.shooting.repository.SmartBoxRepository;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verarbeitet Config-ACK-Nachrichten von SmartBoxen.
 *
 * Topic:   smartboxes/{mac}/config/ack
 * Payload: (leer oder beliebig – nur das Eintreffen der Nachricht zählt)
 *
 * Setzt configSynced = true für die betreffende SmartBox.
 */
@Component
@NullMarked
public class SmartBoxConfigAckHandler implements MessageHandler {

    private static final Logger  log          = LoggerFactory.getLogger(SmartBoxConfigAckHandler.class);
    private static final Pattern TOPIC_PATTERN =
        Pattern.compile("smartboxes/([^/]+)/config/ack");

    private final SmartBoxRepository smartBoxRepository;

    public SmartBoxConfigAckHandler(SmartBoxRepository smartBoxRepository) {
        this.smartBoxRepository = smartBoxRepository;
    }

    @Override
    @Transactional
    public void handleMessage(Message<?> message) throws MessagingException {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        if (topic == null) return;

        Matcher m = TOPIC_PATTERN.matcher(topic);
        if (!m.matches()) return;

        String mac = m.group(1);
        smartBoxRepository.findByMacAddress(mac).ifPresentOrElse(
            box -> {
                box.setConfigSynced(true);
                smartBoxRepository.save(box);
                log.info("Config-Sync bestätigt für SmartBox {} (MAC: {}).", box.getAlias(), mac);
            },
            () -> log.warn("Config-ACK für unbekannte SmartBox MAC: {}", mac)
        );
    }
}

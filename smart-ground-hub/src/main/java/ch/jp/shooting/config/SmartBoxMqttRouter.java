package ch.jp.shooting.config;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

@Component
@NullMarked
public class SmartBoxMqttRouter implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SmartBoxMqttRouter.class);

    private final SmartBoxDiscoveryHandler      discoveryHandler;
    private final SmartBoxStatusHandler         statusHandler;
    private final SmartBoxConfigAckHandler      configAckHandler;
    private final SmartBoxDeviceExecutedHandler deviceExecutedHandler;
    private final SmartBoxOtaStatusHandler      otaStatusHandler;
    private final MqttDynsecClient              dynsecClient;

    public SmartBoxMqttRouter(
            SmartBoxDiscoveryHandler discoveryHandler,
            SmartBoxStatusHandler statusHandler,
            SmartBoxConfigAckHandler configAckHandler,
            SmartBoxDeviceExecutedHandler deviceExecutedHandler,
            SmartBoxOtaStatusHandler otaStatusHandler,
            MqttDynsecClient dynsecClient) {
        this.discoveryHandler      = discoveryHandler;
        this.statusHandler         = statusHandler;
        this.configAckHandler      = configAckHandler;
        this.deviceExecutedHandler = deviceExecutedHandler;
        this.otaStatusHandler      = otaStatusHandler;
        this.dynsecClient          = dynsecClient;
    }

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        if (topic == null) {
            log.warn("MQTT-Message ohne Topic empfangen, ignoriert.");
            return;
        }

        if (topic.equals(MqttConfig.TOPIC_DYNSEC_RESPONSE)) {
            // Antwort der dynsec-Kontroll-API ($CONTROL/...) – kein SmartBox-Topic.
            dynsecClient.handleResponse(message);
        } else if (topic.endsWith("/discovery")) {
            discoveryHandler.handleMessage(message);
        } else if (topic.endsWith("/ota/status")) {     // VOR /status prüfen!
            otaStatusHandler.handleMessage(message);
        } else if (topic.endsWith("/status")) {
            statusHandler.handleMessage(message);
        } else if (topic.endsWith("/config/ack")) {
            configAckHandler.handleMessage(message);
        } else if (topic.endsWith("/executed")) {
            deviceExecutedHandler.handleMessage(message);
        } else {
            log.debug("Unbekanntes Topic: {}", topic);
        }
    }
}
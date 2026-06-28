package ch.jp.shooting.config;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Veröffentlicht einen OTA-Befehl an eine SmartBox.
 *
 * Topic:   smartboxes/{mac}/ota
 * Payload: { "type", "version", "url", "sha256", "size" }
 * Die Box lädt das Artefakt anschliessend per HTTP von {url} herunter.
 */
@Service
@NullMarked
public class OtaPublishService {

    private static final Logger log = LoggerFactory.getLogger(OtaPublishService.class);
    static final String TOPIC_OTA_TEMPLATE = "smartboxes/%s/ota";

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OtaPublishService(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            ObjectMapper objectMapper,
            @Value("${ota.base-url:http://localhost:8080}") String baseUrl) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record OtaCommand(String type, String version, String url, String sha256, long size) {}

    public void publish(SmartBox box, OtaRelease release) {
        String mac = box.getMacAddress();
        String url = buildUrl(release);
        try {
            OtaCommand cmd = new OtaCommand(
                release.getType().name(), release.getVersion(), url,
                release.getSha256(), release.getSizeBytes());
            String json = objectMapper.writeValueAsString(cmd);
            String topic = TOPIC_OTA_TEMPLATE.formatted(mac);
            mqttOutboundChannel.send(MessageBuilder.withPayload(json)
                .setHeader("mqtt_topic", topic)
                .setHeader("mqtt_qos", 1)
                .build());
            log.info("OTA-Befehl ({} {}) an SmartBox {} → {}", cmd.type(), cmd.version(), mac, topic);
        } catch (Exception e) {
            throw new RuntimeException("OTA-Befehl konnte nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private String buildUrl(OtaRelease release) {
        String segment = release.getType() == OtaType.APP ? "app" : "firmware";
        return "%s/api/ota/%s/%s".formatted(baseUrl, segment, release.getVersion());
    }
}

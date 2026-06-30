package ch.jp.shooting.config;

import ch.jp.shooting.model.FirmwareConfig;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.model.SmartBoxStates;
import ch.jp.shooting.repository.FirmwareConfigRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
public class SmartBoxDiscoveryHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SmartBoxDiscoveryHandler.class);

    record DiscoveryPayload(
        String mac,
        @Nullable String ip,
        @Nullable String appVersion,
        @Nullable String firmwareVersion,
        @Nullable String boxType,
        @Nullable String configSchemaVersion,
        @Nullable JsonNode capabilities
    ) {}

    private final SmartBoxRepository smartBoxRepository;
    private final FirmwareConfigRepository firmwareConfigRepository;
    private final ObjectMapper objectMapper;
    private final SmartBoxConfigPushService configPushService;

    public SmartBoxDiscoveryHandler(SmartBoxRepository smartBoxRepository,
                                    FirmwareConfigRepository firmwareConfigRepository,
                                    ObjectMapper objectMapper,
                                    SmartBoxConfigPushService configPushService) {
        this.smartBoxRepository = smartBoxRepository;
        this.firmwareConfigRepository = firmwareConfigRepository;
        this.objectMapper = objectMapper;
        this.configPushService = configPushService;
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

            DiscoveryPayload payload = objectMapper.readValue(json, DiscoveryPayload.class);
            SmartBox box = upsertSmartBox(payload);

            // Konfiguration nach Discovery sofort pushen, damit die Box ihre GPIO-Belegung kennt
            configPushService.push(box);

        } catch (Exception e) {
            log.warn("Fehler beim Verarbeiten der Discovery-Message: {}", e.getMessage());
        }
    }

    private SmartBox upsertSmartBox(DiscoveryPayload payload) {
        SmartBox box = smartBoxRepository.findByMacAddress(payload.mac())
            .orElseGet(() -> {
                SmartBox newBox = new SmartBox();
                newBox.setMacAddress(payload.mac());
                log.info("Neue SmartBox entdeckt: {}", payload.mac());
                return newBox;
            });

        box.setStatus(SmartBoxStates.ONLINE);
        box.setLastSeen(Instant.now());

        if (payload.appVersion() != null) {
            box.setAppVersion(payload.appVersion());
        }
        if (payload.firmwareVersion() != null) {
            box.setFirmwareVersion(payload.firmwareVersion());
        }

        // Capability-Registry immer aus dem Discovery-Payload upserten.
        // Neue Versionen werden automatisch angelegt – kein manuelles Seeden erforderlich.
        String capabilityVersion = payload.appVersion() != null
            ? payload.appVersion()
            : payload.firmwareVersion();
        if (capabilityVersion != null) {
            String boxType = payload.boxType() != null ? payload.boxType() : "UNKNOWN";
            FirmwareConfig fc = firmwareConfigRepository
                .findByVersionAndBoxType(capabilityVersion, boxType)
                .orElseGet(() -> {
                    log.info("Unbekannte AppVersion {} – neue FirmwareConfig wird erstellt.", capabilityVersion);
                    return new FirmwareConfig(capabilityVersion, boxType);
                });

            if (payload.capabilities() != null) {
                try {
                    fc.setCapabilitiesJson(objectMapper.writeValueAsString(payload.capabilities()));
                } catch (Exception e) {
                    log.warn("Capabilities konnten nicht serialisiert werden: {}", e.getMessage());
                }
            }
            if (payload.configSchemaVersion() != null) {
                fc.setConfigSchemaVersion(payload.configSchemaVersion());
            }

            FirmwareConfig savedFc = firmwareConfigRepository.save(fc);
            box.setFirmwareConfig(savedFc);
        }
        return smartBoxRepository.save(box);
    }
}

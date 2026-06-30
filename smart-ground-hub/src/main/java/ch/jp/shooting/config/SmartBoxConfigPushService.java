package ch.jp.shooting.config;

import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.DeviceTypeRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pusht die aktuelle Gerätekonfiguration einer SmartBox via MQTT.
 *
 * Topic:   smartboxes/{mac}/config
 * Payload: { "firmware_version": "0.4", "devices": [ { "device_id", "alias", "direction", "device", "command", "signal_duration_ms", "blocked" }, … ] }
 *
 * Die SmartBox speichert den Payload lokal als device_config.json und
 * quittiert den Empfang auf smartboxes/{mac}/config/ack.
 */
@Service
@NullMarked
public class SmartBoxConfigPushService {

    private static final Logger log = LoggerFactory.getLogger(SmartBoxConfigPushService.class);

    static final String TOPIC_CONFIG_TEMPLATE = "smartboxes/%s/config";

    private final MessageChannel mqttOutboundChannel;
    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final ObjectMapper objectMapper;

    public SmartBoxConfigPushService(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            DeviceRepository deviceRepository,
            DeviceTypeRepository deviceTypeRepository,
            ObjectMapper objectMapper) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.deviceRepository = deviceRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.objectMapper = objectMapper;
    }

    public record DeviceConfigEntry(
        @JsonProperty("device_id")          UUID deviceId,
        String alias,
        String direction,
        String device,
        String command,
        @JsonProperty("signal_duration_ms") int signalDurationMs,
        boolean blocked
    ) {}

    public record DeviceConfigPayload(String firmwareVersion, List<DeviceConfigEntry> devices) {}

    public void push(SmartBox smartBox) {
        String mac = smartBox.getMacAddress();
        UUID boxId = smartBox.getId();
        if (boxId == null) {
            log.warn("SmartBox {} hat keine ID – Config-Push übersprungen.", mac);
            return;
        }

        try {
            DeviceConfigPayload payload = buildPayload(smartBox);
            String topic = TOPIC_CONFIG_TEMPLATE.formatted(mac);

            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(
                MessageBuilder.withPayload(json)
                    .setHeader("mqtt_topic", topic)
                    .setHeader("mqtt_qos", 1)
                    .build()
            );
            log.info("Config-Push an SmartBox {} ({} Geräte) → Topic: {}", mac, payload.devices().size(), topic);
        } catch (FirmwareNotResolvedException e) {
            log.error("Config-Push für SmartBox {} fehlgeschlagen: Firmware nicht aufgelöst", mac);
        } catch (IncompatibleDeviceException e) {
            log.error("Config-Push für SmartBox {} fehlgeschlagen: Inkompatibles Gerät", mac);
        } catch (Exception e) {
            log.error("Config-Push für SmartBox {} fehlgeschlagen: {}", mac, e.getMessage());
        }
    }

    private DeviceConfigPayload buildPayload(SmartBox box) {
        FirmwareConfig firmware = box.getFirmwareConfig();
        if (firmware == null) {
            throw new FirmwareNotResolvedException(box.getId());
        }

        List<DeviceConfigEntry> entries = new ArrayList<>();
        List<Device> devices = deviceRepository.findBySmartBoxId(box.getId());

        for (Device device : devices) {
            // Resolve DeviceType: group × firmware → unique DeviceType
            DeviceType deviceType = deviceTypeRepository
                .findByGroupIdAndSignalType_FirmwareConfigId(
                    device.getDeviceTypeGroup().getId(),
                    firmware.getId())
                .orElseThrow(() -> new IncompatibleDeviceException(
                    device.getId(), firmware.getId()));

            SignalType signal = deviceType.getSignalType();

            entries.add(new DeviceConfigEntry(
                device.getId(),
                device.getAlias(),
                signal.getCommunicationDirection().name(),
                signal.getDevice().name(),
                signal.getCommand(),
                deviceType.getSignalDurationMs(),
                device.isBlocked() || device.isAdminBlocked()
            ));
        }

        return new DeviceConfigPayload(firmware.getVersion(), entries);
    }

    public static class FirmwareNotResolvedException extends RuntimeException {
        public FirmwareNotResolvedException(UUID smartBoxId) {
            super("Firmware nicht aufgelöst für SmartBox: " + smartBoxId);
        }
    }

    public static class IncompatibleDeviceException extends RuntimeException {
        public IncompatibleDeviceException(UUID deviceId, UUID firmwareConfigId) {
            super("Kein kompatibler DeviceType für Device " + deviceId +
                  " und FirmwareConfig " + firmwareConfigId);
        }
    }
}

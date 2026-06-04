package ch.jp.shooting.mapper;

import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.DeviceType;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.model.SmartBox;
import ch.jp.smartground.model.DeviceResponse;
import ch.jp.smartground.model.DeviceTypeResponse;
import ch.jp.smartground.model.PinConfig;
import ch.jp.smartground.model.RangeDetailResponse;
import ch.jp.smartground.model.RangePositionResponse;
import ch.jp.smartground.model.SmartBoxResponse;
import ch.jp.smartground.model.SmartBoxStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EntityMappers {
    private static final Logger log = LoggerFactory.getLogger(EntityMappers.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private EntityMappers() {}

    public static DeviceResponse toDeviceResponse(Device device) {
        var range = device.getRange();
        var deviceType = device.getDeviceType();
        var deviceTypeGroup = device.getDeviceTypeGroup();
        var smartBox = device.getSmartBox();
        var signalType = deviceType != null ? deviceType.getSignalType() : null;

        return new DeviceResponse()
            .id(device.getId())
            .alias(device.getAlias())
            .smartBoxId(smartBox != null ? smartBox.getId() : null)
            .groupId(deviceTypeGroup != null ? deviceTypeGroup.getId() : null)
            .groupName(deviceTypeGroup != null ? deviceTypeGroup.getName() : null)
            .deviceTypeId(deviceType != null ? deviceType.getId() : null)
            .deviceType(deviceType != null ? deviceType.getName() : null)
            .command(signalType != null ? signalType.getCommand() : null)
            .boxType(smartBox != null ? smartBox.getMacAddress() : null)
            .blocked(device.isBlocked())
            .healthy(device.isHealthy())
            .signalDurationMs(deviceType != null ? deviceType.getSignalDurationMs() : null)
            .delaySignalDurationMs(device.getDelaySignalDurationMs())
            .pinConfig(device.getPinConfig() != null ? toPinConfig(parseJson(device.getPinConfig())) : null)
            .rangeId(range != null ? range.getId() : null)
            .commandsSent(device.getCommandsSent())
            .commandsProcessed(device.getCommandsProcessed())
            .lastCommandSentAt(device.getLastCommandSentAt() != null
                ? java.time.OffsetDateTime.ofInstant(device.getLastCommandSentAt(), java.time.ZoneOffset.UTC)
                : null)
            .lastCommandProcessedAt(device.getLastCommandProcessedAt() != null
                ? java.time.OffsetDateTime.ofInstant(device.getLastCommandProcessedAt(), java.time.ZoneOffset.UTC)
                : null);
    }

    public static SmartBoxResponse toSmartBoxResponse(SmartBox smartBox) {
        var status = smartBox.getStatus();
        return new SmartBoxResponse()
            .id(smartBox.getId())
            .macAddress(smartBox.getMacAddress())
            .alias(smartBox.getAlias())
            .status(status != null ? SmartBoxStatus.fromValue(status.name()) : null)
            .firmwareVersion(smartBox.getFirmwareVersion())
            .configSynced(smartBox.isConfigSynced());
    }

    public static RangePositionResponse toRangePositionResponse(RangePosition position) {
        var device = position.getDevice();
        return new RangePositionResponse()
            .id(position.getId())
            .label(position.getLabel())
            .sortOrder(position.getSortOrder())
            .device(device != null ? toDeviceResponse(device) : null);
    }

    public static RangeDetailResponse toRangeDetailResponse(Range range) {
        var devices = range.getDevices();
        List<DeviceResponse> deviceResponses = devices != null
            ? devices.stream().map(EntityMappers::toDeviceResponse).toList()
            : List.of();
            
        List<RangePositionResponse> positionResponses = range.getPositions() != null
            ? range.getPositions().stream().map(EntityMappers::toRangePositionResponse).toList()
            : List.of();

        var assignedUser = range.getAssignedUser();
        return new RangeDetailResponse()
            .id(range.getId())
            .name(range.getName())
            .description(range.getDescription())
            .locked(range.isLocked())
            .assignedUserId(assignedUser != null ? assignedUser.getId() : null)
            .devices(deviceResponses)
            .positions(positionResponses)
            .createdAt(range.getCreatedAt() != null
                ? java.time.OffsetDateTime.ofInstant(range.getCreatedAt(), java.time.ZoneOffset.UTC)
                : null);
    }

    public static PinConfig toPinConfig(Map<String, Object> map) {
        if (map == null) return null;
        PinConfig pc = new PinConfig();
        if (map.get("pin") instanceof Number n) pc.setPin(n.intValue());
        if (map.get("mode") instanceof String s) pc.setMode(PinConfig.ModeEnum.fromValue(s));
        if (map.get("pwmFrequencyHz") instanceof Number n) pc.setPwmFrequencyHz(n.intValue());
        if (map.get("invertLogic") instanceof Boolean b) pc.setInvertLogic(b);
        return pc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Map) return (Map<String, Object>) obj;
        return null;
    }

    private static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse pin config JSON: {}", json, e);
            return null;
        }
    }

    public static String toPinConfigJson(PinConfig pinConfig) {
        if (pinConfig == null) return null;
        try {
            return objectMapper.writeValueAsString(pinConfig);
        } catch (Exception e) {
            log.warn("Failed to serialize pin config: {}", pinConfig, e);
            return null;
        }
    }

}
package ch.jp.shooting.service;

import ch.jp.shooting.dto.FirmwareConfigManifest;
import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Transactional
public class FirmwareConfigService {

    private final FirmwareConfigRepository firmwareConfigRepository;
    private final SignalTypeRepository signalTypeRepository;
    private final DeviceTypeGroupRepository deviceTypeGroupRepository;
    private final DeviceTypeRepository deviceTypeRepository;

    public FirmwareConfigService(
            FirmwareConfigRepository firmwareConfigRepository,
            SignalTypeRepository signalTypeRepository,
            DeviceTypeGroupRepository deviceTypeGroupRepository,
            DeviceTypeRepository deviceTypeRepository) {
        this.firmwareConfigRepository = firmwareConfigRepository;
        this.signalTypeRepository = signalTypeRepository;
        this.deviceTypeGroupRepository = deviceTypeGroupRepository;
        this.deviceTypeRepository = deviceTypeRepository;
    }

    public FirmwareConfig register(FirmwareConfigManifest manifest) {
        // 1. Validate no duplicate (version + boxType)
        firmwareConfigRepository.findByVersionAndBoxType(manifest.version(), manifest.boxType())
            .ifPresent(fc -> {
                throw new DuplicateFirmwareConfigException(
                    "FirmwareConfig already exists for version=" + manifest.version() +
                    ", boxType=" + manifest.boxType());
            });

        // 2. Create FirmwareConfig
        FirmwareConfig fc = new FirmwareConfig();
        fc.setVersion(manifest.version());
        fc.setBoxType(manifest.boxType());
        fc = firmwareConfigRepository.save(fc);

        final FirmwareConfig finalFc = fc;

        // 3. For each signal type entry in the manifest:
        for (FirmwareConfigManifest.SignalTypeEntry entry : manifest.signalTypes()) {
            // 3a. Validate command is non-empty
            if (entry.command() == null || entry.command().isBlank()) {
                throw new IllegalArgumentException("Command cannot be empty for device=" + entry.device());
            }

            // 3b. Create SignalType
            SignalType st = new SignalType();
            st.setFirmwareConfig(finalFc);
            st.setCommunicationDirection(entry.direction());
            st.setDevice(entry.device());
            st.setCommand(entry.command());
            st = signalTypeRepository.save(st);

            // 3c. Resolve or create DeviceTypeGroup
            DeviceTypeGroup group = deviceTypeGroupRepository
                .findByName(entry.groupName())
                .orElseGet(() -> {
                    DeviceTypeGroup g = new DeviceTypeGroup();
                    g.setName(entry.groupName());
                    return deviceTypeGroupRepository.save(g);
                });

            // 3d. Validate no duplicate DeviceType for this group × SignalType
            deviceTypeRepository.findByGroupIdAndSignalType_FirmwareConfigId(
                group.getId(), finalFc.getId()).ifPresent(dt -> {
                    throw new DuplicateDeviceTypeException(
                        "DeviceType already exists for groupId=" + group.getId() +
                        ", firmwareConfigId=" + finalFc.getId());
                });

            // 3e. Create DeviceType
            DeviceType dt = new DeviceType();
            dt.setSignalType(st);
            dt.setGroup(group);
            dt.setName(entry.name());
            dt.setSignalDurationMs(entry.signalDurationMs());
            dt.setDelaySignalDurationMs(entry.delaySignalDurationMs());
            deviceTypeRepository.save(dt);
        }

        return finalFc;
    }

    public static class DuplicateFirmwareConfigException extends RuntimeException {
        public DuplicateFirmwareConfigException(String message) {
            super(message);
        }
    }

    public static class DuplicateDeviceTypeException extends RuntimeException {
        public DuplicateDeviceTypeException(String message) {
            super(message);
        }
    }
}

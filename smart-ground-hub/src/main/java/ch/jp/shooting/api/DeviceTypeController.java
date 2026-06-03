package ch.jp.shooting.api;

import ch.jp.shooting.dto.FirmwareConfigManifest;
import ch.jp.shooting.exception.DeviceTemplateNotFoundException;
import ch.jp.shooting.exception.NotFoundException;
import ch.jp.shooting.model.CommunicationDirection;
import ch.jp.shooting.model.DeviceKind;
import ch.jp.shooting.model.DeviceType;
import ch.jp.shooting.model.DeviceTypeGroup;
import ch.jp.shooting.model.FirmwareConfig;
import ch.jp.shooting.repository.DeviceTypeGroupRepository;
import ch.jp.shooting.repository.DeviceTypeRepository;
import ch.jp.shooting.repository.FirmwareConfigRepository;
import ch.jp.shooting.service.FirmwareConfigService;
import ch.jp.smartground.api.DeviceTypeApi;
import ch.jp.smartground.model.DeviceTypeGroupResponse;
import ch.jp.smartground.model.DeviceTypeResponse;
import ch.jp.smartground.model.FirmwareConfigResponse;
import ch.jp.smartground.model.FirmwareConfigSignalTypeResponse;
import ch.jp.smartground.model.RegisterFirmwareConfigRequest;
import ch.jp.smartground.model.UpdateDeviceTypeRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class DeviceTypeController implements DeviceTypeApi {

    private final DeviceTypeRepository deviceTypeRepository;
    private final DeviceTypeGroupRepository deviceTypeGroupRepository;
    private final FirmwareConfigService firmwareConfigService;
    private final FirmwareConfigRepository firmwareConfigRepository;

    public DeviceTypeController(
            DeviceTypeRepository deviceTypeRepository,
            DeviceTypeGroupRepository deviceTypeGroupRepository,
            FirmwareConfigService firmwareConfigService,
            FirmwareConfigRepository firmwareConfigRepository) {
        this.deviceTypeRepository = deviceTypeRepository;
        this.deviceTypeGroupRepository = deviceTypeGroupRepository;
        this.firmwareConfigService = firmwareConfigService;
        this.firmwareConfigRepository = firmwareConfigRepository;
    }

    // ── DeviceType ──

    @Override
    public ResponseEntity<List<DeviceTypeResponse>> listDeviceTypes(UUID firmwareConfigId) {
        List<DeviceTypeResponse> responses = deviceTypeRepository.findAll().stream()
            .filter(dt -> firmwareConfigId == null
                || dt.getSignalType().getFirmwareConfig().getId().equals(firmwareConfigId))
            .map(this::toDeviceTypeResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<DeviceTypeResponse> getDeviceType(UUID id) {
        return deviceTypeRepository.findById(id)
            .map(dt -> ResponseEntity.ok(toDeviceTypeResponse(dt)))
            .orElseThrow(() -> new DeviceTemplateNotFoundException(id));
    }

    @Override
    public ResponseEntity<DeviceTypeResponse> updateDeviceType(UUID id, UpdateDeviceTypeRequest request) {
        DeviceType deviceType = deviceTypeRepository.findById(id)
            .orElseThrow(() -> new DeviceTemplateNotFoundException(id));

        if (request.getName() != null) {
            deviceType.setName(request.getName());
        }

        var signalDurationMs = request.getSignalDurationMs();
        if (signalDurationMs != null && signalDurationMs.isPresent()) {
            deviceType.setSignalDurationMs(signalDurationMs.get());
        }

        var delaySignalDurationMs = request.getDelaySignalDurationMs();
        if (delaySignalDurationMs != null && delaySignalDurationMs.isPresent()) {
            deviceType.setDelaySignalDurationMs(delaySignalDurationMs.get());
        }

        DeviceType saved = deviceTypeRepository.save(deviceType);
        return ResponseEntity.ok(toDeviceTypeResponse(saved));
    }

    // ── DeviceTypeGroup ──

    @Override
    public ResponseEntity<List<DeviceTypeGroupResponse>> listDeviceTypeGroups() {
        List<DeviceTypeGroupResponse> responses = deviceTypeGroupRepository.findAll().stream()
            .map(this::toGroupResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<DeviceTypeGroupResponse> getDeviceTypeGroup(UUID id) {
        return deviceTypeGroupRepository.findById(id)
            .map(group -> ResponseEntity.ok(toGroupResponse(group)))
            .orElseThrow(() -> new NotFoundException("DeviceTypeGroup nicht gefunden: " + id));
    }

    // ── FirmwareConfig ──

    @Override
    public ResponseEntity<List<FirmwareConfigResponse>> listFirmwareConfigs() {
        List<FirmwareConfigResponse> responses = firmwareConfigRepository.findAll().stream()
            .map(this::toFirmwareConfigResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<FirmwareConfigResponse> getFirmwareConfig(UUID id) {
        return firmwareConfigRepository.findById(id)
            .map(fc -> ResponseEntity.ok(toFirmwareConfigResponse(fc)))
            .orElseThrow(() -> new NotFoundException("FirmwareConfig nicht gefunden: " + id));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FirmwareConfigResponse> registerFirmwareConfig(RegisterFirmwareConfigRequest request) {
        // Eingabe-DTO auf internes Manifest abbilden
        FirmwareConfigManifest manifest = new FirmwareConfigManifest(
            request.getVersion(),
            request.getBoxType(),
            request.getSignalTypes().stream()
                .map(e -> new FirmwareConfigManifest.SignalTypeEntry(
                    CommunicationDirection.valueOf(e.getDirection().toUpperCase()),
                    DeviceKind.valueOf(e.getDevice().toUpperCase()),
                    e.getCommand(),
                    e.getGroupName(),
                    e.getName(),
                    e.getSignalDurationMs(),
                    e.getDelaySignalDurationMs().isPresent() ? e.getDelaySignalDurationMs().get() : null
                ))
                .toList()
        );

        FirmwareConfig fc = firmwareConfigService.register(manifest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toFirmwareConfigResponse(fc));
    }

    // ── Hilfsmethoden ──

    private DeviceTypeResponse toDeviceTypeResponse(DeviceType t) {
        var signalType = t.getSignalType();
        var group = t.getGroup();
        return new DeviceTypeResponse()
            .id(t.getId())
            .name(t.getName())
            .signalTypeId(signalType.getId())
            .groupId(group.getId())
            .groupName(group.getName())
            .signalDurationMs(t.getSignalDurationMs())
            .delaySignalDurationMs(t.getDelaySignalDurationMs())
            .command(signalType.getCommand())
            .device(signalType.getDevice().name())
            .direction(signalType.getCommunicationDirection().name());
    }

    private DeviceTypeGroupResponse toGroupResponse(DeviceTypeGroup group) {
        return new DeviceTypeGroupResponse()
            .id(group.getId())
            .name(group.getName());
    }

    private FirmwareConfigResponse toFirmwareConfigResponse(FirmwareConfig fc) {
        List<FirmwareConfigSignalTypeResponse> signalTypes = fc.getSignalTypes().stream()
            .map(st -> new FirmwareConfigSignalTypeResponse()
                .id(st.getId())
                .communicationDirection(st.getCommunicationDirection().name())
                .device(st.getDevice().name())
                .command(st.getCommand()))
            .toList();
        return new FirmwareConfigResponse()
            .id(fc.getId())
            .version(fc.getVersion())
            .boxType(fc.getBoxType())
            .signalTypes(signalTypes);
    }
}

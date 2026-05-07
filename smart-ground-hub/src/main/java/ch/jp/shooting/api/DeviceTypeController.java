package ch.jp.shooting.api;

import ch.jp.shooting.exception.DeviceTemplateNotFoundException;
import ch.jp.shooting.mapper.EntityMappers;
import ch.jp.shooting.model.DeviceType;
import ch.jp.shooting.repository.DeviceTypeRepository;
import ch.jp.smartground.api.DeviceTypeApi;
import ch.jp.smartground.model.DeviceTypeResponse;
import ch.jp.smartground.model.UpdateDeviceTypeRequest;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class DeviceTypeController implements DeviceTypeApi {

    private final DeviceTypeRepository repository;

    public DeviceTypeController(DeviceTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    public ResponseEntity<List<DeviceTypeResponse>> listDeviceTypes(UUID firmwareConfigId) {
        List<DeviceTypeResponse> responses = repository.findAll().stream()
            .filter(dt -> firmwareConfigId == null || dt.getSignalType().getFirmwareConfig().getId().equals(firmwareConfigId))
            .map(this::toResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<DeviceTypeResponse> getDeviceType(@PathVariable UUID id) {
        return repository.findById(id)
            .map(dt -> ResponseEntity.ok(toResponse(dt)))
            .orElseThrow(() -> new DeviceTemplateNotFoundException(id));
    }

    @Override
    public ResponseEntity<DeviceTypeResponse> updateDeviceType(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDeviceTypeRequest request) {

        DeviceType deviceType = repository.findById(id)
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

        DeviceType saved = repository.save(deviceType);
        return ResponseEntity.ok(toResponse(saved));
    }

    private DeviceTypeResponse toResponse(DeviceType t) {
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
}

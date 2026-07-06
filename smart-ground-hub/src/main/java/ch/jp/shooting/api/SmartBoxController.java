package ch.jp.shooting.api;

import ch.jp.smartground.api.SmartBoxApi;
import ch.jp.smartground.model.PageMeta;
import ch.jp.smartground.model.SetAliasRequest;
import ch.jp.smartground.model.SmartBoxPageResponse;
import ch.jp.smartground.model.SmartBoxResponse;
import ch.jp.smartground.model.SmartBoxStatus;
import ch.jp.shooting.config.SmartBoxConfigPushService;
import ch.jp.shooting.exception.SmartBoxNotFoundException;
import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.RangePositionRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class SmartBoxController implements SmartBoxApi {

    private final SmartBoxRepository smartBoxRepository;
    private final SmartBoxConfigPushService configPushService;
    private final DeviceRepository deviceRepository;
    private final RangePositionRepository rangePositionRepository;

    public SmartBoxController(SmartBoxRepository smartBoxRepository,
                               SmartBoxConfigPushService configPushService,
                               DeviceRepository deviceRepository,
                               RangePositionRepository rangePositionRepository) {
        this.smartBoxRepository = smartBoxRepository;
        this.configPushService = configPushService;
        this.deviceRepository = deviceRepository;
        this.rangePositionRepository = rangePositionRepository;
    }

    @Override
    public ResponseEntity<SmartBoxPageResponse> listSmartBoxes(
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "50") Integer size) {

        Page<SmartBox> boxPage = smartBoxRepository.findByDeletedAtIsNull(PageRequest.of(page, size));

        SmartBoxPageResponse response = new SmartBoxPageResponse();
        response.setContent(boxPage.getContent().stream().map(this::toResponse).toList());
        response.setMeta(new PageMeta()
            .page(boxPage.getNumber())
            .size(boxPage.getSize())
            .totalElements((int) boxPage.getTotalElements())
            .totalPages(boxPage.getTotalPages()));

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SmartBoxResponse> getSmartBox(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(toResponse(findBox(id)));
    }

    @Override
    public ResponseEntity<SmartBoxResponse> setSmartBoxAlias(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SetAliasRequest request) {

        SmartBox box = findBox(id);
        box.setAlias(request.getAlias());
        return ResponseEntity.ok(toResponse(smartBoxRepository.save(box)));
    }

    @Override
    public ResponseEntity<Void> pushSmartBoxConfig(@PathVariable("id") UUID id) {
        SmartBox box = findBox(id);
        box.setConfigSynced(false);
        configPushService.push(smartBoxRepository.save(box));
        return ResponseEntity.accepted().build();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSmartBox(@PathVariable("id") UUID id) {
        SmartBox box = findBox(id);

        // Geräte hängen physisch an den GPIO-Pins dieser Box – sie werden mitgelöscht.
        // Zuvor jede Range-Position freigeben, sonst verletzt der FK range_positions.device_id.
        List<Device> devices = deviceRepository.findBySmartBoxId(id);
        for (Device device : devices) {
            RangePosition position = device.getRangePosition();
            if (position != null) {
                position.setDevice(null);
                rangePositionRepository.save(position);
            }
        }
        deviceRepository.deleteAll(devices);

        // Soft-Delete der Box selbst: Zeile bleibt für Historie, wird aber ausgeblendet.
        box.setDeletedAt(Instant.now());
        smartBoxRepository.save(box);
        return ResponseEntity.noContent().build();
    }

    private SmartBox findBox(UUID id) {
        return smartBoxRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new SmartBoxNotFoundException(id));
    }

    private SmartBoxResponse toResponse(SmartBox box) {
        SmartBoxStatus statusEnum = SmartBoxStatus.fromValue(box.getStatus().name());
        return new SmartBoxResponse()
            .id(box.getId())
            .macAddress(box.getMacAddress())
            .alias(box.getAlias())
            .status(statusEnum)
            .appVersion(box.getAppVersion())
            .firmwareVersion(box.getFirmwareVersion())
            .configSynced(box.isConfigSynced())
            .firmwareConfigId(box.getFirmwareConfig() != null ? box.getFirmwareConfig().getId() : null);
    }
}
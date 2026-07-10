package ch.jp.shooting.api;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.config.SmartBoxConfigPushService;
import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.exception.DeviceAlreadyAssignedException;
import ch.jp.shooting.exception.DeviceNotFoundException;
import ch.jp.shooting.exception.ForbiddenException;
import ch.jp.shooting.exception.RangeNotFoundException;
import ch.jp.shooting.exception.SmartBoxNotFoundException;
import ch.jp.shooting.model.auth.Permission;
import ch.jp.shooting.service.ReservationService;
import ch.jp.shooting.service.auth.PermissionService;
import ch.jp.shooting.mapper.EntityMappers;
import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.DeviceTypeGroup;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.DeviceRepository;
import ch.jp.shooting.repository.DeviceTypeGroupRepository;
import ch.jp.shooting.repository.DeviceTypeRepository;
import ch.jp.shooting.repository.RangePositionRepository;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import ch.jp.smartground.api.DeviceApi;
import ch.jp.smartground.model.AssignRangeRequest;
import ch.jp.smartground.model.CommandResponse;
import ch.jp.smartground.model.CreateDeviceRequest;
import ch.jp.smartground.model.DevicePageResponse;
import ch.jp.smartground.model.DeviceResponse;
import ch.jp.smartground.model.PageMeta;
import ch.jp.smartground.model.UpdateDeviceRequest;
import jakarta.validation.Valid;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

@RestController
@NullMarked
public class DeviceController implements DeviceApi {

    private final DeviceRepository deviceRepository;
    private final SmartBoxRepository smartBoxRepository;
    private final DeviceTypeGroupRepository deviceTypeGroupRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final RangeRepository rangeRepository;
    private final RangePositionRepository rangePositionRepository;
    private final SmartBoxConfigPushService configPushService;
    private final ReservationService reservationService;
    private final SecurityHelper securityHelper;
    private final PermissionService permissionService;

    public DeviceController(
            DeviceRepository deviceRepository,
            SmartBoxRepository smartBoxRepository,
            DeviceTypeGroupRepository deviceTypeGroupRepository,
            DeviceTypeRepository deviceTypeRepository,
            RangeRepository rangeRepository,
            RangePositionRepository rangePositionRepository,
            SmartBoxConfigPushService configPushService,
            ReservationService reservationService,
            SecurityHelper securityHelper,
            PermissionService permissionService) {
        this.deviceRepository = deviceRepository;
        this.smartBoxRepository = smartBoxRepository;
        this.deviceTypeGroupRepository = deviceTypeGroupRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.rangeRepository = rangeRepository;
        this.rangePositionRepository = rangePositionRepository;
        this.configPushService = configPushService;
        this.reservationService = reservationService;
        this.securityHelper = securityHelper;
        this.permissionService = permissionService;
    }

    @Override
    public ResponseEntity<DevicePageResponse> listDevices(
            @RequestParam(required = false) UUID rangeId,
            @RequestParam(required = false) UUID smartBoxId,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "50") Integer size) {

        Page<Device> devicePage;
        if (rangeId != null) {
            devicePage = deviceRepository.findByRangeId(rangeId, PageRequest.of(page, size));
        } else {
            devicePage = deviceRepository.findAll(PageRequest.of(page, size));
        }

        DevicePageResponse response = new DevicePageResponse();
        response.setContent(devicePage.getContent().stream()
            .map(EntityMappers::toDeviceResponse)
            .toList());
        response.setMeta(new PageMeta()
            .page(devicePage.getNumber())
            .size(devicePage.getSize())
            .totalElements((int) devicePage.getTotalElements())
            .totalPages(devicePage.getTotalPages()));

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceResponse> createDevice(
            @Valid @RequestBody CreateDeviceRequest request) {

        SmartBox box = smartBoxRepository.findById(request.getSmartBoxId())
            .orElseThrow(() -> new SmartBoxNotFoundException(request.getSmartBoxId()));

        var group = deviceTypeGroupRepository.findById(request.getGroupId())
            .orElseThrow(() -> new IllegalArgumentException("DeviceTypeGroup nicht gefunden: " + request.getGroupId()));

        var deviceType = deviceTypeRepository.findById(request.getDeviceTypeId())
            .orElseThrow(() -> new IllegalArgumentException("DeviceType nicht gefunden: " + request.getDeviceTypeId()));

        Device device = new Device();
        device.setSmartBox(box);
        device.setDeviceTypeGroup(group);
        device.setDeviceType(deviceType);
        device.setAlias(request.getAlias());

        var delayNullable = request.getDelaySignalDurationMs();
        if (delayNullable != null && delayNullable.isPresent()) {
            Integer delay = delayNullable.get();
            device.setDelaySignalDurationMs(delay != null ? delay : 0);
        } else {
            device.setDelaySignalDurationMs(0);
        }

        // Gerät optional einer Range zuordnen
        var rangeIdNullable = request.getRangeId();
        if (rangeIdNullable != null && rangeIdNullable.isPresent()) {
            UUID rangeId = rangeIdNullable.get();
            if (rangeId != null) {
                Range range = rangeRepository.findById(rangeId)
                    .orElseThrow(() -> new RangeNotFoundException(rangeId));
                device.setRange(range);
            }
        }

        Device saved = deviceRepository.save(device);

        box.setConfigSynced(false);
        SmartBox savedBox = smartBoxRepository.save(box);
        configPushService.push(savedBox);

        return ResponseEntity.status(HttpStatus.CREATED).body(EntityMappers.toDeviceResponse(saved));
    }

    @Override
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable UUID id) {
        Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new DeviceNotFoundException(id));
        return ResponseEntity.ok(EntityMappers.toDeviceResponse(device));
    }

    @Override
    public ResponseEntity<DeviceResponse> updateDevice(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDeviceRequest request) {

        Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new DeviceNotFoundException(id));

        // Gruppe neu zuordnen erfordert ADMIN (strukturelle Änderung analog zu GPIO-Pin)
        boolean changesGroup = request.getGroupId() != null;
        if (changesGroup) {
            var currentUser = securityHelper.currentUser();
            boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals("ROLE_ADMIN"));
            if (!isAdmin) {
                throw new ForbiddenException("ADMIN erforderlich um Gerätegruppe zu ändern");
            }
        }

        // Signal-Dauer ändern erfordert MANAGE_RANGES
        boolean changesSignalDuration = request.getSignalDurationMs() != null && request.getSignalDurationMs().isPresent()
            && request.getSignalDurationMs().get() != null;
        if (changesSignalDuration) {
            var currentUser = securityHelper.currentUser();
            permissionService.require(currentUser.getId(), Permission.MANAGE_RANGES);
        }

        // Alias aktualisieren
        if (request.getAlias() != null) {
            device.setAlias(request.getAlias());
        }

        // Signal-Dauer aktualisieren
        if (changesSignalDuration) {
            device.getDeviceType().setSignalDurationMs(request.getSignalDurationMs().get());
        }

        // Verzögerung aktualisieren
        if (request.getDelaySignalDurationMs() != null && request.getDelaySignalDurationMs().isPresent()) {
            Integer delay = request.getDelaySignalDurationMs().get();
            if (delay != null) {
                device.setDelaySignalDurationMs(delay);
            }
        }

        // Gruppe neu zuordnen
        if (changesGroup) {
            DeviceTypeGroup newGroup = deviceTypeGroupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new IllegalArgumentException("DeviceTypeGroup nicht gefunden: " + request.getGroupId()));
            device.setDeviceTypeGroup(newGroup);
        }

        Device saved = deviceRepository.save(device);
        return ResponseEntity.ok(EntityMappers.toDeviceResponse(saved));
    }

    @Override
    @Transactional
    public ResponseEntity<Void> deleteDevice(@PathVariable UUID id) {
        Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new DeviceNotFoundException(id));

        // Zuvor die Range-Position freigeben, sonst verletzt der FK range_positions.device_id.
        RangePosition position = device.getRangePosition();
        if (position != null) {
            position.setDevice(null);
            rangePositionRepository.save(position);
        }

        deviceRepository.delete(device);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CommandResponse> sendDeviceCommand(@PathVariable UUID id) {
        Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new DeviceNotFoundException(id));

        if (device.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gerät ist gesperrt");
        }

        // Prüfe Reservierung: nur Reserver oder Admin darf Befehle senden
        if (device.getRange() != null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication != null ? authentication.getName() : "";
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMIN"));

            if (!reservationService.canUserCommandRange(device.getRange().getId(), username, isAdmin)) {
                throw new ConflictException("Already reserved");
            }

            // Markiere Activity
            var reservation = reservationService.getActiveReservation(device.getRange().getId());
            if (reservation != null) {
                reservationService.markActivity(reservation.getId());
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
            "Command-Dispatch nicht verfügbar — wartet auf node-channel (#4)");
    }

    @Override
    public ResponseEntity<DeviceResponse> assignDeviceToRange(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRangeRequest request) {

        Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new DeviceNotFoundException(id));

        Range range = rangeRepository.findById(request.getRangeId())
            .orElseThrow(() -> new RangeNotFoundException(request.getRangeId()));

        if (device.getRange() != null && !device.getRange().getId().equals(request.getRangeId())) {
            throw new DeviceAlreadyAssignedException(id, device.getRange().getId());
        }

        device.setRange(range);
        Device saved = deviceRepository.save(device);
        return ResponseEntity.ok(EntityMappers.toDeviceResponse(saved));
    }

    @Override
    public ResponseEntity<Void> removeDeviceFromRange(@PathVariable UUID id) {
        Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new DeviceNotFoundException(id));

        if (device.getRange() == null) {
            throw new DeviceNotFoundException(id);
        }

        device.setRange(null);
        deviceRepository.save(device);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<DeviceResponse> blockDevice(@PathVariable UUID id) {
        Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new DeviceNotFoundException(id));

        // Jeder authentifizierte Benutzer darf sperren; ADMIN setzt adminBlocked zusätzlich
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_ADMIN"));

        device.setBlocked(true);
        if (isAdmin) {
            device.setAdminBlocked(true);
        }

        Device saved = deviceRepository.save(device);
        return ResponseEntity.ok(EntityMappers.toDeviceResponse(saved));
    }

    @Override
    public ResponseEntity<DeviceResponse> unblockDevice(@PathVariable UUID id) {
        Device device = deviceRepository.findById(id)
            .orElseThrow(() -> new DeviceNotFoundException(id));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_ADMIN"));

        // Entsperren wenn adminBlocked erfordert ADMIN
        if (device.isAdminBlocked() && !isAdmin) {
            throw new ForbiddenException("ADMIN erforderlich um admin-gesperrtes Gerät freizugeben");
        }

        device.setBlocked(false);
        if (isAdmin) {
            device.setAdminBlocked(false);
        }

        Device saved = deviceRepository.save(device);
        return ResponseEntity.ok(EntityMappers.toDeviceResponse(saved));
    }
}

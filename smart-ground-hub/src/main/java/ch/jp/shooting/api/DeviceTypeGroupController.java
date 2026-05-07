package ch.jp.shooting.api;

import ch.jp.shooting.model.DeviceTypeGroup;
import ch.jp.shooting.repository.DeviceTypeGroupRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/device-types/groups")
@NullMarked
public class DeviceTypeGroupController {

    private final DeviceTypeGroupRepository deviceTypeGroupRepository;

    public DeviceTypeGroupController(DeviceTypeGroupRepository deviceTypeGroupRepository) {
        this.deviceTypeGroupRepository = deviceTypeGroupRepository;
    }

    @GetMapping
    public ResponseEntity<List<DeviceTypeGroupResponse>> listGroups() {
        List<DeviceTypeGroup> groups = deviceTypeGroupRepository.findAll();
        return ResponseEntity.ok(groups.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceTypeGroupResponse> getGroup(@PathVariable UUID id) {
        return deviceTypeGroupRepository.findById(id)
            .map(group -> ResponseEntity.ok(toResponse(group)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createGroup(@RequestBody CreateGroupRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Name ist erforderlich"));
        }

        try {
            DeviceTypeGroup group = new DeviceTypeGroup();
            group.setName(request.name());
            DeviceTypeGroup created = deviceTypeGroupRepository.save(group);
            return ResponseEntity.status(201).body(toResponse(created));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(new ErrorResponse("Eine Gruppe mit diesem Namen existiert bereits"));
        }
    }

    private DeviceTypeGroupResponse toResponse(DeviceTypeGroup group) {
        return new DeviceTypeGroupResponse(group.getId(), group.getName());
    }

    public record DeviceTypeGroupResponse(UUID id, String name) {}

    public record CreateGroupRequest(String name) {}

    public record ErrorResponse(String message) {}
}

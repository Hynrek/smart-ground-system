package ch.jp.shooting.api;

import ch.jp.shooting.exception.RangeHasDevicesException;
import ch.jp.shooting.exception.RangeNameAlreadyExistsException;
import ch.jp.shooting.exception.RangeNotFoundException;
import ch.jp.shooting.exception.UserNotFoundException;
import ch.jp.shooting.mapper.EntityMappers;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.service.RangePositionService;
import ch.jp.smartground.api.RangeApi;
import ch.jp.smartground.model.AssignDeviceToPositionRequest;
import ch.jp.smartground.model.CommandResponse;
import ch.jp.smartground.model.CreateRangePositionRequest;
import ch.jp.smartground.model.CreateRangeRequest;
import ch.jp.smartground.model.DeviceResponse;
import ch.jp.smartground.model.RangeDetailResponse;
import ch.jp.smartground.model.RangePageResponse;
import ch.jp.smartground.model.RangePositionResponse;
import ch.jp.smartground.model.RangeSummaryResponse;
import ch.jp.smartground.model.SetLockedRequest;
import ch.jp.smartground.model.SetRangeAssignedUserRequest;
import org.openapitools.jackson.nullable.JsonNullable;
import ch.jp.smartground.model.UpdateRangePositionLabelRequest;
import ch.jp.smartground.model.UpdateRangeRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class RangeController implements RangeApi {

    private final RangeRepository rangeRepository;
    private final RangePositionService positionService;
    private final UserRepository userRepository;

    public RangeController(RangeRepository rangeRepository,
                           RangePositionService positionService,
                           UserRepository userRepository) {
        this.rangeRepository = rangeRepository;
        this.positionService = positionService;
        this.userRepository = userRepository;
    }

    // ── Range CRUD ────────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<RangePageResponse> listRanges(
            @Min(0) @Valid @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @Min(1) @Max(200) @Valid @RequestParam(value = "size", required = false, defaultValue = "50") Integer size) {

        Page<Range> rangePage = rangeRepository.findAll(PageRequest.of(page, size));

        RangePageResponse response = new RangePageResponse();
        response.setContent(rangePage.getContent().stream()
                .map(this::toSummaryResponse)
                .toList());
        response.setMeta(new ch.jp.smartground.model.PageMeta()
                .page(rangePage.getNumber())
                .size(rangePage.getSize())
                .totalElements((int) rangePage.getTotalElements())
                .totalPages(rangePage.getTotalPages()));

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RangeDetailResponse> getRange(UUID id) {
        Range range = rangeRepository.findById(id)
                .orElseThrow(() -> new RangeNotFoundException(id));
        return ResponseEntity.ok(EntityMappers.toRangeDetailResponse(range));
    }

    @Override
    public ResponseEntity<RangeSummaryResponse> createRange(@Valid @RequestBody CreateRangeRequest request) {
        if (rangeRepository.existsByName(request.getName())) {
            throw new RangeNameAlreadyExistsException(request.getName());
        }
        Range range = new Range();
        range.setName(request.getName());
        range.setDescription(request.getDescription());
        Range saved = rangeRepository.save(range);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummaryResponse(saved));
    }

    @Override
    public ResponseEntity<RangeSummaryResponse> updateRange(UUID id, @Valid @RequestBody UpdateRangeRequest request) {
        Range range = rangeRepository.findById(id)
                .orElseThrow(() -> new RangeNotFoundException(id));
        if (!range.getName().equals(request.getName()) && rangeRepository.existsByName(request.getName())) {
            throw new RangeNameAlreadyExistsException(request.getName());
        }
        range.setName(request.getName());
        range.setDescription(request.getDescription());
        Range saved = rangeRepository.save(range);
        return ResponseEntity.ok(toSummaryResponse(saved));
    }

    @Override
    public ResponseEntity<Void> deleteRange(UUID id) {
        Range range = rangeRepository.findById(id)
                .orElseThrow(() -> new RangeNotFoundException(id));
        if (!range.getDevices().isEmpty()) {
            throw new RangeHasDevicesException(id, range.getDevices().size());
        }
        rangeRepository.delete(range);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RangeSummaryResponse> setRangeLocked(UUID id, @Valid @RequestBody SetLockedRequest request) {
        Range range = rangeRepository.findById(id)
                .orElseThrow(() -> new RangeNotFoundException(id));
        range.setLocked(request.getLocked());
        Range saved = rangeRepository.save(range);
        return ResponseEntity.ok(toSummaryResponse(saved));
    }

    @Override
    public ResponseEntity<RangeSummaryResponse> setRangeAssignedUser(UUID id, @Valid @RequestBody SetRangeAssignedUserRequest request) {
        Range range = rangeRepository.findById(id)
                .orElseThrow(() -> new RangeNotFoundException(id));
        JsonNullable<UUID> userIdNullable = request.getUserId();
        if (userIdNullable.isPresent() && userIdNullable.get() != null) {
            UUID userId = userIdNullable.get();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            range.setAssignedUser(user);
        } else {
            range.setAssignedUser(null);
        }
        Range saved = rangeRepository.save(range);
        return ResponseEntity.ok(toSummaryResponse(saved));
    }

    // ── Range Positions ───────────────────────────────────────────────────────

    @Override
    public ResponseEntity<List<RangePositionResponse>> listRangePositions(UUID id) {
        return ResponseEntity.ok(positionService.listPositions(id));
    }

    @Override
    public ResponseEntity<RangePositionResponse> createRangePosition(
            UUID id, @Valid @RequestBody CreateRangePositionRequest request) {
        var label = request.getLabel();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(positionService.createPosition(id, label));
    }

    @Override
    public ResponseEntity<RangePositionResponse> updateRangePosition(
            UUID id, UUID positionId, @Valid @RequestBody UpdateRangePositionLabelRequest request) {
        return ResponseEntity.ok(positionService.renamePosition(id, positionId, request.getLabel()));
    }

    @Override
    public ResponseEntity<Void> deleteRangePosition(UUID id, UUID positionId) {
        positionService.deletePosition(id, positionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RangePositionResponse> assignDeviceToPosition(
            UUID id, UUID positionId, @Valid @RequestBody AssignDeviceToPositionRequest request) {
        return ResponseEntity.ok(positionService.assignDevice(id, positionId, request.getDeviceId()));
    }

    @Override
    public ResponseEntity<RangePositionResponse> removeDeviceFromPosition(UUID id, UUID positionId) {
        return ResponseEntity.ok(positionService.removeDevice(id, positionId));
    }

    @Override
    public ResponseEntity<CommandResponse> sendPositionCommand(UUID id, UUID positionId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : "";
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMIN"));
        return ResponseEntity.ok(positionService.sendPositionCommand(id, positionId, username, isAdmin));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private RangeSummaryResponse toSummaryResponse(Range range) {
        var assignedUser = range.getAssignedUser();
        return new RangeSummaryResponse()
                .id(range.getId())
                .name(range.getName())
                .description(range.getDescription())
                .locked(range.isLocked())
                .deviceCount(range.getDevices().size())
                .assignedUserId(assignedUser != null ? assignedUser.getId() : null)
                .createdAt(range.getCreatedAt() != null
                        ? range.getCreatedAt().atOffset(java.time.ZoneOffset.UTC)
                        : null);
    }
}

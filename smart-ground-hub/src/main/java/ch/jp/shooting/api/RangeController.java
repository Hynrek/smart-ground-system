package ch.jp.shooting.api;

import ch.jp.shooting.exception.RangeHasDevicesException;
import ch.jp.shooting.exception.RangeNameAlreadyExistsException;
import ch.jp.shooting.exception.RangeNotFoundException;
import ch.jp.shooting.mapper.EntityMappers;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.smartground.api.RangeApi;
import ch.jp.smartground.model.CreateRangeRequest;
import ch.jp.smartground.model.DeviceResponse;
import ch.jp.smartground.model.RangeDetailResponse;
import ch.jp.smartground.model.RangePageResponse;
import ch.jp.smartground.model.RangeSummaryResponse;
import ch.jp.smartground.model.SetLockedRequest;
import ch.jp.smartground.model.UpdateRangeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
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

    public RangeController(RangeRepository rangeRepository) {
        this.rangeRepository = rangeRepository;
    }

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
        return ResponseEntity.ok(toDetailResponse(range));
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toSummaryResponse(saved));
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

    private RangeSummaryResponse toSummaryResponse(Range range) {
        return new RangeSummaryResponse()
                .id(range.getId())
                .name(range.getName())
                .description(range.getDescription())
                .locked(range.isLocked())
                .deviceCount(range.getDevices().size())
                .createdAt(range.getCreatedAt() != null
                        ? range.getCreatedAt().atOffset(java.time.ZoneOffset.UTC)
                        : null);
    }

    private RangeDetailResponse toDetailResponse(Range range) {
        List<DeviceResponse> deviceResponses = range.getDevices().stream()
                .map(EntityMappers::toDeviceResponse)
                .toList();
        return new RangeDetailResponse()
                .id(range.getId())
                .name(range.getName())
                .description(range.getDescription())
                .locked(range.isLocked())
                .devices(deviceResponses)
                .createdAt(range.getCreatedAt() != null
                        ? range.getCreatedAt().atOffset(java.time.ZoneOffset.UTC)
                        : null);
    }
}
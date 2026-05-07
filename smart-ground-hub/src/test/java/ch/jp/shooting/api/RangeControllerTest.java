package ch.jp.shooting.api;

import ch.jp.smartground.model.CreateRangeRequest;
import ch.jp.smartground.model.RangeDetailResponse;
import ch.jp.smartground.model.RangeSummaryResponse;
import ch.jp.smartground.model.UpdateRangeRequest;
import ch.jp.shooting.exception.RangeHasDevicesException;
import ch.jp.shooting.exception.RangeNameAlreadyExistsException;
import ch.jp.shooting.exception.RangeNotFoundException;
import ch.jp.shooting.model.Device;
import ch.jp.shooting.model.DeviceType;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.RangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RangeController")
class RangeControllerTest {

    @Mock
    private RangeRepository rangeRepository;

    @InjectMocks
    private RangeController controller;

    private Range testRange;
    private Device testDevice;
    private UUID rangeId;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        rangeId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID smartBoxId = UUID.randomUUID();

        testRange = new Range();
        testRange.setId(rangeId);
        testRange.setName("Main Range");
        testRange.setDescription("Primary shooting range");
        testRange.setLocked(false);
        testRange.setCreatedAt(java.time.Instant.parse("2026-04-21T10:00:00Z"));

        SmartBox smartBox = new SmartBox();
        smartBox.setId(smartBoxId);

        DeviceType template = new DeviceType();
        template.setId(templateId);

        testDevice = new Device();
        testDevice.setId(deviceId);
        testDevice.setAlias("DEV-001");
        testDevice.setSmartBox(smartBox);
    }

    @Test
    @DisplayName("listRanges returns all ranges")
    void listRanges_returnsAllRanges() {
        when(rangeRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(testRange)));

        var result = controller.listRanges(0, 50);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).hasSize(1);
        assertThat(result.getBody().getContent().get(0).getId()).isEqualTo(rangeId);
        assertThat(result.getBody().getContent().get(0).getName()).isEqualTo("Main Range");
    }

    @Test
    @DisplayName("getRange returns range with devices")
    void getRange_returnsWithDevices() {
        when(rangeRepository.findById(rangeId)).thenReturn(Optional.of(testRange));

        var result = controller.getRange(rangeId);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(rangeId);
        assertThat(result.getBody().getName()).isEqualTo("Main Range");
    }

    @Test
    @DisplayName("getRange throws RangeNotFoundException when not found")
    void getRange_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(rangeRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getRange(unknownId))
                .isInstanceOf(RangeNotFoundException.class);
    }

    @Test
    @DisplayName("createRange creates new range")
    void createRange_createsNewRange() {
        UUID newId = UUID.randomUUID();
        when(rangeRepository.existsByName("New Range")).thenReturn(false);
        when(rangeRepository.save(any(Range.class))).thenAnswer(inv -> {
            Range r = inv.getArgument(0);
            r.setId(newId);
            return r;
        });

        var request = new CreateRangeRequest("New Range");
        request.setDescription("Description");
        ResponseEntity<RangeSummaryResponse> result = controller.createRange(request);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("New Range");
    }

    @Test
    @DisplayName("createRange throws RangeNameAlreadyExistsException when name exists")
    void createRange_throwsNameExists() {
        when(rangeRepository.existsByName("Existing")).thenReturn(true);

        var request = new CreateRangeRequest("Existing");
        request.setDescription(null);

        assertThatThrownBy(() -> controller.createRange(request))
                .isInstanceOf(RangeNameAlreadyExistsException.class);
    }

    @Test
    @DisplayName("updateRange updates existing range")
    void updateRange_updatesRange() {
        when(rangeRepository.findById(rangeId)).thenReturn(Optional.of(testRange));
        when(rangeRepository.existsByName("Updated Name")).thenReturn(false);
        when(rangeRepository.save(any(Range.class))).thenReturn(testRange);

        var request = new UpdateRangeRequest("Updated Name");
        request.setDescription("New desc");
        var result = controller.updateRange(rangeId, request);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("deleteRange deletes range when empty")
    void deleteRange_deletesWhenEmpty() {
        when(rangeRepository.findById(rangeId)).thenReturn(Optional.of(testRange));

        var result = controller.deleteRange(rangeId);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    @DisplayName("deleteRange throws RangeHasDevicesException when devices exist")
    void deleteRange_throwsHasDevices() {
        testRange.setDevices(List.of(testDevice));
        when(rangeRepository.findById(rangeId)).thenReturn(Optional.of(testRange));

        assertThatThrownBy(() -> controller.deleteRange(rangeId))
                .isInstanceOf(RangeHasDevicesException.class);
    }

}
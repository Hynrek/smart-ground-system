package ch.jp.shooting.api;

import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.service.TestDataService;
import ch.jp.smartground.api.TestingApi;
import ch.jp.smartground.model.CreateMockSmartBoxRequest;
import ch.jp.smartground.model.CreateTestUserRequest;
import ch.jp.smartground.model.MockSmartBoxResponse;
import ch.jp.smartground.model.SeedRangesResponse;
import ch.jp.smartground.model.SeededRange;
import ch.jp.smartground.model.TestUserResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Admin-only Dev-Tooling-Endpunkte zum Anlegen von Testdaten (Benutzer, Ranges, Mock-SmartBoxes).
@RestController
@NullMarked
public class TestingController implements TestingApi {

    private final TestDataService testDataService;

    public TestingController(TestDataService testDataService) {
        this.testDataService = testDataService;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TestUserResponse> createTestUser(CreateTestUserRequest createTestUserRequest) {
        User user = testDataService.createTestUser(createTestUserRequest.getCredential());
        TestUserResponse body = new TestUserResponse()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SeedRangesResponse> seedTestRanges() {
        List<SeededRange> ranges = testDataService.seedRanges().stream()
                .map(sr -> {
                    Range r = sr.range();
                    return new SeededRange()
                            .id(r.getId())
                            .name(r.getName())
                            .created(sr.created())
                            .positionsCreated(sr.positionsCreated())
                            .boxesCreated(sr.boxesCreated())
                            .devicesAssigned(sr.devicesAssigned());
                })
                .toList();
        return ResponseEntity.ok(new SeedRangesResponse().ranges(ranges));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MockSmartBoxResponse> createMockSmartBox(CreateMockSmartBoxRequest createMockSmartBoxRequest) {
        String alias = createMockSmartBoxRequest.getAlias().orElse(null);
        SmartBox box = testDataService.createMockSmartBox(createMockSmartBoxRequest.getDeviceCount(), alias);
        MockSmartBoxResponse body = new MockSmartBoxResponse()
                .id(box.getId())
                .macAddress(box.getMacAddress())
                .deviceCount(createMockSmartBoxRequest.getDeviceCount());
        if (box.getAlias() != null) {
            body.alias(box.getAlias());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}

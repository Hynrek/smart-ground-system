package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.model.*;
import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.*;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.repository.auth.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestDataServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RangeRepository rangeRepository;
    @Mock RangePositionRepository positionRepository;
    @Mock SmartBoxRepository smartBoxRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock DeviceTypeRepository deviceTypeRepository;
    @Mock DeviceTypeGroupRepository deviceTypeGroupRepository;
    @Mock FirmwareConfigRepository firmwareConfigRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks TestDataService service;

    private static final List<String> RANGE_NAMES =
            List.of("Vorderlader", "Trapstand", "Rollhase", "Kippreh");

    @Test
    void createTestUser_createsShooterWithDerivedFields() {
        when(userRepository.findByUsernameLower("bob")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("bob@test.local")).thenReturn(Optional.empty());
        when(roleRepository.findByName("SHOOTER")).thenReturn(Optional.of(new Role("SHOOTER", "x")));
        when(passwordEncoder.encode("bob")).thenReturn("ENC");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.createTestUser("bob");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("bob");
        assertThat(saved.getEmail()).isEqualTo("bob@test.local");
        assertThat(saved.getPasswordHash()).isEqualTo("ENC");
        assertThat(saved.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        verify(userRoleRepository).save(any());
        assertThat(result.getUsername()).isEqualTo("bob");
    }

    @Test
    void createTestUser_duplicateUsername_throwsConflict() {
        when(userRepository.findByUsernameLower("bob")).thenReturn(Optional.of(new User()));
        assertThatThrownBy(() -> service.createTestUser("bob"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createTestUser_blankCredential_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createTestUser("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createTestUser_credentialWithAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createTestUser("a@b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seedRanges_createsMissingLeavesExisting() {
        Range existing = new Range();
        existing.setId(UUID.randomUUID());
        existing.setName("Trapstand");
        when(rangeRepository.findByName("Vorderlader")).thenReturn(Optional.empty());
        when(rangeRepository.findByName("Trapstand")).thenReturn(Optional.of(existing));
        when(rangeRepository.findByName("Rollhase")).thenReturn(Optional.empty());
        when(rangeRepository.findByName("Kippreh")).thenReturn(Optional.empty());
        when(rangeRepository.save(any(Range.class))).thenAnswer(inv -> {
            Range r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        // Isolate range-creation behavior: treat every range as already fully wired
        // so this test doesn't need to stub firmware/box/device resolution.
        when(positionRepository.countByRangeId(any())).thenReturn(8);

        List<TestDataService.SeededRange> result = service.seedRanges();

        Map<String, Boolean> createdByName = new HashMap<>();
        result.forEach(r -> createdByName.put(r.range().getName(), r.created()));
        assertThat(createdByName.get("Vorderlader")).isTrue();
        assertThat(createdByName.get("Trapstand")).isFalse();
        assertThat(createdByName.get("Rollhase")).isTrue();
        assertThat(createdByName.get("Kippreh")).isTrue();
        // 3 new ranges saved, the existing one not re-saved
        verify(rangeRepository, times(3)).save(any(Range.class));
        result.forEach(r -> {
            assertThat(r.positionsCreated()).isZero();
            assertThat(r.boxesCreated()).isZero();
            assertThat(r.devicesAssigned()).isZero();
        });
        verifyNoInteractions(firmwareConfigRepository, deviceTypeGroupRepository, deviceTypeRepository);
    }

    @Test
    void seedRanges_freshDatabase_createsFullyWiredSetupPerRange() {
        mockFreshRanges();
        mockPositionRepositorySaveIdentity();
        mockWerferDeviceTypeResolution();
        when(smartBoxRepository.findByMacAddress(anyString())).thenReturn(Optional.empty());
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TestDataService.SeededRange> result = service.seedRanges();

        assertThat(result).hasSize(4);
        result.forEach(r -> {
            assertThat(r.created()).isTrue();
            assertThat(r.positionsCreated()).isEqualTo(8);
            assertThat(r.boxesCreated()).isEqualTo(2);
            assertThat(r.devicesAssigned()).isEqualTo(8);
        });
        // 4 ranges x 2 boxes
        verify(smartBoxRepository, times(8)).save(any(SmartBox.class));
        // 4 ranges x 8 devices
        verify(deviceRepository, times(32)).save(any(Device.class));
    }

    @Test
    void seedRanges_freshDatabase_wiresPositionsToDevicesInOrder() {
        mockFreshRanges();
        mockPositionRepositorySaveIdentity();
        mockWerferDeviceTypeResolution();
        when(smartBoxRepository.findByMacAddress(anyString())).thenReturn(Optional.empty());
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        service.seedRanges();

        ArgumentCaptor<RangePosition> captor = ArgumentCaptor.forClass(RangePosition.class);
        verify(positionRepository, atLeastOnce()).save(captor.capture());

        Map<String, RangePosition> vorderladerByLabel = new HashMap<>();
        captor.getAllValues().stream()
                .filter(p -> p.getRange().getName().equals("Vorderlader"))
                .filter(p -> p.getDevice() != null)
                .forEach(p -> vorderladerByLabel.put(p.getLabel(), p));

        assertThat(vorderladerByLabel).hasSize(8);
        assertThat(vorderladerByLabel.get("A").getDevice().getAlias()).isEqualTo("Werfer A");
        assertThat(vorderladerByLabel.get("A").getDevice().getSmartBox().getAlias())
                .isEqualTo("Vorderlader Box 1");
        assertThat(vorderladerByLabel.get("D").getDevice().getSmartBox().getAlias())
                .isEqualTo("Vorderlader Box 1");
        assertThat(vorderladerByLabel.get("E").getDevice().getSmartBox().getAlias())
                .isEqualTo("Vorderlader Box 2");
        assertThat(vorderladerByLabel.get("H").getDevice().getSmartBox().getAlias())
                .isEqualTo("Vorderlader Box 2");
        assertThat(vorderladerByLabel.get("E").getDevice().getAlias()).isEqualTo("Werfer E");
        assertThat(vorderladerByLabel.get("A").getDevice().getRange().getName()).isEqualTo("Vorderlader");
    }

    @Test
    void seedRanges_secondCall_isNoOpWhenAlreadyFullyWired() {
        for (String name : RANGE_NAMES) {
            Range existing = new Range();
            existing.setId(UUID.randomUUID());
            existing.setName(name);
            when(rangeRepository.findByName(name)).thenReturn(Optional.of(existing));
        }
        when(positionRepository.countByRangeId(any())).thenReturn(8);

        List<TestDataService.SeededRange> result = service.seedRanges();

        result.forEach(r -> {
            assertThat(r.created()).isFalse();
            assertThat(r.positionsCreated()).isZero();
            assertThat(r.boxesCreated()).isZero();
            assertThat(r.devicesAssigned()).isZero();
        });
        verify(rangeRepository, never()).save(any());
        verify(positionRepository, never()).save(any());
        verify(smartBoxRepository, never()).save(any());
        verify(deviceRepository, never()).save(any());
        verifyNoInteractions(firmwareConfigRepository, deviceTypeGroupRepository, deviceTypeRepository);
    }

    @Test
    void seedRanges_partiallyWiredRange_isLeftUntouched() {
        Map<String, UUID> ids = new HashMap<>();
        for (String name : RANGE_NAMES) {
            Range existing = new Range();
            UUID id = UUID.randomUUID();
            existing.setId(id);
            existing.setName(name);
            ids.put(name, id);
            when(rangeRepository.findByName(name)).thenReturn(Optional.of(existing));
        }
        // Trapstand was manually half-configured; everything else is already fully wired.
        when(positionRepository.countByRangeId(ids.get("Trapstand"))).thenReturn(3);
        when(positionRepository.countByRangeId(ids.get("Vorderlader"))).thenReturn(8);
        when(positionRepository.countByRangeId(ids.get("Rollhase"))).thenReturn(8);
        when(positionRepository.countByRangeId(ids.get("Kippreh"))).thenReturn(8);

        List<TestDataService.SeededRange> result = service.seedRanges();

        TestDataService.SeededRange trapstand = result.stream()
                .filter(r -> r.range().getName().equals("Trapstand"))
                .findFirst().orElseThrow();
        assertThat(trapstand.created()).isFalse();
        assertThat(trapstand.positionsCreated()).isZero();
        assertThat(trapstand.boxesCreated()).isZero();
        assertThat(trapstand.devicesAssigned()).isZero();
        verify(positionRepository, never()).save(any());
        verify(smartBoxRepository, never()).save(any());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void seedRanges_missingFirmwareConfig_throwsIllegalState() {
        mockFreshRanges();
        mockPositionRepositorySaveIdentity();
        when(firmwareConfigRepository.findByVersionAndBoxType("0.6", "xiao-esp32s3"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.seedRanges())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createMockSmartBox_createsBoxAndDevices() {
        DeviceTypeGroup group = new DeviceTypeGroup("Wurfmaschine");
        DeviceType werfer = new DeviceType();
        werfer.setName("Werfer");
        werfer.setGroup(group);
        FirmwareConfig fw = new FirmwareConfig("0.6", "xiao-esp32s3");

        when(firmwareConfigRepository.findByVersionAndBoxType("0.6", "xiao-esp32s3"))
                .thenReturn(Optional.of(fw));
        when(deviceTypeGroupRepository.findByName("Wurfmaschine")).thenReturn(Optional.of(group));
        when(deviceTypeRepository.findByGroupIdAndSignalType_FirmwareConfigId(any(), any()))
                .thenReturn(Optional.of(werfer));
        when(smartBoxRepository.findByMacAddress(anyString())).thenReturn(Optional.empty());
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        SmartBox box = service.createMockSmartBox(3, "Mock-1");

        assertThat(box.getAlias()).isEqualTo("Mock-1");
        assertThat(box.getStatus()).isEqualTo(SmartBoxStates.OFFLINE);
        assertThat(box.getMacAddress()).isNotBlank();
        verify(deviceRepository, times(3)).save(any(Device.class));
    }

    @Test
    void createMockSmartBox_deviceCountZero_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createMockSmartBox(0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createMockSmartBox_deviceCountTooHigh_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createMockSmartBox(51, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Shared test setup helpers ────────────────────────────────────────────

    private void mockFreshRanges() {
        for (String name : RANGE_NAMES) {
            lenient().when(rangeRepository.findByName(name)).thenReturn(Optional.empty());
        }
        lenient().when(rangeRepository.save(any(Range.class))).thenAnswer(inv -> {
            Range r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    private void mockPositionRepositorySaveIdentity() {
        when(positionRepository.countByRangeId(any())).thenReturn(0);
        when(positionRepository.save(any(RangePosition.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void mockWerferDeviceTypeResolution() {
        DeviceTypeGroup group = new DeviceTypeGroup("Wurfmaschine");
        DeviceType werfer = new DeviceType();
        werfer.setName("Werfer");
        werfer.setGroup(group);
        FirmwareConfig fw = new FirmwareConfig("0.6", "xiao-esp32s3");
        when(firmwareConfigRepository.findByVersionAndBoxType("0.6", "xiao-esp32s3"))
                .thenReturn(Optional.of(fw));
        when(deviceTypeGroupRepository.findByName("Wurfmaschine")).thenReturn(Optional.of(group));
        when(deviceTypeRepository.findByGroupIdAndSignalType_FirmwareConfigId(any(), any()))
                .thenReturn(Optional.of(werfer));
    }
}

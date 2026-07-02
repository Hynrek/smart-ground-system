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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestDataServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RangeRepository rangeRepository;
    @Mock SmartBoxRepository smartBoxRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock DeviceTypeRepository deviceTypeRepository;
    @Mock DeviceTypeGroupRepository deviceTypeGroupRepository;
    @Mock FirmwareConfigRepository firmwareConfigRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks TestDataService service;

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
        existing.setName("Trapstand");
        when(rangeRepository.findByName("Vorderlader")).thenReturn(Optional.empty());
        when(rangeRepository.findByName("Trapstand")).thenReturn(Optional.of(existing));
        when(rangeRepository.findByName("Rollhase")).thenReturn(Optional.empty());
        when(rangeRepository.findByName("Kippreh")).thenReturn(Optional.empty());
        when(rangeRepository.save(any(Range.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TestDataService.SeededRange> result = service.seedRanges();

        Map<String, Boolean> createdByName = new java.util.HashMap<>();
        result.forEach(r -> createdByName.put(r.range().getName(), r.created()));
        assertThat(createdByName.get("Vorderlader")).isTrue();
        assertThat(createdByName.get("Trapstand")).isFalse();
        assertThat(createdByName.get("Rollhase")).isTrue();
        assertThat(createdByName.get("Kippreh")).isTrue();
        // 3 new ranges saved, the existing one not re-saved
        verify(rangeRepository, times(3)).save(any(Range.class));
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
}

package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
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
}

package ch.jp.shooting.service;

import ch.jp.shooting.exception.UserNotFoundException;
import ch.jp.shooting.mapper.UserMapper;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.ScopedAccessRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.service.auth.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock ScopedAccessRepository scopedAccessRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserMapper userMapper;

    @InjectMocks UserService userService;

    @Test
    void getUserById_notFound_throwsUserNotFoundException() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> userService.getUserById(UUID.randomUUID()));
    }

    @Test
    void getUserByEmail_notFound_throwsUserNotFoundException() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> userService.getUserByEmail("nobody@example.com"));
    }
}

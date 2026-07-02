package ch.jp.shooting.service;

import ch.jp.shooting.exception.UserNotFoundException;
import ch.jp.shooting.mapper.UserMapper;
import ch.jp.shooting.model.auth.User;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceQrTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock ScopedAccessRepository scopedAccessRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserMapper userMapper;

    @InjectMocks UserService userService;

    private User userWithoutToken() {
        return new User("anna@test.local", "Anna", "Muster");
    }

    @Test
    void getOrCreateQrToken_generatesAndPersistsTokenOnFirstAccess() {
        UUID id = UUID.randomUUID();
        User user = userWithoutToken();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String token = userService.getOrCreateQrToken(id);

        assertNotNull(token);
        assertEquals(token, user.getQrToken());
        verify(userRepository).save(user);
    }

    @Test
    void getOrCreateQrToken_returnsExistingTokenWithoutSaving() {
        UUID id = UUID.randomUUID();
        User user = userWithoutToken();
        user.setQrToken("existing-token");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        String token = userService.getOrCreateQrToken(id);

        assertEquals("existing-token", token);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rotateQrToken_replacesTheToken() {
        UUID id = UUID.randomUUID();
        User user = userWithoutToken();
        user.setQrToken("old-token");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String newToken = userService.rotateQrToken(id);

        assertNotNull(newToken);
        assertNotEquals("old-token", newToken);
        assertEquals(newToken, user.getQrToken());
    }

    @Test
    void getUserByQrToken_unknownTokenThrowsNotFound() {
        when(userRepository.findByQrToken("nope")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserByQrToken("nope"));
    }
}

package ch.jp.shooting.service;

import ch.jp.shooting.dto.CreateUserRequest;
import ch.jp.shooting.dto.UpdateUserRequest;
import ch.jp.shooting.exception.ConflictException;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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

    @Test
    void createUser_duplicateUsername_throwsConflict() {
        CreateUserRequest req = new CreateUserRequest("a@b.ch", "pw", "Jonas", "Studer");
        req.setUsername("JonasS");

        lenient().when(userRepository.findByEmail("a@b.ch")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameLower("jonass"))
                .thenReturn(Optional.of(new User()));

        assertThrows(ConflictException.class, () -> userService.createUser(req));
    }

    @Test
    void createUser_invalidUsername_throwsIllegalArgument() {
        CreateUserRequest req = new CreateUserRequest("a@b.ch", "pw", "Jonas", "Studer");
        req.setUsername("ab"); // zu kurz

        lenient().when(userRepository.findByEmail("a@b.ch")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
    }

    @Test
    void updateUser_usernameTakenByAnotherUser_throwsConflict() {
        UUID targetId = UUID.randomUUID();
        User target = new User("a@b.ch", "Jonas", "Studer");
        target.setUsername("JonasS");

        User other = new User("c@d.ch", "Other", "Person");
        other.setUsername("taken");

        UpdateUserRequest req = new UpdateUserRequest();
        req.setUsername("taken");

        target.setId(targetId);
        other.setId(UUID.randomUUID());

        when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(userRepository.findByUsernameLower("taken")).thenReturn(Optional.of(other));

        assertThrows(ConflictException.class, () -> userService.updateUser(targetId, req));
    }
}

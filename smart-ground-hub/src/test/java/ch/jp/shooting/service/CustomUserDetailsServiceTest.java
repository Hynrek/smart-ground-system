package ch.jp.shooting.service;

import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks CustomUserDetailsService service;

    @Test
    void loadUserByUsername_resolvesByUsername_butPrincipalIsEmail() {
        User user = new User("jonas@example.com", "Jonas", "Studer");
        user.setUsername("JonasS");
        user.setPasswordHash("hash");
        when(userRepository.findByEmailOrUsernameWithRoles("jonass"))
                .thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("jonass");

        // JWT-Subject bleibt die kanonische E-Mail
        assertEquals("jonas@example.com", details.getUsername());
    }
}

package ch.jp.shooting.repository.auth;

import ch.jp.shooting.model.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// @DataJpaTest is in spring-boot-data-jpa-test, a separate module not on this classpath.
// Following the same pattern as CompetitionTiebreakerRepositoryTest: full context + H2 profile.
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class UserRepositoryTest {

    @Autowired UserRepository userRepository;

    @BeforeEach
    void seed() {
        User u = new User("jonas@example.com", "Jonas", "Studer");
        u.setUsername("JonasS");
        userRepository.save(u);
    }

    @Test
    void findByEmailOrUsername_matchesEmailCaseInsensitive() {
        Optional<User> found = userRepository.findByEmailOrUsernameWithRoles("JONAS@example.com");
        assertTrue(found.isPresent());
        assertEquals("JonasS", found.get().getUsername());
    }

    @Test
    void findByEmailOrUsername_matchesUsernameCaseInsensitive() {
        Optional<User> found = userRepository.findByEmailOrUsernameWithRoles("jonass");
        assertTrue(found.isPresent());
        assertEquals("jonas@example.com", found.get().getEmail());
    }

    @Test
    void findByUsernameLower_returnsUser() {
        assertTrue(userRepository.findByUsernameLower("jonass").isPresent());
    }
}

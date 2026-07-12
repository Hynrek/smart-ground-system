package ch.jp.shooting.api;

import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// spring-boot-test-autoconfigure in this build carries no web/servlet slice (no
// @AutoConfigureMockMvc) — build MockMvc manually from the real WebApplicationContext instead,
// same as the rest of this suite (see UserControllerTest/RoleControllerTest). Applying
// springSecurity() keeps the real SecurityConfig filter chain in the loop, which is the point
// of this test: proving the new permitAll rule actually works end-to-end.
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class SyncControllerSyncSerienTest {

    @Autowired WebApplicationContext wac;
    @Autowired SerieRepository serieRepository;
    @Autowired UserRepository userRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
    }

    private User owner() {
        var u = new User("sync-" + UUID.randomUUID() + "@example.com", "Sync", "Test");
        u.setUsername("sync-" + UUID.randomUUID());
        u.setPasswordHash("x");
        return userRepository.save(u);
    }

    private void serie(User o, String name, boolean deleted) {
        var s = new Serie();
        s.setName(name);
        s.setOwnership("user");
        s.setStepsJson("[]");
        s.setOwner(o);
        s.setDeleted(deleted);
        serieRepository.save(s);
    }

    @Test
    void syncSerien_returnsPageIncludingTombstone_unauthenticated() throws Exception {
        var o = owner();
        serie(o, "alive", false);
        serie(o, "dead", true);

        mockMvc.perform(get("/api/sync/serien").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.nextCursor").exists())
            .andExpect(jsonPath("$.hasMore").exists())
            // at least one tombstone present
            .andExpect(jsonPath("$.items[?(@.deleted == true)].name").exists());
    }

    @Test
    void syncSerien_limitOne_setsHasMoreTrue() throws Exception {
        var o = owner();
        serie(o, "a", false);
        serie(o, "b", false);

        mockMvc.perform(get("/api/sync/serien").param("limit", "1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.hasMore").value(true));
    }
}

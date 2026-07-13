package ch.jp.shooting.api;

import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// spring-boot-test-autoconfigure in this build carries no web/servlet slice (no
// @AutoConfigureMockMvc) — build MockMvc manually from the real WebApplicationContext instead,
// same as the rest of this suite (see SyncControllerSyncSerienTest/UserControllerTest). Applying
// springSecurity() keeps the real SecurityConfig filter chain in the loop, which is the point
// of this test: proving the new /api/outbox/** permitAll rule actually works end-to-end.
@SpringBootTest
@ActiveProfiles("h2")
@Transactional
class OutboxControllerPushSerieTest {

    @Autowired WebApplicationContext wac;
    @Autowired UserRepository userRepository;

    MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
    }

    private User owner() {
        var u = new User("outbox-" + UUID.randomUUID() + "@example.com", "Outbox", "Test");
        u.setUsername("outbox-" + UUID.randomUUID());
        u.setPasswordHash("x");
        return userRepository.save(u);
    }

    @Test
    void pushSerieOutboxItem_unauthenticated_createsAndReturnsAccepted() throws Exception {
        var owner = owner();
        var id = UUID.randomUUID();
        var body = Map.of(
                "id", id.toString(),
                "name", "Offline-Serie",
                "ownership", "user",
                "ownerId", owner.getId().toString(),
                "stepsJson", "[]",
                "published", false
        );

        mockMvc.perform(post("/api/outbox/serien")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void pushSerieOutboxItem_sameBodyTwice_secondCallIsIdempotent() throws Exception {
        var owner = owner();
        var id = UUID.randomUUID();
        var body = Map.of(
                "id", id.toString(),
                "name", "Offline-Serie",
                "ownership", "user",
                "ownerId", owner.getId().toString(),
                "stepsJson", "[]",
                "published", false
        );
        String json = mapper.writeValueAsString(body);

        mockMvc.perform(post("/api/outbox/serien").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mockMvc.perform(post("/api/outbox/serien").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }
}

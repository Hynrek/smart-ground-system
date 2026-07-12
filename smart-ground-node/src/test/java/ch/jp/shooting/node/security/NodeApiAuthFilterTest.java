package ch.jp.shooting.node.security;

import ch.jp.shooting.node.onboarding.OnboardingController;
import ch.jp.shooting.node.onboarding.OnboardingService;
import ch.jp.shooting.node.onboarding.PendingBoxRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NodeApiAuthFilterTest {

    private static final String SECRET = "bWVpbi1zZWNyZXQta2V5LWZ1ZXItc21hcnQtZ3JvdW5kLWlzdC1sYW5n";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PendingBoxRegistry registry = new PendingBoxRegistry();
        OnboardingService service = new OnboardingService(registry, null, null,
                (d, f) -> { }, "30:ae:a4:1f:2b:3c", "S", "P", "https://x");
        NodeApiAuthFilter filter = new NodeApiAuthFilter(new NodeJwtVerifier(SECRET));
        mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(registry, service))
                .addFilters(filter).build();
    }

    private static String validJwt() throws Exception {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String header = url.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = url.encodeToString(
                ("{\"sub\":\"admin\",\"exp\":" + (Instant.now().getEpochSecond() + 3600) + "}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"));
        String sig = url.encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII)));
        return header + "." + payload + "." + sig;
    }

    @Test
    void noToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/node-api/v1/onboarding/pending")).andExpect(status().isUnauthorized());
    }

    @Test
    void validToken_passesThrough() throws Exception {
        mockMvc.perform(get("/node-api/v1/onboarding/pending").header("Authorization", "Bearer " + validJwt()))
            .andExpect(status().isOk());
    }
}

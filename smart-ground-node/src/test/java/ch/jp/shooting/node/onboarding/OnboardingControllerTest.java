package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * standaloneSetup with real beans under @SpringBootTest @Transactional — same offline
 * constraint and pattern as BoxDiscoveryControllerTest. The pending registry is in-RAM;
 * this test seeds it via onHello and constructs the OnboardingService with a no-op RadioSender.
 */
@SpringBootTest
@Transactional
class OnboardingControllerTest {

    @Autowired
    private PendingBoxRegistry registry;
    @Autowired
    private ProvisioningTokenService tokenService;
    @Autowired
    private NodeCertFingerprint certFingerprint;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registry.remove("AA:BB:CC:DD:EE:50");
        OnboardingService service = new OnboardingService(registry, tokenService, certFingerprint,
                (dest, frame) -> { }, "30:ae:a4:1f:2b:3c", "SmartGround-Node-1", "provision-pw-123", "https://192.168.4.1:8443");
        mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(registry, service)).build();
    }

    @Test
    void pending_listsRegisteredBoxes_withoutExposingNonce() throws Exception {
        registry.onHello("AA:BB:CC:DD:EE:50", -44, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

        mockMvc.perform(get("/node-api/v1/onboarding/pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.mac=='AA:BB:CC:DD:EE:50')].rssi").value(org.hamcrest.Matchers.hasItem(-44)))
            .andExpect(jsonPath("$[0].boxNonce").doesNotExist());
    }

    @Test
    void couple_returnsOfferedStatus() throws Exception {
        registry.onHello("AA:BB:CC:DD:EE:50", -44, new byte[8]);

        mockMvc.perform(post("/node-api/v1/onboarding/AA:BB:CC:DD:EE:50/couple"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mac").value("AA:BB:CC:DD:EE:50"))
            .andExpect(jsonPath("$.status").value("offered"));
    }

    @Test
    void couple_unknownMac_returns404() throws Exception {
        mockMvc.perform(post("/node-api/v1/onboarding/AA:BB:CC:DD:EE:51/couple"))
            .andExpect(status().isNotFound());
    }
}

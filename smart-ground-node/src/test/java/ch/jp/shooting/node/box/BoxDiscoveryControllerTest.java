package ch.jp.shooting.node.box;

import ch.jp.shooting.node.onboarding.ProvisioningTokenService;
import ch.jp.shooting.node.onboarding.outbox.RegistrationOutboxRepository;
import ch.jp.shooting.node.onboarding.outbox.RegistrationOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Uses {@code MockMvcBuilders.standaloneSetup(...)} with real {@code @Autowired} beans under
 * {@code @SpringBootTest @Transactional} — the offline environment's trimmed
 * {@code spring-boot-test-autoconfigure} jar has no {@code @AutoConfigureMockMvc}; see the
 * Hub's {@code OtaDownloadControllerTest} pattern. Fixture MACs are defensively cleared in
 * {@code @BeforeEach} because this module's H2 datasource is file-based, not in-memory.
 */
@SpringBootTest
@Transactional
class BoxDiscoveryControllerTest {

    @Autowired
    private BoxProvisioningService provisioningService;
    @Autowired
    private BoxRecordRepository repository;
    @Autowired
    private ProvisioningTokenService tokenService;
    @Autowired
    private RegistrationOutboxService outboxService;
    @Autowired
    private RegistrationOutboxRepository outboxRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository.findByMacAddress("AA:BB:CC:DD:EE:10").ifPresent(repository::delete);
        repository.findByMacAddress("AA:BB:CC:DD:EE:11").ifPresent(repository::delete);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BoxDiscoveryController(provisioningService, repository, tokenService, outboxService)).build();
    }

    private String body(String mac, String token) {
        return """
            {"macAddress":"%s","token":"%s","appVersion":"1.0.0",
             "firmwareVersion":"micropython-1.23","boxType":"thrower","capabilitiesJson":"{}"}
            """.formatted(mac, token);
    }

    @Test
    void discovery_withValidToken_provisionsAndEnqueuesRegistration() throws Exception {
        String token = tokenService.mint("AA:BB:CC:DD:EE:10").hex();

        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body("AA:BB:CC:DD:EE:10", token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provisioned").value(true))
            .andExpect(jsonPath("$.kBoxBase64").isNotEmpty());

        assertThat(outboxRepository.findByStatus("PENDING"))
                .anyMatch(r -> r.getMacAddress().equals("AA:BB:CC:DD:EE:10"));
    }

    @Test
    void discovery_withoutToken_isRejected() throws Exception {
        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body("AA:BB:CC:DD:EE:11", "deadbeef")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void discovery_reusedToken_isRejectedSecondTime() throws Exception {
        String token = tokenService.mint("AA:BB:CC:DD:EE:11").hex();

        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body("AA:BB:CC:DD:EE:11", token)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body("AA:BB:CC:DD:EE:11", token)))
            .andExpect(status().isBadRequest());
    }
}

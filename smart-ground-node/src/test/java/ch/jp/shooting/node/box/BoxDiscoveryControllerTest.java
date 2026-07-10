package ch.jp.shooting.node.box;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Deviation from the task-6 brief's exact test code, documented in task-6-report.md: the
 * offline environment's cached {@code spring-boot-test-autoconfigure} jar is missing the
 * {@code web/servlet} package (no {@code @AutoConfigureMockMvc}), and there is no network
 * access to fetch a complete one. Per coordinator instruction, this uses
 * {@code MockMvcBuilders.standaloneSetup(...)}, mirroring the Hub's
 * {@code OtaDownloadControllerTest} pattern, while still loading real
 * {@code BoxProvisioningService}/{@code BoxRecordRepository} beans via {@code @SpringBootTest}
 * so the test exercises the real JPA path end-to-end rather than mocks. {@code @Transactional}
 * mirrors {@code BoxProvisioningServiceTest}/{@code BoxRecordRepositoryTest} in this same
 * package, which roll back against the file-based H2 datasource for the same reason — no
 * in-memory test datasource is configured for this module.
 */
@SpringBootTest
@Transactional
class BoxDiscoveryControllerTest {

    @Autowired
    private BoxProvisioningService provisioningService;

    @Autowired
    private BoxRecordRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Defensively clear this test's fixture MACs within the (rolled-back) test transaction:
        // this module's H2 datasource is file-based, not in-memory, so a MAC committed by an
        // earlier, non-transactional run against this same local dev file would otherwise leak
        // across runs. The delete itself is rolled back with everything else in this test.
        repository.findByMacAddress("AA:BB:CC:DD:EE:10").ifPresent(repository::delete);
        repository.findByMacAddress("AA:BB:CC:DD:EE:11").ifPresent(repository::delete);
        mockMvc = MockMvcBuilders.standaloneSetup(new BoxDiscoveryController(provisioningService, repository)).build();
    }

    @Test
    void discovery_newBox_returnsProvisionedTrueWithKBox() throws Exception {
        mockMvc.perform(post("/box-api/v1/discovery")
                .contentType("application/json")
                .content("""
                    {"macAddress":"AA:BB:CC:DD:EE:10","appVersion":"1.0.0",
                     "firmwareVersion":"micropython-1.23","boxType":"thrower","capabilitiesJson":"{}"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provisioned").value(true))
            .andExpect(jsonPath("$.kBoxBase64").isNotEmpty());
    }

    @Test
    void discovery_sameBoxTwice_secondCallReportsProvisionedFalse() throws Exception {
        String body = """
            {"macAddress":"AA:BB:CC:DD:EE:11","appVersion":"1.0.0",
             "firmwareVersion":"micropython-1.23","boxType":"thrower","capabilitiesJson":"{}"}
            """;
        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body))
            .andExpect(status().isOk());

        mockMvc.perform(post("/box-api/v1/discovery").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provisioned").value(false));
    }
}

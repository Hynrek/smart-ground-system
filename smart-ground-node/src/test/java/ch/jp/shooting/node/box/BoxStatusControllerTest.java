package ch.jp.shooting.node.box;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deviation from the task-7 brief's exact test code, documented in task-7-report.md: the
 * offline environment's cached {@code spring-boot-test-autoconfigure} jar is missing the
 * {@code web/servlet} package (no {@code @AutoConfigureMockMvc}), and there is no network
 * access to fetch a complete one. Per coordinator instruction, this uses
 * {@code MockMvcBuilders.standaloneSetup(...)}, mirroring
 * {@code BoxDiscoveryControllerTest} in this same package, while still loading a real
 * {@code BoxRecordRepository} bean via {@code @SpringBootTest} so the test exercises the
 * real JPA path end-to-end rather than mocks. {@code @Transactional} mirrors
 * {@code BoxDiscoveryControllerTest}/{@code BoxProvisioningServiceTest}/
 * {@code BoxRecordRepositoryTest} in this same package, which roll back against the
 * file-based H2 datasource for the same reason — no in-memory test datasource is
 * configured for this module.
 */
@SpringBootTest
@Transactional
class BoxStatusControllerTest {

    @Autowired
    private BoxRecordRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void seedKnownBox() {
        // Defensively clear this test's fixture MAC within the (rolled-back) test transaction:
        // this module's H2 datasource is file-based, not in-memory, so a MAC committed by an
        // earlier, non-transactional run against this same local dev file would otherwise leak
        // across runs. The delete itself is rolled back with everything else in this test.
        repository.findByMacAddress("AA:BB:CC:DD:EE:20").ifPresent(repository::delete);

        BoxRecord record = new BoxRecord();
        record.setMacAddress("AA:BB:CC:DD:EE:20");
        record.setKBox(new byte[32]);
        record.setProvisionedAt(Instant.now());
        repository.save(record);

        mockMvc = MockMvcBuilders.standaloneSetup(new BoxStatusController(repository))
                .setControllerAdvice(new ProblemDetailAdvice())
                .build();
    }

    /**
     * The app-level fix ({@code spring.mvc.problemdetails.enabled=true} in
     * {@code application.properties}) is what makes the deployed app render
     * {@link ch.jp.shooting.node.hub.ErrorResponseException}-style ProblemDetail bodies as
     * real {@code application/problem+json} responses. It only takes effect through Boot's
     * autoconfigured {@code DefaultErrorAttributes}/{@code ResponseEntityExceptionHandler}
     * wiring, which {@link MockMvcBuilders#standaloneSetup} deliberately bypasses (it builds a
     * bare {@code DispatcherServlet} with no Boot autoconfiguration at all). This advice is
     * therefore still required for this standalone test to render a body — but it now exists
     * purely to reproduce, inside the test harness, the same rendering behavior the property
     * already provides in the real, Boot-autoconfigured application; it is no longer a
     * substitute for that behavior. A genuine end-to-end test (real embedded server via
     * {@code TestRestTemplate}, no test-only advice) was attempted but is not buildable in
     * this offline environment: the cached {@code spring-boot-test} jar here does not expose
     * {@code org.springframework.boot.test.web.client.TestRestTemplate} and there is no
     * network access to fetch a complete one — the same constraint noted on this class
     * regarding {@code @AutoConfigureMockMvc}.
     */
    @ControllerAdvice
    static class ProblemDetailAdvice extends ResponseEntityExceptionHandler {
    }

    @Test
    void status_knownBox_returns200AndUpdatesLastSeen() throws Exception {
        mockMvc.perform(post("/box-api/v1/boxes/{mac}/status", "AA:BB:CC:DD:EE:20")
                .contentType("application/json")
                .content("""
                    {"status":"idle"}
                    """))
            .andExpect(status().isOk());

        var updated = repository.findByMacAddress("AA:BB:CC:DD:EE:20").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getLastStatus()).isEqualTo("idle");
        org.assertj.core.api.Assertions.assertThat(updated.getLastSeenAt()).isNotNull();
    }

    @Test
    void status_unknownBox_returns404() throws Exception {
        mockMvc.perform(post("/box-api/v1/boxes/{mac}/status", "00:00:00:00:00:99")
                .contentType("application/json")
                .content("""
                    {"status":"idle"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("/errors/box-unknown"));
    }
}

package ch.jp.shooting.node.box;

import ch.jp.shooting.node.hub.HubClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Deviation from the task-9 brief's exact test code, documented in task-9-report.md, on two
 * counts:
 *
 * <p>1. The offline environment's cached {@code spring-boot-test-autoconfigure} jar is missing
 * the {@code web/servlet} package (no {@code @AutoConfigureMockMvc}), and there is no network
 * access to fetch a complete one. Per the established, already-proven workaround (see
 * {@code BoxDiscoveryControllerTest}/{@code BoxStatusControllerTest} in this same package),
 * this uses {@code MockMvcBuilders.standaloneSetup(...)} with the controller built manually
 * in {@code @BeforeEach}, while still loading a real {@code HubClient} bean via a
 * (non-web) {@code @SpringBootTest} context.
 *
 * <p>2. The brief's test doubles the Hub with {@code okhttp3.mockwebserver.MockWebServer}, but
 * that artifact (and its {@code okhttp} runtime dependency) is not present anywhere in the
 * local {@code .m2} repository and cannot be fetched (offline, {@code mvn -o}). This test
 * substitutes a hand-rolled fake Hub built on the JDK-native
 * {@code com.sun.net.httpserver.HttpServer} (zero extra dependency, already on the classpath
 * via {@code java.net.http}/{@code jdk.httpserver}), which provides the same capability the
 * test needs: bind a local HTTP port, queue one canned response, and record the request path
 * the client actually hit.
 */
@SpringBootTest
class BoxOtaControllerTest {

    private static HttpServer hub;
    private static final BlockingQueue<String> recordedPaths = new ArrayBlockingQueue<>(10);

    // Reserved once, ahead of Spring context startup, so @DynamicPropertySource (which runs
    // before any @BeforeEach) can report a stable port for hub.base-url; the fake Hub server
    // itself is (re)bound to this same port per-test below.
    private static final int FIXED_PORT = choosePort();

    private static int choosePort() {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void startHub() throws IOException {
        recordedPaths.clear();
        hub = HttpServer.create(new InetSocketAddress("localhost", FIXED_PORT), 0);
        hub.createContext("/api/ota/app/1.0.0/manifest.json", exchange -> {
            recordedPaths.add(exchange.getRequestURI().getPath());
            byte[] body = "{\"files\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        hub.start();
    }

    @AfterEach
    void stopHub() {
        hub.stop(0);
    }

    @DynamicPropertySource
    static void hubUrl(DynamicPropertyRegistry registry) {
        registry.add("hub.base-url", () -> "http://localhost:" + FIXED_PORT);
    }

    @Autowired
    private HubClient hubClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BoxOtaController(hubClient)).build();
    }

    @Test
    void manifest_proxiesHubResponseBytes() throws Exception {
        mockMvc.perform(get("/box-api/v1/ota/app/{version}/manifest.json", "1.0.0"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"files\":[]}"));

        String recordedPath = recordedPaths.poll(2, TimeUnit.SECONDS);
        org.assertj.core.api.Assertions.assertThat(recordedPath)
            .isEqualTo("/api/ota/app/1.0.0/manifest.json");
    }
}

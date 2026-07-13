package ch.jp.shooting.node.hub;

import ch.jp.smartground.model.LoginRequest;
import ch.jp.smartground.model.LoginResponse;
import ch.jp.smartground.model.PlayInstanceOutboxItem;
import ch.jp.smartground.model.PlayInstanceOutboxResult;
import ch.jp.smartground.model.SerieOutboxItem;
import ch.jp.smartground.model.SerieOutboxResult;
import ch.jp.smartground.model.SerieSyncPage;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * Node's only channel to the Hub. Depends solely on {@code contracts} wire types —
 * never on Hub-internal packages (enforced by {@code ModuleBoundaryTest}).
 */
public class HubClient {

    private final RestClient restClient;

    public HubClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public LoginResponse login(String username, String password) {
        LoginRequest request = new LoginRequest(username, password);
        return restClient.post()
                .uri("/api/auth/login")
                .body(request)
                .retrieve()
                .body(LoginResponse.class);
    }

    public byte[] fetchOtaAppManifest(String version) {
        return restClient.get().uri("/api/ota/app/{version}/manifest.json", version)
                .retrieve().body(byte[].class);
    }

    public byte[] fetchOtaAppFile(String version, String path) {
        return restClient.get().uri("/api/ota/app/{version}/files{path}", version, path)
                .retrieve().body(byte[].class);
    }

    public byte[] fetchOtaFirmware(String version) {
        return restClient.get().uri("/api/ota/firmware/{version}", version)
                .retrieve().body(byte[].class);
    }

    /**
     * Zieht eine Sync-Seite der Serien vom Hub (hub-api, abwärts). updatedAfter als ISO-8601-Instant
     * (inklusive untere Schranke). Auth folgt mit Teilprojekt #6 (Service-Token) — heute offen.
     */
    public SerieSyncPage fetchSerieSyncPage(Instant updatedAfter, int limit) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/sync/serien")
                        .queryParam("updatedAfter", updatedAfter.toString())
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(SerieSyncPage.class);
    }

    /** Push eines Node-vergebenen Serie-Outbox-Eintrags (hub-api, aufwärts). */
    public SerieOutboxResult pushSerieOutboxItem(SerieOutboxItem item) {
        return restClient.post()
                .uri("/api/outbox/serien")
                .body(item)
                .retrieve()
                .body(SerieOutboxResult.class);
    }

    /** Push eines Node-vergebenen PlayInstance-Outbox-Eintrags (hub-api, aufwärts). */
    public PlayInstanceOutboxResult pushPlayInstanceOutboxItem(PlayInstanceOutboxItem item) {
        return restClient.post()
                .uri("/api/outbox/play-instances")
                .body(item)
                .retrieve()
                .body(PlayInstanceOutboxResult.class);
    }
}

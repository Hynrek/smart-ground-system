package ch.jp.shooting.node.hub;

import ch.jp.smartground.model.LoginRequest;
import ch.jp.smartground.model.LoginResponse;
import org.springframework.web.client.RestClient;

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
}

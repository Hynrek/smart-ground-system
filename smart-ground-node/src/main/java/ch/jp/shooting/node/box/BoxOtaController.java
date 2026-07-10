package ch.jp.shooting.node.box;

import ch.jp.shooting.node.hub.HubClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Box-zugewandter OTA-Proxy: reicht Artefakt-Bytes unverändert vom Hub weiter, damit die
 * Box nur den Node (box-api, HTTPS) kontaktieren muss und nie eine Hub-Adresse kennt.
 * Auslösung/Trigger bleibt vorerst manuell (#7 baut nur den Lesepfad) — automatisches
 * Anstossen folgt mit node-channel (#4).
 */
@RestController
public class BoxOtaController {

    private final HubClient hubClient;

    public BoxOtaController(HubClient hubClient) {
        this.hubClient = hubClient;
    }

    @GetMapping("/box-api/v1/ota/app/{version}/manifest.json")
    public ResponseEntity<byte[]> manifest(@PathVariable String version) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(hubClient.fetchOtaAppManifest(version));
    }

    @GetMapping("/box-api/v1/ota/app/{version}/files/{*path}")
    public ResponseEntity<byte[]> appFile(@PathVariable String version, @PathVariable String path) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(hubClient.fetchOtaAppFile(version, path));
    }

    @GetMapping("/box-api/v1/ota/firmware/{version}")
    public ResponseEntity<byte[]> firmware(@PathVariable String version) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(hubClient.fetchOtaFirmware(version));
    }
}

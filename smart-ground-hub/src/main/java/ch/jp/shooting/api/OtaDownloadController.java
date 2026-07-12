package ch.jp.shooting.api;

import ch.jp.shooting.service.OtaArtifactStore;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Box-zugewandte OTA-Download-Endpunkte (manifest.json, App-Dateien, Firmware-Image).
 *
 * BEWUSSTE AUSNAHME von der Contract-First-Regel (kein OpenAPI-Eintrag): Diese GETs
 * liefern Binär-/Datei-Inhalte an die unauthentifizierte Firmware aus und brauchen einen
 * mehrsegmentigen Catch-all-Pfad ({*path}), den openapi-generator nicht abbilden kann.
 * Bewusst nur lesende GETs; in CLAUDE.md dokumentiert.
 */
@RestController
@NullMarked
public class OtaDownloadController {

    private final OtaArtifactStore store;

    public OtaDownloadController(OtaArtifactStore store) {
        this.store = store;
    }

    @GetMapping("/api/ota/app/{version}/manifest.json")
    public ResponseEntity<byte[]> manifest(@PathVariable("version") String version) {
        byte[] body = store.readAppFile(version, "manifest.json");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    // {*path} ist Springs PathPattern-Catch-all: erfasst den Rest inkl. "/" (z.B. "/boards/x.py")
    @GetMapping("/api/ota/app/{version}/files/{*path}")
    public ResponseEntity<byte[]> appFile(@PathVariable("version") String version,
                                          @PathVariable("path") String path) {
        // path beginnt mit "/" → "files" + path ergibt "files/<pfad...>"
        byte[] body = store.readAppFile(version, "files" + path);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(body);
    }

    @GetMapping("/api/ota/firmware/{version}")
    public ResponseEntity<byte[]> firmware(@PathVariable("version") String version) {
        byte[] body = store.readFirmwareImage(version);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(body);
    }
}

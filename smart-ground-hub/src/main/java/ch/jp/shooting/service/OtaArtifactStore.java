package ch.jp.shooting.service;

import ch.jp.shooting.exception.InvalidOtaArtifactException;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Dateibasierter OTA-Artefaktspeicher.
 *
 * Layout unter {ota.artifact-dir}:
 *   app/{version}/manifest.json
 *   app/{version}/files/<pfad...>
 *   firmware/{version}.bin
 */
@Service
@NullMarked
public class OtaArtifactStore {

    private static final java.util.regex.Pattern VALID_VERSION =
        java.util.regex.Pattern.compile("[A-Za-z0-9._-]+");

    private final Path root;

    public OtaArtifactStore(@Value("${ota.artifact-dir:./ota-artifacts}") String artifactDir) {
        this.root = Path.of(artifactDir).toAbsolutePath().normalize();
    }

    // Version-Allowlist: verhindert Path-Traversal über den Versionsnamen
    private void validateVersion(String version) {
        if (!VALID_VERSION.matcher(version).matches()) {
            throw new InvalidOtaArtifactException("Ungültige Version: " + version);
        }
    }

    public record ManifestFile(String path, String sha256, long size) {}
    public record StoredApp(String manifestSha256, long sizeBytes) {}
    public record StoredFirmware(String sha256, long sizeBytes) {}

    /** Entpackt das ZIP nach app/{version}/files, baut manifest.json und gibt dessen Hash zurück. */
    public StoredApp storeAppBundle(String version, byte[] zipBytes) {
        validateVersion(version);
        Path appDir = appDir(version);
        Path filesDir = appDir.resolve("files");
        List<ManifestFile> files = new ArrayList<>();
        try {
            // Alte Dateien entfernen, damit ein erneuter Upload keine veralteten Dateien hinterlässt
            deleteRecursively(appDir);
            Files.createDirectories(filesDir);
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String name = entry.getName().replace('\\', '/');
                    // userconfig/ ist geräteeigener Zustand (WLAN-Zugangsdaten, device_config,
                    // ota_state) – ein Release darf ihn niemals überschreiben. Die Firmware
                    // (ota.py) lehnt solche Manifeste ebenfalls ab; hier scheitert der Upload früh.
                    if (name.startsWith("userconfig/")) {
                        throw new InvalidOtaArtifactException("Geschützter Pfad im ZIP: " + name);
                    }
                    Path dest = filesDir.resolve(name).normalize();
                    // Zip-Slip verhindern: Ziel muss unterhalb von filesDir liegen
                    if (!dest.startsWith(filesDir)) {
                        throw new InvalidOtaArtifactException("Ungültiger Pfad im ZIP: " + name);
                    }
                    byte[] body = zis.readAllBytes();
                    Files.createDirectories(dest.getParent());
                    Files.write(dest, body);
                    files.add(new ManifestFile(name, sha256Hex(body), body.length));
                }
            }
            if (files.isEmpty()) {
                throw new InvalidOtaArtifactException("ZIP enthält keine Dateien");
            }
            byte[] manifest = buildManifestJson(version, files);
            Files.write(appDir.resolve("manifest.json"), manifest);
            // sizeBytes ist die UNKOMPRIMIERTE Gesamtgröße aller entpackten Dateien
            long total = files.stream().mapToLong(ManifestFile::size).sum();
            return new StoredApp(sha256Hex(manifest), total);
        } catch (InvalidOtaArtifactException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidOtaArtifactException("App-Bundle konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public StoredFirmware storeFirmwareImage(String version, byte[] bin) {
        validateVersion(version);
        try {
            Path dir = root.resolve("firmware");
            Files.createDirectories(dir);
            Files.write(dir.resolve(version + ".bin"), bin);
            return new StoredFirmware(sha256Hex(bin), bin.length);
        } catch (InvalidOtaArtifactException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidOtaArtifactException("Firmware-Image konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Liest eine App-Datei relativ zu app/{version} (z.B. "manifest.json" oder "files/main.py"). */
    public byte[] readAppFile(String version, String relative) {
        validateVersion(version);
        Path base = appDir(version);
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) {
            throw new InvalidOtaArtifactException("Ungültiger Pfad: " + relative);
        }
        if (!Files.isRegularFile(target)) {
            throw new InvalidOtaArtifactException("Datei nicht gefunden: " + version + "/" + relative);
        }
        try {
            return Files.readAllBytes(target);
        } catch (Exception e) {
            throw new InvalidOtaArtifactException("Lesefehler: " + e.getMessage());
        }
    }

    public byte[] readFirmwareImage(String version) {
        validateVersion(version);
        Path bin = root.resolve("firmware").resolve(version + ".bin");
        if (!Files.isRegularFile(bin)) {
            throw new InvalidOtaArtifactException("Firmware-Image nicht gefunden: " + version);
        }
        try {
            return Files.readAllBytes(bin);
        } catch (Exception e) {
            throw new InvalidOtaArtifactException("Lesefehler: " + e.getMessage());
        }
    }

    // Verzeichnis rekursiv löschen (tiefste Pfade zuerst)
    private void deleteRecursively(Path p) throws java.io.IOException {
        if (!Files.exists(p)) return;
        try (var walk = Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.delete(path); } catch (java.io.IOException ignored) {}
            });
        }
    }

    private Path appDir(String version) {
        Path dir = root.resolve("app").resolve(version).normalize();
        if (!dir.startsWith(root)) {
            throw new InvalidOtaArtifactException("Ungültige Version: " + version);
        }
        return dir;
    }

    // Manifest ohne externe JSON-Lib bauen, damit der Hash exakt den gespeicherten Bytes entspricht
    private byte[] buildManifestJson(String version, List<ManifestFile> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"appVersion\":\"").append(jsonEscape(version)).append("\",\"files\":[");
        for (int i = 0; i < files.size(); i++) {
            ManifestFile f = files.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"path\":\"").append(jsonEscape(f.path()))
              .append("\",\"sha256\":\"").append(f.sha256())
              .append("\",\"size\":").append(f.size()).append('}');
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // JSON-String-Escaping: das Manifest ist der Trust-Anchor der Firmware, ein loses Anführungszeichen würde es brechen
    private String jsonEscape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.toString();
    }

    private String sha256Hex(byte[] body) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(body);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 nicht verfügbar", e);
        }
    }
}

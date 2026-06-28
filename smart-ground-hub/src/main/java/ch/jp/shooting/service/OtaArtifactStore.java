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

    private final Path root;

    public OtaArtifactStore(@Value("${ota.artifact-dir:./ota-artifacts}") String artifactDir) {
        this.root = Path.of(artifactDir).toAbsolutePath().normalize();
    }

    public record ManifestFile(String path, String sha256, long size) {}
    public record StoredApp(String manifestSha256, long sizeBytes) {}
    public record StoredFirmware(String sha256, long sizeBytes) {}

    /** Entpackt das ZIP nach app/{version}/files, baut manifest.json und gibt dessen Hash zurück. */
    public StoredApp storeAppBundle(String version, byte[] zipBytes) {
        Path appDir = appDir(version);
        Path filesDir = appDir.resolve("files");
        List<ManifestFile> files = new ArrayList<>();
        try {
            Files.createDirectories(filesDir);
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String name = entry.getName().replace('\\', '/');
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
            long total = files.stream().mapToLong(ManifestFile::size).sum();
            return new StoredApp(sha256Hex(manifest), total);
        } catch (InvalidOtaArtifactException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidOtaArtifactException("App-Bundle konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public StoredFirmware storeFirmwareImage(String version, byte[] bin) {
        try {
            Path dir = root.resolve("firmware");
            Files.createDirectories(dir);
            Files.write(dir.resolve(version + ".bin"), bin);
            return new StoredFirmware(sha256Hex(bin), bin.length);
        } catch (Exception e) {
            throw new InvalidOtaArtifactException("Firmware-Image konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Liest eine App-Datei relativ zu app/{version} (z.B. "manifest.json" oder "files/main.py"). */
    public byte[] readAppFile(String version, String relative) {
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
        sb.append("{\"appVersion\":\"").append(version).append("\",\"files\":[");
        for (int i = 0; i < files.size(); i++) {
            ManifestFile f = files.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"path\":\"").append(f.path())
              .append("\",\"sha256\":\"").append(f.sha256())
              .append("\",\"size\":").append(f.size()).append('}');
        }
        sb.append("]}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String sha256Hex(byte[] body) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(body);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new InvalidOtaArtifactException("Hash-Fehler: " + e.getMessage());
        }
    }
}

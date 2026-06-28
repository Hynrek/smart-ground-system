package ch.jp.shooting.service;

import ch.jp.shooting.exception.InvalidOtaArtifactException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtaArtifactStoreTest {

    private byte[] zip(String name, byte[] body) throws Exception {
        var bos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(body);
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    private String sha256Hex(byte[] b) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    @Test
    void storeAppBuildsManifestAndHashes(@TempDir Path dir) throws Exception {
        OtaArtifactStore store = new OtaArtifactStore(dir.toString());
        byte[] body = "print('v7')\n".getBytes(StandardCharsets.UTF_8);
        byte[] zip = zip("boards/xiao_esp32s3.py", body);

        OtaArtifactStore.StoredApp stored = store.storeAppBundle("0.7", zip);

        String manifest = new String(store.readAppFile("0.7", "manifest.json"), StandardCharsets.UTF_8);
        assertThat(manifest).contains("\"appVersion\":\"0.7\"");
        assertThat(manifest).contains("boards/xiao_esp32s3.py");
        assertThat(manifest).contains(sha256Hex(body));
        assertThat(store.readAppFile("0.7", "files/boards/xiao_esp32s3.py")).isEqualTo(body);
        assertThat(stored.manifestSha256())
            .isEqualTo(sha256Hex(store.readAppFile("0.7", "manifest.json")));
        assertThat(stored.sizeBytes()).isGreaterThan(0L);
    }

    @Test
    void storeFirmwareHashesImage(@TempDir Path dir) throws Exception {
        OtaArtifactStore store = new OtaArtifactStore(dir.toString());
        byte[] bin = new byte[]{1, 2, 3, 4, 5};
        OtaArtifactStore.StoredFirmware stored = store.storeFirmwareImage("mp-1.24", bin);
        assertThat(stored.sha256()).isEqualTo(sha256Hex(bin));
        assertThat(store.readFirmwareImage("mp-1.24")).isEqualTo(bin);
    }

    @Test
    void rejectsZipSlip(@TempDir Path dir) throws Exception {
        OtaArtifactStore store = new OtaArtifactStore(dir.toString());
        byte[] zip = zip("../evil.py", new byte[]{1});
        assertThatThrownBy(() -> store.storeAppBundle("0.7", zip))
            .isInstanceOf(InvalidOtaArtifactException.class);
    }

    @Test
    void readMissingFileThrows(@TempDir Path dir) {
        OtaArtifactStore store = new OtaArtifactStore(dir.toString());
        assertThatThrownBy(() -> store.readAppFile("9.9", "manifest.json"))
            .isInstanceOf(InvalidOtaArtifactException.class);
    }

    @Test
    void rejectsInvalidVersion(@TempDir Path dir) {
        OtaArtifactStore store = new OtaArtifactStore(dir.toString());
        assertThatThrownBy(() -> store.storeFirmwareImage("../evil", new byte[]{1}))
            .isInstanceOf(InvalidOtaArtifactException.class);
        assertThatThrownBy(() -> store.storeAppBundle("../firmware", zip("main.py", new byte[]{1})))
            .isInstanceOf(InvalidOtaArtifactException.class);
    }

    @Test
    void reuploadReplacesStaleFiles(@TempDir Path dir) throws Exception {
        OtaArtifactStore store = new OtaArtifactStore(dir.toString());
        store.storeAppBundle("0.7", zip("old.py", new byte[]{1}));
        store.storeAppBundle("0.7", zip("main.py", new byte[]{2}));
        assertThatThrownBy(() -> store.readAppFile("0.7", "files/old.py"))
            .isInstanceOf(InvalidOtaArtifactException.class);
        assertThat(store.readAppFile("0.7", "files/main.py")).isEqualTo(new byte[]{2});
    }
}

# SmartBox OTA — Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Spring Boot backend the server side of SmartBox OTA: store uploaded App-Code/firmware releases, serve them over HTTP to the (unauthenticated) box, trigger an update over MQTT, and track per-box OTA status — fulfilling the contract the firmware already implements.

**Architecture:** Releases are uploaded by an admin (`POST /api/ota/releases`, multipart), unzipped/hashed and written to a filesystem artifact store, and registered as `OtaRelease` rows. The box pulls them from a dedicated `OtaDownloadController` (manifest + nested files + firmware `.bin`). An admin triggers an update (`POST /api/smart-boxes/{id}/ota`) which publishes `smartboxes/{mac}/ota` over MQTT, mirroring the existing `SmartBoxConfigPushService`. The box reports progress on `smartboxes/{mac}/ota/status`, handled by a new MQTT handler that updates OTA fields on `SmartBox`. Discovery now captures `appVersion` (the OTA target) alongside `firmwareVersion`.

**Tech Stack:** Java 25, Spring Boot 4, JPA/Hibernate (H2 in tests), Eclipse Paho MQTTv5 + Spring Integration, OpenAPI contract-first (openapi-generator), JUnit 5 + Mockito, MockMvc.

**Input spec:** `docs/superpowers/specs/2026-06-27-smartbox-ota-design.md`
**Firmware contract (already merged):** `docs/superpowers/plans/2026-06-27-smartbox-ota-firmware.md` (bottom section)

---

## Conventions for this plan (read once)

- All handwritten code is under `ch.jp.shooting.*`. Never edit generated `ch.jp.smartground.*`.
- `@NullMarked` on every new class; `@Nullable` on nullable fields/params. UUID PK via `@GeneratedValue(strategy = GenerationType.UUID)`. JSON columns use `TEXT`.
- German inline comments for domain logic; English identifiers.
- Run all tests: `./mvnw test` (from `smart-ground-backend/`). Single class: `./mvnw test -Dtest=FooTest`.
- OpenAPI changes: edit `src/main/resources/static/openapi.yaml`, then `./mvnw generate-sources`, then implement `class FooController implements FooApi` (no Spring mapping annotations on contract-first controllers).
- New exceptions go in `ch.jp.shooting.exception` and are registered in `GlobalExceptionHandler`.
- Commit messages: `[backend] short description`.

## Architectural decisions (locked; recorded in CLAUDE.md in the final task)

1. **Artifact store = filesystem** under `ota.artifact-dir` (default `./ota-artifacts`). Layout:
   - `app/{version}/manifest.json` and `app/{version}/files/<path...>`
   - `firmware/{version}.bin`
2. **`OtaRelease` entity** registers each release: `(type, version)` unique, plus `sha256` (manifest hash for APP, image hash for FIRMWARE), `sizeBytes`, `createdAt`.
3. **Two endpoint planes:**
   - **Admin/control plane** (OpenAPI contract-first): upload release, list releases, trigger OTA, read OTA status.
   - **Box data plane** (`OtaDownloadController`, plain Spring `@GetMapping` with `{*path}` catch-all): serve manifest, files, firmware `.bin`. **Deliberate, documented exception** to the "no controller without an OpenAPI entry" rule — OpenAPI codegen cannot express a multi-segment catch-all binary download, and the client is firmware, not a REST consumer. Kept as narrow as possible (read-only GETs).
4. **Fetch URL** built from config property `ota.base-url` (e.g. `http://192.168.1.100:8080`). The box receives an absolute URL in the MQTT payload.
5. **Auth:** `GET /api/ota/app/**` and `GET /api/ota/firmware/**` are permitted without JWT (the box has no token). All admin endpoints stay authenticated.

## Contract the firmware expects (target — do not change)

- MQTT `smartboxes/{mac}/ota` (Backend→box): `{ "type":"APP"|"FIRMWARE", "version", "url", "sha256", "size" }`.
- MQTT `smartboxes/{mac}/ota/status` (box→Backend): `{ "version", "phase", "progress", "detail" }`, phase ∈ `DOWNLOADING|VERIFYING|APPLYING|APPLIED|FAILED|ROLLED_BACK`.
- `GET {url}/manifest.json` → `{ "appVersion", "files":[{ "path","sha256","size" }] }`; `GET {url}/files/{path}` → raw bytes; firmware `GET {url}` → raw `.bin`.
  - So for APP, `url = {ota.base-url}/api/ota/app/{version}`; for FIRMWARE, `url = {ota.base-url}/api/ota/firmware/{version}` and the box GETs that exact URL for the `.bin`.
- Discovery now reports `appVersion` + `firmwareVersion`.

## File map (what each new/changed file owns)

- Create `model/OtaType.java` — enum `APP, FIRMWARE`.
- Create `model/OtaRelease.java` — release registry entity.
- Create `repository/OtaReleaseRepository.java`.
- Create `service/OtaArtifactStore.java` — filesystem layout, unzip+hash+manifest, read paths.
- Create `service/OtaService.java` — upload→store+register, list, trigger orchestration, status read.
- Create `config/OtaPublishService.java` — build + publish the `/ota` MQTT payload (mirrors `SmartBoxConfigPushService`).
- Create `config/SmartBoxOtaStatusHandler.java` — MQTT handler for `/ota/status`.
- Create `api/OtaController.java` — admin REST (implements generated `OtaApi`).
- Create `api/OtaDownloadController.java` — box-facing downloads (Spring `@GetMapping`, documented exception).
- Create `exception/OtaReleaseNotFoundException.java`, `exception/InvalidOtaArtifactException.java`.
- Modify `model/SmartBox.java` — add `appVersion` + OTA status fields.
- Modify `config/SmartBoxDiscoveryHandler.java` — capture `appVersion`.
- Modify `config/SmartBoxMqttRouter.java` — route `/ota/status` (before `/status`).
- Modify `config/MqttConfig.java` — subscribe `smartboxes/+/ota/status`.
- Modify `config/SecurityConfig.java` — permit box-facing OTA GETs.
- Modify `src/main/resources/static/openapi.yaml` — admin OTA endpoints + schemas.
- Modify `application*.properties` — `ota.base-url`, `ota.artifact-dir`.
- Modify `smart-ground-backend/CLAUDE.md` — document the feature + the controller exception.

---

## Task 1: `OtaRelease` entity + repository + `OtaType`

**Files:**
- Create: `src/main/java/ch/jp/shooting/model/OtaType.java`
- Create: `src/main/java/ch/jp/shooting/model/OtaRelease.java`
- Create: `src/main/java/ch/jp/shooting/repository/OtaReleaseRepository.java`
- Test: `src/test/java/ch/jp/shooting/repository/OtaReleaseRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.repository;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OtaReleaseRepositoryTest {

    @Autowired
    OtaReleaseRepository repository;

    @Test
    void savesAndFindsByTypeAndVersion() {
        OtaRelease r = new OtaRelease();
        r.setType(OtaType.APP);
        r.setVersion("0.7");
        r.setSha256("ab".repeat(32));
        r.setSizeBytes(123L);
        r.setCreatedAt(Instant.now());
        repository.save(r);

        Optional<OtaRelease> found = repository.findByTypeAndVersion(OtaType.APP, "0.7");
        assertThat(found).isPresent();
        assertThat(found.get().getSha256()).isEqualTo("ab".repeat(32));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=OtaReleaseRepositoryTest`
Expected: FAIL — `OtaRelease` / `OtaReleaseRepository` do not exist (compilation error).

- [ ] **Step 3: Create the enum, entity, and repository**

`model/OtaType.java`:

```java
package ch.jp.shooting.model;

public enum OtaType { APP, FIRMWARE }
```

`model/OtaRelease.java`:

```java
package ch.jp.shooting.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ota_releases",
       uniqueConstraints = @UniqueConstraint(columnNames = {"type", "version"}))
@NullMarked
public class OtaRelease {

    public OtaRelease() {}

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Nullable
    private UUID id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OtaType type = OtaType.APP;

    @Column(nullable = false)
    private String version = "";

    // APP: SHA-256 des manifest.json; FIRMWARE: SHA-256 des .bin-Images
    @Column(nullable = false)
    private String sha256 = "";

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public @Nullable UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public OtaType getType() { return type; }
    public void setType(OtaType type) { this.type = type; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

`repository/OtaReleaseRepository.java`:

```java
package ch.jp.shooting.repository;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtaReleaseRepository extends JpaRepository<OtaRelease, UUID> {
    Optional<OtaRelease> findByTypeAndVersion(OtaType type, String version);
    List<OtaRelease> findAllByOrderByCreatedAtDesc();
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=OtaReleaseRepositoryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/model/OtaType.java \
        src/main/java/ch/jp/shooting/model/OtaRelease.java \
        src/main/java/ch/jp/shooting/repository/OtaReleaseRepository.java \
        src/test/java/ch/jp/shooting/repository/OtaReleaseRepositoryTest.java
git commit -m "[backend] Add OtaRelease entity, type enum and repository"
```

---

## Task 2: `OtaArtifactStore` — filesystem layout, unzip + hash + manifest

**Files:**
- Create: `src/main/java/ch/jp/shooting/exception/InvalidOtaArtifactException.java`
- Create: `src/main/java/ch/jp/shooting/service/OtaArtifactStore.java`
- Test: `src/test/java/ch/jp/shooting/service/OtaArtifactStoreTest.java`

- [ ] **Step 1: Write the failing test**

```java
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

        // Manifest enthält die Datei mit korrektem Hash
        String manifest = new String(store.readAppFile("0.7", "manifest.json"), StandardCharsets.UTF_8);
        assertThat(manifest).contains("\"appVersion\":\"0.7\"");
        assertThat(manifest).contains("boards/xiao_esp32s3.py");
        assertThat(manifest).contains(sha256Hex(body));
        // Datei ist abrufbar
        assertThat(store.readAppFile("0.7", "files/boards/xiao_esp32s3.py")).isEqualTo(body);
        // Manifest-Hash stimmt mit dem gespeicherten manifest.json überein
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
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=OtaArtifactStoreTest`
Expected: FAIL — `OtaArtifactStore` / `InvalidOtaArtifactException` do not exist.

- [ ] **Step 3: Create the exception and the store**

`exception/InvalidOtaArtifactException.java`:

```java
package ch.jp.shooting.exception;

public class InvalidOtaArtifactException extends RuntimeException {
    public InvalidOtaArtifactException(String message) { super(message); }
}
```

`service/OtaArtifactStore.java`:

```java
package ch.jp.shooting.service;

import ch.jp.shooting.exception.InvalidOtaArtifactException;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    private String sha256Hex(byte[] body) throws RuntimeException {
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
```

> Note: the manifest is built by hand (not Jackson) so the stored bytes and their SHA-256 are byte-identical to what the box downloads and verifies. The firmware's `download_app` verifies the manifest against this exact hash.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=OtaArtifactStoreTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/exception/InvalidOtaArtifactException.java \
        src/main/java/ch/jp/shooting/service/OtaArtifactStore.java \
        src/test/java/ch/jp/shooting/service/OtaArtifactStoreTest.java
git commit -m "[backend] Add OtaArtifactStore (unzip, hash, manifest, file serving)"
```

---

## Task 3: `OtaService` — register uploads, list releases

**Files:**
- Create: `src/main/java/ch/jp/shooting/service/OtaService.java`
- Test: `src/test/java/ch/jp/shooting/service/OtaServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.repository.OtaReleaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtaServiceTest {

    @Mock OtaArtifactStore store;
    @Mock OtaReleaseRepository repository;
    @InjectMocks OtaService service;

    @Test
    void uploadAppStoresArtifactAndRegistersRelease() {
        when(store.storeAppBundle("0.7", new byte[]{1}))
            .thenReturn(new OtaArtifactStore.StoredApp("deadbeef", 42L));
        when(repository.save(any(OtaRelease.class))).thenAnswer(i -> i.getArgument(0));

        OtaRelease saved = service.uploadApp("0.7", new byte[]{1});

        assertThat(saved.getType()).isEqualTo(OtaType.APP);
        assertThat(saved.getVersion()).isEqualTo("0.7");
        assertThat(saved.getSha256()).isEqualTo("deadbeef");
        assertThat(saved.getSizeBytes()).isEqualTo(42L);

        ArgumentCaptor<OtaRelease> cap = ArgumentCaptor.forClass(OtaRelease.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getType()).isEqualTo(OtaType.APP);
    }

    @Test
    void uploadFirmwareStoresImageAndRegistersRelease() {
        when(store.storeFirmwareImage("mp-1.24", new byte[]{9}))
            .thenReturn(new OtaArtifactStore.StoredFirmware("cafe", 5L));
        when(repository.save(any(OtaRelease.class))).thenAnswer(i -> i.getArgument(0));

        OtaRelease saved = service.uploadFirmware("mp-1.24", new byte[]{9});

        assertThat(saved.getType()).isEqualTo(OtaType.FIRMWARE);
        assertThat(saved.getSha256()).isEqualTo("cafe");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=OtaServiceTest`
Expected: FAIL — `OtaService` does not exist.

- [ ] **Step 3: Create `OtaService` (upload + list)**

```java
package ch.jp.shooting.service;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.repository.OtaReleaseRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@NullMarked
public class OtaService {

    private final OtaArtifactStore store;
    private final OtaReleaseRepository repository;

    public OtaService(OtaArtifactStore store, OtaReleaseRepository repository) {
        this.store = store;
        this.repository = repository;
    }

    @Transactional
    public OtaRelease uploadApp(String version, byte[] zipBytes) {
        OtaArtifactStore.StoredApp stored = store.storeAppBundle(version, zipBytes);
        return register(OtaType.APP, version, stored.manifestSha256(), stored.sizeBytes());
    }

    @Transactional
    public OtaRelease uploadFirmware(String version, byte[] binBytes) {
        OtaArtifactStore.StoredFirmware stored = store.storeFirmwareImage(version, binBytes);
        return register(OtaType.FIRMWARE, version, stored.sha256(), stored.sizeBytes());
    }

    public List<OtaRelease> listReleases() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    private OtaRelease register(OtaType type, String version, String sha256, long size) {
        OtaRelease release = repository.findByTypeAndVersion(type, version).orElseGet(OtaRelease::new);
        release.setType(type);
        release.setVersion(version);
        release.setSha256(sha256);
        release.setSizeBytes(size);
        release.setCreatedAt(Instant.now());
        return repository.save(release);
    }
}
```

> Re-uploading an existing `(type, version)` overwrites the artifacts (Task 2) and updates the row — convenient for fixing a bad bundle pre-v1.0.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=OtaServiceTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/OtaService.java \
        src/test/java/ch/jp/shooting/service/OtaServiceTest.java
git commit -m "[backend] Add OtaService for release upload/registration"
```

---

## Task 4: OpenAPI — admin OTA endpoints + schemas

**Files:**
- Modify: `src/main/resources/static/openapi.yaml`

- [ ] **Step 1: Add the paths**

Under `paths:` (next to the SmartBox block), add:

```yaml
  /api/ota/releases:
    get:
      summary: List uploaded OTA releases
      operationId: listOtaReleases
      tags: [Ota]
      responses:
        '200':
          description: Releases
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/OtaReleaseResponse'
    post:
      summary: Upload an OTA release (App Code zip or firmware .bin)
      operationId: uploadOtaRelease
      tags: [Ota]
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [type, version, file]
              properties:
                type:
                  $ref: '#/components/schemas/OtaTypeValue'
                version:
                  type: string
                file:
                  type: string
                  format: binary
      responses:
        '201':
          description: Release stored
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OtaReleaseResponse'
        '400':
          description: Invalid artifact

  /api/smart-boxes/{id}/ota:
    post:
      summary: Trigger an OTA update on a smart box
      operationId: triggerSmartBoxOta
      tags: [Ota]
      parameters:
        - name: id
          in: path
          required: true
          schema: { type: string, format: uuid }
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/TriggerOtaRequest'
      responses:
        '202':
          description: OTA command published
        '404':
          description: Smart box or release not found
    get:
      summary: Get the latest OTA status reported by a smart box
      operationId: getSmartBoxOtaStatus
      tags: [Ota]
      parameters:
        - name: id
          in: path
          required: true
          schema: { type: string, format: uuid }
      responses:
        '200':
          description: OTA status
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OtaStatusResponse'
        '404':
          description: Smart box not found
```

- [ ] **Step 2: Add the schemas**

Under `components: schemas:`, add:

```yaml
    OtaTypeValue:
      type: string
      enum: [APP, FIRMWARE]

    OtaReleaseResponse:
      type: object
      properties:
        id: { type: string, format: uuid }
        type: { $ref: '#/components/schemas/OtaTypeValue' }
        version: { type: string }
        sha256: { type: string }
        sizeBytes: { type: integer, format: int64 }
        createdAt: { type: string, format: date-time }

    TriggerOtaRequest:
      type: object
      required: [type, version]
      properties:
        type: { $ref: '#/components/schemas/OtaTypeValue' }
        version: { type: string }

    OtaStatusResponse:
      type: object
      properties:
        version: { type: string }
        phase: { type: string }
        progress: { type: integer }
        detail: { type: string }
        updatedAt: { type: string, format: date-time }
```

- [ ] **Step 3: Regenerate sources**

Run: `./mvnw generate-sources`
Expected: BUILD SUCCESS; `target/generated-sources/openapi/ch/jp/smartground/api/OtaApi.java` now exists with `listOtaReleases`, `uploadOtaRelease`, `triggerSmartBoxOta`, `getSmartBoxOtaStatus`.

- [ ] **Step 4: Verify the interface generated**

Run: `ls target/generated-sources/openapi/ch/jp/smartground/api/OtaApi.java`
Expected: file exists.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/openapi.yaml
git commit -m "[backend] Add OpenAPI contract for OTA release + trigger + status endpoints"
```

---

## Task 5: SmartBox entity — `appVersion` + OTA status fields; discovery captures `appVersion`

**Files:**
- Modify: `src/main/java/ch/jp/shooting/model/SmartBox.java`
- Modify: `src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java`
- Test: `src/test/java/ch/jp/shooting/config/SmartBoxDiscoveryHandlerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.config;

import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.FirmwareConfigRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxDiscoveryHandlerTest {

    @Mock SmartBoxRepository smartBoxRepository;
    @Mock FirmwareConfigRepository firmwareConfigRepository;
    @Mock SmartBoxConfigPushService configPushService;

    @Test
    void capturesAppVersionAndFirmwareVersion() {
        var handler = new SmartBoxDiscoveryHandler(
            smartBoxRepository, firmwareConfigRepository, new ObjectMapper(), configPushService);
        when(smartBoxRepository.findByMacAddress("aabbccddeeff")).thenReturn(Optional.empty());
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));

        String json = "{\"mac\":\"aabbccddeeff\",\"appVersion\":\"0.7\","
                    + "\"firmwareVersion\":\"micropython-1.23.0\",\"boxType\":\"xiao-esp32s3\",\"ip\":\"1.2.3.4\"}";
        handler.handleMessage(MessageBuilder.withPayload(json.getBytes()).build());

        ArgumentCaptor<SmartBox> cap = ArgumentCaptor.forClass(SmartBox.class);
        verify(smartBoxRepository).save(cap.capture());
        assertThat(cap.getValue().getAppVersion()).isEqualTo("0.7");
        assertThat(cap.getValue().getFirmwareVersion()).isEqualTo("micropython-1.23.0");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=SmartBoxDiscoveryHandlerTest`
Expected: FAIL — `SmartBox.getAppVersion()` does not exist.

- [ ] **Step 3: Add fields to `SmartBox`**

In `model/SmartBox.java`, after the `firmwareVersion` field (line ~40) add:

```java
    @Column(name = "app_version")
    @Nullable
    private String appVersion;

    // Letzter über smartboxes/{mac}/ota/status gemeldeter OTA-Zustand
    @Column(name = "ota_phase")
    @Nullable
    private String otaPhase;

    @Column(name = "ota_version")
    @Nullable
    private String otaVersion;

    @Column(name = "ota_progress")
    @Nullable
    private Integer otaProgress;

    @Column(name = "ota_detail")
    @Nullable
    private String otaDetail;

    @Column(name = "ota_updated_at")
    @Nullable
    private java.time.Instant otaUpdatedAt;
```

And add the accessors (after the existing ones):

```java
    public @Nullable String getAppVersion() { return appVersion; }
    public void setAppVersion(@Nullable String appVersion) { this.appVersion = appVersion; }
    public @Nullable String getOtaPhase() { return otaPhase; }
    public void setOtaPhase(@Nullable String otaPhase) { this.otaPhase = otaPhase; }
    public @Nullable String getOtaVersion() { return otaVersion; }
    public void setOtaVersion(@Nullable String otaVersion) { this.otaVersion = otaVersion; }
    public @Nullable Integer getOtaProgress() { return otaProgress; }
    public void setOtaProgress(@Nullable Integer otaProgress) { this.otaProgress = otaProgress; }
    public @Nullable String getOtaDetail() { return otaDetail; }
    public void setOtaDetail(@Nullable String otaDetail) { this.otaDetail = otaDetail; }
    public @Nullable java.time.Instant getOtaUpdatedAt() { return otaUpdatedAt; }
    public void setOtaUpdatedAt(@Nullable java.time.Instant otaUpdatedAt) { this.otaUpdatedAt = otaUpdatedAt; }
```

- [ ] **Step 4: Capture `appVersion` in the discovery handler**

In `config/SmartBoxDiscoveryHandler.java`, extend the `DiscoveryPayload` record:

```java
    record DiscoveryPayload(
        String mac,
        @Nullable String ip,
        @Nullable String appVersion,
        @Nullable String firmwareVersion,
        @Nullable String boxType
    ) {}
```

In `upsertSmartBox`, after `box.setLastSeen(...)`, add:

```java
        if (payload.appVersion() != null) {
            box.setAppVersion(payload.appVersion());
        }
```

(Leave the existing `firmwareVersion`/firmware-config resolution as is.)

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SmartBoxDiscoveryHandlerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/model/SmartBox.java \
        src/main/java/ch/jp/shooting/config/SmartBoxDiscoveryHandler.java \
        src/test/java/ch/jp/shooting/config/SmartBoxDiscoveryHandlerTest.java
git commit -m "[backend] Capture appVersion and add OTA status fields on SmartBox"
```

---

## Task 6: `OtaPublishService` — build + publish the `/ota` MQTT command

**Files:**
- Create: `src/main/java/ch/jp/shooting/exception/OtaReleaseNotFoundException.java`
- Create: `src/main/java/ch/jp/shooting/config/OtaPublishService.java`
- Test: `src/test/java/ch/jp/shooting/config/OtaPublishServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.config;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OtaPublishServiceTest {

    @Test
    void publishesAppOtaCommandWithUrlAndHash() throws Exception {
        MessageChannel channel = mock(MessageChannel.class);
        when(channel.send(any())).thenReturn(true);
        ObjectMapper mapper = new ObjectMapper();
        OtaPublishService svc = new OtaPublishService(channel, mapper, "http://10.0.0.5:8080");

        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        OtaRelease release = new OtaRelease();
        release.setType(OtaType.APP);
        release.setVersion("0.7");
        release.setSha256("deadbeef");
        release.setSizeBytes(42L);

        svc.publish(box, release);

        ArgumentCaptor<Message<?>> cap = ArgumentCaptor.forClass(Message.class);
        verify(channel).send(cap.capture());
        Message<?> msg = cap.getValue();
        assertThat(msg.getHeaders().get("mqtt_topic")).isEqualTo("smartboxes/aabbccddeeff/ota");
        JsonNode payload = mapper.readTree((String) msg.getPayload());
        assertThat(payload.get("type").asText()).isEqualTo("APP");
        assertThat(payload.get("version").asText()).isEqualTo("0.7");
        assertThat(payload.get("url").asText()).isEqualTo("http://10.0.0.5:8080/api/ota/app/0.7");
        assertThat(payload.get("sha256").asText()).isEqualTo("deadbeef");
        assertThat(payload.get("size").asLong()).isEqualTo(42L);
    }

    @Test
    void firmwareUrlPointsAtBinEndpoint() throws Exception {
        MessageChannel channel = mock(MessageChannel.class);
        when(channel.send(any())).thenReturn(true);
        ObjectMapper mapper = new ObjectMapper();
        OtaPublishService svc = new OtaPublishService(channel, mapper, "http://10.0.0.5:8080");

        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        OtaRelease release = new OtaRelease();
        release.setType(OtaType.FIRMWARE);
        release.setVersion("mp-1.24");
        release.setSha256("cafe");

        svc.publish(box, release);

        ArgumentCaptor<Message<?>> cap = ArgumentCaptor.forClass(Message.class);
        verify(channel).send(cap.capture());
        JsonNode payload = mapper.readTree((String) cap.getValue().getPayload());
        assertThat(payload.get("url").asText()).isEqualTo("http://10.0.0.5:8080/api/ota/firmware/mp-1.24");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=OtaPublishServiceTest`
Expected: FAIL — `OtaPublishService` does not exist.

- [ ] **Step 3: Create the exception and the publish service**

`exception/OtaReleaseNotFoundException.java`:

```java
package ch.jp.shooting.exception;

import ch.jp.shooting.model.OtaType;

public class OtaReleaseNotFoundException extends RuntimeException {
    public OtaReleaseNotFoundException(OtaType type, String version) {
        super("OTA-Release nicht gefunden: " + type + " " + version);
    }
}
```

`config/OtaPublishService.java` (mirrors `SmartBoxConfigPushService`):

```java
package ch.jp.shooting.config;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Veröffentlicht einen OTA-Befehl an eine SmartBox.
 *
 * Topic:   smartboxes/{mac}/ota
 * Payload: { "type", "version", "url", "sha256", "size" }
 * Die Box lädt das Artefakt anschliessend per HTTP von {url} herunter.
 */
@Service
@NullMarked
public class OtaPublishService {

    private static final Logger log = LoggerFactory.getLogger(OtaPublishService.class);
    static final String TOPIC_OTA_TEMPLATE = "smartboxes/%s/ota";

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OtaPublishService(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            ObjectMapper objectMapper,
            @Value("${ota.base-url:http://localhost:8080}") String baseUrl) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record OtaCommand(String type, String version, String url, String sha256, long size) {}

    public void publish(SmartBox box, OtaRelease release) {
        String mac = box.getMacAddress();
        String url = buildUrl(release);
        try {
            OtaCommand cmd = new OtaCommand(
                release.getType().name(), release.getVersion(), url,
                release.getSha256(), release.getSizeBytes());
            String json = objectMapper.writeValueAsString(cmd);
            String topic = TOPIC_OTA_TEMPLATE.formatted(mac);
            mqttOutboundChannel.send(MessageBuilder.withPayload(json)
                .setHeader("mqtt_topic", topic)
                .setHeader("mqtt_qos", 1)
                .build());
            log.info("OTA-Befehl ({} {}) an SmartBox {} → {}", cmd.type(), cmd.version(), mac, topic);
        } catch (Exception e) {
            throw new RuntimeException("OTA-Befehl konnte nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private String buildUrl(OtaRelease release) {
        String segment = release.getType() == OtaType.APP ? "app" : "firmware";
        return "%s/api/ota/%s/%s".formatted(baseUrl, segment, release.getVersion());
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=OtaPublishServiceTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/exception/OtaReleaseNotFoundException.java \
        src/main/java/ch/jp/shooting/config/OtaPublishService.java \
        src/test/java/ch/jp/shooting/config/OtaPublishServiceTest.java
git commit -m "[backend] Add OtaPublishService to publish /ota MQTT commands"
```

---

## Task 7: `OtaService` trigger + status read; register the trigger flow

**Files:**
- Modify: `src/main/java/ch/jp/shooting/service/OtaService.java`
- Test: `src/test/java/ch/jp/shooting/service/OtaServiceTriggerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.service;

import ch.jp.shooting.config.OtaPublishService;
import ch.jp.shooting.exception.OtaReleaseNotFoundException;
import ch.jp.shooting.exception.SmartBoxNotFoundException;
import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.OtaReleaseRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtaServiceTriggerTest {

    @Mock OtaArtifactStore store;
    @Mock OtaReleaseRepository releaseRepository;
    @Mock SmartBoxRepository smartBoxRepository;
    @Mock OtaPublishService publishService;

    OtaService service() {
        return new OtaService(store, releaseRepository, smartBoxRepository, publishService);
    }

    @Test
    void triggerPublishesForResolvedBoxAndRelease() {
        UUID boxId = UUID.randomUUID();
        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        OtaRelease release = new OtaRelease();
        release.setType(OtaType.APP);
        release.setVersion("0.7");
        when(smartBoxRepository.findById(boxId)).thenReturn(Optional.of(box));
        when(releaseRepository.findByTypeAndVersion(OtaType.APP, "0.7")).thenReturn(Optional.of(release));

        service().triggerOta(boxId, OtaType.APP, "0.7");

        verify(publishService).publish(box, release);
    }

    @Test
    void triggerThrowsWhenBoxMissing() {
        UUID boxId = UUID.randomUUID();
        when(smartBoxRepository.findById(boxId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().triggerOta(boxId, OtaType.APP, "0.7"))
            .isInstanceOf(SmartBoxNotFoundException.class);
    }

    @Test
    void triggerThrowsWhenReleaseMissing() {
        UUID boxId = UUID.randomUUID();
        when(smartBoxRepository.findById(boxId)).thenReturn(Optional.of(new SmartBox()));
        when(releaseRepository.findByTypeAndVersion(OtaType.APP, "9.9")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().triggerOta(boxId, OtaType.APP, "9.9"))
            .isInstanceOf(OtaReleaseNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=OtaServiceTriggerTest`
Expected: FAIL — `OtaService` constructor signature mismatch / `triggerOta` missing.

- [ ] **Step 3: Extend `OtaService` with the trigger + status read**

Update the constructor and add methods in `service/OtaService.java`:

```java
    private final SmartBoxRepository smartBoxRepository;
    private final OtaPublishService publishService;

    public OtaService(OtaArtifactStore store,
                      OtaReleaseRepository repository,
                      SmartBoxRepository smartBoxRepository,
                      OtaPublishService publishService) {
        this.store = store;
        this.repository = repository;
        this.smartBoxRepository = smartBoxRepository;
        this.publishService = publishService;
    }

    @Transactional
    public void triggerOta(UUID smartBoxId, OtaType type, String version) {
        SmartBox box = smartBoxRepository.findById(smartBoxId)
            .orElseThrow(() -> new SmartBoxNotFoundException(smartBoxId));
        OtaRelease release = repository.findByTypeAndVersion(type, version)
            .orElseThrow(() -> new OtaReleaseNotFoundException(type, version));
        publishService.publish(box, release);
    }

    @Transactional(readOnly = true)
    public SmartBox getBox(UUID smartBoxId) {
        return smartBoxRepository.findById(smartBoxId)
            .orElseThrow(() -> new SmartBoxNotFoundException(smartBoxId));
    }
```

Add the imports: `ch.jp.shooting.config.OtaPublishService`, `ch.jp.shooting.exception.OtaReleaseNotFoundException`, `ch.jp.shooting.exception.SmartBoxNotFoundException`, `ch.jp.shooting.model.SmartBox`, `ch.jp.shooting.repository.SmartBoxRepository`, `java.util.UUID`.

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw test -Dtest=OtaServiceTriggerTest`
Expected: PASS (3 tests). Also re-run `./mvnw test -Dtest=OtaServiceTest` — still PASS (constructor now needs the two extra mocks; update `OtaServiceTest` to pass `null, null` for the new params if those tests don't use them, OR add `@Mock SmartBoxRepository` / `@Mock OtaPublishService` and rely on `@InjectMocks`). Use `@InjectMocks` with the extra `@Mock` fields so injection still works.

> Concretely: in `OtaServiceTest`, add `@Mock SmartBoxRepository smartBoxRepository;` and `@Mock OtaPublishService publishService;` so `@InjectMocks OtaService service;` constructs with all four dependencies.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/service/OtaService.java \
        src/test/java/ch/jp/shooting/service/OtaServiceTriggerTest.java \
        src/test/java/ch/jp/shooting/service/OtaServiceTest.java
git commit -m "[backend] Add OTA trigger + box lookup to OtaService"
```

---

## Task 8: `OtaController` — admin REST (upload, list, trigger, status)

**Files:**
- Create: `src/main/java/ch/jp/shooting/api/OtaController.java`
- Modify: `src/main/java/ch/jp/shooting/config/GlobalExceptionHandler.java` (register the two new exceptions)
- Test: `src/test/java/ch/jp/shooting/api/OtaControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package ch.jp.shooting.api;

import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.service.OtaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtaControllerTest {

    @Mock OtaService otaService;

    @Test
    void uploadAppReturns201() throws Exception {
        OtaRelease release = new OtaRelease();
        release.setType(OtaType.APP);
        release.setVersion("0.7");
        when(otaService.uploadApp(eq("0.7"), any())).thenReturn(release);

        var controller = new OtaController(otaService);
        var file = new MockMultipartFile("file", "bundle.zip", "application/zip", new byte[]{1});
        var resp = controller.uploadOtaRelease(
            ch.jp.smartground.model.OtaTypeValue.APP, "0.7", file);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getVersion()).isEqualTo("0.7");
        verify(otaService).uploadApp("0.7", new byte[]{1});
    }

    @Test
    void triggerReturns202() {
        UUID id = UUID.randomUUID();
        var controller = new OtaController(otaService);
        var req = new ch.jp.smartground.model.TriggerOtaRequest()
            .type(ch.jp.smartground.model.OtaTypeValue.APP).version("0.7");
        var resp = controller.triggerSmartBoxOta(id, req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(otaService).triggerOta(id, OtaType.APP, "0.7");
    }

    @Test
    void statusReflectsBoxFields() {
        UUID id = UUID.randomUUID();
        SmartBox box = new SmartBox();
        box.setOtaPhase("APPLIED");
        box.setOtaVersion("0.7");
        box.setOtaProgress(100);
        when(otaService.getBox(id)).thenReturn(box);

        var controller = new OtaController(otaService);
        var resp = controller.getSmartBoxOtaStatus(id);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getPhase()).isEqualTo("APPLIED");
        assertThat(resp.getBody().getProgress()).isEqualTo(100);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=OtaControllerTest`
Expected: FAIL — `OtaController` does not exist.

- [ ] **Step 3: Implement `OtaController` (implements generated `OtaApi`)**

```java
package ch.jp.shooting.api;

import ch.jp.smartground.api.OtaApi;
import ch.jp.smartground.model.OtaReleaseResponse;
import ch.jp.smartground.model.OtaStatusResponse;
import ch.jp.smartground.model.OtaTypeValue;
import ch.jp.smartground.model.TriggerOtaRequest;
import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.service.OtaService;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@RestController
@NullMarked
public class OtaController implements OtaApi {

    private final OtaService otaService;

    public OtaController(OtaService otaService) {
        this.otaService = otaService;
    }

    @Override
    public ResponseEntity<java.util.List<OtaReleaseResponse>> listOtaReleases() {
        return ResponseEntity.ok(otaService.listReleases().stream().map(this::toResponse).toList());
    }

    @Override
    public ResponseEntity<OtaReleaseResponse> uploadOtaRelease(
            OtaTypeValue type, String version, MultipartFile file) {
        byte[] bytes = readBytes(file);
        OtaRelease release = type == OtaTypeValue.APP
            ? otaService.uploadApp(version, bytes)
            : otaService.uploadFirmware(version, bytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(release));
    }

    @Override
    public ResponseEntity<Void> triggerSmartBoxOta(UUID id, TriggerOtaRequest request) {
        OtaType type = OtaType.valueOf(request.getType().getValue());
        otaService.triggerOta(id, type, request.getVersion());
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<OtaStatusResponse> getSmartBoxOtaStatus(UUID id) {
        SmartBox box = otaService.getBox(id);
        OtaStatusResponse resp = new OtaStatusResponse()
            .version(box.getOtaVersion())
            .phase(box.getOtaPhase())
            .progress(box.getOtaProgress())
            .detail(box.getOtaDetail())
            .updatedAt(box.getOtaUpdatedAt() == null ? null
                : OffsetDateTime.ofInstant(box.getOtaUpdatedAt(), ZoneOffset.UTC));
        return ResponseEntity.ok(resp);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private OtaReleaseResponse toResponse(OtaRelease r) {
        return new OtaReleaseResponse()
            .id(r.getId())
            .type(OtaTypeValue.fromValue(r.getType().name()))
            .version(r.getVersion())
            .sha256(r.getSha256())
            .sizeBytes(r.getSizeBytes())
            .createdAt(OffsetDateTime.ofInstant(r.getCreatedAt(), ZoneOffset.UTC));
    }
}
```

> The generated `uploadOtaRelease` signature for a `multipart/form-data` body with a `binary` property is `(OtaTypeValue type, String version, MultipartFile file)`. If the generator names the parameter differently, match the generated `OtaApi` interface exactly.

- [ ] **Step 4: Register the new exceptions in `GlobalExceptionHandler`**

Add handlers (follow the existing `/errors/{slug}` pattern):

```java
    @ExceptionHandler(OtaReleaseNotFoundException.class)
    public ProblemDetail handleOtaReleaseNotFound(OtaReleaseNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("/errors/ota-release-not-found"));
        return pd;
    }

    @ExceptionHandler(InvalidOtaArtifactException.class)
    public ProblemDetail handleInvalidOtaArtifact(InvalidOtaArtifactException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("/errors/invalid-ota-artifact"));
        return pd;
    }
```

Add imports `ch.jp.shooting.exception.OtaReleaseNotFoundException` and `ch.jp.shooting.exception.InvalidOtaArtifactException` (and `java.net.URI` / `org.springframework.http.ProblemDetail` if not already present).

- [ ] **Step 5: Run it to verify it passes**

Run: `./mvnw test -Dtest=OtaControllerTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/api/OtaController.java \
        src/main/java/ch/jp/shooting/config/GlobalExceptionHandler.java \
        src/test/java/ch/jp/shooting/api/OtaControllerTest.java
git commit -m "[backend] Add OtaController (upload, list, trigger, status) + error mapping"
```

---

## Task 9: `OtaDownloadController` — box-facing downloads (documented OpenAPI exception)

**Files:**
- Create: `src/main/java/ch/jp/shooting/api/OtaDownloadController.java`
- Test: `src/test/java/ch/jp/shooting/api/OtaDownloadControllerTest.java`

- [ ] **Step 1: Write the failing test (MockMvc, standalone)**

```java
package ch.jp.shooting.api;

import ch.jp.shooting.service.OtaArtifactStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OtaDownloadControllerTest {

    OtaArtifactStore store;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        store = mock(OtaArtifactStore.class);
        mvc = MockMvcBuilders.standaloneSetup(new OtaDownloadController(store)).build();
    }

    @Test
    void servesManifest() throws Exception {
        when(store.readAppFile("0.7", "manifest.json"))
            .thenReturn("{\"appVersion\":\"0.7\"}".getBytes());
        mvc.perform(get("/api/ota/app/0.7/manifest.json"))
            .andExpect(status().isOk())
            .andExpect(content().string("{\"appVersion\":\"0.7\"}"));
    }

    @Test
    void servesNestedFile() throws Exception {
        when(store.readAppFile("0.7", "files/boards/xiao_esp32s3.py"))
            .thenReturn("BOX".getBytes());
        mvc.perform(get("/api/ota/app/0.7/files/boards/xiao_esp32s3.py"))
            .andExpect(status().isOk())
            .andExpect(content().bytes("BOX".getBytes()));
        verify(store).readAppFile("0.7", "files/boards/xiao_esp32s3.py");
    }

    @Test
    void servesFirmwareBin() throws Exception {
        when(store.readFirmwareImage("mp-1.24")).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get("/api/ota/firmware/mp-1.24"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=OtaDownloadControllerTest`
Expected: FAIL — `OtaDownloadController` does not exist.

- [ ] **Step 3: Implement the download controller**

```java
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
```

> The `{*path}` catch-all captures the multi-segment remainder (including `/`), so `files/boards/xiao_esp32s3.py` reaches the store intact (`"files" + "/boards/xiao_esp32s3.py"`). `OtaArtifactStore.readAppFile` already guards against traversal.

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw test -Dtest=OtaDownloadControllerTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ch/jp/shooting/api/OtaDownloadController.java \
        src/test/java/ch/jp/shooting/api/OtaDownloadControllerTest.java
git commit -m "[backend] Add box-facing OtaDownloadController (manifest/files/firmware)"
```

---

## Task 10: MQTT `/ota/status` handler + router + inbound subscription

**Files:**
- Create: `src/main/java/ch/jp/shooting/config/SmartBoxOtaStatusHandler.java`
- Modify: `src/main/java/ch/jp/shooting/config/SmartBoxMqttRouter.java`
- Modify: `src/main/java/ch/jp/shooting/config/MqttConfig.java`
- Test: `src/test/java/ch/jp/shooting/config/SmartBoxOtaStatusHandlerTest.java`
- Test: `src/test/java/ch/jp/shooting/config/SmartBoxMqttRouterTest.java`

- [ ] **Step 1: Write the failing tests**

`SmartBoxOtaStatusHandlerTest.java`:

```java
package ch.jp.shooting.config;

import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.SmartBoxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxOtaStatusHandlerTest {

    @Mock SmartBoxRepository repository;

    @Test
    void updatesOtaFieldsFromPayload() {
        var handler = new SmartBoxOtaStatusHandler(repository, new ObjectMapper());
        SmartBox box = new SmartBox();
        box.setMacAddress("aabbccddeeff");
        when(repository.findByMacAddress("aabbccddeeff")).thenReturn(Optional.of(box));
        when(repository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));

        String json = "{\"version\":\"0.7\",\"phase\":\"DOWNLOADING\",\"progress\":40,\"detail\":\"\"}";
        // Topic header trägt den /ota/status-Topic
        handler.handleMessage(MessageBuilder.withPayload(json.getBytes())
            .setHeader("mqtt_receivedTopic", "smartboxes/aabbccddeeff/ota/status").build());

        assertThat(box.getOtaPhase()).isEqualTo("DOWNLOADING");
        assertThat(box.getOtaVersion()).isEqualTo("0.7");
        assertThat(box.getOtaProgress()).isEqualTo(40);
        assertThat(box.getOtaUpdatedAt()).isNotNull();
    }
}
```

`SmartBoxMqttRouterTest.java`:

```java
package ch.jp.shooting.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxMqttRouterTest {

    @Mock SmartBoxDiscoveryHandler discovery;
    @Mock SmartBoxStatusHandler status;
    @Mock SmartBoxConfigAckHandler configAck;
    @Mock SmartBoxDeviceExecutedHandler executed;
    @Mock SmartBoxOtaStatusHandler otaStatus;

    @Test
    void otaStatusTopicRoutesToOtaHandlerNotStatusHandler() {
        var router = new SmartBoxMqttRouter(discovery, status, configAck, executed, otaStatus);
        var msg = MessageBuilder.withPayload("{}".getBytes())
            .setHeader("mqtt_receivedTopic", "smartboxes/aabbccddeeff/ota/status").build();
        router.handleMessage(msg);
        verify(otaStatus).handleMessage(msg);
        verify(status, never()).handleMessage(any());
    }

    @Test
    void plainStatusTopicStillRoutesToStatusHandler() {
        var router = new SmartBoxMqttRouter(discovery, status, configAck, executed, otaStatus);
        var msg = MessageBuilder.withPayload("{}".getBytes())
            .setHeader("mqtt_receivedTopic", "smartboxes/aabbccddeeff/status").build();
        router.handleMessage(msg);
        verify(status).handleMessage(msg);
        verify(otaStatus, never()).handleMessage(any());
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./mvnw test -Dtest=SmartBoxOtaStatusHandlerTest,SmartBoxMqttRouterTest`
Expected: FAIL — `SmartBoxOtaStatusHandler` missing; router constructor has 4 args.

- [ ] **Step 3: Create the OTA status handler**

```java
package ch.jp.shooting.config;

import ch.jp.shooting.repository.SmartBoxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@NullMarked
public class SmartBoxOtaStatusHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SmartBoxOtaStatusHandler.class);

    record OtaStatusPayload(
        @Nullable String version, @Nullable String phase,
        @Nullable Integer progress, @Nullable String detail) {}

    private final SmartBoxRepository smartBoxRepository;
    private final ObjectMapper objectMapper;

    public SmartBoxOtaStatusHandler(SmartBoxRepository smartBoxRepository, ObjectMapper objectMapper) {
        this.smartBoxRepository = smartBoxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void handleMessage(Message<?> message) throws MessagingException {
        try {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            if (topic == null) return;
            String mac = extractMac(topic);

            String json = switch (message.getPayload()) {
                case byte[] b -> new String(b, StandardCharsets.UTF_8);
                case String s -> s;
                default       -> objectMapper.writeValueAsString(message.getPayload());
            };
            OtaStatusPayload p = objectMapper.readValue(json, OtaStatusPayload.class);

            smartBoxRepository.findByMacAddress(mac).ifPresent(box -> {
                box.setOtaPhase(p.phase());
                box.setOtaVersion(p.version());
                box.setOtaProgress(p.progress());
                box.setOtaDetail(p.detail());
                box.setOtaUpdatedAt(Instant.now());
                smartBoxRepository.save(box);
                log.info("OTA-Status von {}: {} {} ({}%)", mac, p.phase(), p.version(), p.progress());
            });
        } catch (Exception e) {
            log.warn("Fehler beim Verarbeiten des OTA-Status: {}", e.getMessage());
        }
    }

    // Topic-Form: smartboxes/{mac}/ota/status
    private String extractMac(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 2 ? parts[1] : "";
    }
}
```

- [ ] **Step 4: Wire the router (OTA check BEFORE the `/status` check)**

In `config/SmartBoxMqttRouter.java`, add the `SmartBoxOtaStatusHandler` constructor dependency and put its branch **before** the `/status` branch (because `/ota/status` also ends with `/status`):

```java
    private final SmartBoxOtaStatusHandler otaStatusHandler;

    public SmartBoxMqttRouter(
            SmartBoxDiscoveryHandler discoveryHandler,
            SmartBoxStatusHandler statusHandler,
            SmartBoxConfigAckHandler configAckHandler,
            SmartBoxDeviceExecutedHandler deviceExecutedHandler,
            SmartBoxOtaStatusHandler otaStatusHandler) {
        this.discoveryHandler      = discoveryHandler;
        this.statusHandler         = statusHandler;
        this.configAckHandler      = configAckHandler;
        this.deviceExecutedHandler = deviceExecutedHandler;
        this.otaStatusHandler      = otaStatusHandler;
    }
```

In `handleMessage`, change the dispatch chain to:

```java
        if (topic.endsWith("/discovery")) {
            discoveryHandler.handleMessage(message);
        } else if (topic.endsWith("/ota/status")) {     // VOR /status prüfen!
            otaStatusHandler.handleMessage(message);
        } else if (topic.endsWith("/status")) {
            statusHandler.handleMessage(message);
        } else if (topic.endsWith("/config/ack")) {
            configAckHandler.handleMessage(message);
        } else if (topic.endsWith("/executed")) {
            deviceExecutedHandler.handleMessage(message);
        } else {
            log.debug("Unbekanntes Topic: {}", topic);
        }
```

- [ ] **Step 5: Subscribe to the inbound topic in `MqttConfig`**

In `config/MqttConfig.java`, add the constant and include it in the adapter:

```java
    static final String TOPIC_OTA_STATUS = "smartboxes/+/ota/status";
```

In `mqttInboundAdapter(...)`, add `TOPIC_OTA_STATUS` to the `Mqttv5PahoMessageDrivenChannelAdapter(...)` topic list and the log line.

- [ ] **Step 6: Run the tests**

Run: `./mvnw test -Dtest=SmartBoxOtaStatusHandlerTest,SmartBoxMqttRouterTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ch/jp/shooting/config/SmartBoxOtaStatusHandler.java \
        src/main/java/ch/jp/shooting/config/SmartBoxMqttRouter.java \
        src/main/java/ch/jp/shooting/config/MqttConfig.java \
        src/test/java/ch/jp/shooting/config/SmartBoxOtaStatusHandlerTest.java \
        src/test/java/ch/jp/shooting/config/SmartBoxMqttRouterTest.java
git commit -m "[backend] Handle /ota/status MQTT, route before /status, subscribe inbound"
```

---

## Task 11: Security permit + config properties

**Files:**
- Modify: `src/main/java/ch/jp/shooting/config/SecurityConfig.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/resources/application-h2.properties` (or the test properties)
- Test: `src/test/java/ch/jp/shooting/api/OtaDownloadSecurityTest.java`

- [ ] **Step 1: Write the failing test (box-facing GET reachable without auth)**

```java
package ch.jp.shooting.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.jp.shooting.service.OtaArtifactStore;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("h2")
class OtaDownloadSecurityTest {

    @Autowired MockMvc mvc;
    @MockBean OtaArtifactStore store;

    @Test
    void manifestIsReachableWithoutAuth() throws Exception {
        when(store.readAppFile("0.7", "manifest.json")).thenReturn("{}".getBytes());
        mvc.perform(get("/api/ota/app/0.7/manifest.json")).andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=OtaDownloadSecurityTest`
Expected: FAIL — 401 Unauthorized (box-facing path not yet permitted).

- [ ] **Step 3: Permit the box-facing OTA GETs in `SecurityConfig`**

In `config/SecurityConfig.java`, in the authorization rules, add (next to the existing public matchers like Swagger / `/api/auth/login`):

```java
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/ota/app/**", "/api/ota/firmware/**").permitAll()
```

Place it before the catch-all `.anyRequest().authenticated()`. Do **not** permit the admin endpoints (`POST /api/ota/releases`, `POST/GET /api/smart-boxes/{id}/ota`) — those stay authenticated.

- [ ] **Step 4: Add config properties**

In `application.properties`:

```properties
# OTA: base URL the SmartBox uses to pull artifacts, and where artifacts are stored
ota.base-url=http://localhost:8080
ota.artifact-dir=./ota-artifacts
```

In `application-h2.properties` (so tests use a temp dir, not the repo):

```properties
ota.base-url=http://localhost:8080
ota.artifact-dir=${java.io.tmpdir}/smartground-ota-test
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./mvnw test -Dtest=OtaDownloadSecurityTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/jp/shooting/config/SecurityConfig.java \
        src/main/resources/application.properties \
        src/main/resources/application-h2.properties \
        src/test/java/ch/jp/shooting/api/OtaDownloadSecurityTest.java
git commit -m "[backend] Permit box-facing OTA GETs; add ota.base-url/artifact-dir config"
```

---

## Task 12: Full-suite green + documentation

**Files:**
- Modify: `smart-ground-backend/CLAUDE.md`

- [ ] **Step 1: Run the whole suite**

Run: `./mvnw clean test`
Expected: BUILD SUCCESS, no failures, no warnings.

- [ ] **Step 2: Update `CLAUDE.md`**

Make these edits to `smart-ground-backend/CLAUDE.md`:

1. **MQTT topic table** — add the two OTA topics:
   - `smartboxes/{mac}/ota` (Backend → SmartBox: OTA trigger)
   - `smartboxes/{mac}/ota/status` (SmartBox → Backend: OTA progress)
   And update the discovery payload example to include `appVersion`.

2. **Controllers Overview table** — add:
   - `OtaController` | POST /api/ota/releases, GET /api/ota/releases, POST/GET /api/smart-boxes/{id}/ota | OTA release upload, listing, trigger, status
   - `OtaDownloadController` | GET /api/ota/app/{version}/manifest.json, /files/**, /api/ota/firmware/{version} | Box-facing artifact download

3. **Add an OpenAPI-rule exception note** (under "OpenAPI & REST Contracts → Hard rules"): record that `OtaDownloadController` is the single, deliberate exception — read-only binary download for the unauthenticated firmware, requiring a multi-segment `{*path}` that OpenAPI codegen cannot express. All other controllers remain contract-first.

4. **DB schema (IoT / Device tables)** — add `ota_releases (id, type, version, sha256, size_bytes, created_at  UNIQUE(type, version))` and note the new `smart_boxes` columns: `app_version, ota_phase, ota_version, ota_progress, ota_detail, ota_updated_at`.

5. **Error Responses table** — add `OtaReleaseNotFoundException` → 404 `/errors/ota-release-not-found` and `InvalidOtaArtifactException` → 400 `/errors/invalid-ota-artifact`.

6. **Implementation Status** — move "OTA firmware update delivery" from ❌ Not Yet Implemented to ✅ Implemented, with a one-line description of the upload→serve→trigger→status flow and the `ota.base-url` / `ota.artifact-dir` properties.

- [ ] **Step 3: Commit**

```bash
git add smart-ground-backend/CLAUDE.md
git commit -m "[backend] Document OTA endpoints, schema, and OpenAPI exception in CLAUDE.md"
```

---

## Out of scope (this plan)

- Frontend UI for uploading releases and triggering OTA (separate `smart-ground-ui` plan).
- Signed/authenticated artifacts (SHA-256 integrity only; LAN trust — matches the firmware spec's stance).
- Automatic "push to whole fleet" / scheduled rollouts (single-box trigger only for now).
- Delta updates; resumable downloads.

## Backend ⇄ firmware contract check (already satisfied by the firmware)

- Publishes `smartboxes/{mac}/ota` `{ type, version, url, sha256, size }` — ✅ Task 6.
- Serves `GET {url}/manifest.json`, `GET {url}/files/{path}`, firmware `GET {url}` — ✅ Tasks 2, 9.
- The `sha256` in the command is the **manifest** hash (APP) / **image** hash (FIRMWARE) — ✅ Tasks 2, 3, 6 (the firmware uses it as the trust anchor).
- Subscribes `smartboxes/{mac}/ota/status` — ✅ Task 10.
- Reads `appVersion` from discovery — ✅ Task 5.

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
        // Existierende Version überschreiben (upsert-Semantik vor v1.0)
        OtaRelease release = repository.findByTypeAndVersion(type, version).orElseGet(OtaRelease::new);
        release.setType(type);
        release.setVersion(version);
        release.setSha256(sha256);
        release.setSizeBytes(size);
        release.setCreatedAt(Instant.now());
        return repository.save(release);
    }
}

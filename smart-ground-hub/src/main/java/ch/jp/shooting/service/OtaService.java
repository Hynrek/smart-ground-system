package ch.jp.shooting.service;

import ch.jp.shooting.config.OtaPublishService;
import ch.jp.shooting.exception.OtaReleaseNotFoundException;
import ch.jp.shooting.exception.SmartBoxNotFoundException;
import ch.jp.shooting.model.OtaRelease;
import ch.jp.shooting.model.OtaType;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.OtaReleaseRepository;
import ch.jp.shooting.repository.SmartBoxRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@NullMarked
public class OtaService {

    private final OtaArtifactStore store;
    private final OtaReleaseRepository repository;
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

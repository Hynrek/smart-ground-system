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
import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class OtaController implements OtaApi {

    private final OtaService otaService;

    public OtaController(OtaService otaService) {
        this.otaService = otaService;
    }

    @Override
    public ResponseEntity<List<OtaReleaseResponse>> listOtaReleases() {
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
    public ResponseEntity<Void> triggerSmartBoxOta(UUID id, TriggerOtaRequest triggerOtaRequest) {
        OtaType type = OtaType.valueOf(triggerOtaRequest.getType().getValue());
        otaService.triggerOta(id, type, triggerOtaRequest.getVersion());
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

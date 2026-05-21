package ch.jp.shooting.api;

import ch.jp.shooting.service.AblaufService;
import ch.jp.shooting.service.ProgrammService;
import ch.jp.smartground.api.ProgrammeApi;
import ch.jp.smartground.model.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Implementiert ProgrammeApi – enthält sowohl Ablauf- als auch Programm-Endpunkte
@RestController
@NullMarked
public class ProgrammeApiController implements ProgrammeApi {

    private final AblaufService ablaufService;
    private final ProgrammService programmService;

    public ProgrammeApiController(AblaufService ablaufService, ProgrammService programmService) {
        this.ablaufService = ablaufService;
        this.programmService = programmService;
    }

    // ── Abläufe ──────────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<List<AblaufResponse>> listAblaeufe(String ownership, UUID rangeId) {
        return ResponseEntity.ok(ablaufService.listAblaeufe(ownership, rangeId));
    }

    @Override
    public ResponseEntity<AblaufResponse> createAblauf(CreateAblaufRequest createAblaufRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ablaufService.createAblauf(createAblaufRequest));
    }

    @Override
    public ResponseEntity<AblaufResponse> getAblauf(UUID id) {
        return ResponseEntity.ok(ablaufService.getAblauf(id));
    }

    @Override
    public ResponseEntity<AblaufResponse> updateAblauf(UUID id, UpdateAblaufRequest updateAblaufRequest) {
        return ResponseEntity.ok(ablaufService.updateAblauf(id, updateAblaufRequest));
    }

    @Override
    public ResponseEntity<AblaufResponse> updateAblaufOwnership(UUID id,
            UpdateAblaufOwnershipRequest updateAblaufOwnershipRequest) {
        return ResponseEntity.ok(ablaufService.updateAblaufOwnership(id, updateAblaufOwnershipRequest));
    }

    @Override
    public ResponseEntity<Void> deleteAblauf(UUID id) {
        ablaufService.deleteAblauf(id);
        return ResponseEntity.noContent().build();
    }

    // ── Programme ────────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<List<ProgrammeResponse>> listProgrammes() {
        return ResponseEntity.ok(programmService.listProgramme());
    }

    @Override
    public ResponseEntity<ProgrammeResponse> createProgramme(CreateProgrammeRequest createProgrammeRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(programmService.createProgramm(createProgrammeRequest));
    }

    @Override
    public ResponseEntity<ProgrammeResponse> getProgramme(UUID id) {
        return ResponseEntity.ok(programmService.getProgramm(id));
    }

    @Override
    public ResponseEntity<ProgrammeResponse> updateProgramme(UUID id,
            UpdateProgrammeRequest updateProgrammeRequest) {
        return ResponseEntity.ok(programmService.updateProgramm(id, updateProgrammeRequest));
    }

    @Override
    public ResponseEntity<Void> deleteProgramme(UUID id) {
        programmService.deleteProgramm(id);
        return ResponseEntity.noContent().build();
    }
}

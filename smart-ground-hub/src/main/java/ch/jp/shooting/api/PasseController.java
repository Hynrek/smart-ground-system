package ch.jp.shooting.api;

import ch.jp.shooting.service.PasseService;
import ch.jp.smartground.api.PasseApi;
import ch.jp.smartground.model.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class PasseController implements PasseApi {

    private final PasseService passeService;

    public PasseController(PasseService passeService) {
        this.passeService = passeService;
    }

    @Override
    public ResponseEntity<List<PasseResponse>> listPassen() {
        return ResponseEntity.ok(passeService.listPassen());
    }

    @Override
    public ResponseEntity<PasseResponse> createPasse(CreatePasseRequest createPasseRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(passeService.createPasse(createPasseRequest));
    }

    @Override
    public ResponseEntity<PasseResponse> getPasse(UUID id) {
        return ResponseEntity.ok(passeService.getPasse(id));
    }

    @Override
    public ResponseEntity<PasseResponse> updatePasse(UUID id,
            UpdatePasseRequest updatePasseRequest) {
        return ResponseEntity.ok(passeService.updatePasse(id, updatePasseRequest));
    }

    @Override
    public ResponseEntity<Void> deletePasse(UUID id) {
        passeService.deletePasse(id);
        return ResponseEntity.noContent().build();
    }
}

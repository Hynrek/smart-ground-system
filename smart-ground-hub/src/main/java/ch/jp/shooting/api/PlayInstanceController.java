package ch.jp.shooting.api;

import ch.jp.shooting.service.PlayInstanceService;
import ch.jp.smartground.api.PlayInstanceApi;
import ch.jp.smartground.model.CompleteBlockRequest;
import ch.jp.smartground.model.PlayInstanceResponse;
import ch.jp.smartground.model.StartPasseInstanceRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Implementiert PlayInstanceApi (generierte Schnittstelle)
@RestController
@NullMarked
public class PlayInstanceController implements PlayInstanceApi {

    private final PlayInstanceService playInstanceService;

    public PlayInstanceController(PlayInstanceService playInstanceService) {
        this.playInstanceService = playInstanceService;
    }

    @Override
    public ResponseEntity<PlayInstanceResponse> startPasseInstance(
            StartPasseInstanceRequest startPasseInstanceRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(playInstanceService.startPasseInstance(startPasseInstanceRequest));
    }

    @Override
    public ResponseEntity<List<PlayInstanceResponse>> listPlayInstances(String status, UUID rangeId) {
        return ResponseEntity.ok(playInstanceService.listPlayInstances(status, rangeId));
    }

    @Override
    public ResponseEntity<PlayInstanceResponse> getPlayInstance(UUID instanceId) {
        return ResponseEntity.ok(playInstanceService.getPlayInstance(instanceId));
    }

    @Override
    public ResponseEntity<Void> stopPlayInstance(UUID instanceId) {
        playInstanceService.stopPlayInstance(instanceId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PlayInstanceResponse> startBlock(UUID instanceId, UUID blockId) {
        playInstanceService.startBlock(instanceId, blockId);
        return ResponseEntity.ok(playInstanceService.getPlayInstance(instanceId));
    }

    @Override
    public ResponseEntity<PlayInstanceResponse> completeBlock(UUID instanceId, UUID blockId,
            CompleteBlockRequest completeBlockRequest) {
        return ResponseEntity.ok(playInstanceService.completeBlock(instanceId, blockId, completeBlockRequest));
    }
}

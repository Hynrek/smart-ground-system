package ch.jp.shooting.api;

import ch.jp.shooting.service.PlayInstanceService;
import ch.jp.smartground.api.PlayResultApi;
import ch.jp.smartground.model.PlayResultPage;
import ch.jp.smartground.model.PlayResultResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

// Implementiert PlayResultApi (generierte Schnittstelle)
@RestController
@NullMarked
public class PlayResultController implements PlayResultApi {

    private final PlayInstanceService playInstanceService;

    public PlayResultController(PlayInstanceService playInstanceService) {
        this.playInstanceService = playInstanceService;
    }

    @Override
    public ResponseEntity<PlayResultPage> listPlayResults(UUID rangeId, OffsetDateTime from, OffsetDateTime to,
            Integer page, Integer size) {
        return ResponseEntity.ok(playInstanceService.listPlayResults(
            rangeId, from, to,
            page != null ? page : 0,
            size != null ? size : 20));
    }

    @Override
    public ResponseEntity<PlayResultResponse> getPlayResult(UUID resultId) {
        return ResponseEntity.ok(playInstanceService.getPlayResult(resultId));
    }
}

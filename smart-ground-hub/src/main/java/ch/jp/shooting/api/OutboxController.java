package ch.jp.shooting.api;

import ch.jp.shooting.service.PlayInstanceOutboxService;
import ch.jp.shooting.service.SerieOutboxService;
import ch.jp.smartground.api.OutboxApi;
import ch.jp.smartground.model.PlayInstanceOutboxItem;
import ch.jp.smartground.model.PlayInstanceOutboxResult;
import ch.jp.smartground.model.SerieOutboxItem;
import ch.jp.smartground.model.SerieOutboxResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OutboxController implements OutboxApi {

    private final SerieOutboxService serieOutboxService;
    private final PlayInstanceOutboxService playInstanceOutboxService;

    public OutboxController(SerieOutboxService serieOutboxService,
                             PlayInstanceOutboxService playInstanceOutboxService) {
        this.serieOutboxService = serieOutboxService;
        this.playInstanceOutboxService = playInstanceOutboxService;
    }

    @Override
    public ResponseEntity<SerieOutboxResult> pushSerieOutboxItem(SerieOutboxItem serieOutboxItem) {
        return ResponseEntity.ok(serieOutboxService.push(serieOutboxItem));
    }

    @Override
    public ResponseEntity<PlayInstanceOutboxResult> pushPlayInstanceOutboxItem(PlayInstanceOutboxItem playInstanceOutboxItem) {
        return ResponseEntity.ok(playInstanceOutboxService.push(playInstanceOutboxItem));
    }
}

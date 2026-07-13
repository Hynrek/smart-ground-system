package ch.jp.shooting.api;

import ch.jp.shooting.service.SerieOutboxService;
import ch.jp.smartground.api.OutboxApi;
import ch.jp.smartground.model.SerieOutboxItem;
import ch.jp.smartground.model.SerieOutboxResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OutboxController implements OutboxApi {

    private final SerieOutboxService serieOutboxService;

    public OutboxController(SerieOutboxService serieOutboxService) {
        this.serieOutboxService = serieOutboxService;
    }

    @Override
    public ResponseEntity<SerieOutboxResult> pushSerieOutboxItem(SerieOutboxItem serieOutboxItem) {
        return ResponseEntity.ok(serieOutboxService.push(serieOutboxItem));
    }
}

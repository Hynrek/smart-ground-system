package ch.jp.shooting.api;

import ch.jp.shooting.service.SerieService;
import ch.jp.smartground.api.SerieApi;
import ch.jp.smartground.model.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class SerieController implements SerieApi {

    private final SerieService serieService;

    public SerieController(SerieService serieService) {
        this.serieService = serieService;
    }

    @Override
    public ResponseEntity<List<SerieResponse>> listSerien(String ownership, UUID rangeId) {
        return ResponseEntity.ok(serieService.listSerien(ownership, rangeId));
    }

    @Override
    public ResponseEntity<SerieResponse> createSerie(CreateSerieRequest createSerieRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(serieService.createSerie(createSerieRequest));
    }

    @Override
    public ResponseEntity<SerieResponse> getSerie(UUID id) {
        return ResponseEntity.ok(serieService.getSerie(id));
    }

    @Override
    public ResponseEntity<SerieResponse> updateSerie(UUID id, UpdateSerieRequest updateSerieRequest) {
        return ResponseEntity.ok(serieService.updateSerie(id, updateSerieRequest));
    }

    @Override
    public ResponseEntity<SerieResponse> updateSerieOwnership(UUID id,
            UpdateSerieOwnershipRequest updateSerieOwnershipRequest) {
        return ResponseEntity.ok(serieService.updateSerieOwnership(id, updateSerieOwnershipRequest));
    }

    @Override
    public ResponseEntity<SerieResponse> updateSeriePublished(UUID id,
            UpdateSeriePublishedRequest updateSeriePublishedRequest) {
        return ResponseEntity.ok(serieService.updateSeriePublished(id, updateSeriePublishedRequest));
    }

    @Override
    public ResponseEntity<Void> deleteSerie(UUID id) {
        serieService.deleteSerie(id);
        return ResponseEntity.noContent().build();
    }
}

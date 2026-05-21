package ch.jp.shooting.api;

import ch.jp.shooting.service.TrainingService;
import ch.jp.smartground.api.TrainingApi;
import ch.jp.smartground.model.CreateTrainingRequest;
import ch.jp.smartground.model.TrainingResponse;
import ch.jp.smartground.model.UpdateTrainingRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Implementiert TrainingApi (generierte Schnittstelle)
@RestController
@NullMarked
public class TrainingController implements TrainingApi {

    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @Override
    public ResponseEntity<List<TrainingResponse>> listTrainings() {
        return ResponseEntity.ok(trainingService.listTrainings());
    }

    @Override
    public ResponseEntity<TrainingResponse> createTraining(CreateTrainingRequest createTrainingRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(trainingService.createTraining(createTrainingRequest));
    }

    @Override
    public ResponseEntity<TrainingResponse> getTraining(UUID id) {
        return ResponseEntity.ok(trainingService.getTraining(id));
    }

    @Override
    public ResponseEntity<TrainingResponse> updateTraining(UUID id, UpdateTrainingRequest updateTrainingRequest) {
        return ResponseEntity.ok(trainingService.updateTraining(id, updateTrainingRequest));
    }

    @Override
    public ResponseEntity<Void> deleteTraining(UUID id) {
        trainingService.deleteTraining(id);
        return ResponseEntity.noContent().build();
    }
}

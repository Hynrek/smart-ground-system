package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.EmbeddedSerieRecord;
import ch.jp.shooting.dto.play.TrainingPasseRecord;
import ch.jp.shooting.exception.PasseNotFoundException;
import ch.jp.shooting.exception.TrainingNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Training;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.TrainingRepository;
import ch.jp.smartground.model.CreateTrainingRequest;
import ch.jp.smartground.model.TrainingResponse;
import ch.jp.smartground.model.UpdateTrainingRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// Geschäftslogik für Trainings (Sammlungen von Passen)
@Service
@NullMarked
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final PasseRepository passeRepository;
    private final SecurityHelper securityHelper;

    public TrainingService(TrainingRepository trainingRepository,
                           PasseRepository passeRepository,
                           SecurityHelper securityHelper) {
        this.trainingRepository = trainingRepository;
        this.passeRepository = passeRepository;
        this.securityHelper = securityHelper;
    }

    /** Listet alle Trainings des aktuellen Nutzers. */
    public List<TrainingResponse> listTrainings() {
        var owner = securityHelper.currentUser();
        return trainingRepository.findByOwner(owner).stream()
            .map(PlayMapper::toTrainingResponse)
            .toList();
    }

    /**
     * Erstellt ein neues Training. Die gewählten Passen werden als Snapshot eingebettet.
     * request.getPasseIds() gibt List<UUID> zurück.
     * Für jede Passe werden ihre eingebetteten Serien als TrainingPasseRecord übernommen.
     */
    public TrainingResponse createTraining(CreateTrainingRequest request) {
        var owner = securityHelper.currentUser();
        var passen = request.getPasseIds().stream()
            .map(id -> passeRepository.findById(id)
                .orElseThrow(() -> new PasseNotFoundException(id)))
            .map(p -> new TrainingPasseRecord(
                p.getId(),
                p.getName(),
                PlayMapper.parseEmbeddedSerien(p.getSerienJson())
            ))
            .toList();

        var training = new Training();
        training.setName(request.getName());
        training.setOwner(owner);
        training.setProgrammesJson(PlayMapper.writeTrainingPassen(passen));

        return PlayMapper.toTrainingResponse(trainingRepository.save(training));
    }

    /** Gibt ein Training zurück – nur der Besitzer darf es sehen. */
    public TrainingResponse getTraining(UUID id) {
        var training = trainingRepository.findById(id)
            .orElseThrow(() -> new TrainingNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!training.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return PlayMapper.toTrainingResponse(training);
    }

    /** Benennt ein Training um. */
    public TrainingResponse updateTraining(UUID id, UpdateTrainingRequest request) {
        var training = trainingRepository.findById(id)
            .orElseThrow(() -> new TrainingNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!training.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        training.setName(request.getName());
        return PlayMapper.toTrainingResponse(trainingRepository.save(training));
    }

    /** Löscht ein Training (nur Besitzer). */
    public void deleteTraining(UUID id) {
        var training = trainingRepository.findById(id)
            .orElseThrow(() -> new TrainingNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!training.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        trainingRepository.delete(training);
    }
}

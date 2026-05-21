package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.TrainingProgrammeRecord;
import ch.jp.shooting.exception.ProgrammNotFoundException;
import ch.jp.shooting.exception.TrainingNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Training;
import ch.jp.shooting.repository.ProgrammRepository;
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

// Geschäftslogik für Trainings (Sammlungen von Programmen)
@Service
@NullMarked
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final ProgrammRepository programmRepository;
    private final SecurityHelper securityHelper;

    public TrainingService(TrainingRepository trainingRepository,
                           ProgrammRepository programmRepository,
                           SecurityHelper securityHelper) {
        this.trainingRepository = trainingRepository;
        this.programmRepository = programmRepository;
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
     * Erstellt ein neues Training. Die gewählten Programme werden als Snapshot eingebettet.
     * request.getProgrammeIds() gibt List<UUID> zurück.
     * Für jedes Programm werden seine eingebetteten Abläufe als TrainingProgrammeRecord übernommen.
     */
    public TrainingResponse createTraining(CreateTrainingRequest request) {
        var owner = securityHelper.currentUser();
        // Snapshot jedes Programms (inklusive seiner eingebetteten Abläufe)
        var programmes = request.getProgrammeIds().stream()
            .map(id -> programmRepository.findById(id)
                .orElseThrow(() -> new ProgrammNotFoundException(id)))
            .map(p -> new TrainingProgrammeRecord(
                p.getId(),
                p.getName(),
                PlayMapper.parseEmbeddedAblaeufe(p.getAblaufeJson())  // Snapshot der Abläufe
            ))
            .toList();

        var training = new Training();
        training.setName(request.getName());
        training.setOwner(owner);
        training.setProgrammesJson(PlayMapper.writeTrainingProgrammes(programmes));

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

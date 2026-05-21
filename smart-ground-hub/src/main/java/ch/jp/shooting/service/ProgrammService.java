package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.EmbeddedAblaufRecord;
import ch.jp.shooting.exception.AblaufNotFoundException;
import ch.jp.shooting.exception.ProgrammNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Programm;
import ch.jp.shooting.repository.AblaufRepository;
import ch.jp.shooting.repository.ProgrammRepository;
import ch.jp.smartground.model.CreateProgrammeRequest;
import ch.jp.smartground.model.ProgrammeResponse;
import ch.jp.smartground.model.UpdateProgrammeRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// Geschäftslogik für Programme (Sammlungen von Abläufen)
@Service
@NullMarked
public class ProgrammService {

    private final ProgrammRepository programmRepository;
    private final AblaufRepository ablaufRepository;
    private final SecurityHelper securityHelper;

    public ProgrammService(ProgrammRepository programmRepository,
                           AblaufRepository ablaufRepository,
                           SecurityHelper securityHelper) {
        this.programmRepository = programmRepository;
        this.ablaufRepository = ablaufRepository;
        this.securityHelper = securityHelper;
    }

    /** Listet alle Programme des aktuellen Nutzers. */
    public List<ProgrammeResponse> listProgramme() {
        var owner = securityHelper.currentUser();
        return programmRepository.findByOwner(owner).stream()
            .map(PlayMapper::toProgrammeResponse)
            .toList();
    }

    /**
     * Erstellt ein neues Programm. Die gewählten Abläufe werden als Snapshot eingebettet.
     * request.getAblaufIds() ist eine List<UUID>.
     */
    public ProgrammeResponse createProgramm(CreateProgrammeRequest request) {
        var owner = securityHelper.currentUser();
        // Snapshot jedes Ablaufs
        var ablaeufe = request.getAblaufIds().stream()
            .map(id -> ablaufRepository.findById(id)
                .orElseThrow(() -> new AblaufNotFoundException(id)))
            .map(a -> new EmbeddedAblaufRecord(
                a.getId(),
                a.getName(),
                a.getRange() != null ? a.getRange().getId() : null,
                a.getRange() != null ? a.getRange().getName() : null,
                PlayMapper.parseSteps(a.getStepsJson())
            ))
            .toList();

        var programm = new Programm();
        programm.setName(request.getName());
        programm.setOwner(owner);
        programm.setAblaufeJson(PlayMapper.writeEmbeddedAblaeufe(ablaeufe));

        return PlayMapper.toProgrammeResponse(programmRepository.save(programm));
    }

    /** Gibt ein Programm zurück – nur der Besitzer darf es sehen. */
    public ProgrammeResponse getProgramm(UUID id) {
        var programm = programmRepository.findById(id)
            .orElseThrow(() -> new ProgrammNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!programm.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return PlayMapper.toProgrammeResponse(programm);
    }

    /** Benennt ein Programm um. */
    public ProgrammeResponse updateProgramm(UUID id, UpdateProgrammeRequest request) {
        var programm = programmRepository.findById(id)
            .orElseThrow(() -> new ProgrammNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!programm.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        programm.setName(request.getName());
        return PlayMapper.toProgrammeResponse(programmRepository.save(programm));
    }

    /** Löscht ein Programm (nur Besitzer). */
    public void deleteProgramm(UUID id) {
        var programm = programmRepository.findById(id)
            .orElseThrow(() -> new ProgrammNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!programm.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        programmRepository.delete(programm);
    }
}

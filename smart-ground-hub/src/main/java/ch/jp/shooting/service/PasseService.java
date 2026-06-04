package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.EmbeddedSerieRecord;
import ch.jp.shooting.exception.PasseNotFoundException;
import ch.jp.shooting.exception.SerieNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Passe;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.smartground.model.CreatePasseRequest;
import ch.jp.smartground.model.PasseResponse;
import ch.jp.smartground.model.UpdatePasseRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// Geschäftslogik für Passen (Sammlungen von Serien)
@Service
@NullMarked
public class PasseService {

    private final PasseRepository passeRepository;
    private final SerieRepository serieRepository;
    private final SecurityHelper securityHelper;

    public PasseService(PasseRepository passeRepository,
                        SerieRepository serieRepository,
                        SecurityHelper securityHelper) {
        this.passeRepository = passeRepository;
        this.serieRepository = serieRepository;
        this.securityHelper = securityHelper;
    }

    /** Listet alle Passen des aktuellen Nutzers. */
    public List<PasseResponse> listPassen() {
        var owner = securityHelper.currentUser();
        return passeRepository.findByOwner(owner).stream()
            .map(PlayMapper::toPasseResponse)
            .toList();
    }

    /**
     * Erstellt eine neue Passe. Die gewählten Serien werden als Snapshot eingebettet.
     */
    public PasseResponse createPasse(CreatePasseRequest request) {
        var owner = securityHelper.currentUser();
        var serien = request.getSerieIds().stream()
            .map(id -> serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id)))
            .map(s -> new EmbeddedSerieRecord(
                s.getId(),
                s.getName(),
                s.getRange() != null ? s.getRange().getId() : null,
                s.getRange() != null ? s.getRange().getName() : null,
                PlayMapper.parseSteps(s.getStepsJson())
            ))
            .toList();

        var passe = new Passe();
        passe.setName(request.getName());
        passe.setOwner(owner);
        passe.setSerienJson(PlayMapper.writeEmbeddedSerien(serien));

        return PlayMapper.toPasseResponse(passeRepository.save(passe));
    }

    /** Gibt eine Passe zurück – nur der Besitzer darf sie sehen. */
    public PasseResponse getPasse(UUID id) {
        var passe = passeRepository.findById(id)
            .orElseThrow(() -> new PasseNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!passe.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return PlayMapper.toPasseResponse(passe);
    }

    /** Benennt eine Passe um. */
    public PasseResponse updatePasse(UUID id, UpdatePasseRequest request) {
        var passe = passeRepository.findById(id)
            .orElseThrow(() -> new PasseNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!passe.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        passe.setName(request.getName());
        return PlayMapper.toPasseResponse(passeRepository.save(passe));
    }

    /** Löscht eine Passe (nur Besitzer). */
    public void deletePasse(UUID id) {
        var passe = passeRepository.findById(id)
            .orElseThrow(() -> new PasseNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!passe.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        passeRepository.delete(passe);
    }
}

package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.EmbeddedSerieRecord;
import ch.jp.shooting.dto.play.StepRecord;
import ch.jp.shooting.exception.PasseNotFoundException;
import ch.jp.shooting.exception.SerieNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Passe;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.smartground.model.CreatePasseRequest;
import ch.jp.smartground.model.PasseResponse;
import ch.jp.smartground.model.UpdatePasseRequest;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Geschäftslogik für Passen (geordnete Referenzen auf Serien, live verbunden)
@Service
@NullMarked
public class PasseService {

    /** Anzeige-Platzhalter für eine referenzierte, aber gelöschte Serie. */
    static final String MISSING_SERIE_ALIAS = "—";

    private final PasseRepository passeRepository;
    private final SerieRepository serieRepository;
    private final SecurityHelper securityHelper;
    private final PositionLabelResolver positionLabelResolver;

    public PasseService(PasseRepository passeRepository,
                        SerieRepository serieRepository,
                        SecurityHelper securityHelper,
                        PositionLabelResolver positionLabelResolver) {
        this.passeRepository = passeRepository;
        this.serieRepository = serieRepository;
        this.securityHelper = securityHelper;
        this.positionLabelResolver = positionLabelResolver;
    }

    /** Listet alle Passen des aktuellen Nutzers. */
    public List<PasseResponse> listPassen() {
        var owner = securityHelper.currentUser();
        return passeRepository.findByOwner(owner).stream()
            .map(this::toResponse)
            .toList();
    }

    /** Erstellt eine neue Passe als geordnete Referenz auf bestehende Serien. */
    public PasseResponse createPasse(CreatePasseRequest request) {
        var owner = securityHelper.currentUser();
        // Validierung: alle referenzierten Serien müssen existieren
        request.getSerieIds().forEach(id -> {
            if (!serieRepository.existsById(id)) throw new SerieNotFoundException(id);
        });

        var passe = new Passe();
        passe.setName(request.getName());
        passe.setOwner(owner);
        passe.setSerieIdsJson(PlayMapper.writeSerieIds(request.getSerieIds()));

        return toResponse(passeRepository.save(passe));
    }

    /** Gibt eine Passe zurück – nur der Besitzer darf sie sehen. */
    public PasseResponse getPasse(UUID id) {
        var passe = passeRepository.findById(id)
            .orElseThrow(() -> new PasseNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!passe.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return toResponse(passe);
    }

    /** Aktualisiert Name und optional die Serie-Zuordnung einer Passe. */
    public PasseResponse updatePasse(UUID id, UpdatePasseRequest request) {
        var passe = passeRepository.findById(id)
            .orElseThrow(() -> new PasseNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!passe.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        passe.setName(request.getName());
        if (request.getSerieIds() != null && !request.getSerieIds().isEmpty()) {
            request.getSerieIds().forEach(serieId -> {
                if (!serieRepository.existsById(serieId)) throw new SerieNotFoundException(serieId);
            });
            passe.setSerieIdsJson(PlayMapper.writeSerieIds(request.getSerieIds()));
        }
        return toResponse(passeRepository.save(passe));
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

    /**
     * Verbindet die referenzierten Serie-IDs einer Passe live mit den aktuellen Serien
     * und löst die Step-Labels über die aktuellen Positionen auf (Reihenfolge bleibt erhalten).
     * Eine gelöschte Serie wird als Platzhalter ({@code missing = true}, keine Steps) zurückgegeben.
     * Wiederverwendet von PlayInstanceService und SessionService.
     */
    public List<EmbeddedSerieRecord> resolveLiveSerien(Passe passe) {
        var ids = PlayMapper.parseSerieIds(passe.getSerieIdsJson());
        var serienById = serieRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Serie::getId, s -> s));

        // Steps je vorhandener Serie parsen (Reihenfolge der IDs bleibt erhalten)
        Map<UUID, List<StepRecord>> stepsBySerie = ids.stream()
            .filter(serienById::containsKey)
            .collect(Collectors.toMap(
                id -> id,
                id -> PlayMapper.parseSteps(serienById.get(id).getStepsJson()),
                (a, b) -> a,
                LinkedHashMap::new));

        // Alle Positions-IDs in EINEM Lookup auflösen
        var posIds = stepsBySerie.values().stream()
            .flatMap(List::stream)
            .flatMap(s -> Stream.of(s.posId(), s.posId1(), s.posId2()))
            .filter(Objects::nonNull)
            .toList();
        var positions = positionLabelResolver.byPosIds(posIds);

        return ids.stream().map(id -> {
            var serie = serienById.get(id);
            if (serie == null) {
                return new EmbeddedSerieRecord(id, MISSING_SERIE_ALIAS, null, null, List.of(), true);
            }
            var resolved = stepsBySerie.get(id).stream()
                .map(step -> PositionLabelResolver.resolveStep(step, positions))
                .toList();
            var range = serie.getRange();
            return new EmbeddedSerieRecord(
                serie.getId(),
                serie.getName(),
                range != null ? range.getId() : null,
                range != null ? range.getName() : null,
                resolved,
                false);
        }).toList();
    }

    private PasseResponse toResponse(Passe passe) {
        var serien = resolveLiveSerien(passe).stream()
            .map(PlayMapper::toEmbeddedSerie)
            .toList();
        return PlayMapper.toPasseResponse(passe, serien);
    }
}

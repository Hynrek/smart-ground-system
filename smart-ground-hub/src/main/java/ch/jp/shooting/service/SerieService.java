package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.StepRecord;
import ch.jp.shooting.exception.RangeNotFoundException;
import ch.jp.shooting.exception.SerieNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.smartground.model.CreateSerieRequest;
import ch.jp.smartground.model.SerieResponse;
import ch.jp.smartground.model.UpdateSerieOwnershipRequest;
import ch.jp.smartground.model.UpdateSeriePublishedRequest;
import ch.jp.smartground.model.UpdateSerieRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// Geschäftslogik für Serien (Wurfsequenzen)
@Service
@NullMarked
public class SerieService {

    private final SerieRepository serieRepository;
    private final RangeRepository rangeRepository;
    private final SecurityHelper securityHelper;

    public SerieService(SerieRepository serieRepository,
                        RangeRepository rangeRepository,
                        SecurityHelper securityHelper) {
        this.serieRepository = serieRepository;
        this.rangeRepository = rangeRepository;
        this.securityHelper = securityHelper;
    }

    /**
     * Listet sichtbare Serien auf – optional gefiltert nach Ownership und Platz.
     * Admins sehen alle Platz-Serien; reguläre Nutzer nur publizierte.
     */
    public List<SerieResponse> listSerien(@Nullable String ownership, @Nullable UUID rangeId) {
        var owner = securityHelper.currentUser();
        boolean isAdmin = securityHelper.isAdminOrOwner();
        List<Serie> result;

        if ("user".equals(ownership)) {
            result = rangeId != null
                    ? serieRepository.findByOwnerAndRange_Id(owner, rangeId)
                    : serieRepository.findByOwner(owner);
        } else if ("range".equals(ownership)) {
            // Admins sehen alle, reguläre Nutzer nur publizierte
            result = isAdmin
                    ? serieRepository.findByOwnership("range")
                    : serieRepository.findByOwnershipAndPublished("range", true);
            if (rangeId != null) {
                final UUID finalRangeId = rangeId;
                result = result.stream()
                        .filter(s -> s.getRange() != null && finalRangeId.equals(s.getRange().getId()))
                        .toList();
            }
        } else {
            // Kein Filter: eigene + sichtbare Platz-Serien
            result = isAdmin
                    ? serieRepository.findByOwnerOrOwnership(owner, "range")
                    : serieRepository.findByOwnerOrPublishedRange(owner);
            if (rangeId != null) {
                final UUID finalRangeId = rangeId;
                result = result.stream()
                        .filter(s -> s.getRange() != null && finalRangeId.equals(s.getRange().getId()))
                        .toList();
            }
        }

        return result.stream().map(PlayMapper::toSerieResponse).toList();
    }

    /** Erstellt eine neue Serie für den aktuellen Nutzer. */
    public SerieResponse createSerie(CreateSerieRequest request) {
        var owner = securityHelper.currentUser();
        var serie = new Serie();
        serie.setName(request.getName());
        serie.setOwner(owner);
        serie.setOwnership(
                request.getOwnership() != null ? request.getOwnership().getValue() : "user"
        );
        serie.setStepsJson(PlayMapper.writeSteps(
                request.getSteps().stream()
                        .map(step -> new StepRecord(
                                step.getId(),
                                step.getType().getValue(),
                                stringOrNull(step.getPosId()),
                                stringOrNull(step.getAlias()),
                                stringOrNull(step.getPosId1()),
                                stringOrNull(step.getPosId2()),
                                stringOrNull(step.getAlias1()),
                                stringOrNull(step.getAlias2()),
                                step.getLetter(),
                                step.getLetter1(),
                                step.getLetter2()
                        ))
                        .toList()
        ));
        var rangeIdJn = request.getRangeId();
        if (rangeIdJn != null && rangeIdJn.isPresent()) {
            UUID rangeId = rangeIdJn.get();
            if (rangeId != null) {
                serie.setRange(rangeRepository.findById(rangeId)
                        .orElseThrow(() -> new RangeNotFoundException(rangeId)));
            }
        }
        return PlayMapper.toSerieResponse(serieRepository.save(serie));
    }

    /**
     * Gibt eine Serie zurück, wenn der Nutzer Zugriff hat.
     * Unpublizierte Platz-Serien erscheinen für reguläre Nutzer als nicht gefunden (404).
     */
    public SerieResponse getSerie(UUID id) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        var owner = securityHelper.currentUser();
        boolean isOwner = serie.getOwner().getId().equals(owner.getId());
        boolean isAdmin = securityHelper.isAdminOrOwner();
        boolean isVisibleRangeSerie = "range".equals(serie.getOwnership()) && serie.isPublished();

        if (!isOwner && !isAdmin && !isVisibleRangeSerie) {
            // Unpublizierte Platz-Serie: 404 statt 403, um Existenz nicht preiszugeben
            throw new SerieNotFoundException(id);
        }
        return PlayMapper.toSerieResponse(serie);
    }

    /** Aktualisiert Name und optionale Platz-Zuordnung einer Serie. */
    public SerieResponse updateSerie(UUID id, UpdateSerieRequest request) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!serie.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        serie.setName(request.getName());
        var rangeIdJn = request.getRangeId();
        if (rangeIdJn != null && rangeIdJn.isPresent()) {
            UUID rangeId = rangeIdJn.get();
            if (rangeId != null) {
                serie.setRange(rangeRepository.findById(rangeId)
                        .orElseThrow(() -> new RangeNotFoundException(rangeId)));
            } else {
                serie.setRange(null);
            }
        }
        return PlayMapper.toSerieResponse(serieRepository.save(serie));
    }

    /**
     * Ändert die Ownership einer Serie.
     * Nur ADMIN/GROUND_OWNER dürfen auf "range" setzen.
     */
    public SerieResponse updateSerieOwnership(UUID id, UpdateSerieOwnershipRequest request) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!serie.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if ("range".equals(request.getOwnership().getValue()) && !securityHelper.isAdminOrOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        serie.setOwnership(request.getOwnership().getValue());
        return PlayMapper.toSerieResponse(serieRepository.save(serie));
    }

    /**
     * Publiziert oder versteckt eine Platz-Serie.
     * Nur ADMIN/GROUND_OWNER dürfen diese Aktion ausführen.
     */
    public SerieResponse updateSeriePublished(UUID id, UpdateSeriePublishedRequest request) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        if (!securityHelper.isAdminOrOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!"range".equals(serie.getOwnership())) {
            // published-Flag ist nur für Platz-Serien sinnvoll
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        serie.setPublished(request.getPublished());
        return PlayMapper.toSerieResponse(serieRepository.save(serie));
    }

    /** Löscht eine Serie (nur Besitzer). */
    public void deleteSerie(UUID id) {
        var serie = serieRepository.findById(id)
                .orElseThrow(() -> new SerieNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!serie.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        serieRepository.delete(serie);
    }

    @Nullable
    private static String stringOrNull(@Nullable JsonNullable<String> jn) {
        if (jn == null || !jn.isPresent()) return null;
        return jn.get();
    }
}

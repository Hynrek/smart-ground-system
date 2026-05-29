package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.StepRecord;
import ch.jp.shooting.exception.AblaufNotFoundException;
import ch.jp.shooting.exception.RangeNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.Ablauf;
import ch.jp.shooting.repository.AblaufRepository;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.smartground.model.AblaufResponse;
import ch.jp.smartground.model.CreateAblaufRequest;
import ch.jp.smartground.model.UpdateAblaufOwnershipRequest;
import ch.jp.smartground.model.UpdateAblaufRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// Geschäftslogik für Abläufe (Wurfsequenzen)
@Service
@NullMarked
public class AblaufService {

    private final AblaufRepository ablaufRepository;
    private final RangeRepository rangeRepository;
    private final SecurityHelper securityHelper;

    public AblaufService(AblaufRepository ablaufRepository,
                         RangeRepository rangeRepository,
                         SecurityHelper securityHelper) {
        this.ablaufRepository = ablaufRepository;
        this.rangeRepository = rangeRepository;
        this.securityHelper = securityHelper;
    }

    /**
     * Listet sichtbare Abläufe auf – optional gefiltert nach Ownership und Platz.
     *
     * @param ownership "user" = eigene, "range" = platzweit, null = alle sichtbaren
     * @param rangeId   optionale Platz-ID
     */
    public List<AblaufResponse> listAblaeufe(@Nullable String ownership, @Nullable UUID rangeId) {
        var owner = securityHelper.currentUser();
        List<Ablauf> result;

        if ("user".equals(ownership)) {
            // Nur eigene Abläufe
            result = rangeId != null
                    ? ablaufRepository.findByOwnerAndRange_Id(owner, rangeId)
                    : ablaufRepository.findByOwner(owner);
        } else if ("range".equals(ownership)) {
            // Platzweit veröffentlichte Abläufe
            if (rangeId != null) {
                final UUID finalRangeId = rangeId;
                result = ablaufRepository.findByOwnership("range").stream()
                        .filter(a -> a.getRange() != null && finalRangeId.equals(a.getRange().getId()))
                        .toList();
            } else {
                result = ablaufRepository.findByOwnership("range");
            }
        } else {
            // Alle sichtbaren: eigene + platzweit veröffentlichte
            result = ablaufRepository.findByOwnerOrOwnership(owner, "range");
            if (rangeId != null) {
                final UUID finalRangeId = rangeId;
                result = result.stream()
                        .filter(a -> a.getRange() != null && finalRangeId.equals(a.getRange().getId()))
                        .toList();
            }
        }

        return result.stream().map(PlayMapper::toAblaufResponse).toList();
    }

    /**
     * Erstellt einen neuen Ablauf für den aktuellen Nutzer.
     */
    public AblaufResponse createAblauf(CreateAblaufRequest request) {
        var owner = securityHelper.currentUser();
        var ablauf = new Ablauf();
        ablauf.setName(request.getName());
        ablauf.setOwner(owner);
        // Ownership: "user" falls nicht angegeben
        ablauf.setOwnership(
                request.getOwnership() != null ? request.getOwnership().getValue() : "user"
        );
        // Schritte als JSON serialisieren
        ablauf.setStepsJson(PlayMapper.writeSteps(
                request.getSteps().stream()
                        .map(step -> new StepRecord(
                                step.getId(),
                                step.getType().getValue(),
                                stringOrNull(step.getPosId()),
                                stringOrNull(step.getAlias()),
                                stringOrNull(step.getPosId1()),
                                stringOrNull(step.getPosId2()),
                                stringOrNull(step.getAlias1()),
                                stringOrNull(step.getAlias2())
                        ))
                        .toList()
        ));
        // Optionale Platz-Zuordnung
        var rangeIdJn = request.getRangeId();
        if (rangeIdJn != null && rangeIdJn.isPresent()) {
            UUID rangeId = rangeIdJn.get();
            if (rangeId != null) {
                ablauf.setRange(rangeRepository.findById(rangeId)
                        .orElseThrow(() -> new RangeNotFoundException(rangeId)));
            }
        }
        return PlayMapper.toAblaufResponse(ablaufRepository.save(ablauf));
    }

    /**
     * Gibt einen Ablauf zurück, wenn der Nutzer Zugriff hat.
     */
    public AblaufResponse getAblauf(UUID id) {
        var ablauf = ablaufRepository.findById(id)
                .orElseThrow(() -> new AblaufNotFoundException(id));
        var owner = securityHelper.currentUser();
        // Zugriff: Besitzer, range-öffentlich, oder ADMIN/GROUND_OWNER
        boolean hatZugriff = ablauf.getOwner().getId().equals(owner.getId())
                || "range".equals(ablauf.getOwnership())
                || securityHelper.isAdminOrOwner();
        if (!hatZugriff) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return PlayMapper.toAblaufResponse(ablauf);
    }

    /**
     * Aktualisiert Name und optionale Platz-Zuordnung eines Ablaufs.
     */
    public AblaufResponse updateAblauf(UUID id, UpdateAblaufRequest request) {
        var ablauf = ablaufRepository.findById(id)
                .orElseThrow(() -> new AblaufNotFoundException(id));
        var owner = securityHelper.currentUser();
        // Nur Besitzer darf bearbeiten
        if (!ablauf.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        ablauf.setName(request.getName());
        // Platz-Zuordnung: JsonNullable – explizit null = Zuordnung aufheben
        var rangeIdJn = request.getRangeId();
        if (rangeIdJn != null && rangeIdJn.isPresent()) {
            UUID rangeId = rangeIdJn.get();
            if (rangeId != null) {
                ablauf.setRange(rangeRepository.findById(rangeId)
                        .orElseThrow(() -> new RangeNotFoundException(rangeId)));
            } else {
                ablauf.setRange(null);
            }
        }
        return PlayMapper.toAblaufResponse(ablaufRepository.save(ablauf));
    }

    /**
     * Ändert die Ownership eines Ablaufs.
     * Nur ADMIN/GROUND_OWNER dürfen auf "range" setzen.
     */
    public AblaufResponse updateAblaufOwnership(UUID id, UpdateAblaufOwnershipRequest request) {
        var ablauf = ablaufRepository.findById(id)
                .orElseThrow(() -> new AblaufNotFoundException(id));
        var owner = securityHelper.currentUser();
        // Nur Besitzer darf Ownership ändern
        if (!ablauf.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        // Platzweite Freigabe nur für berechtigte Rollen
        if ("range".equals(request.getOwnership().getValue()) && !securityHelper.isAdminOrOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        ablauf.setOwnership(request.getOwnership().getValue());
        return PlayMapper.toAblaufResponse(ablaufRepository.save(ablauf));
    }

    /**
     * Löscht einen Ablauf (nur Besitzer).
     */
    public void deleteAblauf(UUID id) {
        var ablauf = ablaufRepository.findById(id)
                .orElseThrow(() -> new AblaufNotFoundException(id));
        var owner = securityHelper.currentUser();
        if (!ablauf.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        ablaufRepository.delete(ablauf);
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    @Nullable
    private static UUID uuidOrNull(@Nullable JsonNullable<UUID> jn) {
        if (jn == null || !jn.isPresent()) return null;
        return jn.get();
    }

    @Nullable
    private static String stringOrNull(@Nullable JsonNullable<String> jn) {
        if (jn == null || !jn.isPresent()) return null;
        return jn.get();
    }
}

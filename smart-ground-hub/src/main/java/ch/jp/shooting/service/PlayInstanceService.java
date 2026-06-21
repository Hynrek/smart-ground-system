package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.BlockResultRecord;
import ch.jp.shooting.dto.play.EmbeddedSerieRecord;
import ch.jp.shooting.dto.play.PlayBlockRecord;
import ch.jp.shooting.dto.play.PlayerRefRecord;
import ch.jp.shooting.dto.play.PlayerResultRecord;
import ch.jp.shooting.exception.BlockStateException;
import ch.jp.shooting.exception.PasseNotFoundException;
import ch.jp.shooting.exception.PlayInstanceNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.PlayInstanceRepository;
import ch.jp.smartground.model.CompleteBlockRequest;
import ch.jp.smartground.model.PageMeta;
import ch.jp.smartground.model.PlayInstanceResponse;
import ch.jp.smartground.model.PlayResultPage;
import ch.jp.smartground.model.PlayResultResponse;
import ch.jp.smartground.model.PlayResultSummary;
import ch.jp.smartground.model.PlayerRef;
import ch.jp.smartground.model.StartPasseInstanceRequest;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Geschäftslogik für Play-Instanzen (Passe-Läufe)
@Service
@NullMarked
public class PlayInstanceService {

    private final PlayInstanceRepository playInstanceRepository;
    private final PasseRepository passeRepository;
    private final PasseService passeService;
    private final SecurityHelper securityHelper;
    private final PositionLabelResolver positionLabelResolver;

    public PlayInstanceService(PlayInstanceRepository playInstanceRepository,
                               PasseRepository passeRepository,
                               PasseService passeService,
                               SecurityHelper securityHelper,
                               PositionLabelResolver positionLabelResolver) {
        this.playInstanceRepository = playInstanceRepository;
        this.passeRepository = passeRepository;
        this.passeService = passeService;
        this.securityHelper = securityHelper;
        this.positionLabelResolver = positionLabelResolver;
    }

    // ── Neue Instanz starten ──────────────────────────────────────────────────

    /** Startet einen neuen Passe-Lauf für einen oder mehrere Spieler. */
    public PlayInstanceResponse startPasseInstance(StartPasseInstanceRequest request) {
        var passe = passeRepository.findById(request.getPasseId())
            .orElseThrow(() -> new PasseNotFoundException(request.getPasseId()));
        var serien = passeService.resolveLiveSerien(passe);
        return buildAndSaveInstance(passe.getId(), passe.getName(), serien, request.getPlayers());
    }

    /**
     * Startet einen Einzel-Serie-Lauf als einblockige Passe-Instanz.
     * Erwartet einen Serien-Listen-Snapshot (gleiche Form wie {@code Passe.serienJson}),
     * der genau eine Serie enthält. Es wird KEINE persistierte Passe benötigt.
     */
    public PlayInstanceResponse startSerieInstance(UUID serieId, String serieName,
                                                   String serienSnapshotJson, List<PlayerRef> players) {
        var serien = PlayMapper.parseEmbeddedSerien(serienSnapshotJson);
        if (serien.size() != 1) {
            throw new IllegalArgumentException(
                "Serien-Snapshot für startSerieInstance muss genau eine Serie enthalten, enthält aber: " + serien.size());
        }
        return buildAndSaveInstance(serieId, serieName, serien, players);
    }

    /** Baut aus einer Serien-Liste eine aktive Passe-Instanz und speichert sie. */
    private PlayInstanceResponse buildAndSaveInstance(UUID templateId, String templateName,
                                                      List<EmbeddedSerieRecord> serien,
                                                      List<PlayerRef> players) {
        var owner = securityHelper.currentUser();

        var blocks = serien.stream().map(s ->
            new PlayBlockRecord(
                UUID.randomUUID(), s.id(), s.alias(),
                s.rangeId(), s.rangeName(),
                s.steps(), "pending", null, null
            )
        ).toList();

        var playerRecords = players.stream()
            .map(p -> new PlayerRefRecord(p.getId(), p.getType().getValue(), p.getDisplayName()))
            .toList();

        var instance = new PlayInstance();
        instance.setType("passe");
        instance.setTemplateId(templateId);
        instance.setTemplateName(templateName);
        instance.setOwner(owner);
        instance.setPlayersJson(PlayMapper.writePlayerRefs(playerRecords));
        instance.setStateJson(PlayMapper.writeBlocks(blocks));
        instance.setStatus("active");

        return PlayMapper.toPlayInstanceResponse(playInstanceRepository.save(instance));
    }

    // ── Abfragen ──────────────────────────────────────────────────────────────

    /** Listet Instanzen nach Status, optional gefiltert nach Range. */
    public List<PlayInstanceResponse> listPlayInstances(@Nullable String status, @Nullable UUID rangeId) {
        var owner = securityHelper.currentUser();
        String effectiveStatus = status != null ? status : "active";
        var instances = playInstanceRepository.findByOwnerAndStatus(owner, effectiveStatus);
        if (rangeId != null) {
            final UUID finalRangeId = rangeId;
            instances = instances.stream()
                .filter(inst -> hatBlockAufRange(inst, finalRangeId))
                .toList();
        }
        return instances.stream()
            .map(PlayMapper::toPlayInstanceResponse)
            .map(this::withResolvedBlockLabels)
            .toList();
    }

    /** Gibt eine einzelne Instanz zurück. */
    public PlayInstanceResponse getPlayInstance(UUID instanceId) {
        var instance = playInstanceRepository.findById(instanceId)
            .orElseThrow(() -> new PlayInstanceNotFoundException(instanceId));
        return withResolvedBlockLabels(PlayMapper.toPlayInstanceResponse(instance));
    }

    /**
     * Löst die Step-Buchstaben aller Blöcke einer (aktiven) Instanz live aus den
     * aktuellen Positionen auf — nur fürs Anzeigen; state_json bleibt unverändert.
     */
    private PlayInstanceResponse withResolvedBlockLabels(PlayInstanceResponse response) {
        var steps = response.getBlocks().orElse(List.of()).stream()
            .flatMap(b -> b.getSteps().stream())
            .toList();
        var positions = positionLabelResolver.byPosIds(PositionLabelResolver.posIdsOf(steps));
        steps.forEach(step -> PositionLabelResolver.applyResolvedLabels(step, positions));
        return response;
    }

    // ── Block-Zustandsmaschine ────────────────────────────────────────────────

    /** Setzt einen Block auf 'in_progress'. Idempotent wenn bereits in_progress. */
    public void startBlock(UUID instanceId, UUID blockId) {
        var instance = playInstanceRepository.findById(instanceId)
            .orElseThrow(() -> new PlayInstanceNotFoundException(instanceId));
        var blocks = new ArrayList<>(PlayMapper.parseBlocks(instance.getStateJson()));
        int idx = findeBlockIndex(blocks, blockId, instanceId);
        var block = blocks.get(idx);
        if ("done".equals(block.status())) {
            throw new BlockStateException("Block bereits abgeschlossen: " + blockId);
        }
        if (!"in_progress".equals(block.status())) {
            blocks.set(idx, new PlayBlockRecord(block.blockId(), block.serieId(), block.serieAlias(),
                block.rangeId(), block.rangeName(), block.steps(), "in_progress", null, null));
        }
        instance.setStateJson(PlayMapper.writeBlocks(blocks));
        playInstanceRepository.save(instance);
    }

    /** Schliesst einen Block ab und speichert Spieler-Ergebnisse. */
    public PlayInstanceResponse completeBlock(UUID instanceId, UUID blockId, CompleteBlockRequest request) {
        var instance = playInstanceRepository.findById(instanceId)
            .orElseThrow(() -> new PlayInstanceNotFoundException(instanceId));

        var playerResults = request.getPlayerResults().stream()
            .map(pr -> new PlayerResultRecord(
                pr.getPlayerId() != null ? pr.getPlayerId() : "",
                pr.getDisplayName() != null ? pr.getDisplayName() : "",
                pr.getTotalPoints() != null ? pr.getTotalPoints() : 0,
                pr.getMaxPoints() != null ? pr.getMaxPoints() : 0,
                pr.getStepStates().stream()
                    .map(ss -> new ch.jp.shooting.dto.play.StepStateRecord(
                        ss.getPlayerId() != null ? ss.getPlayerId() : "",
                        ss.getSerieIndex() != null ? ss.getSerieIndex() : 0,
                        ss.getStepIndex() != null ? ss.getStepIndex() : 0,
                        ss.getState() != null ? ss.getState().getValue() : "miss",
                        ss.getPointValue() != null ? ss.getPointValue() : 0,
                        ss.getNoBirds() != null ? ss.getNoBirds() : 0,
                        ss.getPointsEarned() != null ? ss.getPointsEarned() : 0
                    ))
                    .toList()
            ))
            .toList();
        var blockResult = new BlockResultRecord(playerResults);

        var blocks = new ArrayList<>(PlayMapper.parseBlocks(instance.getStateJson()));
        int idx = findeBlockIndex(blocks, blockId, instanceId);
        var block = blocks.get(idx);
        if (!"in_progress".equals(block.status())) {
            throw new BlockStateException("Block ist nicht aktiv: " + blockId);
        }
        blocks.set(idx, new PlayBlockRecord(block.blockId(), block.serieId(), block.serieAlias(),
            block.rangeId(), block.rangeName(), block.steps(), "done", Instant.now(), blockResult));
        instance.setStateJson(PlayMapper.writeBlocks(blocks));
        if (blocks.stream().allMatch(b -> "done".equals(b.status()))) {
            instance.setStatus("completed");
            instance.setCompletedAt(Instant.now());
        }

        return PlayMapper.toPlayInstanceResponse(playInstanceRepository.save(instance));
    }

    /** Bricht eine Instanz ab (nur der Besitzer). */
    public void stopPlayInstance(UUID instanceId) {
        var instance = playInstanceRepository.findById(instanceId)
            .orElseThrow(() -> new PlayInstanceNotFoundException(instanceId));
        var owner = securityHelper.currentUser();
        if (!instance.getOwner().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        instance.setStatus("cancelled");
        playInstanceRepository.save(instance);
    }

    // ── Ergebnisse ────────────────────────────────────────────────────────────

    /** Gibt paginierte, abgeschlossene Instanzen zurück. */
    public PlayResultPage listPlayResults(@Nullable UUID rangeId,
                                          @Nullable OffsetDateTime from,
                                          @Nullable OffsetDateTime to,
                                          int page, int size) {
        var owner = securityHelper.currentUser();
        var pageable = PageRequest.of(page, size);
        var pageResult = playInstanceRepository
            .findByOwnerAndStatusOrderByCompletedAtDesc(owner, "completed", pageable);

        var content = pageResult.getContent().stream()
            .filter(inst -> {
                if (inst.getCompletedAt() == null) return false;
                var completedAt = OffsetDateTime.ofInstant(inst.getCompletedAt(), ZoneOffset.UTC);
                if (from != null && completedAt.isBefore(from)) return false;
                if (to != null && completedAt.isAfter(to)) return false;
                if (rangeId != null && !hatBlockAufRange(inst, rangeId)) return false;
                return true;
            })
            .map(this::toPlayResultSummary)
            .toList();

        var meta = new PageMeta()
            .page(pageResult.getNumber())
            .size(pageResult.getSize())
            .totalPages(pageResult.getTotalPages())
            .totalElements((int) pageResult.getTotalElements());

        return new PlayResultPage().content(content).meta(meta);
    }

    /** Gibt das vollständige Ergebnis einer abgeschlossenen Instanz zurück. */
    public PlayResultResponse getPlayResult(UUID resultId) {
        var instance = playInstanceRepository.findById(resultId)
            .orElseThrow(() -> new PlayInstanceNotFoundException(resultId));
        if (!"completed".equals(instance.getStatus())) {
            throw new PlayInstanceNotFoundException(resultId);
        }
        return toPlayResultResponse(instance);
    }

    // ── Private Hilfsmethoden ─────────────────────────────────────────────────

    /** Prüft ob die Instanz einen nicht-abgeschlossenen Block auf der angegebenen Range hat. */
    private boolean hatBlockAufRange(PlayInstance instance, UUID rangeId) {
        return PlayMapper.parseBlocks(instance.getStateJson()).stream()
            .anyMatch(b -> rangeId.equals(b.rangeId()) && !"done".equals(b.status()));
    }

    /** Sucht den Index eines Blocks anhand der blockId. */
    private int findeBlockIndex(List<PlayBlockRecord> blocks, UUID blockId, UUID instanceId) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blockId.equals(blocks.get(i).blockId())) return i;
        }
        throw new PlayInstanceNotFoundException(instanceId);
    }

    /** Erstellt eine Ergebniszusammenfassung für die Ergebnisliste. */
    private PlayResultSummary toPlayResultSummary(PlayInstance instance) {
        int topScore = berechneTopScore(instance);
        var players = PlayMapper.parsePlayerRefs(instance.getPlayersJson()).stream()
            .map(p -> new PlayerRef()
                .id(p.id())
                .type(PlayerRef.TypeEnum.fromValue(p.type()))
                .displayName(p.displayName()))
            .toList();
        return new PlayResultSummary()
            .resultId(instance.getInstanceId())
            .type(PlayResultSummary.TypeEnum.fromValue(instance.getType()))
            .templateId(instance.getTemplateId())
            .templateName(instance.getTemplateName())
            .players(players)
            .completedAt(instance.getCompletedAt() != null
                ? OffsetDateTime.ofInstant(instance.getCompletedAt(), ZoneOffset.UTC) : null)
            .topScore(topScore);
    }

    /** Erstellt die vollständige Ergebnisantwort für eine abgeschlossene Instanz. */
    private PlayResultResponse toPlayResultResponse(PlayInstance instance) {
        var response = new PlayResultResponse()
            .resultId(instance.getInstanceId())
            .type(PlayResultResponse.TypeEnum.fromValue(instance.getType()))
            .templateId(instance.getTemplateId())
            .templateName(instance.getTemplateName())
            .players(PlayMapper.parsePlayerRefs(instance.getPlayersJson()).stream()
                .map(p -> new PlayerRef()
                    .id(p.id())
                    .type(PlayerRef.TypeEnum.fromValue(p.type()))
                    .displayName(p.displayName()))
                .toList())
            .startedAt(OffsetDateTime.ofInstant(instance.getStartedAt(), ZoneOffset.UTC));
        if (instance.getCompletedAt() != null) {
            response.completedAt(OffsetDateTime.ofInstant(instance.getCompletedAt(), ZoneOffset.UTC));
        }
        response.blocks(PlayMapper.parseBlocks(instance.getStateJson()).stream()
            .map(PlayMapper::toPlayBlockPublic)
            .toList());
        return response;
    }

    /** Berechnet den höchsten Gesamtpunktestand aller Spieler. */
    private int berechneTopScore(PlayInstance instance) {
        return PlayMapper.parseBlocks(instance.getStateJson()).stream()
            .filter(b -> b.result() != null)
            .flatMap(b -> b.result().playerResults().stream())
            .mapToInt(PlayerResultRecord::totalPoints)
            .max()
            .orElse(0);
    }
}

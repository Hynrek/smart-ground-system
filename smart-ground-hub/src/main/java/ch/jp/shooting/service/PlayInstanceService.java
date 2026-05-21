package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.BlockResultRecord;
import ch.jp.shooting.dto.play.PlayBlockRecord;
import ch.jp.shooting.dto.play.PlayPhaseRecord;
import ch.jp.shooting.dto.play.PlayerRefRecord;
import ch.jp.shooting.dto.play.PlayerResultRecord;
import ch.jp.shooting.exception.BlockStateException;
import ch.jp.shooting.exception.PlayInstanceNotFoundException;
import ch.jp.shooting.exception.ProgrammNotFoundException;
import ch.jp.shooting.exception.TrainingNotFoundException;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.repository.PlayInstanceRepository;
import ch.jp.shooting.repository.ProgrammRepository;
import ch.jp.shooting.repository.TrainingRepository;
import ch.jp.smartground.model.CompleteBlockRequest;
import ch.jp.smartground.model.PageMeta;
import ch.jp.smartground.model.PlayInstanceResponse;
import ch.jp.smartground.model.PlayResultPage;
import ch.jp.smartground.model.PlayResultResponse;
import ch.jp.smartground.model.PlayResultSummary;
import ch.jp.smartground.model.PlayerRef;
import ch.jp.smartground.model.StartProgrammeInstanceRequest;
import ch.jp.smartground.model.StartTrainingInstanceRequest;
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

// Geschäftslogik für Play-Instanzen (Programm- und Training-Läufe)
@Service
@NullMarked
public class PlayInstanceService {

    private final PlayInstanceRepository playInstanceRepository;
    private final ProgrammRepository programmRepository;
    private final TrainingRepository trainingRepository;
    private final SecurityHelper securityHelper;

    public PlayInstanceService(PlayInstanceRepository playInstanceRepository,
                               ProgrammRepository programmRepository,
                               TrainingRepository trainingRepository,
                               SecurityHelper securityHelper) {
        this.playInstanceRepository = playInstanceRepository;
        this.programmRepository = programmRepository;
        this.trainingRepository = trainingRepository;
        this.securityHelper = securityHelper;
    }

    // ── Neue Instanz starten ──────────────────────────────────────────────────

    /** Startet einen neuen Programm-Lauf für einen oder mehrere Spieler. */
    public PlayInstanceResponse startProgrammeInstance(StartProgrammeInstanceRequest request) {
        var owner = securityHelper.currentUser();
        var programm = programmRepository.findById(request.getProgrammeId())
            .orElseThrow(() -> new ProgrammNotFoundException(request.getProgrammeId()));
        var ablaeufe = PlayMapper.parseEmbeddedAblaeufe(programm.getAblaufeJson());

        var blocks = ablaeufe.stream().map(abl ->
            new PlayBlockRecord(
                UUID.randomUUID(), abl.id(), abl.alias(),
                abl.rangeId(), abl.rangeName(),
                abl.steps(), "pending", null, null
            )
        ).toList();

        var players = request.getPlayers().stream()
            .map(p -> new PlayerRefRecord(p.getId(), p.getType().getValue(), p.getDisplayName()))
            .toList();

        var instance = new PlayInstance();
        instance.setType("programm");
        instance.setTemplateId(programm.getId());
        instance.setTemplateName(programm.getName());
        instance.setOwner(owner);
        instance.setPlayersJson(PlayMapper.writePlayerRefs(players));
        instance.setStateJson(PlayMapper.writeBlocks(blocks));
        instance.setStatus("active");

        return PlayMapper.toPlayInstanceResponse(playInstanceRepository.save(instance));
    }

    /** Startet einen neuen Training-Lauf (mehrere Programm-Phasen). */
    public PlayInstanceResponse startTrainingInstance(StartTrainingInstanceRequest request) {
        var owner = securityHelper.currentUser();
        var training = trainingRepository.findById(request.getTrainingId())
            .orElseThrow(() -> new TrainingNotFoundException(request.getTrainingId()));
        var programmes = PlayMapper.parseTrainingProgrammes(training.getProgrammesJson());

        var phases = new ArrayList<PlayPhaseRecord>();
        for (int i = 0; i < programmes.size(); i++) {
            var prog = programmes.get(i);
            var blocks = prog.ablaeufe().stream().map(abl ->
                new PlayBlockRecord(
                    UUID.randomUUID(), abl.id(), abl.alias(),
                    abl.rangeId(), abl.rangeName(),
                    abl.steps(), "pending", null, null
                )
            ).toList();
            phases.add(new PlayPhaseRecord(i, prog.id(), prog.name(),
                i == 0 ? "active" : "pending", blocks));
        }

        var players = request.getPlayers().stream()
            .map(p -> new PlayerRefRecord(p.getId(), p.getType().getValue(), p.getDisplayName()))
            .toList();

        var instance = new PlayInstance();
        instance.setType("training");
        instance.setTemplateId(training.getId());
        instance.setTemplateName(training.getName());
        instance.setOwner(owner);
        instance.setPlayersJson(PlayMapper.writePlayerRefs(players));
        instance.setStateJson(PlayMapper.writePhases(phases));
        instance.setCurrentPhaseIndex(0);
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
        return instances.stream().map(PlayMapper::toPlayInstanceResponse).toList();
    }

    /** Gibt eine einzelne Instanz zurück. */
    public PlayInstanceResponse getPlayInstance(UUID instanceId) {
        var instance = playInstanceRepository.findById(instanceId)
            .orElseThrow(() -> new PlayInstanceNotFoundException(instanceId));
        return PlayMapper.toPlayInstanceResponse(instance);
    }

    // ── Block-Zustandsmaschine ────────────────────────────────────────────────

    /** Setzt einen Block auf 'in_progress'. Idempotent wenn bereits in_progress. */
    public void startBlock(UUID instanceId, UUID blockId) {
        var instance = playInstanceRepository.findById(instanceId)
            .orElseThrow(() -> new PlayInstanceNotFoundException(instanceId));
        if ("programm".equals(instance.getType())) {
            var blocks = new ArrayList<>(PlayMapper.parseBlocks(instance.getStateJson()));
            int idx = findeBlockIndex(blocks, blockId, instanceId);
            var block = blocks.get(idx);
            if ("done".equals(block.status())) {
                throw new BlockStateException("Block bereits abgeschlossen: " + blockId);
            }
            if (!"in_progress".equals(block.status())) {
                blocks.set(idx, new PlayBlockRecord(block.blockId(), block.ablaufId(), block.ablaufAlias(),
                    block.rangeId(), block.rangeName(), block.steps(), "in_progress", null, null));
            }
            instance.setStateJson(PlayMapper.writeBlocks(blocks));
        } else {
            int phaseIdx = instance.getCurrentPhaseIndex() != null ? instance.getCurrentPhaseIndex() : 0;
            var phases = new ArrayList<>(PlayMapper.parsePhases(instance.getStateJson()));
            var phase = phases.get(phaseIdx);
            var blocks = new ArrayList<>(phase.blocks());
            int bIdx = findeBlockIndex(blocks, blockId, instanceId);
            var block = blocks.get(bIdx);
            if ("done".equals(block.status())) {
                throw new BlockStateException("Block bereits abgeschlossen: " + blockId);
            }
            if (!"in_progress".equals(block.status())) {
                blocks.set(bIdx, new PlayBlockRecord(block.blockId(), block.ablaufId(), block.ablaufAlias(),
                    block.rangeId(), block.rangeName(), block.steps(), "in_progress", null, null));
            }
            phases.set(phaseIdx, new PlayPhaseRecord(phase.phaseIndex(), phase.programmeId(),
                phase.programmeName(), phase.status(), blocks));
            instance.setStateJson(PlayMapper.writePhases(phases));
        }
        playInstanceRepository.save(instance);
    }

    /** Schliesst einen Block ab und speichert Spieler-Ergebnisse. */
    public PlayInstanceResponse completeBlock(UUID instanceId, UUID blockId, CompleteBlockRequest request) {
        var instance = playInstanceRepository.findById(instanceId)
            .orElseThrow(() -> new PlayInstanceNotFoundException(instanceId));

        // Ergebnisse aus Request konvertieren
        var playerResults = request.getPlayerResults().stream()
            .map(pr -> new PlayerResultRecord(
                pr.getPlayerId() != null ? pr.getPlayerId() : "",
                pr.getDisplayName() != null ? pr.getDisplayName() : "",
                pr.getTotalPoints() != null ? pr.getTotalPoints() : 0,
                pr.getMaxPoints() != null ? pr.getMaxPoints() : 0,
                pr.getStepStates().stream()
                    .map(ss -> new ch.jp.shooting.dto.play.StepStateRecord(
                        ss.getPlayerId() != null ? ss.getPlayerId() : "",
                        ss.getAblaufIndex() != null ? ss.getAblaufIndex() : 0,
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

        if ("programm".equals(instance.getType())) {
            var blocks = new ArrayList<>(PlayMapper.parseBlocks(instance.getStateJson()));
            int idx = findeBlockIndex(blocks, blockId, instanceId);
            var block = blocks.get(idx);
            if (!"in_progress".equals(block.status())) {
                throw new BlockStateException("Block ist nicht aktiv: " + blockId);
            }
            blocks.set(idx, new PlayBlockRecord(block.blockId(), block.ablaufId(), block.ablaufAlias(),
                block.rangeId(), block.rangeName(), block.steps(), "done", Instant.now(), blockResult));
            instance.setStateJson(PlayMapper.writeBlocks(blocks));
            if (blocks.stream().allMatch(b -> "done".equals(b.status()))) {
                instance.setStatus("completed");
                instance.setCompletedAt(Instant.now());
            }
        } else {
            int phaseIdx = instance.getCurrentPhaseIndex() != null ? instance.getCurrentPhaseIndex() : 0;
            var phases = new ArrayList<>(PlayMapper.parsePhases(instance.getStateJson()));
            var phase = phases.get(phaseIdx);
            var blocks = new ArrayList<>(phase.blocks());
            int bIdx = findeBlockIndex(blocks, blockId, instanceId);
            var block = blocks.get(bIdx);
            if (!"in_progress".equals(block.status())) {
                throw new BlockStateException("Block ist nicht aktiv: " + blockId);
            }
            blocks.set(bIdx, new PlayBlockRecord(block.blockId(), block.ablaufId(), block.ablaufAlias(),
                block.rangeId(), block.rangeName(), block.steps(), "done", Instant.now(), blockResult));
            boolean phaseDone = blocks.stream().allMatch(b -> "done".equals(b.status()));
            var updatedPhaseStatus = phaseDone ? "done" : phase.status();
            phases.set(phaseIdx, new PlayPhaseRecord(phase.phaseIndex(), phase.programmeId(),
                phase.programmeName(), updatedPhaseStatus, blocks));
            if (phaseDone) {
                int next = phaseIdx + 1;
                if (next < phases.size()) {
                    var nextPhase = phases.get(next);
                    phases.set(next, new PlayPhaseRecord(nextPhase.phaseIndex(), nextPhase.programmeId(),
                        nextPhase.programmeName(), "active", nextPhase.blocks()));
                    instance.setCurrentPhaseIndex(next);
                } else {
                    instance.setStatus("completed");
                    instance.setCompletedAt(Instant.now());
                }
            }
            instance.setStateJson(PlayMapper.writePhases(phases));
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
        if ("programm".equals(instance.getType())) {
            return PlayMapper.parseBlocks(instance.getStateJson()).stream()
                .anyMatch(b -> rangeId.equals(b.rangeId()) && !"done".equals(b.status()));
        } else {
            int phaseIdx = instance.getCurrentPhaseIndex() != null ? instance.getCurrentPhaseIndex() : 0;
            var phases = PlayMapper.parsePhases(instance.getStateJson());
            if (phaseIdx >= phases.size()) return false;
            return phases.get(phaseIdx).blocks().stream()
                .anyMatch(b -> rangeId.equals(b.rangeId()) && !"done".equals(b.status()));
        }
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
        if ("programm".equals(instance.getType())) {
            response.blocks(PlayMapper.parseBlocks(instance.getStateJson()).stream()
                .map(PlayMapper::toPlayBlockPublic)
                .toList());
        } else {
            response.phases(PlayMapper.parsePhases(instance.getStateJson()).stream()
                .map(PlayMapper::toPlayPhasePublic)
                .toList());
        }
        return response;
    }

    /** Berechnet den höchsten Gesamtpunktestand aller Spieler. */
    private int berechneTopScore(PlayInstance instance) {
        List<PlayBlockRecord> allBlocks;
        if ("programm".equals(instance.getType())) {
            allBlocks = PlayMapper.parseBlocks(instance.getStateJson());
        } else {
            allBlocks = PlayMapper.parsePhases(instance.getStateJson()).stream()
                .flatMap(p -> p.blocks().stream())
                .toList();
        }
        return allBlocks.stream()
            .filter(b -> b.result() != null)
            .flatMap(b -> b.result().playerResults().stream())
            .mapToInt(PlayerResultRecord::totalPoints)
            .max()
            .orElse(0);
    }
}

package ch.jp.shooting.mapper;

import ch.jp.shooting.dto.play.BlockResultRecord;
import ch.jp.shooting.dto.play.EmbeddedAblaufRecord;
import ch.jp.shooting.dto.play.PlayBlockRecord;
import ch.jp.shooting.dto.play.PlayPhaseRecord;
import ch.jp.shooting.dto.play.PlayerRefRecord;
import ch.jp.shooting.dto.play.PlayerResultRecord;
import ch.jp.shooting.dto.play.TrainingProgrammeRecord;
import ch.jp.shooting.model.Ablauf;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.Programm;
import ch.jp.shooting.model.Training;
import ch.jp.shooting.model.Guest;
import ch.jp.smartground.model.AblaufOwnership;
import ch.jp.smartground.model.AblaufResponse;
import ch.jp.smartground.model.BlockResult;
import ch.jp.smartground.model.BlockStatus;
import ch.jp.smartground.model.EmbeddedAblauf;
import ch.jp.smartground.model.GuestResponse;
import ch.jp.smartground.model.PlayBlock;
import ch.jp.smartground.model.PlayInstanceResponse;
import ch.jp.smartground.model.PlayInstanceStatus;
import ch.jp.smartground.model.PlayPhase;
import ch.jp.smartground.model.PlayerRef;
import ch.jp.smartground.model.PlayerResult;
import ch.jp.smartground.model.ProgrammeResponse;
import ch.jp.smartground.model.Step;
import ch.jp.smartground.model.StepState;
import ch.jp.smartground.model.StepType;
import ch.jp.smartground.model.TrainingProgramme;
import ch.jp.smartground.model.TrainingResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

public final class PlayMapper {

    private static final Logger log = LoggerFactory.getLogger(PlayMapper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    private PlayMapper() {}

    // ── Guest ────────────────────────────────────────────────────────────────

    public static GuestResponse toGuestResponse(Guest guest) {
        return new GuestResponse()
            .id(guest.getId())
            .displayName(guest.getDisplayName())
            .createdAt(OffsetDateTime.ofInstant(guest.getCreatedAt(), ZoneOffset.UTC));
    }

    // ── Ablauf ───────────────────────────────────────────────────────────────

    public static AblaufResponse toAblaufResponse(Ablauf ablauf) {
        var range = ablauf.getRange();
        return new AblaufResponse()
            .id(ablauf.getId())
            .name(ablauf.getName())
            .ownership(AblaufOwnership.fromValue(ablauf.getOwnership()))
            .rangeId(range != null ? range.getId() : null)
            .rangeName(range != null ? range.getName() : null)
            .steps(parseSteps(ablauf.getStepsJson()).stream()
                .map(PlayMapper::toStep)
                .toList())
            .createdAt(OffsetDateTime.ofInstant(ablauf.getCreatedAt(), ZoneOffset.UTC))
            .ownerUsername(ablauf.getOwner().getEmail());
    }

    // ── Programm ─────────────────────────────────────────────────────────────

    public static ProgrammeResponse toProgrammeResponse(Programm programm) {
        return new ProgrammeResponse()
            .id(programm.getId())
            .name(programm.getName())
            .ablaeufe(parseEmbeddedAblaeufe(programm.getAblaufeJson()).stream()
                .map(PlayMapper::toEmbeddedAblauf)
                .toList())
            .createdAt(OffsetDateTime.ofInstant(programm.getCreatedAt(), ZoneOffset.UTC))
            .ownerUsername(programm.getOwner().getEmail());
    }

    // ── Training ─────────────────────────────────────────────────────────────

    public static TrainingResponse toTrainingResponse(Training training) {
        return new TrainingResponse()
            .id(training.getId())
            .name(training.getName())
            .programmes(parseTrainingProgrammes(training.getProgrammesJson()).stream()
                .map(PlayMapper::toTrainingProgramme)
                .toList())
            .createdAt(OffsetDateTime.ofInstant(training.getCreatedAt(), ZoneOffset.UTC))
            .ownerUsername(training.getOwner().getEmail());
    }

    // ── PlayInstance ──────────────────────────────────────────────────────────

    public static PlayInstanceResponse toPlayInstanceResponse(PlayInstance instance) {
        var response = new PlayInstanceResponse()
            .instanceId(instance.getInstanceId())
            .type(PlayInstanceResponse.TypeEnum.fromValue(instance.getType()))
            .templateId(instance.getTemplateId())
            .templateName(instance.getTemplateName())
            .status(PlayInstanceStatus.fromValue(instance.getStatus()))
            .players(parsePlayerRefs(instance.getPlayersJson()).stream()
                .map(PlayMapper::toPlayerRef)
                .toList())
            .startedAt(OffsetDateTime.ofInstant(instance.getStartedAt(), ZoneOffset.UTC));

        if (instance.getCompletedAt() != null) {
            response.completedAt(OffsetDateTime.ofInstant(instance.getCompletedAt(), ZoneOffset.UTC));
        }

        if (instance.getCurrentPhaseIndex() != null) {
            response.currentPhaseIndex(instance.getCurrentPhaseIndex());
        }

        if ("programm".equals(instance.getType())) {
            response.blocks(parseBlocks(instance.getStateJson()).stream()
                .map(PlayMapper::toPlayBlock)
                .toList());
        } else {
            response.phases(parsePhases(instance.getStateJson()).stream()
                .map(PlayMapper::toPlayPhase)
                .toList());
        }
        return response;
    }

    // ── JSON parsing helpers ─────────────────────────────────────────────────

    public static List<ch.jp.shooting.dto.play.StepRecord> parseSteps(String json) {
        return parseList(json, new TypeReference<>() {});
    }

    public static String writeSteps(List<ch.jp.shooting.dto.play.StepRecord> steps) {
        return writeValue(steps);
    }

    public static List<EmbeddedAblaufRecord> parseEmbeddedAblaeufe(String json) {
        return parseList(json, new TypeReference<>() {});
    }

    public static String writeEmbeddedAblaeufe(List<EmbeddedAblaufRecord> ablaeufe) {
        return writeValue(ablaeufe);
    }

    public static List<TrainingProgrammeRecord> parseTrainingProgrammes(String json) {
        return parseList(json, new TypeReference<>() {});
    }

    public static String writeTrainingProgrammes(List<TrainingProgrammeRecord> programmes) {
        return writeValue(programmes);
    }

    public static List<PlayerRefRecord> parsePlayerRefs(String json) {
        return parseList(json, new TypeReference<>() {});
    }

    public static String writePlayerRefs(List<PlayerRefRecord> players) {
        return writeValue(players);
    }

    public static List<PlayBlockRecord> parseBlocks(String json) {
        return parseList(json, new TypeReference<>() {});
    }

    public static String writeBlocks(List<PlayBlockRecord> blocks) {
        return writeValue(blocks);
    }

    public static List<PlayPhaseRecord> parsePhases(String json) {
        return parseList(json, new TypeReference<>() {});
    }

    public static String writePhases(List<PlayPhaseRecord> phases) {
        return writeValue(phases);
    }

    // ── Öffentliche Block/Phase-Konvertierung (für PlayResultResponse) ───────

    public static PlayBlock toPlayBlockPublic(PlayBlockRecord r) {
        return toPlayBlock(r);
    }

    public static PlayPhase toPlayPhasePublic(PlayPhaseRecord r) {
        return toPlayPhase(r);
    }

    // ── Private conversion helpers ───────────────────────────────────────────

    private static Step toStep(ch.jp.shooting.dto.play.StepRecord r) {
        return new Step()
            .id(r.id())
            .type(StepType.fromValue(r.type()))
            .deviceId(r.deviceId())
            .alias(r.alias())
            .letter(r.letter())
            .deviceId1(r.deviceId1())
            .deviceId2(r.deviceId2())
            .alias1(r.alias1())
            .alias2(r.alias2())
            .letter1(r.letter1())
            .letter2(r.letter2());
    }

    private static EmbeddedAblauf toEmbeddedAblauf(EmbeddedAblaufRecord r) {
        return new EmbeddedAblauf()
            .id(r.id())
            .alias(r.alias())
            .rangeId(r.rangeId())
            .rangeName(r.rangeName())
            .steps(r.steps().stream().map(PlayMapper::toStep).toList());
    }

    private static TrainingProgramme toTrainingProgramme(TrainingProgrammeRecord r) {
        return new TrainingProgramme()
            .id(r.id())
            .name(r.name())
            .ablaeufe(r.ablaeufe().stream().map(PlayMapper::toEmbeddedAblauf).toList());
    }

    private static PlayerRef toPlayerRef(PlayerRefRecord r) {
        return new PlayerRef()
            .id(r.id())
            .type(PlayerRef.TypeEnum.fromValue(r.type()))
            .displayName(r.displayName());
    }

    private static PlayBlock toPlayBlock(PlayBlockRecord r) {
        var block = new PlayBlock()
            .blockId(r.blockId())
            .ablaufId(r.ablaufId())
            .ablaufAlias(r.ablaufAlias())
            .rangeId(r.rangeId())
            .rangeName(r.rangeName())
            .steps(r.steps().stream().map(PlayMapper::toStep).toList())
            .status(BlockStatus.fromValue(r.status()));
        if (r.completedAt() != null) {
            block.completedAt(OffsetDateTime.ofInstant(r.completedAt(), ZoneOffset.UTC));
        }
        if (r.result() != null) {
            block.result(toBlockResult(r.result()));
        }
        return block;
    }

    private static BlockResult toBlockResult(BlockResultRecord r) {
        return new BlockResult()
            .playerResults(r.playerResults().stream()
                .map(PlayMapper::toPlayerResult)
                .toList());
    }

    private static PlayerResult toPlayerResult(PlayerResultRecord r) {
        return new PlayerResult()
            .playerId(r.playerId())
            .displayName(r.displayName())
            .totalPoints(r.totalPoints())
            .maxPoints(r.maxPoints())
            .stepStates(r.stepStates().stream()
                .map(PlayMapper::toGeneratedStepStateRecord)
                .toList());
    }

    private static ch.jp.smartground.model.StepStateRecord toGeneratedStepStateRecord(
            ch.jp.shooting.dto.play.StepStateRecord r) {
        return new ch.jp.smartground.model.StepStateRecord()
            .playerId(r.playerId())
            .ablaufIndex(r.ablaufIndex())
            .stepIndex(r.stepIndex())
            .state(StepState.fromValue(r.state()))
            .pointValue(r.pointValue())
            .noBirds(r.noBirds())
            .pointsEarned(r.pointsEarned());
    }

    private static PlayPhase toPlayPhase(PlayPhaseRecord r) {
        return new PlayPhase()
            .phaseIndex(r.phaseIndex())
            .programmeId(r.programmeId())
            .programmeName(r.programmeName())
            .status(PlayPhase.StatusEnum.fromValue(r.status()))
            .blocks(r.blocks().stream().map(PlayMapper::toPlayBlock).toList());
    }

    // ── Low-level JSON helpers ───────────────────────────────────────────────

    private static <T> List<T> parseList(String json, TypeReference<List<T>> ref) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, ref);
        } catch (Exception e) {
            log.warn("JSON-Parsing fehlgeschlagen: {}", json, e);
            return Collections.emptyList();
        }
    }

    private static String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON-Serialisierung fehlgeschlagen: {}", value, e);
            return "[]";
        }
    }
}

package ch.jp.shooting.mapper;

import ch.jp.shooting.dto.play.BlockResultRecord;
import ch.jp.shooting.dto.play.EmbeddedSerieRecord;
import ch.jp.shooting.dto.play.PlayBlockRecord;
import ch.jp.shooting.dto.play.PlayerRefRecord;
import ch.jp.shooting.dto.play.PlayerResultRecord;
import ch.jp.shooting.model.Passe;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.Guest;
import ch.jp.smartground.model.BlockResult;
import ch.jp.smartground.model.BlockStatus;
import ch.jp.smartground.model.EmbeddedSerie;
import ch.jp.smartground.model.GuestResponse;
import ch.jp.smartground.model.PasseResponse;
import ch.jp.smartground.model.PlayBlock;
import ch.jp.smartground.model.PlayInstanceResponse;
import ch.jp.smartground.model.PlayInstanceStatus;
import ch.jp.smartground.model.PlayerRef;
import ch.jp.smartground.model.PlayerResult;
import ch.jp.smartground.model.SerieOwnership;
import ch.jp.smartground.model.SerieResponse;
import ch.jp.smartground.model.Step;
import ch.jp.smartground.model.StepState;
import ch.jp.smartground.model.StepType;
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

    // ── Serie ────────────────────────────────────────────────────────────────

    public static SerieResponse toSerieResponse(Serie serie) {
        var range = serie.getRange();
        return new SerieResponse()
            .id(serie.getId())
            .name(serie.getName())
            .ownership(SerieOwnership.fromValue(serie.getOwnership()))
            .rangeId(range != null ? range.getId() : null)
            .rangeName(range != null ? range.getName() : null)
            .steps(parseSteps(serie.getStepsJson()).stream()
                .map(PlayMapper::toStep)
                .toList())
            .createdAt(OffsetDateTime.ofInstant(serie.getCreatedAt(), ZoneOffset.UTC))
            .ownerUsername(serie.getOwner().getEmail())
            .published(serie.isPublished());
    }

    // ── Passe ─────────────────────────────────────────────────────────────────

    public static PasseResponse toPasseResponse(Passe passe) {
        return new PasseResponse()
            .id(passe.getId())
            .name(passe.getName())
            .serien(parseEmbeddedSerien(passe.getSerienJson()).stream()
                .map(PlayMapper::toEmbeddedSerie)
                .toList())
            .createdAt(OffsetDateTime.ofInstant(passe.getCreatedAt(), ZoneOffset.UTC))
            .ownerUsername(passe.getOwner().getEmail());
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

        response.blocks(parseBlocks(instance.getStateJson()).stream()
            .map(PlayMapper::toPlayBlock)
            .toList());
        return response;
    }

    // ── JSON parsing helpers ─────────────────────────────────────────────────

    public static List<ch.jp.shooting.dto.play.StepRecord> parseSteps(String json) {
        return parseList(json, new TypeReference<>() {});
    }

    public static String writeSteps(List<ch.jp.shooting.dto.play.StepRecord> steps) {
        return writeValue(steps);
    }

    public static List<EmbeddedSerieRecord> parseEmbeddedSerien(String json) {
        return parseList(json, new TypeReference<>() {});
    }

    public static String writeEmbeddedSerien(List<EmbeddedSerieRecord> serien) {
        return writeValue(serien);
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

    // ── Öffentliche Block-Konvertierung (für PlayResultResponse) ─────────────

    public static PlayBlock toPlayBlockPublic(PlayBlockRecord r) {
        return toPlayBlock(r);
    }

    // ── Private conversion helpers ───────────────────────────────────────────

    private static Step toStep(ch.jp.shooting.dto.play.StepRecord r) {
        return new Step()
            .id(r.id())
            .type(StepType.fromValue(r.type()))
            .posId(r.posId())
            .alias(r.alias())
            .posId1(r.posId1())
            .posId2(r.posId2())
            .alias1(r.alias1())
            .alias2(r.alias2())
            .letter(r.letter())
            .letter1(r.letter1())
            .letter2(r.letter2());
    }

    public static EmbeddedSerie toEmbeddedSerie(EmbeddedSerieRecord r) {
        return new EmbeddedSerie()
            .id(r.id())
            .alias(r.alias())
            .rangeId(r.rangeId())
            .rangeName(r.rangeName())
            .steps(r.steps().stream().map(PlayMapper::toStep).toList());
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
            .serieId(r.serieId())
            .serieAlias(r.serieAlias())
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
            .serieIndex(r.serieIndex())
            .stepIndex(r.stepIndex())
            .state(StepState.fromValue(r.state()))
            .pointValue(r.pointValue())
            .noBirds(r.noBirds())
            .pointsEarned(r.pointsEarned());
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

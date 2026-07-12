package ch.jp.shooting.service;

import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.repository.RangePositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionLabelResolverTest {

    @Mock RangePositionRepository positionRepository;
    @InjectMocks PositionLabelResolver resolver;

    private RangePosition position(UUID id, String label) {
        var p = new RangePosition();
        p.setId(id);
        p.setLabel(label);
        return p;
    }

    @Test
    void byPosIds_mapsUuidStringsToEntities() {
        var id = UUID.randomUUID();
        when(positionRepository.findAllById(anyIterable())).thenReturn(List.of(position(id, "A1")));

        var result = resolver.byPosIds(List.of(id.toString()));

        assertThat(result).containsKey(id.toString());
        assertThat(result.get(id.toString()).getLabel()).isEqualTo("A1");
    }

    @Test
    void byPosIds_ignoresNullBlankAndInvalidUuids() {
        var result = resolver.byPosIds(java.util.Arrays.asList(null, "", "not-a-uuid"));
        assertThat(result).isEmpty();
    }

    @Test
    void byPosIds_emptyInput_returnsEmptyWithoutQuery() {
        var result = resolver.byPosIds(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void aliasOf_withDeviceAlias_returnsDeviceAlias() {
        var p = new RangePosition();
        p.setLabel("A1");
        var device = new ch.jp.shooting.model.Device();
        device.setAlias("Werfer 3");
        p.setDevice(device);

        assertThat(PositionLabelResolver.aliasOf(p)).isEqualTo("Werfer 3");
    }

    @Test
    void aliasOf_withoutDevice_fallsBackToLabel() {
        var p = new RangePosition();
        p.setLabel("A1");

        assertThat(PositionLabelResolver.aliasOf(p)).isEqualTo("A1");
    }

    @Test
    void aliasOf_blankDeviceAlias_fallsBackToLabel() {
        var p = new RangePosition();
        p.setLabel("A1");
        var device = new ch.jp.shooting.model.Device();
        device.setAlias("   ");
        p.setDevice(device);

        assertThat(PositionLabelResolver.aliasOf(p)).isEqualTo("A1");
    }

    @Test
    void resolveSteps_overridesLetterAndAliasFromCurrentPositions() {
        var posId = UUID.randomUUID();
        when(positionRepository.findAllById(anyIterable()))
            .thenReturn(List.of(position(posId, "C3")));

        var stale = new ch.jp.shooting.dto.play.StepRecord(
            "1", "solo", posId.toString(), "STALE_ALIAS",
            null, null, null, null, "OLD", null, null);

        var resolved = resolver.resolveSteps(List.of(stale));

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).letter()).isEqualTo("C3");
        assertThat(resolved.get(0).alias()).isEqualTo("C3"); // no device -> alias falls back to label
        assertThat(resolved.get(0).posId()).isEqualTo(posId.toString()); // posId preserved
    }

    @Test
    void resolveSteps_deletedPosition_yieldsNullLetterAndAlias() {
        var posId = UUID.randomUUID();
        // repository returns nothing -> position deleted
        var stale = new ch.jp.shooting.dto.play.StepRecord(
            "1", "solo", posId.toString(), "STALE",
            null, null, null, null, "OLD", null, null);

        var resolved = resolver.resolveSteps(List.of(stale));

        assertThat(resolved.get(0).letter()).isNull();
        assertThat(resolved.get(0).alias()).isNull();
    }

    @Test
    void posIdsOf_collectsAllNonNullPositionIds() {
        var solo = new ch.jp.smartground.model.Step().id("1")
            .type(ch.jp.smartground.model.StepType.SOLO).posId("p1");
        var pair = new ch.jp.smartground.model.Step().id("2")
            .type(ch.jp.smartground.model.StepType.PAIR).posId1("p2").posId2("p3");

        var ids = PositionLabelResolver.posIdsOf(java.util.List.of(solo, pair));

        assertThat(ids).containsExactlyInAnyOrder("p1", "p2", "p3");
    }

    @Test
    void applyResolvedLabels_overridesSoloLetterAndAlias() {
        var id = UUID.randomUUID();
        var step = new ch.jp.smartground.model.Step().id("1")
            .type(ch.jp.smartground.model.StepType.SOLO).posId(id.toString()).letter("OLD");

        PositionLabelResolver.applyResolvedLabels(step,
            java.util.Map.of(id.toString(), position(id, "A1")));

        assertThat(step.getLetter()).isEqualTo("A1");
        assertThat(step.getAlias().orElse(null)).isEqualTo("A1"); // no device -> alias falls back to label
    }

    @Test
    void applyResolvedLabels_missingPosition_nullsLetterAndAlias() {
        var step = new ch.jp.smartground.model.Step().id("1")
            .type(ch.jp.smartground.model.StepType.SOLO).posId(UUID.randomUUID().toString()).letter("OLD");

        PositionLabelResolver.applyResolvedLabels(step, java.util.Map.of()); // deleted

        assertThat(step.getLetter()).isNull();
        assertThat(step.getAlias().orElse(null)).isNull();
    }
}

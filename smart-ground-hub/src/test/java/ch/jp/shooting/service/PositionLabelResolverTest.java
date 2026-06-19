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
}

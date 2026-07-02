package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.model.Passe;
import ch.jp.shooting.model.RangePosition;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.smartground.model.CreatePasseRequest;
import ch.jp.smartground.model.UpdatePasseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasseServiceTest {

    @Mock PasseRepository passeRepository;
    @Mock SerieRepository serieRepository;
    @Mock SecurityHelper securityHelper;
    @Mock PositionLabelResolver positionLabelResolver;

    @InjectMocks PasseService passeService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@example.com");
    }

    private RangePosition pos(UUID id, String label) {
        var p = new RangePosition();
        p.setId(id);
        p.setLabel(label);
        return p;
    }

    private Serie soloSerie(UUID serieId, UUID posId, String name) {
        var serie = new Serie();
        serie.setId(serieId);
        serie.setName(name);
        serie.setOwnership("user");
        serie.setOwner(user);
        serie.setCreatedAt(Instant.now());
        serie.setStepsJson(
            "[{\"id\":\"1\",\"type\":\"solo\",\"posId\":\"" + posId + "\","
            + "\"alias\":\"STALE\",\"letter\":\"OLD\"}]");
        return serie;
    }

    private Passe passeReferencing(UUID... serieIds) {
        var passe = new Passe();
        passe.setId(UUID.randomUUID());
        passe.setName("Meine Passe");
        passe.setOwner(user);
        var json = "[" + java.util.Arrays.stream(serieIds)
            .map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(",")) + "]";
        passe.setSerieIdsJson(json);
        passe.setCreatedAt(Instant.now());
        return passe;
    }

    @Test
    void getPasse_joinsSerienLiveAndResolvesLabels() {
        var serieId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var serie = soloSerie(serieId, posId, "Serie 1");
        var passe = passeReferencing(serieId);

        when(passeRepository.findById(passe.getId())).thenReturn(Optional.of(passe));
        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.findAllById(anyIterable())).thenReturn(List.of(serie));
        when(positionLabelResolver.byPosIds(anyCollection()))
            .thenReturn(Map.of(posId.toString(), pos(posId, "A1")));

        var result = passeService.getPasse(passe.getId());

        assertThat(result.getSerien()).hasSize(1);
        var embedded = result.getSerien().get(0);
        assertThat(embedded.getId()).isEqualTo(serieId);
        assertThat(embedded.getAlias()).isEqualTo("Serie 1"); // live serie name
        assertThat(embedded.getMissing()).isFalse();
        assertThat(embedded.getSteps()).hasSize(1);
        assertThat(embedded.getSteps().get(0).getLetter()).isEqualTo("A1"); // resolved, not "OLD"
    }

    @Test
    void getPasse_deletedSerie_returnsMissingPlaceholderPreservingOrder() {
        var keptId = UUID.randomUUID();
        var deletedId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var kept = soloSerie(keptId, posId, "Kept");
        var passe = passeReferencing(deletedId, keptId); // deleted first, kept second

        when(passeRepository.findById(passe.getId())).thenReturn(Optional.of(passe));
        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.findAllById(anyIterable())).thenReturn(List.of(kept)); // deletedId absent
        when(positionLabelResolver.byPosIds(anyCollection()))
            .thenReturn(Map.of(posId.toString(), pos(posId, "A1")));

        var result = passeService.getPasse(passe.getId());

        assertThat(result.getSerien()).hasSize(2);
        assertThat(result.getSerien().get(0).getMissing()).isTrue();   // order preserved
        assertThat(result.getSerien().get(0).getSteps()).isEmpty();
        assertThat(result.getSerien().get(1).getId()).isEqualTo(keptId);
        assertThat(result.getSerien().get(1).getMissing()).isFalse();
    }

    @Test
    void createPasse_storesOrderedSerieIds() {
        var serieId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var serie = soloSerie(serieId, posId, "Serie 1");

        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.existsById(serieId)).thenReturn(true);
        when(passeRepository.save(any(Passe.class))).thenAnswer(i -> i.getArgument(0));
        when(serieRepository.findAllById(anyIterable())).thenReturn(List.of(serie));
        when(positionLabelResolver.byPosIds(anyCollection()))
            .thenReturn(Map.of(posId.toString(), pos(posId, "A1")));

        var request = new CreatePasseRequest().name("P").serieIds(List.of(serieId));
        var result = passeService.createPasse(request);

        assertThat(result.getSerien()).hasSize(1);
        assertThat(result.getSerien().get(0).getId()).isEqualTo(serieId);
    }

    @Test
    void createPasse_unknownSerie_throws() {
        var serieId = UUID.randomUUID();
        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.existsById(serieId)).thenReturn(false);

        var request = new CreatePasseRequest().name("P").serieIds(List.of(serieId));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> passeService.createPasse(request))
            .isInstanceOf(ch.jp.shooting.exception.SerieNotFoundException.class);
    }

    @Test
    void updatePasse_withSerieIds_replacesSerieMembership() {
        var oldSerieId = UUID.randomUUID();
        var newSerieId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var newSerie = soloSerie(newSerieId, posId, "Serie neu");
        var passe = passeReferencing(oldSerieId);

        when(passeRepository.findById(passe.getId())).thenReturn(Optional.of(passe));
        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.existsById(newSerieId)).thenReturn(true);
        when(passeRepository.save(any(Passe.class))).thenAnswer(i -> i.getArgument(0));
        when(serieRepository.findAllById(anyIterable())).thenReturn(List.of(newSerie));
        when(positionLabelResolver.byPosIds(anyCollection()))
            .thenReturn(Map.of(posId.toString(), pos(posId, "A1")));

        var request = new UpdatePasseRequest().name("Umbenannt").serieIds(List.of(newSerieId));
        var result = passeService.updatePasse(passe.getId(), request);

        assertThat(result.getName()).isEqualTo("Umbenannt");
        assertThat(result.getSerien()).hasSize(1);
        assertThat(result.getSerien().get(0).getId()).isEqualTo(newSerieId);
    }

    @Test
    void updatePasse_withoutSerieIds_preservesExistingMembership() {
        var serieId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var serie = soloSerie(serieId, posId, "Serie 1");
        var passe = passeReferencing(serieId);

        when(passeRepository.findById(passe.getId())).thenReturn(Optional.of(passe));
        when(securityHelper.currentUser()).thenReturn(user);
        when(passeRepository.save(any(Passe.class))).thenAnswer(i -> i.getArgument(0));
        when(serieRepository.findAllById(anyIterable())).thenReturn(List.of(serie));
        when(positionLabelResolver.byPosIds(anyCollection()))
            .thenReturn(Map.of(posId.toString(), pos(posId, "A1")));

        var request = new UpdatePasseRequest().name("Nur Name");
        var result = passeService.updatePasse(passe.getId(), request);

        assertThat(result.getName()).isEqualTo("Nur Name");
        assertThat(result.getSerien()).hasSize(1);
        assertThat(result.getSerien().get(0).getId()).isEqualTo(serieId);
    }

    @Test
    void updatePasse_unknownSerie_throws() {
        var serieId = UUID.randomUUID();
        var unknownSerieId = UUID.randomUUID();
        var passe = passeReferencing(serieId);

        when(passeRepository.findById(passe.getId())).thenReturn(Optional.of(passe));
        when(securityHelper.currentUser()).thenReturn(user);
        when(serieRepository.existsById(unknownSerieId)).thenReturn(false);

        var request = new UpdatePasseRequest().name("P").serieIds(List.of(unknownSerieId));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> passeService.updatePasse(passe.getId(), request))
            .isInstanceOf(ch.jp.shooting.exception.SerieNotFoundException.class);
    }
}

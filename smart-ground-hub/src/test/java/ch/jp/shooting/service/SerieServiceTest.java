package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.model.Range;
import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.SerieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SerieServiceTest {

    @Mock SerieRepository serieRepository;
    @Mock RangeRepository rangeRepository;
    @Mock SecurityHelper securityHelper;

    @InjectMocks SerieService serieService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        when(securityHelper.currentUser()).thenReturn(user);
    }

    private Serie rangeOwnedSerie(boolean published) {
        var serie = new Serie();
        serie.setId(UUID.randomUUID());
        serie.setName("Test");
        serie.setOwnership("range");
        serie.setPublished(published);
        serie.setStepsJson("[]");
        serie.setCreatedAt(Instant.now());
        serie.setOwner(user);
        return serie;
    }

    @Test
    void toSerieResponse_mapsPublishedField() {
        var serie = rangeOwnedSerie(true);
        var response = ch.jp.shooting.mapper.PlayMapper.toSerieResponse(serie);
        assertThat(response.getPublished()).isTrue();
    }

    @Test
    void toSerieResponse_mapsUnpublishedField() {
        var serie = rangeOwnedSerie(false);
        var response = ch.jp.shooting.mapper.PlayMapper.toSerieResponse(serie);
        assertThat(response.getPublished()).isFalse();
    }

    @Test
    void listSerien_regularUser_rangeOwnership_onlyReturnsPublished() {
        var published = rangeOwnedSerie(true);
        when(securityHelper.isAdminOrOwner()).thenReturn(false);
        when(serieRepository.findByOwnershipAndPublished("range", true))
            .thenReturn(List.of(published));

        var result = serieService.listSerien("range", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPublished()).isTrue();
    }

    @Test
    void listSerien_admin_rangeOwnership_returnsAllIncludingDrafts() {
        var published = rangeOwnedSerie(true);
        var draft = rangeOwnedSerie(false);
        when(securityHelper.isAdminOrOwner()).thenReturn(true);
        when(serieRepository.findByOwnership("range")).thenReturn(List.of(published, draft));

        var result = serieService.listSerien("range", null);

        assertThat(result).hasSize(2);
    }

    @Test
    void listSerien_regularUser_noFilter_hidesUnpublishedRangeSerien() {
        var published = rangeOwnedSerie(true);
        when(securityHelper.isAdminOrOwner()).thenReturn(false);
        when(serieRepository.findByOwnerOrPublishedRange(user)).thenReturn(List.of(published));

        var result = serieService.listSerien(null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void getSerie_regularUser_unpublishedRangeSerie_throws404() {
        var draft = rangeOwnedSerie(false);
        var otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        draft.setOwner(otherUser);
        when(serieRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(securityHelper.isAdminOrOwner()).thenReturn(false);

        assertThrows(
            ch.jp.shooting.exception.SerieNotFoundException.class,
            () -> serieService.getSerie(draft.getId())
        );
    }

    @Test
    void updateSeriePublished_adminPublishes_setsPublishedTrue() {
        var serie = rangeOwnedSerie(false);
        when(securityHelper.isAdminOrOwner()).thenReturn(true);
        when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));
        when(serieRepository.save(serie)).thenReturn(serie);

        var request = new ch.jp.smartground.model.UpdateSeriePublishedRequest().published(true);
        var result = serieService.updateSeriePublished(serie.getId(), request);

        assertThat(result.getPublished()).isTrue();
    }

    @Test
    void updateSeriePublished_regularUser_throws403() {
        var serie = rangeOwnedSerie(false);
        when(securityHelper.isAdminOrOwner()).thenReturn(false);
        when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));

        var request = new ch.jp.smartground.model.UpdateSeriePublishedRequest().published(true);
        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> serieService.updateSeriePublished(serie.getId(), request)
        );
    }

    @Test
    void getSerie_regularUser_otherUserPrivateSerie_throws404() {
        var otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        var privateSerie = new Serie();
        privateSerie.setId(UUID.randomUUID());
        privateSerie.setName("Private");
        privateSerie.setOwnership("user");
        privateSerie.setPublished(false);
        privateSerie.setStepsJson("[]");
        privateSerie.setCreatedAt(java.time.Instant.now());
        privateSerie.setOwner(otherUser);
        when(serieRepository.findById(privateSerie.getId())).thenReturn(Optional.of(privateSerie));
        when(securityHelper.isAdminOrOwner()).thenReturn(false);

        assertThrows(
            ch.jp.shooting.exception.SerieNotFoundException.class,
            () -> serieService.getSerie(privateSerie.getId())
        );
    }

    @Test
    void createSerie_pairStep_preservesLetter1AndLetter2RoundTrip() {
        when(serieRepository.save(org.mockito.ArgumentMatchers.any(Serie.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var pairStep = new ch.jp.smartground.model.Step()
            .id("1")
            .type(ch.jp.smartground.model.StepType.PAIR)
            .posId1("p1").posId2("p2")
            .alias1("Werfer 1").alias2("Werfer 2")
            .letter1("A").letter2("B");
        var request = new ch.jp.smartground.model.CreateSerieRequest()
            .name("Pair Serie")
            .steps(List.of(pairStep));

        var response = serieService.createSerie(request);

        // letter1/letter2 must survive the Step -> StepRecord(JSON) -> Step round-trip
        assertThat(response.getSteps()).hasSize(1);
        assertThat(response.getSteps().get(0).getLetter1()).isEqualTo("A");
        assertThat(response.getSteps().get(0).getLetter2()).isEqualTo("B");
    }

    @Test
    void updateSeriePublished_userOwnedSerie_throws400() {
        var serie = new Serie();
        serie.setId(UUID.randomUUID());
        serie.setName("Private");
        serie.setOwnership("user");
        serie.setPublished(false);
        serie.setStepsJson("[]");
        serie.setCreatedAt(java.time.Instant.now());
        serie.setOwner(user);
        when(securityHelper.isAdminOrOwner()).thenReturn(true);
        when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));

        var request = new ch.jp.smartground.model.UpdateSeriePublishedRequest().published(true);
        var ex = assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> serieService.updateSeriePublished(serie.getId(), request)
        );
        assertThat(ex.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}

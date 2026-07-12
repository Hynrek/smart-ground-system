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
    @Mock PositionLabelResolver positionLabelResolver;

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

    private ch.jp.shooting.model.RangePosition pos(UUID id, String label) {
        var p = new ch.jp.shooting.model.RangePosition();
        p.setId(id);
        p.setLabel(label);
        return p;
    }

    /** A user-owned serie with one solo step whose posId references the given position id,
     *  but whose stored letter is intentionally stale. */
    private Serie soloSerieWithStaleLetter(UUID posId) {
        var serie = new Serie();
        serie.setId(UUID.randomUUID());
        serie.setName("Solo");
        serie.setOwnership("user");
        serie.setStepsJson(
            "[{\"id\":\"1\",\"type\":\"solo\",\"posId\":\"" + posId + "\","
            + "\"alias\":\"STALE_ALIAS\",\"letter\":\"OLD\"}]");
        serie.setCreatedAt(Instant.now());
        serie.setOwner(user);
        return serie;
    }

    @Test
    void listSerien_resolvesStepLetterFromCurrentPosition() {
        var posId = UUID.randomUUID();
        var serie = soloSerieWithStaleLetter(posId);
        when(securityHelper.isAdminOrOwner()).thenReturn(false);
        when(serieRepository.findByOwnerOrPublishedRange(user)).thenReturn(List.of(serie));
        when(positionLabelResolver.byPosIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(java.util.Map.of(posId.toString(), pos(posId, "A1")));

        var result = serieService.listSerien(null, null);

        assertThat(result.get(0).getSteps().get(0).getLetter()).isEqualTo("A1");
    }

    @Test
    void getSerie_unknownPosition_resolvesLetterToNull() {
        var posId = UUID.randomUUID();
        var serie = soloSerieWithStaleLetter(posId);
        when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));
        when(securityHelper.isAdminOrOwner()).thenReturn(true);
        when(positionLabelResolver.byPosIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(java.util.Map.of()); // position was deleted

        var result = serieService.getSerie(serie.getId());

        assertThat(result.getSteps().get(0).getLetter()).isNull();
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
    void updateSerie_withSteps_replacesStepsAndKeepsSameId() {
        var posId = UUID.randomUUID();
        var serie = soloSerieWithStaleLetter(posId); // one solo step, stale letter
        var serieId = serie.getId();
        when(serieRepository.findById(serieId)).thenReturn(Optional.of(serie));
        when(serieRepository.save(org.mockito.ArgumentMatchers.any(Serie.class)))
            .thenAnswer(i -> i.getArgument(0));
        when(positionLabelResolver.byPosIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(java.util.Map.of(posId.toString(), pos(posId, "B2")));

        var newPosId = UUID.randomUUID();
        var request = new ch.jp.smartground.model.UpdateSerieRequest()
            .name("Renamed")
            .steps(List.of(new ch.jp.smartground.model.Step()
                .id("9")
                .type(ch.jp.smartground.model.StepType.SOLO)
                .posId(newPosId.toString())));

        var result = serieService.updateSerie(serieId, request);

        // stable ID
        assertThat(result.getId()).isEqualTo(serieId);
        // steps replaced: the persisted stepsJson now references newPosId, not the old one
        assertThat(serie.getStepsJson()).contains(newPosId.toString());
        assertThat(serie.getStepsJson()).doesNotContain(posId.toString());
        assertThat(result.getSteps()).hasSize(1);
    }

    @Test
    void updateSerie_withoutSteps_keepsExistingSteps() {
        var posId = UUID.randomUUID();
        var serie = soloSerieWithStaleLetter(posId);
        var originalStepsJson = serie.getStepsJson();
        when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));
        when(serieRepository.save(org.mockito.ArgumentMatchers.any(Serie.class)))
            .thenAnswer(i -> i.getArgument(0));
        when(positionLabelResolver.byPosIds(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(java.util.Map.of(posId.toString(), pos(posId, "A1")));

        var request = new ch.jp.smartground.model.UpdateSerieRequest().name("Renamed only");

        var result = serieService.updateSerie(serie.getId(), request);

        assertThat(result.getName()).isEqualTo("Renamed only");
        assertThat(serie.getStepsJson()).isEqualTo(originalStepsJson); // steps untouched
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

    @Test
    void deleteSerie_softDeletes_setsDeletedFlagAndSaves_neverHardDeletes() {
        var serie = new Serie();
        serie.setId(UUID.randomUUID());
        serie.setName("ToDelete");
        serie.setOwnership("user");
        serie.setStepsJson("[]");
        serie.setCreatedAt(Instant.now());
        serie.setOwner(user);
        when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));
        when(serieRepository.save(org.mockito.ArgumentMatchers.any(Serie.class)))
            .thenAnswer(i -> i.getArgument(0));

        serieService.deleteSerie(serie.getId());

        // soft-delete: flag set, row saved, hard delete never called
        assertThat(serie.isDeleted()).isTrue();
        org.mockito.Mockito.verify(serieRepository).save(serie);
        org.mockito.Mockito.verify(serieRepository, org.mockito.Mockito.never())
            .delete(org.mockito.ArgumentMatchers.any(Serie.class));
    }

    @Test
    void deleteSerie_notOwner_throws403_andDoesNotTouchDeletedFlag() {
        var otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        var serie = new Serie();
        serie.setId(UUID.randomUUID());
        serie.setName("NotMine");
        serie.setOwnership("user");
        serie.setStepsJson("[]");
        serie.setCreatedAt(Instant.now());
        serie.setOwner(otherUser);
        when(serieRepository.findById(serie.getId())).thenReturn(Optional.of(serie));

        assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> serieService.deleteSerie(serie.getId())
        );
        assertThat(serie.isDeleted()).isFalse();
    }
}

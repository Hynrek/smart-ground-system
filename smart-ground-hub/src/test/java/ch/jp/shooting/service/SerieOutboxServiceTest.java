package ch.jp.shooting.service;

import ch.jp.shooting.model.Serie;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.RangeRepository;
import ch.jp.shooting.repository.SerieRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.smartground.model.SerieOutboxItem;
import ch.jp.smartground.model.SerieOutboxResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SerieOutboxServiceTest {

    @Mock SerieRepository serieRepository;
    @Mock UserRepository userRepository;
    @Mock RangeRepository rangeRepository;

    SerieOutboxService service;
    User owner;

    @BeforeEach
    void setUp() {
        service = new SerieOutboxService(serieRepository, userRepository, rangeRepository);
        owner = new User();
        owner.setId(UUID.randomUUID());
    }

    private SerieOutboxItem item(UUID id, String name, OffsetDateTime baseVersion) {
        return new SerieOutboxItem()
                .id(id).name(name).ownership("user").ownerId(owner.getId())
                .stepsJson("[]").published(false).baseVersion(baseVersion);
    }

    @Test
    void push_unknownId_insertsSerieWithClientId_returnsAccepted() {
        var id = UUID.randomUUID();
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(serieRepository.findById(id)).thenReturn(Optional.empty());

        var result = service.push(item(id, "Offline-Serie", null));

        assertThat(result.getStatus()).isEqualTo(SerieOutboxResult.StatusEnum.ACCEPTED);
        verify(serieRepository).insertOutboxCreatedSerie(
                eq(id), eq("Offline-Serie"), eq("user"), isNull(), eq(owner.getId()), eq("[]"),
                any(Instant.class), any(Instant.class), eq(false));
        verify(serieRepository, never()).save(any());
    }

    @Test
    void push_unknownOwner_returnsRejected_neverSaves() {
        var id = UUID.randomUUID();
        when(userRepository.findById(owner.getId())).thenReturn(Optional.empty());

        var result = service.push(item(id, "x", null));

        assertThat(result.getStatus()).isEqualTo(SerieOutboxResult.StatusEnum.REJECTED);
        verify(serieRepository, never()).save(any());
    }

    @Test
    void push_sameIdIdenticalContent_isIdempotent_returnsAcceptedWithoutSaving() {
        var id = UUID.randomUUID();
        var existing = new Serie();
        existing.setId(id);
        existing.setName("Offline-Serie");
        existing.setOwnership("user");
        existing.setOwner(owner);
        existing.setStepsJson("[]");
        existing.setPublished(false);
        existing.setUpdatedAt(Instant.parse("2026-07-13T10:00:00Z"));
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(serieRepository.findById(id)).thenReturn(Optional.of(existing));

        var result = service.push(item(id, "Offline-Serie", null));

        assertThat(result.getStatus()).isEqualTo(SerieOutboxResult.StatusEnum.ACCEPTED);
        verify(serieRepository, never()).save(any());
    }

    @Test
    void push_matchingBaseVersion_appliesUpdate_returnsAccepted() {
        var id = UUID.randomUUID();
        var hubUpdatedAt = Instant.parse("2026-07-13T10:00:00Z");
        var existing = new Serie();
        existing.setId(id);
        existing.setName("Alt");
        existing.setOwnership("user");
        existing.setOwner(owner);
        existing.setStepsJson("[]");
        existing.setPublished(false);
        existing.setUpdatedAt(hubUpdatedAt);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(serieRepository.findById(id)).thenReturn(Optional.of(existing));
        when(serieRepository.save(any(Serie.class))).thenAnswer(i -> i.getArgument(0));

        var result = service.push(item(id, "Neu", OffsetDateTime.ofInstant(hubUpdatedAt, ZoneOffset.UTC)));

        assertThat(result.getStatus()).isEqualTo(SerieOutboxResult.StatusEnum.ACCEPTED);
        assertThat(existing.getName()).isEqualTo("Neu");
        verify(serieRepository).save(existing);
    }

    @Test
    void push_staleBaseVersion_differentContent_returnsConflict_leavesHubRowUnchanged() {
        var id = UUID.randomUUID();
        var hubUpdatedAt = Instant.parse("2026-07-13T12:00:00Z");   // online zwischenzeitlich geändert
        var staleBaseVersion = Instant.parse("2026-07-13T10:00:00Z"); // was der Node zuletzt sah
        var existing = new Serie();
        existing.setId(id);
        existing.setName("Online-editiert");
        existing.setOwnership("user");
        existing.setOwner(owner);
        existing.setStepsJson("[]");
        existing.setPublished(false);
        existing.setUpdatedAt(hubUpdatedAt);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(serieRepository.findById(id)).thenReturn(Optional.of(existing));

        var result = service.push(item(id, "Offline-abweichend", OffsetDateTime.ofInstant(staleBaseVersion, ZoneOffset.UTC)));

        assertThat(result.getStatus()).isEqualTo(SerieOutboxResult.StatusEnum.CONFLICT);
        assertThat(existing.getName()).isEqualTo("Online-editiert");
        verify(serieRepository, never()).save(any());
    }
}

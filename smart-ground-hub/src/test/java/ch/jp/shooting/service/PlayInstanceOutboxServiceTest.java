package ch.jp.shooting.service;

import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.PlayInstanceRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.smartground.model.PlayInstanceOutboxItem;
import ch.jp.smartground.model.PlayInstanceOutboxResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class PlayInstanceOutboxServiceTest {

    @Mock PlayInstanceRepository playInstanceRepository;
    @Mock UserRepository userRepository;

    PlayInstanceOutboxService service;
    User owner;

    @BeforeEach
    void setUp() {
        service = new PlayInstanceOutboxService(playInstanceRepository, userRepository);
        owner = new User();
        owner.setId(UUID.randomUUID());
    }

    private PlayInstanceOutboxItem item(UUID id, UUID templateId) {
        return new PlayInstanceOutboxItem()
                .instanceId(id).type("serie").templateId(templateId).templateName("Offline-Serie")
                .ownerId(owner.getId()).playersJson("[]").stateJson("[]").status("active")
                .startedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void push_unknownInstanceId_insertsPlayInstanceWithClientId_returnsAccepted() {
        var id = UUID.randomUUID();
        var templateId = UUID.randomUUID();
        when(playInstanceRepository.existsById(id)).thenReturn(false);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        var result = service.push(item(id, templateId));

        assertThat(result.getStatus()).isEqualTo(PlayInstanceOutboxResult.StatusEnum.ACCEPTED);
        verify(playInstanceRepository).insertOutboxCreatedPlayInstance(
                eq(id), eq("serie"), eq(templateId), eq("Offline-Serie"), eq("active"), eq(owner.getId()),
                eq("[]"), eq("[]"), any(Instant.class), isNull());
        verify(playInstanceRepository, never()).save(any());
    }

    @Test
    void push_existingInstanceId_isIdempotent_returnsAccepted_neverLooksUpOwnerOrInserts() {
        var id = UUID.randomUUID();
        when(playInstanceRepository.existsById(id)).thenReturn(true);

        var result = service.push(item(id, UUID.randomUUID()));

        assertThat(result.getStatus()).isEqualTo(PlayInstanceOutboxResult.StatusEnum.ACCEPTED);
        verify(userRepository, never()).findById(any());
        verify(playInstanceRepository, never()).insertOutboxCreatedPlayInstance(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void push_unknownOwner_returnsRejected() {
        var id = UUID.randomUUID();
        when(playInstanceRepository.existsById(id)).thenReturn(false);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.empty());

        var result = service.push(item(id, UUID.randomUUID()));

        assertThat(result.getStatus()).isEqualTo(PlayInstanceOutboxResult.StatusEnum.REJECTED);
        verify(playInstanceRepository, never()).insertOutboxCreatedPlayInstance(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}

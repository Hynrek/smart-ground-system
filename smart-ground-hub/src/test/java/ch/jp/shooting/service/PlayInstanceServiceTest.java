package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.dto.play.PlayBlockRecord;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.PlayInstanceRepository;
import ch.jp.smartground.model.PlayerRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayInstanceServiceTest {

    @Mock PlayInstanceRepository playInstanceRepository;
    @Mock PasseRepository passeRepository;
    @Mock SecurityHelper securityHelper;

    @InjectMocks PlayInstanceService service;

    @Test
    void startSerieInstance_buildsSinglePasseBlockFromSerie() {
        when(securityHelper.currentUser()).thenReturn(mock(User.class));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID serieId = UUID.randomUUID();
        // Snapshot hat keine Range, daher fliessen rangeId/rangeName als null durch
        String snapshot = "[{\"id\":\"" + serieId + "\",\"alias\":\"Stech-Serie\",\"steps\":[]}]";
        var players = List.of(new PlayerRef()
                .id(UUID.randomUUID().toString())
                .type(PlayerRef.TypeEnum.USER)
                .displayName("Anna"));

        service.startSerieInstance(serieId, "Stech-Serie", snapshot, players);

        ArgumentCaptor<PlayInstance> captor = ArgumentCaptor.forClass(PlayInstance.class);
        verify(playInstanceRepository).save(captor.capture());
        PlayInstance saved = captor.getValue();

        assertEquals("passe", saved.getType());
        assertEquals(serieId, saved.getTemplateId());
        assertEquals("Stech-Serie", saved.getTemplateName());

        List<PlayBlockRecord> blocks = PlayMapper.parseBlocks(saved.getStateJson());
        assertEquals(1, blocks.size());
        assertEquals("Stech-Serie", blocks.get(0).serieAlias());
        assertEquals("pending", blocks.get(0).status());

        var savedPlayers = PlayMapper.parsePlayerRefs(saved.getPlayersJson());
        assertEquals(1, savedPlayers.size());
        assertEquals("Anna", savedPlayers.get(0).displayName());
    }

    @Test
    void startSerieInstance_rejectsSnapshotWithoutExactlyOneSerie() {
        assertThrows(IllegalArgumentException.class, () ->
            service.startSerieInstance(java.util.UUID.randomUUID(), "X", "[]", java.util.List.of()));
    }
}

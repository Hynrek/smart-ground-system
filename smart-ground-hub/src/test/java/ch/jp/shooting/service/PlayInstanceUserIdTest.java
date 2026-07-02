package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.mapper.PlayMapper;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.PlayInstanceRepository;
import ch.jp.smartground.model.CompleteBlockRequest;
import ch.jp.smartground.model.PlayerRef;
import ch.jp.smartground.model.PlayerResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayInstanceUserIdTest {

    @Mock PlayInstanceRepository playInstanceRepository;
    @Mock PasseRepository passeRepository;
    @Mock PasseService passeService;
    @Mock SecurityHelper securityHelper;
    @Mock PositionLabelResolver positionLabelResolver;

    @InjectMocks PlayInstanceService service;

    @Test
    void startPasseInstance_persistsUserIdInPlayersJson() {
        var passeId = UUID.randomUUID();
        var passe = new ch.jp.shooting.model.Passe();
        passe.setId(passeId);
        passe.setName("Passe X");
        var liveSerien = List.of(new ch.jp.shooting.dto.play.EmbeddedSerieRecord(
            UUID.randomUUID(), "Serie 1", null, null, List.of(), false));

        when(passeRepository.findById(passeId)).thenReturn(Optional.of(passe));
        when(passeService.resolveLiveSerien(passe)).thenReturn(liveSerien);
        when(securityHelper.currentUser()).thenReturn(mock(User.class));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID accountId = UUID.randomUUID();
        var request = new ch.jp.smartground.model.StartPasseInstanceRequest()
            .passeId(passeId)
            .players(List.of(
                new PlayerRef().id("gp-1").type(PlayerRef.TypeEnum.USER)
                    .displayName("Anna").userId(accountId),
                new PlayerRef().id("gp-2").type(PlayerRef.TypeEnum.GUEST)
                    .displayName("Schütze 2")));

        service.startPasseInstance(request);

        ArgumentCaptor<PlayInstance> captor = ArgumentCaptor.forClass(PlayInstance.class);
        verify(playInstanceRepository).save(captor.capture());
        var savedPlayers = PlayMapper.parsePlayerRefs(captor.getValue().getPlayersJson());
        assertEquals(2, savedPlayers.size());
        assertEquals(accountId, savedPlayers.get(0).userId());
        assertNull(savedPlayers.get(1).userId());
    }

    @Test
    void completeBlock_persistsUserIdInBlockResult() {
        var instanceId = UUID.randomUUID();
        var blockId = UUID.randomUUID();
        var instance = new PlayInstance();
        instance.setInstanceId(instanceId);
        instance.setType("passe");
        instance.setTemplateId(UUID.randomUUID());
        instance.setTemplateName("T");
        instance.setStatus("active");
        instance.setPlayersJson("[]");
        instance.setStateJson("[{\"blockId\":\"" + blockId + "\",\"serieId\":\"" + UUID.randomUUID()
            + "\",\"serieAlias\":\"S\",\"steps\":[],\"status\":\"in_progress\"}]");

        when(playInstanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID accountId = UUID.randomUUID();
        var request = new CompleteBlockRequest().playerResults(List.of(
            new PlayerResult().playerId("gp-1").userId(accountId)
                .displayName("Anna").totalPoints(7).maxPoints(9)));

        service.completeBlock(instanceId, blockId, request);

        ArgumentCaptor<PlayInstance> captor = ArgumentCaptor.forClass(PlayInstance.class);
        verify(playInstanceRepository).save(captor.capture());
        var blocks = PlayMapper.parseBlocks(captor.getValue().getStateJson());
        var results = blocks.get(0).result().playerResults();
        assertEquals(1, results.size());
        assertEquals(accountId, results.get(0).userId());
        assertEquals(7, results.get(0).totalPoints());
    }
}

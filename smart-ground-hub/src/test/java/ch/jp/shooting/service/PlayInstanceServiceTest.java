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
    @Mock PasseService passeService;
    @Mock SecurityHelper securityHelper;
    @Mock PositionLabelResolver positionLabelResolver;
    @Mock UserScoreService userScoreService;

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

        service.startSerieInstance(serieId, "Stech-Serie", snapshot, players, "serie");

        ArgumentCaptor<PlayInstance> captor = ArgumentCaptor.forClass(PlayInstance.class);
        verify(playInstanceRepository).save(captor.capture());
        PlayInstance saved = captor.getValue();

        assertEquals("serie", saved.getType());
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
    void startPasseInstance_buildsBlocksFromLiveResolvedSerien() {
        var passeId = UUID.randomUUID();
        var passe = new ch.jp.shooting.model.Passe();
        passe.setId(passeId);
        passe.setName("Passe X");

        var serieId = UUID.randomUUID();
        var liveSerien = List.of(new ch.jp.shooting.dto.play.EmbeddedSerieRecord(
            serieId, "Serie 1", null, null, List.of(), false));

        when(passeRepository.findById(passeId)).thenReturn(java.util.Optional.of(passe));
        when(passeService.resolveLiveSerien(passe)).thenReturn(liveSerien);
        when(securityHelper.currentUser()).thenReturn(mock(User.class));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = new ch.jp.smartground.model.StartPasseInstanceRequest()
            .passeId(passeId)
            .players(List.of(new PlayerRef()
                .id(UUID.randomUUID().toString())
                .type(PlayerRef.TypeEnum.USER)
                .displayName("Anna")));

        service.startPasseInstance(request);

        ArgumentCaptor<PlayInstance> captor = ArgumentCaptor.forClass(PlayInstance.class);
        verify(playInstanceRepository).save(captor.capture());
        var blocks = PlayMapper.parseBlocks(captor.getValue().getStateJson());
        assertEquals(1, blocks.size());
        assertEquals("Serie 1", blocks.get(0).serieAlias());
    }

    private PlayInstance instanceWithSoloBlock(UUID instanceId, String posId, String staleLetter) {
        var inst = new PlayInstance();
        inst.setInstanceId(instanceId);
        inst.setType("passe");
        inst.setTemplateId(UUID.randomUUID());
        inst.setTemplateName("T");
        inst.setStatus("active");
        inst.setOwner(mock(User.class));
        inst.setStartedAt(java.time.Instant.now());
        inst.setPlayersJson("[]");
        // one block, one solo step with a stale letter
        inst.setStateJson("[{\"blockId\":\"" + UUID.randomUUID() + "\",\"serieId\":\"" + UUID.randomUUID()
            + "\",\"serieAlias\":\"S\",\"steps\":[{\"id\":\"1\",\"type\":\"solo\",\"posId\":\"" + posId
            + "\",\"letter\":\"" + staleLetter + "\"}],\"status\":\"pending\"}]");
        return inst;
    }

    @Test
    void getPlayInstance_reresolvesBlockStepLettersFromCurrentPositions() {
        var instanceId = UUID.randomUUID();
        var posId = UUID.randomUUID();
        var inst = instanceWithSoloBlock(instanceId, posId.toString(), "OLD");
        when(playInstanceRepository.findById(instanceId)).thenReturn(java.util.Optional.of(inst));
        var pos = new ch.jp.shooting.model.RangePosition();
        pos.setId(posId); pos.setLabel("A1");
        when(positionLabelResolver.byPosIds(any())).thenReturn(java.util.Map.of(posId.toString(), pos));

        var resp = service.getPlayInstance(instanceId);

        assertEquals("A1", resp.getBlocks().get().get(0).getSteps().get(0).getLetter());
    }

    @Test
    void getPlayInstance_deletedPosition_yieldsNullLetterNoCrash() {
        var instanceId = UUID.randomUUID();
        var inst = instanceWithSoloBlock(instanceId, UUID.randomUUID().toString(), "OLD");
        when(playInstanceRepository.findById(instanceId)).thenReturn(java.util.Optional.of(inst));
        when(positionLabelResolver.byPosIds(any())).thenReturn(java.util.Map.of());

        var resp = service.getPlayInstance(instanceId);

        assertNull(resp.getBlocks().get().get(0).getSteps().get(0).getLetter());
    }

    @Test
    void startSerieInstance_rejectsSnapshotWithoutExactlyOneSerie() {
        assertThrows(IllegalArgumentException.class, () ->
            service.startSerieInstance(java.util.UUID.randomUUID(), "X", "[]", java.util.List.of(), "serie"));
    }

    @Test
    void completeBlock_onLastBlock_recordsUserScores() {
        var instanceId = UUID.randomUUID();
        var blockId = UUID.randomUUID();
        var inst = new PlayInstance();
        inst.setInstanceId(instanceId);
        inst.setType("passe");
        inst.setTemplateId(UUID.randomUUID());
        inst.setTemplateName("T");
        inst.setStatus("active");
        inst.setOwner(mock(User.class));
        inst.setPlayersJson("[]");
        inst.setStateJson("[{\"blockId\":\"" + blockId + "\",\"serieId\":\"" + UUID.randomUUID()
            + "\",\"serieAlias\":\"S\",\"steps\":[],\"status\":\"in_progress\"}]");
        when(playInstanceRepository.findById(instanceId)).thenReturn(java.util.Optional.of(inst));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = new ch.jp.smartground.model.CompleteBlockRequest()
            .playerResults(List.of(new ch.jp.smartground.model.PlayerResult()
                .playerId("p1").displayName("Anna").totalPoints(5).maxPoints(10)
                .userId(UUID.randomUUID())));
        service.completeBlock(instanceId, blockId, request);

        verify(userScoreService).recordTrainingInstance(inst);
    }

    @Test
    void completeBlock_whenBlocksRemain_doesNotRecordScores() {
        var instanceId = UUID.randomUUID();
        var blockId = UUID.randomUUID();
        var inst = new PlayInstance();
        inst.setInstanceId(instanceId);
        inst.setType("passe");
        inst.setTemplateId(UUID.randomUUID());
        inst.setTemplateName("T");
        inst.setStatus("active");
        inst.setOwner(mock(User.class));
        inst.setPlayersJson("[]");
        inst.setStateJson("[{\"blockId\":\"" + blockId + "\",\"serieId\":\"" + UUID.randomUUID()
            + "\",\"serieAlias\":\"S1\",\"steps\":[],\"status\":\"in_progress\"},"
            + "{\"blockId\":\"" + UUID.randomUUID() + "\",\"serieId\":\"" + UUID.randomUUID()
            + "\",\"serieAlias\":\"S2\",\"steps\":[],\"status\":\"pending\"}]");
        when(playInstanceRepository.findById(instanceId)).thenReturn(java.util.Optional.of(inst));
        when(playInstanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = new ch.jp.smartground.model.CompleteBlockRequest()
            .playerResults(List.of(new ch.jp.smartground.model.PlayerResult()
                .playerId("p1").displayName("Anna").totalPoints(5).maxPoints(10)));
        service.completeBlock(instanceId, blockId, request);

        verify(userScoreService, never()).recordTrainingInstance(any());
    }
}

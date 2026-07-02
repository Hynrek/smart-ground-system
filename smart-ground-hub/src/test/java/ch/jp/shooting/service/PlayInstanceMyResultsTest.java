package ch.jp.shooting.service;

import ch.jp.shooting.config.SecurityHelper;
import ch.jp.shooting.model.PlayInstance;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.PasseRepository;
import ch.jp.shooting.repository.PlayInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayInstanceMyResultsTest {

    @Mock PlayInstanceRepository playInstanceRepository;
    @Mock PasseRepository passeRepository;
    @Mock PasseService passeService;
    @Mock SecurityHelper securityHelper;
    @Mock PositionLabelResolver positionLabelResolver;

    @InjectMocks PlayInstanceService service;

    private final UUID uid = UUID.randomUUID();

    /** Completed instance: two done blocks, our user scored in both; a second player is noise. */
    private PlayInstance completedInstance() {
        var inst = new PlayInstance();
        inst.setInstanceId(UUID.randomUUID());
        inst.setType("passe");
        inst.setTemplateId(UUID.randomUUID());
        inst.setTemplateName("Jagd 1");
        inst.setStatus("completed");
        inst.setPlayersJson("[]");
        inst.setCompletedAt(Instant.parse("2026-07-01T10:00:00Z"));
        String block = "{\"blockId\":\"%s\",\"serieId\":\"%s\",\"serieAlias\":\"S\",\"rangeName\":\"Trapstand\","
            + "\"steps\":[],\"status\":\"done\",\"result\":{\"playerResults\":["
            + "{\"playerId\":\"gp-1\",\"displayName\":\"Anna\",\"totalPoints\":%d,\"maxPoints\":%d,\"stepStates\":[],\"userId\":\"" + uid + "\"},"
            + "{\"playerId\":\"gp-2\",\"displayName\":\"Gast\",\"totalPoints\":1,\"maxPoints\":9,\"stepStates\":[]}"
            + "]}}";
        inst.setStateJson("[" + block.formatted(UUID.randomUUID(), UUID.randomUUID(), 7, 9)
            + "," + block.formatted(UUID.randomUUID(), UUID.randomUUID(), 5, 9) + "]");
        return inst;
    }

    @Test
    void listMyPlayResults_aggregatesOwnScoreAcrossBlocks() {
        var me = mock(User.class);
        when(me.getId()).thenReturn(uid);
        when(securityHelper.currentUser()).thenReturn(me);
        when(playInstanceRepository.findCompletedByParticipantUserId(uid.toString()))
            .thenReturn(List.of(completedInstance()));

        var results = service.listMyPlayResults();

        assertEquals(1, results.size());
        var entry = results.get(0);
        assertEquals("Jagd 1", entry.getTemplateName());
        assertEquals("Trapstand", entry.getRangeName());
        assertEquals(12, entry.getTotalPoints()); // 7 + 5
        assertEquals(18, entry.getMaxPoints());   // 9 + 9
    }

    @Test
    void listMyPlayResults_skipsLikeMatchesWithoutRealParticipation() {
        // LIKE kann falsch-positiv matchen (z.B. UUID an anderer Stelle im JSON) —
        // Instanzen ohne echtes PlayerResult mit unserer userId werden verworfen.
        var me = mock(User.class);
        when(me.getId()).thenReturn(uid);
        when(securityHelper.currentUser()).thenReturn(me);

        var noise = completedInstance();
        noise.setStateJson(noise.getStateJson().replace(uid.toString(), UUID.randomUUID().toString()));
        when(playInstanceRepository.findCompletedByParticipantUserId(uid.toString()))
            .thenReturn(List.of(noise));

        assertTrue(service.listMyPlayResults().isEmpty());
    }
}

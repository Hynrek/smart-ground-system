package ch.jp.shooting.service;

import ch.jp.shooting.model.*;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.repository.UserSerieScoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// English test comments per project convention; German domain comments live in the service.
@ExtendWith(MockitoExtension.class)
class UserScoreServiceTest {

    @Mock UserSerieScoreRepository scoreRepository;
    @Mock ch.jp.shooting.repository.auth.UserRepository userRepository;
    @Mock ch.jp.shooting.config.SecurityHelper securityHelper;

    UserScoreService service() {
        return new UserScoreService(scoreRepository, userRepository, securityHelper, new ObjectMapper());
    }

    // ── Training ──

    private PlayInstance completedInstance(UUID blockId, UUID userId) {
        var inst = new PlayInstance();
        inst.setInstanceId(UUID.randomUUID());
        inst.setType("passe");
        inst.setTemplateId(UUID.randomUUID());
        inst.setTemplateName("Trainings-Passe");
        inst.setStatus("completed");
        inst.setOwner(mock(User.class));
        inst.setCompletedAt(Instant.now());
        var serieId = UUID.randomUUID();
        // one done block with two players: one with userId, one anonymous
        inst.setStateJson("[{\"blockId\":\"" + blockId + "\",\"serieId\":\"" + serieId
            + "\",\"serieAlias\":\"Serie 1\",\"rangeId\":null,\"rangeName\":\"Platz 1\",\"steps\":[],"
            + "\"status\":\"done\",\"completedAt\":\"" + Instant.now() + "\",\"result\":{\"playerResults\":["
            + "{\"playerId\":\"p1\",\"displayName\":\"Anna\",\"totalPoints\":8,\"maxPoints\":10,"
            + "\"stepStates\":[],\"userId\":\"" + userId + "\"},"
            + "{\"playerId\":\"p2\",\"displayName\":\"Gast\",\"totalPoints\":3,\"maxPoints\":10,"
            + "\"stepStates\":[]}"
            + "]}}]");
        return inst;
    }

    @Test
    void recordTrainingInstance_writesRowOnlyForPlayersWithUserId() {
        var blockId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(Optional.empty());

        service().recordTrainingInstance(completedInstance(blockId, userId));

        var captor = ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository, times(1)).save(captor.capture());
        var row = captor.getValue();
        assertEquals(userId, row.getUserId());
        assertEquals("TRAINING", row.getContext());
        assertEquals(8, row.getTotalPoints());
        assertEquals(10, row.getMaxPoints());
        assertEquals(blockId, row.getSourceId());
        assertEquals("Serie 1", row.getSerieAlias());
        assertEquals("Trainings-Passe", row.getParentName());
        assertEquals("Platz 1", row.getRangeName());
        assertNotNull(row.getPlayInstanceId());
        assertNull(row.getSessionId());
    }

    @Test
    void recordTrainingInstance_isIdempotentPerSourceAndUser() {
        var blockId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var existing = new UserSerieScore();
        existing.setUserId(userId);
        existing.setSourceId(blockId);
        when(scoreRepository.findBySourceIdAndUserId(blockId, userId)).thenReturn(Optional.of(existing));

        service().recordTrainingInstance(completedInstance(blockId, userId));

        // existing row is updated (same object saved), no second row created
        var captor = ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository, times(1)).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertEquals(8, captor.getValue().getTotalPoints());
    }

    // ── Wettkampf ──

    private ShooterGroup groupWithMember(UUID memberId, UUID userId) {
        var user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        var member = mock(SessionPlayer.class);
        lenient().when(member.getId()).thenReturn(memberId);
        lenient().when(member.getUser()).thenReturn(user);
        var group = mock(ShooterGroup.class);
        lenient().when(group.getId()).thenReturn(UUID.randomUUID());
        lenient().when(group.getMembers()).thenReturn(List.of(member));
        return group;
    }

    private CompetitionSerieResult csr(ShooterGroup group) {
        var session = mock(LiveSession.class);
        lenient().when(session.getId()).thenReturn(UUID.randomUUID());
        lenient().when(session.getName()).thenReturn("Feldschiessen");
        var result = new CompetitionSerieResult(session, group, 1, UUID.randomUUID());
        result.setCompletedAt(Instant.now());
        return result;
    }

    @Test
    void recordCompetitionSerie_resolvesUserViaGroupMember() {
        var memberId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var group = groupWithMember(memberId, userId);
        var result = csr(group);
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(Optional.empty());

        var pr = new ch.jp.smartground.model.PlayerResult()
            .playerId(memberId.toString()).displayName("Anna")
            .totalPoints(7).maxPoints(10);
        service().recordCompetitionSerie(result, group, "Stich-Serie", List.of(pr), false);

        var captor = ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository).save(captor.capture());
        var row = captor.getValue();
        assertEquals(userId, row.getUserId());
        assertEquals("COMPETITION", row.getContext());
        assertEquals(7, row.getTotalPoints());
        assertEquals(1, row.getPasseIndex());
        assertEquals("Feldschiessen", row.getParentName());
        assertEquals("Stich-Serie", row.getSerieAlias());
    }

    @Test
    void recordCompetitionSerie_skipsGuestsWithoutUser() {
        var memberId = UUID.randomUUID();
        var member = mock(SessionPlayer.class);
        lenient().when(member.getId()).thenReturn(memberId);
        lenient().when(member.getUser()).thenReturn(null);
        var group = mock(ShooterGroup.class);
        lenient().when(group.getMembers()).thenReturn(List.of(member));
        var result = csr(group);

        var pr = new ch.jp.smartground.model.PlayerResult()
            .playerId(memberId.toString()).displayName("Gast").totalPoints(4).maxPoints(10);
        service().recordCompetitionSerie(result, group, "Serie", List.of(pr), false);

        verify(scoreRepository, never()).save(any());
    }

    @Test
    void recordCompetitionSerie_correctionRemovesStaleRows() {
        var memberId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var group = groupWithMember(memberId, userId);
        var result = csr(group);
        // pretend the csr already has an id and one stale row of another user
        var stale = new UserSerieScore();
        stale.setUserId(UUID.randomUUID());
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(scoreRepository.findBySourceId(any())).thenReturn(List.of(stale));

        var pr = new ch.jp.smartground.model.PlayerResult()
            .playerId(memberId.toString()).displayName("Anna").totalPoints(9).maxPoints(10);
        service().recordCompetitionSerie(result, group, "Serie", List.of(pr), true);

        verify(scoreRepository).delete(stale);
        verify(scoreRepository).save(any(UserSerieScore.class));
    }
}

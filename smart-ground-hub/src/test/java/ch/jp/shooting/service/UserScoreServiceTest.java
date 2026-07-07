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

    private PlayInstance completedInstanceOfType(UUID blockId, UUID userId, String type) {
        var inst = completedInstance(blockId, userId);
        inst.setType(type);
        return inst;
    }

    @Test
    void recordTrainingInstance_serieType_writesSerieKind() {
        var blockId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(Optional.empty());

        service().recordTrainingInstance(completedInstanceOfType(blockId, userId, "serie"));

        var captor = ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository).save(captor.capture());
        assertEquals("SERIE", captor.getValue().getKind());
    }

    @Test
    void recordTrainingInstance_passeType_writesPasseKind() {
        var blockId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        when(scoreRepository.findBySourceIdAndUserId(any(), any())).thenReturn(Optional.empty());

        service().recordTrainingInstance(completedInstanceOfType(blockId, userId, "passe"));

        var captor = ArgumentCaptor.forClass(UserSerieScore.class);
        verify(scoreRepository).save(captor.capture());
        assertEquals("PASSE", captor.getValue().getKind());
    }

    @Test
    void recordTrainingInstance_stechenType_writesNothing() {
        var blockId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        service().recordTrainingInstance(completedInstanceOfType(blockId, userId, "stechen"));

        verify(scoreRepository, never()).save(any());
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
        assertEquals("COMPETITION", row.getKind());
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

    // ── Lesen / Aggregation ──

    private UserSerieScore row(UUID userId, String context, int points, int max,
                               @org.jspecify.annotations.Nullable UUID playInstanceId,
                               @org.jspecify.annotations.Nullable UUID sessionId,
                               Instant completedAt) {
        var s = new UserSerieScore();
        s.setUserId(userId);
        s.setContext(context);
        s.setTotalPoints(points);
        s.setMaxPoints(max);
        s.setSerieId(UUID.randomUUID());
        s.setSerieAlias("Serie");
        s.setSourceId(UUID.randomUUID());
        s.setPlayInstanceId(playInstanceId);
        s.setSessionId(sessionId);
        s.setParentName(playInstanceId != null ? "Passe X" : "Wettkampf Y");
        s.setCompletedAt(completedAt);
        return s;
    }

    @Test
    void getMyScoreSummary_aggregatesContextsAndGroups() {
        var userId = UUID.randomUUID();
        var me = mock(User.class);
        when(me.getId()).thenReturn(userId);
        when(securityHelper.currentUser()).thenReturn(me);

        var passe = UUID.randomUUID();
        var session = UUID.randomUUID();
        var now = Instant.now();
        when(scoreRepository.findByUserIdOrderByCompletedAtDesc(userId)).thenReturn(List.of(
            row(userId, "TRAINING", 8, 10, passe, null, now),
            row(userId, "TRAINING", 6, 10, passe, null, now.minusSeconds(60)),
            row(userId, "COMPETITION", 9, 10, null, session, now.minusSeconds(120))
        ));

        var summary = service().getMyScoreSummary();

        var training = summary.getContexts().stream()
            .filter(c -> "TRAINING".equals(c.getContext().getValue())).findFirst().orElseThrow();
        assertEquals(2, training.getSerieCount());
        assertEquals(14, training.getTotalPoints());
        assertEquals(70.0, training.getAveragePercent(), 0.01);
        assertEquals(80.0, training.getBestPercent().get(), 0.01);

        assertEquals(1, summary.getPassen().size());
        assertEquals(14, summary.getPassen().get(0).getTotalPoints());
        assertEquals(2, summary.getPassen().get(0).getSerieCount());
        assertEquals(1, summary.getWettkaempfe().size());
        assertEquals(9, summary.getWettkaempfe().get(0).getTotalPoints());
    }

    @Test
    void getLeaderboard_ranksByBestPercent() {
        var alice = UUID.randomUUID();
        var bob = UUID.randomUUID();
        var now = Instant.now();
        when(scoreRepository.findForLeaderboard(isNull(), isNull(), isNull(), any())).thenReturn(List.of(
            row(alice, "TRAINING", 9, 10, UUID.randomUUID(), null, now),
            row(alice, "TRAINING", 5, 10, UUID.randomUUID(), null, now),
            row(bob, "TRAINING", 8, 10, UUID.randomUUID(), null, now)
        ));
        var aliceUser = mock(User.class);
        when(aliceUser.getId()).thenReturn(alice);
        when(aliceUser.getFullName()).thenReturn("Alice");
        var bobUser = mock(User.class);
        when(bobUser.getId()).thenReturn(bob);
        when(bobUser.getFullName()).thenReturn("Bob");
        when(userRepository.findAllById(any())).thenReturn(List.of(aliceUser, bobUser));

        var board = service().getLeaderboard(null, null, null, "best", 10, null);

        assertEquals(2, board.getEntries().size());
        assertEquals("Alice", board.getEntries().get(0).getDisplayName());
        assertEquals(90.0, board.getEntries().get(0).getBestPercent(), 0.01);
        assertEquals(70.0, board.getEntries().get(0).getAveragePercent(), 0.01);
    }
}

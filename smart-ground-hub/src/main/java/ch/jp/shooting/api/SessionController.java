package ch.jp.shooting.api;

import ch.jp.shooting.service.BracketService;
import ch.jp.shooting.service.GroupService;
import ch.jp.shooting.service.LeaderboardService;
import ch.jp.shooting.service.ResultsService;
import ch.jp.shooting.service.SessionService;
import ch.jp.smartground.api.SessionApi;
import ch.jp.smartground.model.CreateSessionRequest;
import ch.jp.smartground.model.GroupCreateRequest;
import ch.jp.smartground.model.GroupResponse;
import ch.jp.smartground.model.InitializeBracketRequest;
import ch.jp.smartground.model.PlayerResultResponse;
import ch.jp.smartground.model.SessionLeaderboardResponse;
import ch.jp.smartground.model.SessionPageResponse;
import ch.jp.smartground.model.SessionResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@NullMarked
public class SessionController implements SessionApi {
    private final SessionService sessionService;
    private final GroupService groupService;
    private final BracketService bracketService;
    private final LeaderboardService leaderboardService;
    private final ResultsService resultsService;

    public SessionController(
            SessionService sessionService,
            GroupService groupService,
            BracketService bracketService,
            LeaderboardService leaderboardService,
            ResultsService resultsService) {
        this.sessionService = sessionService;
        this.groupService = groupService;
        this.bracketService = bracketService;
        this.leaderboardService = leaderboardService;
        this.resultsService = resultsService;
    }

    @Override
    public ResponseEntity<SessionResponse> createSession(CreateSessionRequest createSessionRequest) {
        SessionResponse response = sessionService.createSession(createSessionRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<SessionResponse> getSession(UUID sessionId) {
        SessionResponse response = sessionService.getSession(sessionId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SessionPageResponse> listSessions(Integer page, Integer size) {
        SessionPageResponse response = sessionService.listSessions(page, size);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<GroupResponse> createGroup(UUID sessionId, GroupCreateRequest groupCreateRequest) {
        GroupResponse response = groupService.createGroup(sessionId, groupCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<List<GroupResponse>> listGroups(UUID sessionId) {
        List<GroupResponse> groups = groupService.listGroups(sessionId);
        return ResponseEntity.ok(groups);
    }

    @Override
    public ResponseEntity<Void> initializeBracket(UUID sessionId, InitializeBracketRequest initializeBracketRequest) {
        bracketService.initializeBracket(sessionId, initializeBracketRequest);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<SessionLeaderboardResponse> getLeaderboard(UUID sessionId) {
        SessionLeaderboardResponse response = leaderboardService.getLeaderboard(sessionId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<PlayerResultResponse>> getCompetitionResults(UUID sessionId) {
        List<PlayerResultResponse> results = resultsService.getCompetitionResults(sessionId);
        return ResponseEntity.ok(results);
    }
}

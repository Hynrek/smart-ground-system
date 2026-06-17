package ch.jp.shooting.api;

import ch.jp.shooting.service.CompetitionProgressService;
import ch.jp.shooting.service.CompetitionService;
import ch.jp.shooting.service.GroupService;
import ch.jp.shooting.service.SessionService;
import ch.jp.shooting.service.TiebreakerService;
import ch.jp.smartground.api.SessionApi;
import ch.jp.smartground.model.CompleteSerieRequest;
import ch.jp.smartground.model.CreateSessionRequest;
import ch.jp.smartground.model.GroupCreateRequest;
import ch.jp.smartground.model.GroupResponse;
import ch.jp.smartground.model.PatchMemberRequest;
import ch.jp.smartground.model.SessionPageResponse;
import ch.jp.smartground.model.SessionPlayerCreateRequest;
import ch.jp.smartground.model.SessionPlayerResponse;
import ch.jp.smartground.model.SessionProgressResponse;
import ch.jp.smartground.model.SessionResponse;
import ch.jp.smartground.model.SessionTiesResponse;
import ch.jp.smartground.model.StartTiebreakerRequest;
import ch.jp.smartground.model.SubmitTiebreakerResultsRequest;
import ch.jp.smartground.model.TiebreakerResponse;
import ch.jp.smartground.model.UpdateGroupRequest;
import ch.jp.smartground.model.UpdateSessionStatusRequest;
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
    private final CompetitionProgressService progressService;
    private final CompetitionService competitionService;
    private final TiebreakerService tiebreakerService;

    public SessionController(
            SessionService sessionService,
            GroupService groupService,
            CompetitionProgressService progressService,
            CompetitionService competitionService,
            TiebreakerService tiebreakerService) {
        this.sessionService = sessionService;
        this.groupService = groupService;
        this.progressService = progressService;
        this.competitionService = competitionService;
        this.tiebreakerService = tiebreakerService;
    }

    @Override
    public ResponseEntity<SessionResponse> createSession(CreateSessionRequest createSessionRequest) {
        SessionResponse response = sessionService.createSession(createSessionRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<SessionResponse> getSession(UUID sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @Override
    public ResponseEntity<SessionPageResponse> listSessions(
            String type, String status, Integer page, Integer size) {
        return ResponseEntity.ok(sessionService.listSessions(type, status, page, size));
    }

    @Override
    public ResponseEntity<Void> deleteSession(UUID sessionId) {
        sessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SessionResponse> patchSessionStatus(
            UUID sessionId, UpdateSessionStatusRequest updateSessionStatusRequest) {
        SessionResponse response = sessionService.patchSessionStatus(sessionId, updateSessionStatusRequest);
        if ("COMPLETED".equalsIgnoreCase(updateSessionStatusRequest.getStatus())) {
            try {
                competitionService.updateCareerStatsForSession(sessionId);
            } catch (Exception e) {
                // Career stats failure must not roll back the status transition
            }
        }
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<GroupResponse>> listGroups(UUID sessionId) {
        return ResponseEntity.ok(groupService.listGroups(sessionId));
    }

    @Override
    public ResponseEntity<GroupResponse> createGroup(UUID sessionId, GroupCreateRequest groupCreateRequest) {
        GroupResponse response = groupService.createGroup(sessionId, groupCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<GroupResponse> updateGroup(
            UUID sessionId, UUID groupId, UpdateGroupRequest updateGroupRequest) {
        return ResponseEntity.ok(sessionService.updateGroupApi(sessionId, groupId, updateGroupRequest));
    }

    @Override
    public ResponseEntity<Void> deleteGroup(UUID sessionId, UUID groupId) {
        sessionService.deleteGroup(sessionId, groupId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SessionPlayerResponse> addMember(
            UUID sessionId, UUID groupId, SessionPlayerCreateRequest sessionPlayerCreateRequest) {
        SessionPlayerResponse response =
                sessionService.addMemberApi(sessionId, groupId, sessionPlayerCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<Void> removeMember(UUID sessionId, UUID groupId, UUID memberId) {
        sessionService.removeMember(sessionId, groupId, memberId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SessionPlayerResponse> patchMember(
            UUID sessionId, UUID groupId, UUID memberId, PatchMemberRequest patchMemberRequest) {
        return ResponseEntity.ok(sessionService.patchMemberApi(sessionId, groupId, memberId, patchMemberRequest));
    }

    @Override
    public ResponseEntity<SessionProgressResponse> completeSerie(
            UUID sessionId, UUID groupId, UUID serieId, CompleteSerieRequest completeSerieRequest) {
        try {
            return ResponseEntity.ok(
                    progressService.completeSerie(sessionId, groupId, serieId, completeSerieRequest));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResponseEntity<SessionProgressResponse> getSessionProgress(UUID sessionId) {
        try {
            return ResponseEntity.ok(progressService.getProgress(sessionId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── Stechen (Tiebreaker) ──

    @Override
    public ResponseEntity<SessionTiesResponse> getSessionTies(UUID sessionId) {
        try {
            return ResponseEntity.ok(tiebreakerService.listTies(sessionId));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResponseEntity<List<TiebreakerResponse>> listTiebreakers(UUID sessionId) {
        try {
            return ResponseEntity.ok(tiebreakerService.listTiebreakers(sessionId));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResponseEntity<TiebreakerResponse> startTiebreaker(
            UUID sessionId, StartTiebreakerRequest startTiebreakerRequest) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(tiebreakerService.startTiebreaker(sessionId, startTiebreakerRequest));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ResponseEntity<SessionTiesResponse> submitTiebreakerResults(
            UUID sessionId, UUID tiebreakerId, SubmitTiebreakerResultsRequest submitTiebreakerResultsRequest) {
        try {
            return ResponseEntity.ok(
                    tiebreakerService.submitResults(sessionId, tiebreakerId, submitTiebreakerResultsRequest));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

package ch.jp.shooting.api;

import ch.jp.shooting.service.UserScoreService;
import ch.jp.smartground.api.ScoreApi;
import ch.jp.smartground.model.LeaderboardResponse;
import ch.jp.smartground.model.ScoreContext;
import ch.jp.smartground.model.UserScorePage;
import ch.jp.smartground.model.UserScoreSummary;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

// Implementiert ScoreApi (generierte Schnittstelle)
@RestController
@NullMarked
public class ScoreController implements ScoreApi {

    private final UserScoreService userScoreService;

    public ScoreController(UserScoreService userScoreService) {
        this.userScoreService = userScoreService;
    }

    @Override
    public ResponseEntity<UserScorePage> listMyScores(ScoreContext context, UUID serieId,
            OffsetDateTime from, OffsetDateTime to, Integer page, Integer size) {
        return ResponseEntity.ok(userScoreService.listMyScores(
            context != null ? context.getValue() : null, serieId, from, to,
            page != null ? page : 0, size != null ? size : 20));
    }

    @Override
    public ResponseEntity<UserScoreSummary> getMyScoreSummary() {
        return ResponseEntity.ok(userScoreService.getMyScoreSummary());
    }

    @Override
    public ResponseEntity<LeaderboardResponse> getScoreLeaderboard(UUID serieId, UUID rangeId,
            ScoreContext context, String metric, Integer limit, OffsetDateTime from) {
        return ResponseEntity.ok(userScoreService.getLeaderboard(
            serieId, rangeId, context != null ? context.getValue() : null,
            metric != null ? metric : "best",
            limit != null ? Math.min(limit, 100) : 10, from));
    }
}

package ch.jp.shooting.api;

import ch.jp.shooting.service.UserScoreService;
import ch.jp.smartground.api.ScoreApi;
import ch.jp.smartground.model.LeaderboardResponse;
import ch.jp.smartground.model.PasseScoreGroup;
import ch.jp.smartground.model.ScoreContext;
import ch.jp.smartground.model.ScoreKind;
import ch.jp.smartground.model.UserScorePage;
import ch.jp.smartground.model.UserScoreSummary;
import ch.jp.smartground.model.WettkampfScoreGroup;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
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
    public ResponseEntity<UserScorePage> listMyScores(ScoreContext context, ScoreKind kind, UUID serieId,
            OffsetDateTime from, OffsetDateTime to, Integer page, Integer size) {
        return ResponseEntity.ok(userScoreService.listMyScores(
            context != null ? context.getValue() : null, kind != null ? kind.getValue() : null,
            serieId, from, to,
            page != null ? page : 0, size != null ? size : 20));
    }

    @Override
    public ResponseEntity<UserScoreSummary> getMyScoreSummary() {
        return ResponseEntity.ok(userScoreService.getMyScoreSummary());
    }

    @Override
    public ResponseEntity<List<PasseScoreGroup>> listMyPassen() {
        return ResponseEntity.ok(userScoreService.listMyPassen());
    }

    @Override
    public ResponseEntity<List<WettkampfScoreGroup>> listMyWettkaempfe() {
        return ResponseEntity.ok(userScoreService.listMyWettkaempfe());
    }

    @Override
    public ResponseEntity<LeaderboardResponse> getScoreLeaderboard(UUID serieId, UUID rangeId,
            ScoreContext context, ScoreKind kind, String metric, Integer limit, OffsetDateTime from) {
        return ResponseEntity.ok(userScoreService.getLeaderboard(
            serieId, rangeId, context != null ? context.getValue() : null,
            kind != null ? kind.getValue() : null,
            metric != null ? metric : "best",
            limit != null ? Math.min(limit, 100) : 10, from));
    }
}

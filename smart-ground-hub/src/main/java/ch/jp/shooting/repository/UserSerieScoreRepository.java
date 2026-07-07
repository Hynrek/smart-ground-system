package ch.jp.shooting.repository;

import ch.jp.shooting.model.UserSerieScore;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NullMarked
public interface UserSerieScoreRepository extends JpaRepository<UserSerieScore, UUID> {

    Optional<UserSerieScore> findBySourceIdAndUserId(UUID sourceId, UUID userId);

    List<UserSerieScore> findBySourceId(UUID sourceId);

    List<UserSerieScore> findByUserIdOrderByCompletedAtDesc(UUID userId);

    List<UserSerieScore> findByUserIdAndKindOrderByCompletedAtDesc(UUID userId, String kind);

    // from/to sind im Service immer gesetzt (EPOCH/now-Defaults) — vermeidet
    // typisierte Null-Parameter auf Timestamps; context/serieId bleiben optional.
    @Query("select s from UserSerieScore s where s.userId = :userId"
        + " and (:context is null or s.context = :context)"
        + " and (:serieId is null or s.serieId = :serieId)"
        + " and s.completedAt >= :from and s.completedAt <= :to"
        + " order by s.completedAt desc")
    Page<UserSerieScore> findFiltered(@Param("userId") UUID userId,
                                      @Param("context") @Nullable String context,
                                      @Param("serieId") @Nullable UUID serieId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to,
                                      Pageable pageable);

    @Query("select s from UserSerieScore s where"
        + " (:context is null or s.context = :context)"
        + " and (:serieId is null or s.serieId = :serieId)"
        + " and (:rangeId is null or s.rangeId = :rangeId)"
        + " and s.completedAt >= :from")
    List<UserSerieScore> findForLeaderboard(@Param("context") @Nullable String context,
                                            @Param("serieId") @Nullable UUID serieId,
                                            @Param("rangeId") @Nullable UUID rangeId,
                                            @Param("from") Instant from);
}

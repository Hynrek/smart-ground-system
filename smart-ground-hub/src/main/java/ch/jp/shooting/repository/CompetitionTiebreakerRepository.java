package ch.jp.shooting.repository;

import ch.jp.shooting.model.CompetitionTiebreaker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompetitionTiebreakerRepository extends JpaRepository<CompetitionTiebreaker, UUID> {

    List<CompetitionTiebreaker> findBySessionId(UUID sessionId);

    List<CompetitionTiebreaker> findBySessionIdAndTieGroupIdOrderByRoundNumberAsc(
            UUID sessionId, UUID tieGroupId);
}

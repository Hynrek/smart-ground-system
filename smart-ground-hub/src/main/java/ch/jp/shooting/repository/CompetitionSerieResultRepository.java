package ch.jp.shooting.repository;

import ch.jp.shooting.model.CompetitionSerieResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompetitionSerieResultRepository extends JpaRepository<CompetitionSerieResult, UUID> {

    List<CompetitionSerieResult> findBySessionId(UUID sessionId);

    List<CompetitionSerieResult> findBySessionIdAndGroupId(UUID sessionId, UUID groupId);

    boolean existsBySessionIdAndGroupIdAndPasseIndexAndSerieId(
        UUID sessionId, UUID groupId, int passeIndex, UUID serieId
    );

    Optional<CompetitionSerieResult> findBySessionIdAndGroupIdAndPasseIndexAndSerieId(
        UUID sessionId, UUID groupId, int passeIndex, UUID serieId
    );
}

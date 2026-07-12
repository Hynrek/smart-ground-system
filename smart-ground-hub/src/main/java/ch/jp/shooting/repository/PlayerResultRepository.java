package ch.jp.shooting.repository;

import ch.jp.shooting.model.PlayerResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlayerResultRepository extends JpaRepository<PlayerResult, UUID> {
    List<PlayerResult> findBySessionId(UUID sessionId);
    Optional<PlayerResult> findBySessionIdAndPlayerId(UUID sessionId, UUID playerId);
}

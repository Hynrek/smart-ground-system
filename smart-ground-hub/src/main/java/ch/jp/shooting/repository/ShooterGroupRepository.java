package ch.jp.shooting.repository;

import ch.jp.shooting.model.ShooterGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShooterGroupRepository extends JpaRepository<ShooterGroup, UUID> {
    List<ShooterGroup> findBySessionId(UUID sessionId);
    Optional<ShooterGroup> findByIdAndSessionId(UUID id, UUID sessionId);
}

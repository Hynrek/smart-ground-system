package ch.jp.shooting.repository;

import ch.jp.shooting.model.SessionPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionPlayerRepository extends JpaRepository<SessionPlayer, UUID> {
    List<SessionPlayer> findByGroupId(UUID groupId);
}

package ch.jp.shooting.repository;

import ch.jp.shooting.model.LiveSession;
import ch.jp.shooting.model.SessionStatus;
import ch.jp.shooting.model.SessionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSession, UUID> {
    Page<LiveSession> findByStatus(SessionStatus status, Pageable pageable);
    Page<LiveSession> findByType(SessionType type, Pageable pageable);
    Page<LiveSession> findByTypeAndStatus(SessionType type, SessionStatus status, Pageable pageable);
    List<LiveSession> findByStatusOrderByCreatedAtDesc(SessionStatus status);
}

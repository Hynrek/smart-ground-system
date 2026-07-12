package ch.jp.shooting.repository;

import ch.jp.shooting.model.SessionTemplate;
import ch.jp.shooting.model.SessionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionTemplateRepository extends JpaRepository<SessionTemplate, UUID> {
    Optional<SessionTemplate> findByName(String name);
    Page<SessionTemplate> findByType(SessionType type, Pageable pageable);
}

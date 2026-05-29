package ch.jp.shooting.repository.auth;

import ch.jp.shooting.model.auth.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionEntityRepository extends JpaRepository<PermissionEntity, UUID> {
    Optional<PermissionEntity> findByAction(String action);
}

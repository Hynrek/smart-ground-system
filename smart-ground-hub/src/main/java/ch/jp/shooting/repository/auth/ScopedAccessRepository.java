package ch.jp.shooting.repository.auth;

import ch.jp.shooting.model.auth.ScopedAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScopedAccessRepository extends JpaRepository<ScopedAccess, UUID> {
    List<ScopedAccess> findByUserIdAndRoleName(UUID userId, String roleName);

    @Query("SELECT sa FROM ScopedAccess sa WHERE sa.user.id = ?1 AND sa.role.name = ?2 AND sa.scopeType = ?3 AND sa.scopeId = ?4")
    List<ScopedAccess> findByUserIdAndRoleNameAndScopeId(UUID userId, String roleName, String scopeType, UUID scopeId);
}

package ch.jp.shooting.repository.auth;

import ch.jp.shooting.model.auth.UserRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository
        extends JpaRepository<UserRoleEntity, UserRoleEntity.UserRoleId> {

    /**
     * Lädt alle Berechtigungs-Actions eines Benutzers über alle zugewiesenen Rollen.
     */
    @Query("""
        SELECT DISTINCT p.action
        FROM UserRoleEntity ur
        JOIN ur.role r
        JOIN r.permissions p
        WHERE ur.user.id = :userId
        """)
    List<String> findPermissionActionsByUserId(@Param("userId") UUID userId);
}

package ch.jp.shooting.service.auth;

import ch.jp.shooting.exception.ForbiddenException;
import ch.jp.shooting.model.auth.Permission;
import ch.jp.shooting.repository.auth.UserRoleRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@NullMarked
public class PermissionService {

    private final UserRoleRepository userRoleRepository;

    public PermissionService(UserRoleRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * Gibt alle Berechtigungen des Benutzers zurück (über alle zugewiesenen Rollen).
     */
    @Transactional(readOnly = true)
    public Set<Permission> getPermissions(UUID userId) {
        List<String> actions = userRoleRepository.findPermissionActionsByUserId(userId);
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        for (String action : actions) {
            try {
                result.add(Permission.valueOf(action.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Unbekannte DB-Aktion – ignorieren (vorwärtskompatibel)
            }
        }
        return result;
    }

    /**
     * Wirft ForbiddenException wenn der Benutzer die angeforderte Berechtigung nicht hat.
     *
     * Beispiel: permissionService.require(currentUserId, Permission.MANAGE_SERIES);
     */
    public void require(UUID userId, Permission permission) {
        if (!getPermissions(userId).contains(permission)) {
            throw new ForbiddenException("Fehlende Berechtigung: " + permission);
        }
    }

    public boolean has(UUID userId, Permission permission) {
        return getPermissions(userId).contains(permission);
    }
}

package ch.jp.shooting.config;

import ch.jp.shooting.model.auth.Permission;
import ch.jp.shooting.model.auth.PermissionEntity;
import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.repository.auth.PermissionEntityRepository;
import ch.jp.shooting.repository.auth.RoleRepository;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Initialisiert beim Start die 8 Standardrollen und 13 Berechtigungen
 * gemäss der Berechtigungsmatrix. Wird nur ausgeführt wenn nötig (idempotent).
 */
@Component
@NullMarked
public class RoleDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleDataInitializer.class);

    private final RoleRepository roleRepository;
    private final PermissionEntityRepository permissionRepository;

    public RoleDataInitializer(RoleRepository roleRepository,
                               PermissionEntityRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPermissions();
        seedRoles();
        log.info("Rollen und Berechtigungen initialisiert.");
    }

    private void seedPermissions() {
        for (Permission p : Permission.values()) {
            if (permissionRepository.findByAction(p.action()).isEmpty()) {
                permissionRepository.save(new PermissionEntity(p.action(), null));
            }
        }
    }

    private void seedRoles() {
        // Alle Berechtigungen vorladen
        Map<String, PermissionEntity> permMap = new HashMap<>();
        permissionRepository.findAll().forEach(p -> permMap.put(p.getAction(), p));

        seedRole("ADMIN", "Vollzugriff auf alle Funktionen", permMap,
            Permission.values()); // alle

        seedRole("SMART_GROUND_OWNER", "Eigentümer der Anlage – identische Rechte wie Admin (vorerst)", permMap,
            Permission.values()); // alle

        seedRole("RANGE_OPERATOR", "Tages-Betrieb der Anlage", permMap,
            Permission.OPERATE_RANGE,
            Permission.START_COMPETITION,
            Permission.MANAGE_SERIES,
            Permission.RESERVE_REMOTE,
            Permission.VIEW_REMOTE,
            Permission.PLAY_SERIES,
            Permission.PLAY_COMPETITION);

        seedRole("TRAINING_MASTER", "Leitung Trainingseinheiten", permMap,
            Permission.START_TRAINING,
            Permission.MANAGE_SERIES,
            Permission.RESERVE_REMOTE,
            Permission.VIEW_REMOTE,
            Permission.PLAY_SERIES);

        seedRole("COMPETITION_MASTER", "Leitung Wettkämpfe", permMap,
            Permission.MANAGE_COMPETITIONS,
            Permission.START_COMPETITION,
            Permission.MANAGE_SERIES,
            Permission.RESERVE_REMOTE,
            Permission.VIEW_REMOTE,
            Permission.PLAY_COMPETITION);

        seedRole("COMPETITION_MEMBER", "Dauerhaftes Minimalaccount für Wettkämpfe", permMap,
            Permission.VIEW_REMOTE,
            Permission.PLAY_COMPETITION);

        seedRole("SHOOTER", "Standardbenutzer, Teilnahme an Training", permMap,
            Permission.RESERVE_REMOTE,
            Permission.VIEW_REMOTE,
            Permission.PLAY_SERIES);

        seedRole("GUEST", "Nur Lesezugriff auf Remote-Ansicht", permMap,
            Permission.VIEW_REMOTE);
    }

    private void seedRole(String name, String description,
                          Map<String, PermissionEntity> permMap,
                          Permission... permissions) {
        if (roleRepository.findByName(name).isPresent()) {
            return;
        }
        Role role = new Role(name, description);
        Set<PermissionEntity> perms = new HashSet<>();
        for (Permission p : permissions) {
            PermissionEntity entity = permMap.get(p.action());
            if (entity != null) {
                perms.add(entity);
            }
        }
        role.setPermissions(perms);
        roleRepository.save(role);
        log.debug("Rolle '{}' angelegt mit {} Berechtigungen.", name, perms.size());
    }
}

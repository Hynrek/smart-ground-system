package ch.jp.shooting.config;

import ch.jp.shooting.model.*;
import ch.jp.shooting.model.auth.Permission;
import ch.jp.shooting.model.auth.PermissionEntity;
import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.model.auth.UserRoleEntity;
import ch.jp.shooting.repository.*;
import ch.jp.shooting.repository.auth.PermissionEntityRepository;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.repository.auth.UserRoleRepository;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@NullMarked
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final FirmwareConfigRepository firmwareConfigRepository;
    private final SignalTypeRepository signalTypeRepository;
    private final DeviceTypeGroupRepository deviceTypeGroupRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionEntityRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            FirmwareConfigRepository firmwareConfigRepository,
            SignalTypeRepository signalTypeRepository,
            DeviceTypeGroupRepository deviceTypeGroupRepository,
            DeviceTypeRepository deviceTypeRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionEntityRepository permissionRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder) {
        this.firmwareConfigRepository = firmwareConfigRepository;
        this.signalTypeRepository = signalTypeRepository;
        this.deviceTypeGroupRepository = deviceTypeGroupRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        seedUsers();
        seedGroups();
        seedFirmware("0.6", "pico2w");
    }

    private void seedGroups() {
        deviceTypeGroupRepository.findByName("Wurfmaschine")
            .orElseGet(() -> {
                log.info("Seed: DeviceTypeGroup 'Wurfmaschine' erstellt.");
                return deviceTypeGroupRepository.save(new DeviceTypeGroup("Wurfmaschine"));
            });

        deviceTypeGroupRepository.findByName("LED")
            .orElseGet(() -> {
                log.info("Seed: DeviceTypeGroup 'LED' erstellt.");
                return deviceTypeGroupRepository.save(new DeviceTypeGroup("LED"));
            });
    }

    private void seedFirmware(String version, String boxType) {
        if (firmwareConfigRepository.findByVersionAndBoxType(version, boxType).isPresent()) {
            log.debug("Seed: FirmwareConfig {}/{} bereits vorhanden – übersprungen.", version, boxType);
            return;
        }

        FirmwareConfig firmware = firmwareConfigRepository.save(new FirmwareConfig(version, boxType));
        log.info("Seed: FirmwareConfig {}/{} erstellt.", version, boxType);

        // GPIO OUTPUT – Wurfmaschine trigger (Standardpin 15, überschreibbar via Device.pinConfig)
        SignalType gpioSignal = signalTypeRepository.save(
            new SignalType(firmware, CommunicationDirection.OUTPUT, DeviceKind.GPIO, "15"));

        // LED OUTPUT – Statusanzeige
        SignalType ledSignal = signalTypeRepository.save(
            new SignalType(firmware, CommunicationDirection.OUTPUT, DeviceKind.LED, "ON"));

        log.info("Seed: SignalTypes für FirmwareConfig {}/{} erstellt.", version, boxType);

        DeviceTypeGroup wurfmaschineGroup = deviceTypeGroupRepository.findByName("Wurfmaschine")
            .orElseThrow(() -> new IllegalStateException("DeviceTypeGroup 'Wurfmaschine' fehlt nach Seed"));
        DeviceTypeGroup ledGroup = deviceTypeGroupRepository.findByName("LED")
            .orElseThrow(() -> new IllegalStateException("DeviceTypeGroup 'LED' fehlt nach Seed"));

        DeviceType werfer = new DeviceType();
        werfer.setName("Werfer");
        werfer.setSignalType(gpioSignal);
        werfer.setGroup(wurfmaschineGroup);
        werfer.setSignalDurationMs(500);
        deviceTypeRepository.save(werfer);

        DeviceType led = new DeviceType();
        led.setName("LED");
        led.setSignalType(ledSignal);
        led.setGroup(ledGroup);
        led.setSignalDurationMs(2000);
        deviceTypeRepository.save(led);

        log.info("Seed: DeviceTypes für FirmwareConfig {}/{} erstellt.", version, boxType);
    }

    private void seedUsers() {
        if (userRepository.findByEmail("admin@smartground.local").isPresent()) {
            log.debug("Seed: Admin user bereits vorhanden – übersprungen.");
            return;
        }

        // Alle Berechtigungen als PermissionEntity anlegen (falls noch nicht vorhanden)
        for (Permission p : Permission.values()) {
            permissionRepository.findByAction(p.action())
                .orElseGet(() -> permissionRepository.save(new PermissionEntity(p.action(), p.name())));
        }

        // ADMIN-Rolle mit allen Berechtigungen
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role r = new Role("ADMIN", "System administrator with full access");
            roleRepository.save(r);
            for (Permission p : Permission.values()) {
                PermissionEntity pe = permissionRepository.findByAction(p.action())
                    .orElseThrow(() -> new IllegalStateException("PermissionEntity fehlt: " + p.action()));
                r.getPermissions().add(pe);
            }
            return roleRepository.save(r);
        });

        // SHOOTER-Rolle mit eingeschränkten Berechtigungen
        Set<Permission> shooterPermissions = Set.of(
            Permission.VIEW_REMOTE,
            Permission.PLAY_SERIES,
            Permission.PLAY_COMPETITION,
            Permission.START_TRAINING,
            Permission.RESERVE_REMOTE
        );
        Role shooterRole = roleRepository.findByName("SHOOTER").orElseGet(() -> {
            Role r = new Role("SHOOTER", "Regular shooter participant");
            roleRepository.save(r);
            for (Permission p : shooterPermissions) {
                PermissionEntity pe = permissionRepository.findByAction(p.action())
                    .orElseThrow(() -> new IllegalStateException("PermissionEntity fehlt: " + p.action()));
                r.getPermissions().add(pe);
            }
            return roleRepository.save(r);
        });

        // Admin-Benutzer anlegen und Rolle per UserRoleEntity zuweisen
        User admin = new User("admin@smartground.local", "Admin", "User");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setStatus(User.UserStatus.ACTIVE);
        admin.setEmailBestaetigt(true);
        admin.setSprache("DE");
        User savedAdmin = userRepository.save(admin);
        userRoleRepository.save(new UserRoleEntity(savedAdmin, adminRole, null));
        log.info("Seed: Admin user 'admin@smartground.local' mit ADMIN-Rolle erstellt.");

        // Shooter-Benutzer anlegen
        if (userRepository.findByEmail("user@smartground.local").isPresent()) {
            log.debug("Seed: Shooter user bereits vorhanden – übersprungen.");
            return;
        }
        User shooter = new User("user@smartground.local", "Test", "Schütze");
        shooter.setPasswordHash(passwordEncoder.encode("user"));
        shooter.setStatus(User.UserStatus.ACTIVE);
        shooter.setEmailBestaetigt(true);
        shooter.setSprache("DE");
        User savedShooter = userRepository.save(shooter);
        userRoleRepository.save(new UserRoleEntity(savedShooter, shooterRole, null));
        log.info("Seed: Shooter user 'user@smartground.local' mit SHOOTER-Rolle erstellt.");
    }
}

package ch.jp.shooting.config;

import ch.jp.shooting.model.*;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.repository.*;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            FirmwareConfigRepository firmwareConfigRepository,
            SignalTypeRepository signalTypeRepository,
            DeviceTypeGroupRepository deviceTypeGroupRepository,
            DeviceTypeRepository deviceTypeRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.firmwareConfigRepository = firmwareConfigRepository;
        this.signalTypeRepository = signalTypeRepository;
        this.deviceTypeGroupRepository = deviceTypeGroupRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
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
        // Check if admin user already exists
        if (userRepository.findByEmail("admin@smartground.local").isPresent()) {
            log.debug("Seed: Admin user bereits vorhanden – übersprungen.");
            return;
        }

        // Create admin user
        User admin = new User("admin@smartground.local", "Admin", "User");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setStatus(User.UserStatus.ACTIVE);
        admin.setEmailBestaetigt(true);
        admin.setSprache("DE");

        User savedAdmin = userRepository.save(admin);
        log.info("Seed: Admin user mit Email 'admin@smartground.local' erstellt.");

        // Assign ADMIN role (create if not exists)
        Role adminRole = roleRepository.findByName("ADMIN")
            .orElseGet(() -> {
                Role newRole = new Role("ADMIN", "System administrator with full access");
                return roleRepository.save(newRole);
            });

        if (!savedAdmin.getRoles().contains(adminRole)) {
            savedAdmin.getRoles().add(adminRole);
            userRepository.save(savedAdmin);
            log.info("Seed: ADMIN role dem Admin user zugewiesen.");
        }

        // Create regular shooter user
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
        log.info("Seed: Shooter user mit Email 'user@smartground.local' erstellt.");

        // Assign SHOOTER role (create if not exists)
        Role shooterRole = roleRepository.findByName("SHOOTER")
            .orElseGet(() -> {
                Role newRole = new Role("SHOOTER", "Regular shooter participant");
                return roleRepository.save(newRole);
            });

        if (!savedShooter.getRoles().contains(shooterRole)) {
            savedShooter.getRoles().add(shooterRole);
            userRepository.save(savedShooter);
            log.info("Seed: SHOOTER role dem Shooter user zugewiesen.");
        }
    }
}

package ch.jp.shooting.config;

import ch.jp.shooting.model.*;
import ch.jp.shooting.repository.*;
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
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            FirmwareConfigRepository firmwareConfigRepository,
            SignalTypeRepository signalTypeRepository,
            DeviceTypeGroupRepository deviceTypeGroupRepository,
            DeviceTypeRepository deviceTypeRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.firmwareConfigRepository = firmwareConfigRepository;
        this.signalTypeRepository = signalTypeRepository;
        this.deviceTypeGroupRepository = deviceTypeGroupRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.userRepository = userRepository;
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
        userRepository.findByUsername("admin")
            .orElseGet(() -> {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole(UserRole.ADMIN);
                log.info("Seed: Admin user erstellt.");
                return userRepository.save(admin);
            });
    }
}

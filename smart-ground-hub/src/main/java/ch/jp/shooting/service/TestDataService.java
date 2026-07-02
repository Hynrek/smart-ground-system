package ch.jp.shooting.service;

import ch.jp.shooting.exception.ConflictException;
import ch.jp.shooting.model.*;
import ch.jp.shooting.model.auth.Role;
import ch.jp.shooting.model.auth.User;
import ch.jp.shooting.model.auth.UserRoleEntity;
import ch.jp.shooting.repository.*;
import ch.jp.shooting.repository.auth.RoleRepository;
import ch.jp.shooting.repository.auth.UserRepository;
import ch.jp.shooting.repository.auth.UserRoleRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

// Admin-only Dev-Tooling zum Anlegen von Testdaten (Benutzer, Ranges, SmartBoxes).
@Service
@NullMarked
public class TestDataService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RangeRepository rangeRepository;
    private final SmartBoxRepository smartBoxRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final DeviceTypeGroupRepository deviceTypeGroupRepository;
    private final FirmwareConfigRepository firmwareConfigRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            RangeRepository rangeRepository,
            SmartBoxRepository smartBoxRepository,
            DeviceRepository deviceRepository,
            DeviceTypeRepository deviceTypeRepository,
            DeviceTypeGroupRepository deviceTypeGroupRepository,
            FirmwareConfigRepository firmwareConfigRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.rangeRepository = rangeRepository;
        this.smartBoxRepository = smartBoxRepository;
        this.deviceRepository = deviceRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.deviceTypeGroupRepository = deviceTypeGroupRepository;
        this.firmwareConfigRepository = firmwareConfigRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Legt einen Test-Benutzer an: Benutzername = Passwort = credential, E-Mail abgeleitet.
    @Transactional
    public User createTestUser(String credential) {
        String cred = credential == null ? "" : credential.trim();
        if (cred.isEmpty() || cred.contains("@")) {
            throw new IllegalArgumentException("credential must be non-blank and must not contain '@'");
        }
        String email = cred.toLowerCase() + "@test.local";
        if (userRepository.findByUsernameLower(cred.toLowerCase()).isPresent()) {
            throw new ConflictException("Username already in use: " + cred);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email already in use: " + email);
        }
        Role shooter = roleRepository.findByName("SHOOTER")
                .orElseThrow(() -> new IllegalStateException("SHOOTER role missing – seed did not run"));

        User user = new User(email, cred, "Test");
        user.setUsername(cred);
        user.setPasswordHash(passwordEncoder.encode(cred));
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailBestaetigt(true);
        user.setSprache("DE");
        User saved = userRepository.save(user);
        userRoleRepository.save(new UserRoleEntity(saved, shooter, null));
        return saved;
    }

    // Standard-Testplätze; idempotent nach Name.
    private static final List<String> TEST_RANGE_NAMES =
            List.of("Vorderlader", "Trapstand", "Rollhase", "Kippreh");

    public record SeededRange(Range range, boolean created) {}

    @Transactional
    public List<SeededRange> seedRanges() {
        List<SeededRange> result = new ArrayList<>();
        for (String name : TEST_RANGE_NAMES) {
            Range existing = rangeRepository.findByName(name).orElse(null);
            if (existing != null) {
                result.add(new SeededRange(existing, false));
            } else {
                Range range = new Range();
                range.setName(name);
                result.add(new SeededRange(rangeRepository.save(range), true));
            }
        }
        return result;
    }

    // Legt eine Mock-SmartBox mit N Geräten (Typ "Werfer") an, keiner Range zugeordnet.
    @Transactional
    public SmartBox createMockSmartBox(int deviceCount, @Nullable String alias) {
        if (deviceCount < 1 || deviceCount > 50) {
            throw new IllegalArgumentException("deviceCount must be between 1 and 50");
        }
        FirmwareConfig firmware = firmwareConfigRepository
                .findByVersionAndBoxType("0.6", "xiao-esp32s3")
                .orElseThrow(() -> new IllegalStateException("FirmwareConfig 0.6/xiao-esp32s3 missing – seed did not run"));
        DeviceTypeGroup werferGroup = deviceTypeGroupRepository.findByName("Wurfmaschine")
                .orElseThrow(() -> new IllegalStateException("DeviceTypeGroup 'Wurfmaschine' missing – seed did not run"));
        // DeviceType eindeutig über (Gruppe, FirmwareConfig) auflösen – der Name allein ist nicht eindeutig
        // (pro FirmwareConfig existiert ein eigener "Werfer"-Typ, z.B. pico2w vs. xiao-esp32s3).
        DeviceType werfer = deviceTypeRepository
                .findByGroupIdAndSignalType_FirmwareConfigId(werferGroup.getId(), firmware.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "DeviceType für Gruppe 'Wurfmaschine' und FirmwareConfig 0.6/xiao-esp32s3 fehlt – seed did not run"));

        SmartBox box = new SmartBox();
        box.setMacAddress(generateUniqueMac());
        box.setStatus(SmartBoxStates.OFFLINE);
        box.setFirmwareConfig(firmware);
        box.setAppVersion("0.6");
        if (alias != null && !alias.isBlank()) {
            box.setAlias(alias.trim());
        }
        SmartBox savedBox = smartBoxRepository.save(box);

        for (int i = 1; i <= deviceCount; i++) {
            Device device = new Device();
            device.setSmartBox(savedBox);
            device.setDeviceTypeGroup(werferGroup);
            device.setDeviceType(werfer);
            device.setAlias("Werfer " + i);
            deviceRepository.save(device);
        }
        return savedBox;
    }

    private String generateUniqueMac() {
        for (int attempt = 0; attempt < 100; attempt++) {
            byte[] mac = new byte[6];
            ThreadLocalRandom.current().nextBytes(mac);
            mac[0] = 0x02; // lokal verwaltete Unicast-Adresse
            String candidate = String.format(Locale.ROOT, "%02x:%02x:%02x:%02x:%02x:%02x",
                    mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
            if (smartBoxRepository.findByMacAddress(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique MAC after 100 attempts");
    }
}

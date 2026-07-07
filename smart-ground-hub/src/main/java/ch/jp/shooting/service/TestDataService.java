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
    private final RangePositionRepository positionRepository;
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
            RangePositionRepository positionRepository,
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
        this.positionRepository = positionRepository;
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

    // Positions-Labels für ein "ready to go"-Setup: 8 Positionen A–H je Platz.
    private static final List<String> POSITION_LABELS =
            List.of("A", "B", "C", "D", "E", "F", "G", "H");

    private static final int DEVICES_PER_BOX = 4;

    public record SeededRange(
            Range range,
            boolean created,
            int positionsCreated,
            int boxesCreated,
            int devicesAssigned) {}

    // Legt die 4 Standard-Ranges an (idempotent nach Name) und baut für jede neue Range
    // (ohne bestehende Positionen) 8 Positionen + 2 Mock-SmartBoxes à 4 Geräte und weist
    // alle 8 Geräte den 8 Positionen 1:1 zu. Ranges mit bereits vorhandenen Positionen
    // werden nicht angetastet (auch nicht bei einer nur teilweise befüllten Range).
    @Transactional
    public List<SeededRange> seedRanges() {
        List<SeededRange> result = new ArrayList<>();
        for (String name : TEST_RANGE_NAMES) {
            Range existing = rangeRepository.findByName(name).orElse(null);
            boolean created;
            Range range;
            if (existing != null) {
                range = existing;
                created = false;
            } else {
                Range fresh = new Range();
                fresh.setName(name);
                range = rangeRepository.save(fresh);
                created = true;
            }

            if (positionRepository.countByRangeId(range.getId()) > 0) {
                result.add(new SeededRange(range, created, 0, 0, 0));
                continue;
            }

            List<RangePosition> positions = createPositions(range);
            WerferDeviceTypeContext ctx = resolveWerferDeviceType();
            int boxesCreated = buildBoxesAndAssignDevices(range, positions, ctx);
            result.add(new SeededRange(range, created, positions.size(), boxesCreated, positions.size()));
        }
        return result;
    }

    private List<RangePosition> createPositions(Range range) {
        List<RangePosition> positions = new ArrayList<>();
        for (int i = 0; i < POSITION_LABELS.size(); i++) {
            RangePosition position = new RangePosition();
            position.setRange(range);
            position.setLabel(POSITION_LABELS.get(i));
            position.setSortOrder(i);
            positions.add(positionRepository.save(position));
        }
        return positions;
    }

    // Baut 2 Mock-SmartBoxes à 4 Geräte und weist Box 1 → Positionen A–D, Box 2 → Positionen E–H zu.
    private int buildBoxesAndAssignDevices(Range range, List<RangePosition> positions, WerferDeviceTypeContext ctx) {
        int boxCount = positions.size() / DEVICES_PER_BOX;
        for (int b = 0; b < boxCount; b++) {
            SmartBox box = new SmartBox();
            box.setMacAddress(generateUniqueMac());
            box.setStatus(SmartBoxStates.OFFLINE);
            box.setFirmwareConfig(ctx.firmware());
            box.setAppVersion("0.6");
            box.setAlias(range.getName() + " Box " + (b + 1));
            SmartBox savedBox = smartBoxRepository.save(box);

            for (int d = 0; d < DEVICES_PER_BOX; d++) {
                RangePosition position = positions.get(b * DEVICES_PER_BOX + d);
                Device device = new Device();
                device.setSmartBox(savedBox);
                device.setDeviceTypeGroup(ctx.group());
                device.setDeviceType(ctx.deviceType());
                device.setAlias("Werfer " + position.getLabel());
                device.setRange(range);
                device.setRangePosition(position);
                Device savedDevice = deviceRepository.save(device);

                position.setDevice(savedDevice);
                positionRepository.save(position);
            }
        }
        return boxCount;
    }

    private record WerferDeviceTypeContext(FirmwareConfig firmware, DeviceTypeGroup group, DeviceType deviceType) {}

    // Löst FirmwareConfig/DeviceTypeGroup/DeviceType für den generischen "Werfer"-Mock auf.
    private WerferDeviceTypeContext resolveWerferDeviceType() {
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
        return new WerferDeviceTypeContext(firmware, werferGroup, werfer);
    }

    // Legt eine Mock-SmartBox mit N Geräten (Typ "Werfer") an, keiner Range zugeordnet.
    @Transactional
    public SmartBox createMockSmartBox(int deviceCount, @Nullable String alias) {
        if (deviceCount < 1 || deviceCount > 50) {
            throw new IllegalArgumentException("deviceCount must be between 1 and 50");
        }
        WerferDeviceTypeContext ctx = resolveWerferDeviceType();

        SmartBox box = new SmartBox();
        box.setMacAddress(generateUniqueMac());
        box.setStatus(SmartBoxStates.OFFLINE);
        box.setFirmwareConfig(ctx.firmware());
        box.setAppVersion("0.6");
        if (alias != null && !alias.isBlank()) {
            box.setAlias(alias.trim());
        }
        SmartBox savedBox = smartBoxRepository.save(box);

        for (int i = 1; i <= deviceCount; i++) {
            Device device = new Device();
            device.setSmartBox(savedBox);
            device.setDeviceTypeGroup(ctx.group());
            device.setDeviceType(ctx.deviceType());
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
